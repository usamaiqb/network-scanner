package com.networkscanner.app.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.net.wifi.WifiManager
import com.networkscanner.app.data.*
import com.networkscanner.app.util.ArpReader
import com.networkscanner.app.util.MacVendorLookup
import com.networkscanner.app.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.URL
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Network scanner implementation with multiple discovery methods.
 */
class NetworkScanner(private val context: Context) {

    companion object {
        private const val PING_TIMEOUT_MS = 1000  // 1 second timeout for ping
        private const val PING_THREADS = 50       // Max concurrent ping coroutines
        private const val MDNS_TIMEOUT_MS = 2000L // Reduced for speed
        private const val SSDP_TIMEOUT_MS = 2500L // MX: 2s + 500ms buffer for slow devices
        private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900

        // Deep scan constants (quick scan: curated/common port list)
        private const val PORT_TIMEOUT_MS = 500
        private const val PORT_THREADS = 20

        // Full scan (all 65,535 ports): higher concurrency and a shorter
        // per-port timeout, since most ports on a LAN host are closed and
        // RST/respond fast — this keeps the full sweep to a few minutes
        // instead of ~27 min at the quick-scan settings.
        private const val FULL_SCAN_PORT_TIMEOUT_MS = 300
        private const val FULL_SCAN_PORT_THREADS = 128

        private const val BANNER_TIMEOUT_MS = 2000
        private const val BANNER_THREADS = 10

        // Throttle UI updates to avoid flooding
        private const val UI_UPDATE_INTERVAL_MS = 200L

        // Precompiled regex for parsing ping RTT
        private val PING_RTT_PATTERN = Regex("""time[=<]([\d.]+)\s*ms""")
    }

    private val scanJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + scanJob)
    private val discoveredDevices = ConcurrentHashMap<String, Device>()
    private val deviceCache = ConcurrentHashMap<String, Device>()
    private var nsdManager: NsdManager? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var lastUiUpdateTime = 0L

    private val _scanProgress = MutableStateFlow(ScanProgress(
        phase = ScanPhase.INITIALIZING,
        progress = 0f,
        message = "Ready",
        devicesFound = 0
    ))
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    // Deep scan progress
    private val _deepScanProgress = MutableStateFlow(DeepScanProgress())
    val deepScanProgress: StateFlow<DeepScanProgress> = _deepScanProgress.asStateFlow()

    /**
     * Start a full network scan.
     */
    suspend fun scan(interfaceName: String? = null): ScanResult = withContext(scope.coroutineContext) {
        val startTime = Date()
        discoveredDevices.clear()

        val networkInfo = NetworkUtils.getNetworkInfo(context, interfaceName)
            ?: return@withContext ScanResult(
                devices = emptyList(),
                scanStartTime = startTime,
                scanEndTime = Date(),
                networkInfo = NetworkInfo(
                    interfaceName = interfaceName,
                    ssid = null,
                    bssid = null,
                    ipAddress = "0.0.0.0",
                    subnetMask = "255.255.255.0",
                    gateway = null,
                    networkPrefix = 24
                ),
                scanStatus = ScanStatus.ERROR,
                error = "No active network interface"
            )

        // Add current device
        addCurrentDevice(networkInfo)

        try {
            // Acquire multicast lock for mDNS
            acquireMulticastLock()

            // Invalidate ARP cache at scan start so we get fresh reads
            ArpReader.invalidateCache()

            // Phase 0: Ping gateway to ensure network connectivity and populate ARP cache
            updateProgress(ScanPhase.READING_ARP_CACHE, 0.05f, "Checking network connectivity...")
            pingGateway(networkInfo)

            // Phase 1: Read ARP cache
            updateProgress(ScanPhase.READING_ARP_CACHE, 0.1f, "Reading ARP cache...")
            readArpCache()

            // Phase 2: Parallel ping sweep (with concurrency limit)
            updateProgress(ScanPhase.PING_SWEEP, 0.2f, "Scanning network...")
            pingSweep(networkInfo)

            // Phase 2.5: Re-read ARP cache to get MAC addresses for discovered devices
            ArpReader.invalidateCache()
            updateProgress(ScanPhase.PING_SWEEP, 0.55f, "Getting device information...")
            enrichDevicesWithArpData()

            // Phase 3: mDNS discovery
            updateProgress(ScanPhase.MDNS_DISCOVERY, 0.6f, "Discovering services...")
            discoverMdns()

            // Phase 4: SSDP discovery
            updateProgress(ScanPhase.SSDP_DISCOVERY, 0.8f, "Finding UPnP devices...")
            discoverSsdp()

            // Phase 4.5: NetBIOS name resolution
            updateProgress(ScanPhase.IDENTIFYING_DEVICES, 0.85f, "Resolving NetBIOS names...")
            resolveNetBiosNames()

            // Phase 5: Identify devices
            updateProgress(ScanPhase.IDENTIFYING_DEVICES, 0.9f, "Identifying devices...")
            identifyDevices()

            // Phase 5.5: Port heuristics for still-unknown devices
            updateProgress(ScanPhase.IDENTIFYING_DEVICES, 0.95f, "Classifying devices...")
            probePortHeuristics()

            // Phase 6: Finalize
            updateProgress(ScanPhase.FINALIZING, 1.0f, "Scan complete")

            // Update cache
            deviceCache.putAll(discoveredDevices)

            val deviceList = discoveredDevices.values.toList()
                .sortedWith(compareBy({ !it.isCurrentDevice }, { it.ipAddress }))

            _devices.value = deviceList

            ScanResult(
                devices = deviceList,
                scanStartTime = startTime,
                scanEndTime = Date(),
                networkInfo = networkInfo,
                scanStatus = ScanStatus.COMPLETED
            )
        } catch (e: Exception) {
            ScanResult(
                devices = discoveredDevices.values.toList(),
                scanStartTime = startTime,
                scanEndTime = Date(),
                networkInfo = networkInfo,
                scanStatus = ScanStatus.ERROR,
                error = e.message
            )
        } finally {
            releaseMulticastLock()
        }
    }

    /**
     * Perform a deep scan on a single device.
     * Scans ports, grabs banners, and attempts OS detection.
     */
    suspend fun performDeepScan(
        ipAddress: String,
        ports: List<Int> = CommonPorts.TOP_PORTS,
        customServiceNames: Map<Int, String> = emptyMap(),
        fullScan: Boolean = false
    ): DeepScanResult = withContext(scope.coroutineContext) {
        val portThreads = if (fullScan) FULL_SCAN_PORT_THREADS else PORT_THREADS
        val portTimeoutMs = if (fullScan) FULL_SCAN_PORT_TIMEOUT_MS else PORT_TIMEOUT_MS
        val startTime = System.currentTimeMillis()
        val openPorts = mutableListOf<PortInfo>()

        try {
            // Phase 1: Port scanning
            updateDeepScanProgress(
                DeepScanPhase.PORT_SCANNING,
                0f,
                "Scanning ports...",
                portsTotal = ports.size
            )

            val scannedCount = AtomicInteger(0)
            val foundCount = AtomicInteger(0)
            val foundPorts = ConcurrentLinkedQueue<Int>()
            val totalPorts = ports.size

            // Feed ports through a rendezvous channel consumed by a fixed pool
            // of workers. This keeps only `portThreads` scans in flight instead
            // of eagerly allocating one coroutine per port (a full 1-65535 scan
            // would otherwise spawn ~65k coroutines up front).
            val portChannel = Channel<Int>(Channel.RENDEZVOUS)
            val producer = launch {
                try {
                    ports.forEach { portChannel.send(it) }
                } finally {
                    portChannel.close()
                }
            }
            val workers = List(portThreads) {
                async {
                    for (port in portChannel) {
                        val isOpen = isPortOpen(ipAddress, port, portTimeoutMs)
                        val count = scannedCount.incrementAndGet()

                        if (isOpen) {
                            foundCount.incrementAndGet()
                            foundPorts.add(port)
                        }

                        val progress = count.toFloat() / totalPorts * 0.6f
                        updateDeepScanProgress(
                            DeepScanPhase.PORT_SCANNING,
                            progress,
                            "Scanning port $port...",
                            currentPort = port,
                            portsScanned = count,
                            portsTotal = totalPorts,
                            openPortsFound = foundCount.get()
                        )
                    }
                }
            }
            workers.awaitAll()
            producer.join()

            openPorts.addAll(foundPorts.sorted().map { port ->
                PortInfo(
                    port = port,
                    serviceName = customServiceNames[port] ?: CommonPorts.getServiceName(port)
                )
            })

            // Phase 2: Banner grabbing for open ports (parallelized)
            if (openPorts.isNotEmpty()) {
                updateDeepScanProgress(
                    DeepScanPhase.BANNER_GRABBING,
                    0.6f,
                    "Grabbing banners...",
                    portsScanned = ports.size,
                    portsTotal = ports.size,
                    openPortsFound = openPorts.size
                )

                val bannerSemaphore = Semaphore(BANNER_THREADS)
                val enhancedPorts = openPorts.mapIndexed { index, portInfo ->
                    async {
                        bannerSemaphore.withPermit {
                            val banner = grabBanner(ipAddress, portInfo.port)
                            val progress = 0.6f + (index.toFloat() / openPorts.size) * 0.2f

                            updateDeepScanProgress(
                                DeepScanPhase.BANNER_GRABBING,
                                progress,
                                "Analyzing port ${portInfo.port}...",
                                currentPort = portInfo.port,
                                portsScanned = ports.size,
                                portsTotal = ports.size,
                                openPortsFound = openPorts.size
                            )

                            portInfo.copy(
                                banner = banner?.take(200),
                                version = extractVersion(banner),
                                serviceName = portInfo.serviceName ?: detectService(portInfo.port, banner)
                            )
                        }
                    }
                }.awaitAll()

                openPorts.clear()
                openPorts.addAll(enhancedPorts)
            }

            // Phase 3: OS Detection
            updateDeepScanProgress(
                DeepScanPhase.OS_DETECTION,
                0.8f,
                "Detecting OS...",
                portsScanned = ports.size,
                portsTotal = ports.size,
                openPortsFound = openPorts.size
            )

            val osInfo = detectOs(ipAddress, openPorts)

            // Phase 4: Finalize
            updateDeepScanProgress(
                DeepScanPhase.FINALIZING,
                1.0f,
                "Scan complete",
                portsScanned = ports.size,
                portsTotal = ports.size,
                openPortsFound = openPorts.size
            )

            val duration = System.currentTimeMillis() - startTime

            DeepScanResult(
                ipAddress = ipAddress,
                scanTime = Date(),
                openPorts = openPorts.sortedBy { it.port },
                detectedOs = osInfo,
                scanDurationMs = duration,
                status = DeepScanStatus.COMPLETED
            )
        } catch (e: CancellationException) {
            DeepScanResult(
                ipAddress = ipAddress,
                scanTime = Date(),
                openPorts = openPorts.sortedBy { it.port },
                scanDurationMs = System.currentTimeMillis() - startTime,
                status = DeepScanStatus.CANCELLED
            )
        } catch (e: Exception) {
            DeepScanResult(
                ipAddress = ipAddress,
                scanTime = Date(),
                openPorts = openPorts.sortedBy { it.port },
                scanDurationMs = System.currentTimeMillis() - startTime,
                status = DeepScanStatus.FAILED
            )
        }
    }

    /**
     * Check if a port is open on the target.
     */
    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int = PORT_TIMEOUT_MS): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Grab banner from an open port.
     * Uses blocking readLine with socket timeout instead of unreliable reader.ready().
     */
    private fun grabBanner(ip: String, port: Int): String? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), BANNER_TIMEOUT_MS)
                socket.soTimeout = BANNER_TIMEOUT_MS

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val banner = StringBuilder()

                // For HTTP ports, send a request to trigger response
                if (port in listOf(80, 8080, 8000, 8008, 8081, 8888, 443, 8443)) {
                    socket.getOutputStream().write("HEAD / HTTP/1.0\r\n\r\n".toByteArray())
                } else {
                    // For non-HTTP ports, try reading first — many services
                    // (SSH, FTP, SMTP, etc.) send a banner unprompted.
                    // Use a short timeout so we don't block long on silent services.
                    socket.soTimeout = 500
                    val firstLine = try { reader.readLine() } catch (e: Exception) { null }
                    if (firstLine != null) {
                        banner.appendLine(firstLine)
                    } else {
                        // Service didn't send anything — nudge it
                        socket.getOutputStream().write("\r\n".toByteArray())
                    }
                    socket.soTimeout = BANNER_TIMEOUT_MS
                }

                var linesRead = banner.lines().count { it.isNotEmpty() }

                // Read remaining lines
                while (linesRead < 5) {
                    val line = try {
                        reader.readLine()
                    } catch (e: Exception) {
                        break // Timeout or read error
                    }
                    if (line != null) {
                        banner.appendLine(line)
                        linesRead++
                    } else {
                        break
                    }
                }

                banner.toString().trim().takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract version information from banner.
     */
    private fun extractVersion(banner: String?): String? {
        if (banner == null) return null

        // Common version patterns
        val patterns = listOf(
            Regex("""SSH-[\d.]+-([\w\d._-]+)"""),
            Regex("""Server:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE),
            Regex("""Apache/([\d.]+)"""),
            Regex("""nginx/([\d.]+)"""),
            Regex("""OpenSSH_([\d.p]+)"""),
            Regex("""MySQL\s+([\d.]+)"""),
            Regex("""PostgreSQL\s+([\d.]+)"""),
            Regex("""Microsoft-IIS/([\d.]+)"""),
            Regex("""vsftpd\s+([\d.]+)"""),
            Regex("""ProFTPD\s+([\d.]+)"""),
            Regex("""Dropbear\s+([\d.]+)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(banner)
            if (match != null) {
                return match.groupValues.getOrNull(1) ?: match.value
            }
        }

        return null
    }

    /**
     * Detect service from port and banner.
     */
    private fun detectService(port: Int, banner: String?): String? {
        // Check banner first
        if (banner != null) {
            val lowerBanner = banner.lowercase()
            return when {
                lowerBanner.contains("ssh") -> "SSH"
                lowerBanner.contains("http") -> if (port == 443 || port == 8443) "HTTPS" else "HTTP"
                lowerBanner.contains("ftp") -> "FTP"
                lowerBanner.contains("smtp") -> "SMTP"
                lowerBanner.contains("mysql") -> "MySQL"
                lowerBanner.contains("postgresql") -> "PostgreSQL"
                lowerBanner.contains("redis") -> "Redis"
                lowerBanner.contains("mongodb") -> "MongoDB"
                lowerBanner.contains("telnet") -> "Telnet"
                lowerBanner.contains("vnc") -> "VNC"
                lowerBanner.contains("rdp") || lowerBanner.contains("remote desktop") -> "RDP"
                else -> null
            }
        }

        return CommonPorts.getServiceName(port)
    }

    /**
     * Detect operating system based on open ports and banners.
     */
    private fun detectOs(ip: String, openPorts: List<PortInfo>): OsInfo? {
        val portNumbers = openPorts.map { it.port }.toSet()
        val banners = openPorts.mapNotNull { it.banner }.joinToString(" ").lowercase()

        // Windows indicators — 135/445/3389 are strongly Windows-specific
        val windowsScore = calculateOsScore(
            portNumbers,
            banners,
            strongPorts = setOf(135, 445, 3389, 1433, 5985, 5986),
            weakPorts = setOf(139),
            keywords = listOf("windows", "microsoft", "iis", "mssql", "msrpc")
        )

        // Linux indicators — 111/2049 are strong; 22 is shared with macOS
        val linuxScore = calculateOsScore(
            portNumbers,
            banners,
            strongPorts = setOf(111, 2049),
            weakPorts = setOf(22),
            keywords = listOf("linux", "ubuntu", "debian", "centos", "fedora", "openssh", "apache", "nginx")
        )

        // macOS indicators — 548(AFP)/3283(Apple Remote) are strongly macOS-specific
        val macScore = calculateOsScore(
            portNumbers,
            banners,
            strongPorts = setOf(548, 3283),
            weakPorts = setOf(22, 5900, 5000),
            keywords = listOf("darwin", "macos", "apple", "airplay", "afp")
        )

        // Router indicators — 161(SNMP)/53(DNS) are strong; 80/443 are shared
        val routerScore = calculateOsScore(
            portNumbers,
            banners,
            strongPorts = setOf(161, 53, 23),
            weakPorts = setOf(80, 443),
            keywords = listOf("router", "mikrotik", "cisco", "netgear", "asus", "tp-link", "dlink", "ubiquiti")
        )

        // Printer indicators — 9100(JetDirect)/515(LPD)/631(IPP) are strongly printer-specific
        val printerScore = calculateOsScore(
            portNumbers,
            banners,
            strongPorts = setOf(515, 631, 9100),
            weakPorts = emptySet(),
            keywords = listOf("printer", "hp", "epson", "canon", "brother", "cups", "jetdirect")
        )

        // Determine best match
        val scores = mapOf(
            OsFamily.WINDOWS to windowsScore,
            OsFamily.LINUX to linuxScore,
            OsFamily.MACOS to macScore,
            OsFamily.ROUTER_OS to routerScore,
            OsFamily.PRINTER_OS to printerScore
        )

        val bestMatch = scores.maxByOrNull { it.value }
        if (bestMatch != null && bestMatch.value > 0) {
            val confidence = minOf(100, bestMatch.value * 20)
            val name = when (bestMatch.key) {
                OsFamily.WINDOWS -> detectWindowsVersion(banners)
                OsFamily.LINUX -> detectLinuxDistro(banners)
                OsFamily.MACOS -> "macOS"
                OsFamily.ROUTER_OS -> detectRouterType(banners)
                OsFamily.PRINTER_OS -> detectPrinterType(banners)
                else -> bestMatch.key.displayName
            }

            return OsInfo(
                name = name,
                family = bestMatch.key,
                confidence = confidence
            )
        }

        return null
    }

    private fun calculateOsScore(
        ports: Set<Int>,
        banners: String,
        strongPorts: Set<Int>,
        weakPorts: Set<Int>,
        keywords: List<String>
    ): Int {
        var score = 0
        score += ports.intersect(strongPorts).size * 4
        score += ports.intersect(weakPorts).size * 1
        keywords.forEach { keyword ->
            if (banners.contains(keyword)) score += 3
        }
        return score
    }

    private fun detectWindowsVersion(banners: String): String {
        return when {
            banners.contains("windows 11") || banners.contains("10.0") -> "Windows 11/10"
            banners.contains("windows 10") -> "Windows 10"
            banners.contains("windows server 2022") -> "Windows Server 2022"
            banners.contains("windows server 2019") -> "Windows Server 2019"
            banners.contains("windows server 2016") -> "Windows Server 2016"
            banners.contains("windows server") -> "Windows Server"
            else -> "Windows"
        }
    }

    private fun detectLinuxDistro(banners: String): String {
        return when {
            banners.contains("ubuntu") -> "Ubuntu Linux"
            banners.contains("debian") -> "Debian Linux"
            banners.contains("centos") -> "CentOS Linux"
            banners.contains("fedora") -> "Fedora Linux"
            banners.contains("rhel") || banners.contains("red hat") -> "Red Hat Linux"
            banners.contains("arch") -> "Arch Linux"
            banners.contains("alpine") -> "Alpine Linux"
            else -> "Linux"
        }
    }

    private fun detectRouterType(banners: String): String {
        return when {
            banners.contains("mikrotik") -> "MikroTik RouterOS"
            banners.contains("cisco") -> "Cisco IOS"
            banners.contains("ubiquiti") || banners.contains("unifi") -> "Ubiquiti"
            banners.contains("openwrt") -> "OpenWrt"
            banners.contains("dd-wrt") -> "DD-WRT"
            banners.contains("netgear") -> "Netgear"
            banners.contains("asus") -> "ASUS Router"
            banners.contains("tp-link") -> "TP-Link"
            else -> "Router"
        }
    }

    private fun detectPrinterType(banners: String): String {
        return when {
            banners.contains("hp") || banners.contains("hewlett") -> "HP Printer"
            banners.contains("epson") -> "Epson Printer"
            banners.contains("canon") -> "Canon Printer"
            banners.contains("brother") -> "Brother Printer"
            banners.contains("xerox") -> "Xerox Printer"
            banners.contains("lexmark") -> "Lexmark Printer"
            else -> "Printer"
        }
    }

    private fun updateDeepScanProgress(
        phase: DeepScanPhase,
        progress: Float,
        message: String,
        currentPort: Int? = null,
        portsScanned: Int = 0,
        portsTotal: Int = 0,
        openPortsFound: Int = 0
    ) {
        _deepScanProgress.value = DeepScanProgress(
            phase = phase,
            progress = progress.coerceIn(0f, 1f),
            message = message,
            currentPort = currentPort,
            portsScanned = portsScanned,
            portsTotal = portsTotal,
            openPortsFound = openPortsFound
        )
    }

    private fun addCurrentDevice(networkInfo: NetworkInfo) {
        val localIp = networkInfo.ipAddress
        val localMac = NetworkUtils.getLocalMacAddress(networkInfo.interfaceName)
        val vendor = MacVendorLookup.lookup(localMac)

        val currentDevice = Device(
            ipAddress = localIp,
            macAddress = localMac,
            hostname = android.os.Build.MODEL,
            deviceType = DeviceType.SMARTPHONE,
            vendor = vendor ?: android.os.Build.MANUFACTURER,
            isOnline = true,
            isCurrentDevice = true,
            discoveredVia = DiscoveryMethod.MANUAL
        )
        discoveredDevices[localIp] = currentDevice
        updateDeviceCount()
    }

    /**
     * Ping the gateway to verify connectivity and add it as a device.
     */
    private suspend fun pingGateway(networkInfo: NetworkInfo) = withContext(Dispatchers.IO) {
        val gateway = networkInfo.gateway ?: return@withContext
        if (!NetworkUtils.isValidIpAddress(gateway)) return@withContext

        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("/system/bin/ping", "-c", "1", "-W", "1", gateway)
            )
            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0
            process.destroyForcibly()

            if (completed) {
                val latency = PING_RTT_PATTERN.find(output)
                    ?.groupValues?.get(1)?.toFloatOrNull()?.toInt()
                    ?: return@withContext // No real echo reply
                ArpReader.invalidateCache()
                val macAddress = ArpReader.getMacForIp(gateway)
                val vendor = MacVendorLookup.lookup(macAddress)

                val device = Device(
                    ipAddress = gateway,
                    macAddress = macAddress,
                    vendor = vendor,
                    deviceType = DeviceType.ROUTER,
                    isOnline = true,
                    latencyMs = latency,
                    hostname = "Gateway",
                    discoveredVia = DiscoveryMethod.PING
                )
                discoveredDevices[gateway] = device
                updateDeviceCount()
            }
        } catch (e: Exception) {
            // Gateway ping failed, continue anyway
        }
    }

    private fun readArpCache() {
        val entries = ArpReader.readValidEntries()
        for (entry in entries) {
            val vendor = MacVendorLookup.lookup(entry.normalizedMac)
            val existing = discoveredDevices[entry.ipAddress]

            val device = existing?.copy(
                macAddress = entry.normalizedMac,
                vendor = vendor ?: existing.vendor
            ) ?: Device(
                ipAddress = entry.ipAddress,
                macAddress = entry.normalizedMac,
                vendor = vendor,
                isOnline = true,
                discoveredVia = DiscoveryMethod.ARP_CACHE
            )
            discoveredDevices[entry.ipAddress] = device
        }
        updateDeviceCount()
    }

    /**
     * Parallel ping sweep with concurrency limited by PING_THREADS semaphore.
     */
    private suspend fun pingSweep(networkInfo: NetworkInfo) = coroutineScope {
        val ipRange = NetworkUtils.getIpRange(networkInfo)
        val total = ipRange.size
        val completed = AtomicInteger(0)
        val semaphore = Semaphore(PING_THREADS)

        val jobs = ipRange.map { ip ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val pingResult = NetworkUtils.isReachable(ip, PING_TIMEOUT_MS)
                    if (pingResult.reachable) {
                        val macAddress = ArpReader.getMacForIp(ip)
                        val vendor = MacVendorLookup.lookup(macAddress)
                        val existing = discoveredDevices[ip]

                        val device = existing?.copy(
                            isOnline = true,
                            latencyMs = pingResult.latencyMs,
                            ttl = pingResult.ttl ?: existing.ttl,
                            lastSeen = Date()
                        ) ?: Device(
                            ipAddress = ip,
                            macAddress = macAddress,
                            vendor = vendor,
                            isOnline = true,
                            latencyMs = pingResult.latencyMs,
                            ttl = pingResult.ttl,
                            discoveredVia = DiscoveryMethod.PING
                        )
                        discoveredDevices[ip] = device
                        throttledDeviceCountUpdate()
                    }

                    val progress = completed.incrementAndGet()
                    val percent = 0.2f + (progress.toFloat() / total) * 0.4f
                    updateProgress(
                        ScanPhase.PING_SWEEP,
                        percent,
                        "Scanned $progress/$total addresses",
                        ip
                    )
                }
            }
        }
        jobs.awaitAll()
        // Final device count update after sweep
        updateDeviceCount()
    }

    /**
     * Enrich discovered devices with MAC addresses and vendor info from ARP cache.
     * Called after ping sweep to get MAC addresses that were populated during pinging.
     */
    private fun enrichDevicesWithArpData() {
        // Re-read ARP cache - it should now have entries for devices we pinged
        val arpEntries = ArpReader.readValidEntries()
        val arpMap = arpEntries.associateBy { it.ipAddress }

        // Update devices with MAC and vendor info
        for ((ip, device) in discoveredDevices.toMap()) {
            if (device.macAddress == null || device.vendor == null) {
                val arpEntry = arpMap[ip]
                if (arpEntry != null) {
                    val mac = arpEntry.normalizedMac
                    val vendor = MacVendorLookup.lookup(mac)
                    val deviceType = DeviceType.identify(
                        hostname = device.hostname,
                        vendor = vendor,
                        mdnsServiceType = device.mdnsServices.firstOrNull(),
                        ssdpDeviceType = device.ssdpInfo?.deviceType
                    )

                    val updatedDevice = device.copy(
                        macAddress = mac,
                        vendor = vendor ?: device.vendor,
                        deviceType = if (device.isCurrentDevice) DeviceType.SMARTPHONE
                                     else if (device.deviceType == DeviceType.UNKNOWN) deviceType
                                     else device.deviceType
                    )
                    discoveredDevices[ip] = updatedDevice
                }
            }
        }
        updateDeviceCount()
    }

    /**
     * Discover devices via mDNS.
     * Uses a CountDownLatch-style mechanism to wait for pending resolves before returning.
     */
    private suspend fun discoverMdns() = coroutineScope {
        val serviceTypes = listOf(
            "_http._tcp.",
            "_https._tcp.",
            "_workstation._tcp.",
            "_smb._tcp.",
            "_afpovertcp._tcp.",
            "_airplay._tcp.",
            "_raop._tcp.",
            "_googlecast._tcp.",
            "_googlezone._tcp.",
            "_androidtvremote2._tcp.",
            "_spotify-connect._tcp.",
            "_printer._tcp.",
            "_ipp._tcp.",
            "_ssh._tcp.",
            "_sftp-ssh._tcp.",
            "_homekit._tcp.",
            "_matter._tcp.",
            "_sleep-proxy._udp."
        )

        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

        val pendingResolves = AtomicInteger(0)
        val discoveryChannel = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()

        for (serviceType in serviceTypes) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onDiscoveryStarted(serviceType: String?) {}
                override fun onDiscoveryStopped(serviceType: String?) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                    serviceInfo?.let {
                        discoveryChannel.trySend(it)
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
            }

            try {
                nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                listeners.add(listener)
            } catch (e: Exception) {
                // Service type not supported or already discovering
            }
        }

        // Collect discovered services using coroutineScope (not class-level scope)
        val job = launch {
            for (serviceInfo in discoveryChannel) {
                pendingResolves.incrementAndGet()
                resolveService(serviceInfo, pendingResolves)
            }
        }

        delay(MDNS_TIMEOUT_MS)
        job.cancel()
        discoveryChannel.close()

        // Stop all discoveries
        for (listener in listeners) {
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                // Already stopped
            }
        }

        // Wait briefly for pending resolves to complete (up to 1 second)
        val resolveDeadline = System.currentTimeMillis() + 1000L
        while (pendingResolves.get() > 0 && System.currentTimeMillis() < resolveDeadline) {
            delay(50)
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo, pendingResolves: AtomicInteger) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                pendingResolves.decrementAndGet()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                try {
                    // Prefer IPv4 — mDNS can resolve to IPv6 link-local which creates
                    // duplicate entries for the same physical device.
                    var ip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val addrs = serviceInfo?.hostAddresses ?: emptyList()
                        addrs.firstOrNull { it is java.net.Inet4Address }?.hostAddress
                            ?: addrs.firstOrNull()?.hostAddress
                    } else {
                        @Suppress("DEPRECATION")
                        serviceInfo?.host?.hostAddress
                    }

                    // If we got an IPv6 link-local address, try to find the matching
                    // IPv4 device by hostname so we merge services instead of duplicating.
                    if (ip != null && ip.startsWith("fe80")) {
                        val hostname = serviceInfo?.serviceName
                            ?.takeIf { name -> name.isNotBlank() }
                            ?.let { sanitizeMdnsName(it) }
                        val matchByHostname = hostname?.let { h ->
                            discoveredDevices.entries.firstOrNull { (_, d) ->
                                d.hostname != null && d.hostname.equals(h, ignoreCase = true)
                            }
                        }
                        if (matchByHostname != null) {
                            ip = matchByHostname.key // Use the existing IPv4 address
                        } else {
                            return // Skip — IPv6 link-local with no matching IPv4 device
                        }
                    }

                    ip?.let {
                        val existing = discoveredDevices[it]
                        val services = (existing?.mdnsServices ?: emptyList()) + (serviceInfo?.serviceType ?: "")
                        val hostname = serviceInfo?.serviceName
                            ?.takeIf { name -> name.isNotBlank() }
                            ?.let { sanitizeMdnsName(it) }

                        // Identify device type immediately so UI shows correct icon
                        val identifiedType = DeviceType.identify(
                            hostname = hostname ?: existing?.hostname,
                            vendor = existing?.vendor,
                            mdnsServiceType = services.firstOrNull(),
                            ssdpDeviceType = existing?.ssdpInfo?.deviceType
                        )
                        val deviceType = when {
                            existing?.isCurrentDevice == true -> DeviceType.SMARTPHONE
                            existing != null && existing.deviceType != DeviceType.UNKNOWN -> existing.deviceType
                            identifiedType != DeviceType.UNKNOWN -> identifiedType
                            else -> existing?.deviceType ?: DeviceType.UNKNOWN
                        }

                        val device = existing?.copy(
                            hostname = existing.hostname ?: hostname,
                            mdnsServices = services.distinct(),
                            deviceType = deviceType,
                            discoveredVia = if (existing.discoveredVia == DiscoveryMethod.MANUAL)
                                existing.discoveredVia else DiscoveryMethod.MDNS
                        ) ?: Device(
                            ipAddress = it,
                            hostname = hostname,
                            mdnsServices = services,
                            deviceType = identifiedType,
                            isOnline = true,
                            discoveredVia = DiscoveryMethod.MDNS
                        )
                        discoveredDevices[it] = device
                        updateDeviceCount()
                    }
                } finally {
                    pendingResolves.decrementAndGet()
                }
            }
        }

        @Suppress("DEPRECATION")
        nsdManager?.resolveService(serviceInfo, resolveListener)
    }

    /**
     * Discover devices via SSDP/UPnP.
     * Uses socket.use {} to prevent resource leaks.
     */
    private suspend fun discoverSsdp() = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = SSDP_TIMEOUT_MS.toInt()
                socket.broadcast = true

                // SSDP M-SEARCH request
                val searchRequest = """
                    M-SEARCH * HTTP/1.1
                    HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT
                    MAN: "ssdp:discover"
                    MX: 2
                    ST: ssdp:all

                """.trimIndent().replace("\n", "\r\n")

                val requestBytes = searchRequest.toByteArray()
                val multicastAddress = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
                val packet = DatagramPacket(requestBytes, requestBytes.size, multicastAddress, SSDP_PORT)

                socket.send(packet)

                // Receive responses
                val buffer = ByteArray(2048)
                val endTime = System.currentTimeMillis() + SSDP_TIMEOUT_MS

                while (System.currentTimeMillis() < endTime) {
                    try {
                        val response = DatagramPacket(buffer, buffer.size)
                        socket.receive(response)

                        val responseText = String(response.data, 0, response.length)
                        val ip = response.address.hostAddress ?: continue

                        parseSsdpResponse(ip, responseText)
                    } catch (e: Exception) {
                        // Timeout or other error
                        break
                    }
                }
            }

            // Fetch device description XMLs from all location URLs in parallel
            val toFetch = discoveredDevices.values
                .filter { it.ssdpInfo?.locationUrl != null }
                .toList()

            coroutineScope {
                toFetch.map { device ->
                    async {
                        val locationUrl = device.ssdpInfo?.locationUrl ?: return@async
                        val description = fetchSsdpDescription(locationUrl) ?: return@async
                        val existing = discoveredDevices[device.ipAddress] ?: return@async
                        val existingSsdp = existing.ssdpInfo ?: return@async
                        discoveredDevices[device.ipAddress] = existing.copy(
                            hostname = existing.hostname ?: description.friendlyName,
                            ssdpInfo = existingSsdp.copy(
                                friendlyName = description.friendlyName ?: existingSsdp.friendlyName,
                                manufacturer = description.manufacturer ?: existingSsdp.manufacturer,
                                modelName = description.modelName ?: existingSsdp.modelName,
                                modelNumber = description.modelNumber ?: existingSsdp.modelNumber,
                                serialNumber = description.serialNumber ?: existingSsdp.serialNumber,
                                deviceType = description.deviceType ?: existingSsdp.deviceType
                            )
                        )
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            // SSDP discovery failed
        }
    }

    /**
     * Fetch and parse a UPnP device description XML from the given URL.
     * Returns a partially-filled SsdpDeviceInfo with fields from the XML, or null on failure.
     */
    private fun fetchSsdpDescription(locationUrl: String): SsdpDeviceInfo? {
        return try {
            val connection = URL(locationUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.requestMethod = "GET"
            val xml = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            SsdpDeviceInfo(
                friendlyName = extractXmlTag(xml, "friendlyName"),
                manufacturer = extractXmlTag(xml, "manufacturer"),
                modelName = extractXmlTag(xml, "modelName"),
                modelNumber = extractXmlTag(xml, "modelNumber"),
                serialNumber = extractXmlTag(xml, "serialNumber"),
                deviceType = extractXmlTag(xml, "deviceType"),
                locationUrl = locationUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val match = Regex("<$tag>([^<]*)</$tag>", RegexOption.IGNORE_CASE).find(xml)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun parseSsdpResponse(ip: String, response: String) {
        val headers = response.lines()
            .filter { it.contains(":") }
            .associate {
                val parts = it.split(":", limit = 2)
                parts[0].trim().uppercase() to parts.getOrElse(1) { "" }.trim()
            }

        val locationUrl = headers["LOCATION"]
        val server = headers["SERVER"]
        val st = headers["ST"]

        val ssdpInfo = SsdpDeviceInfo(
            deviceType = st,
            locationUrl = locationUrl,
            manufacturer = server
        )

        val existing = discoveredDevices[ip]

        // Identify device type immediately so UI shows correct icon
        val identifiedType = DeviceType.identify(
            hostname = existing?.hostname,
            vendor = existing?.vendor,
            mdnsServiceType = existing?.mdnsServices?.firstOrNull(),
            ssdpDeviceType = st
        )
        val deviceType = when {
            existing?.isCurrentDevice == true -> DeviceType.SMARTPHONE
            existing != null && existing.deviceType != DeviceType.UNKNOWN -> existing.deviceType
            identifiedType != DeviceType.UNKNOWN -> identifiedType
            else -> existing?.deviceType ?: DeviceType.UNKNOWN
        }

        val device = existing?.copy(
            ssdpInfo = ssdpInfo,
            deviceType = deviceType,
            discoveredVia = if (existing.discoveredVia == DiscoveryMethod.MANUAL)
                existing.discoveredVia else DiscoveryMethod.SSDP
        ) ?: Device(
            ipAddress = ip,
            ssdpInfo = ssdpInfo,
            deviceType = identifiedType,
            isOnline = true,
            discoveredVia = DiscoveryMethod.SSDP
        )
        discoveredDevices[ip] = device
        updateDeviceCount()
    }

    /**
     * Query each discovered device via NetBIOS Name Service (UDP port 137) in parallel.
     * Populates hostname, workgroup, and file-server status for Windows/Samba devices.
     */
    private suspend fun resolveNetBiosNames() = coroutineScope {
        val targets = discoveredDevices.values
            .filter { !it.isCurrentDevice }
            .map { it.ipAddress }
            .toList()

        val semaphore = Semaphore(20)
        targets.map { ip ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val info = queryNetBios(ip) ?: return@withPermit
                    val existing = discoveredDevices[ip] ?: return@withPermit
                    // Prefer a pre-existing hostname only if it looks like a real hostname
                    // (all-ASCII, no control/binary chars). Garbage from a failed DNS or
                    // malformed SSDP friendlyName is replaced by the authoritative NetBIOS name.
                    val validExistingHostname = existing.hostname?.takeIf { h ->
                        h.isNotBlank() && h.all { it.isLetterOrDigit() || it == '-' || it == '.' || it == '_' }
                    }
                    // NetBIOS names are always uppercase; accept that as the canonical name.
                    val finalHostname = validExistingHostname ?: info.hostname
                    // Use the hostname (e.g. "LAPTOP-XXX", "DESKTOP-XXX") and vendor to
                    // distinguish laptop from desktop. Fall back to DESKTOP for plain names.
                    val inferredType = when {
                        existing.deviceType != DeviceType.UNKNOWN -> existing.deviceType
                        else -> {
                            val fromName = DeviceType.identify(
                                hostname = finalHostname,
                                vendor = existing.vendor
                            )
                            if (fromName != DeviceType.UNKNOWN) fromName else DeviceType.DESKTOP
                        }
                    }
                    discoveredDevices[ip] = existing.copy(
                        hostname = finalHostname,
                        netBiosInfo = info,
                        deviceType = inferredType
                    )
                    updateDeviceCount()
                }
            }
        }.awaitAll()
    }

    /**
     * Send a NetBIOS NBSTAT query to the given IP and parse the response.
     * Returns null if the device doesn't respond or isn't a NetBIOS device.
     */
    private fun queryNetBios(ip: String): NetBiosInfo? {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 300  // LAN NetBIOS response is < 50ms; 300ms is generous
                val request = buildNetBiosRequest()
                val address = InetAddress.getByName(ip)
                socket.send(DatagramPacket(request, request.size, address, 137))
                val buffer = ByteArray(1024)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                parseNetBiosResponse(buffer, response.length)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Build a NetBIOS NBSTAT request packet (wildcard query for all names).
     *
     * Packet layout (50 bytes total):
     *   12 bytes header  +  34 bytes question (1 length + 32 encoded wildcard name + 1 null + 2 type + 2 class)
     *
     * The wildcard '*' (0x2A) and 15 null padding bytes are first-level encoded:
     *   each input byte X → two output bytes: ((X >> 4) + 0x41), ((X & 0xF) + 0x41)
     *   '*' (0x2A) → 0x43 'C', 0x4B 'K'
     *   0x00      → 0x41 'A', 0x41 'A'
     */
    private fun buildNetBiosRequest(): ByteArray = byteArrayOf(
        // Header
        0x13, 0x37,                                                         // Transaction ID
        0x00, 0x00,                                                         // Flags: standard query
        0x00, 0x01,                                                         // QDCOUNT: 1
        0x00, 0x00,                                                         // ANCOUNT: 0
        0x00, 0x00,                                                         // NSCOUNT: 0
        0x00, 0x00,                                                         // ARCOUNT: 0
        // QNAME: length byte + 32 encoded bytes + null terminator
        0x20,                                                               // Name length: 32
        0x43, 0x4B,                                                         // '*' (0x2A) encoded
        0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,                   // null bytes 1–4 encoded
        0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,                   // null bytes 5–8 encoded
        0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,                   // null bytes 9–12 encoded
        0x41, 0x41, 0x41, 0x41, 0x41, 0x41,                               // null bytes 13–15 encoded
        0x00,                                                               // End of name
        // QTYPE: NBSTAT (33), QCLASS: IN (1)
        0x00, 0x21,
        0x00, 0x01
    )

    /**
     * Parse a NetBIOS NBSTAT response and extract hostname, workgroup, and file-server status.
     *
     * Expected response layout:
     *   Offset  0–11: DNS-style header
     *   Offset 12–49: echoed question section (34 bytes: same QNAME + type + class)
     *   Offset 50–51: answer name (compression pointer 0xC0 0x0C, or full 34-byte name)
     *   Offset 52–61: type(2) + class(2) + TTL(4) + RDLENGTH(2)  [when pointer used]
     *   Offset 62:    NUM_NAMES (1 byte)
     *   Offset 63+:   NAME_ENTRIES — each 18 bytes: 15-byte name + 1-byte suffix + 2-byte flags
     */
    private fun parseNetBiosResponse(data: ByteArray, length: Int): NetBiosInfo? {
        if (length < 57) return null
        val answerCount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (answerCount == 0) return null

        // QDCOUNT tells us whether the question section was echoed back.
        // Windows sets QDCOUNT=0 and omits the question, so the answer starts at offset 12.
        // Implementations that do echo the question (QDCOUNT=1) start the answer at offset 50.
        val qdCount = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val answerStart = if (qdCount > 0) 50 else 12
        if (answerStart >= length) return null

        // Answer name is either a 2-byte compression pointer (0xC0 xx) or a full 34-byte encoded name
        val nameFieldLen = if ((data[answerStart].toInt() and 0xC0) == 0xC0) 2 else 34
        // Skip name + type(2) + class(2) + ttl(4) + rdlength(2) = name + 10 bytes
        val numNamesOffset = answerStart + nameFieldLen + 10
        if (numNamesOffset >= length) return null

        val numNames = data[numNamesOffset].toInt() and 0xFF
        var offset = numNamesOffset + 1

        var hostname: String? = null
        var workgroup: String? = null

        for (i in 0 until numNames) {
            if (offset + 18 > length) break
            val name = String(data, offset, 15, Charsets.ISO_8859_1).trimEnd()
            val suffix = data[offset + 15].toInt() and 0xFF
            val flags = ((data[offset + 16].toInt() and 0xFF) shl 8) or (data[offset + 17].toInt() and 0xFF)
            val isGroup = (flags and 0x8000) != 0
            when {
                suffix == 0x00 && !isGroup && hostname == null -> hostname = name
                (suffix == 0x00 || suffix == 0x1E) && isGroup && workgroup == null -> workgroup = name
            }
            offset += 18
        }

        val resolvedHostname = hostname?.takeIf { it.isNotBlank() } ?: return null
        return NetBiosInfo(
            hostname = resolvedHostname,
            workgroup = workgroup?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Identify devices by resolving hostnames (in parallel) and determining device types.
     */
    private suspend fun identifyDevices() = coroutineScope {
        // Final ARP cache read to catch any remaining MAC addresses
        ArpReader.invalidateCache()
        val arpEntries = ArpReader.readValidEntries()
        val arpMap = arpEntries.associateBy { it.ipAddress }

        val entries = discoveredDevices.toMap()

        // Resolve hostnames in parallel
        val hostnameJobs = entries
            .filter { (_, device) -> device.hostname == null }
            .map { (ip, _) ->
                async(Dispatchers.IO) {
                    ip to try {
                        NetworkUtils.resolveHostname(ip)
                    } catch (e: Exception) {
                        null
                    }
                }
            }

        val resolvedHostnames = hostnameJobs.awaitAll().toMap()

        for ((ip, device) in entries) {
            var updatedDevice = device

            // Try to get MAC if missing
            if (device.macAddress == null) {
                val arpEntry = arpMap[ip]
                if (arpEntry != null) {
                    val mac = arpEntry.normalizedMac
                    val vendor = MacVendorLookup.lookup(mac)
                    updatedDevice = updatedDevice.copy(
                        macAddress = mac,
                        vendor = vendor ?: device.vendor
                    )
                }
            }

            // Use resolved hostname
            val hostname = updatedDevice.hostname ?: resolvedHostnames[ip]

            // Identify device type based on all available info
            val deviceType = DeviceType.identify(
                hostname = hostname,
                vendor = updatedDevice.vendor,
                mdnsServiceType = updatedDevice.mdnsServices.firstOrNull(),
                ssdpDeviceType = updatedDevice.ssdpInfo?.deviceType
            )

            updatedDevice = updatedDevice.copy(
                hostname = hostname ?: updatedDevice.hostname,
                deviceType = when {
                    device.isCurrentDevice -> DeviceType.SMARTPHONE
                    updatedDevice.deviceType != DeviceType.UNKNOWN -> updatedDevice.deviceType
                    else -> deviceType
                }
            )

            discoveredDevices[ip] = updatedDevice
        }
        updateDeviceCount()
    }

    /**
     * Port probes + TTL-based heuristics for still-unknown devices.
     *
     * Step 1: TCP port probes — strong signals from specific ports
     * Step 2: Passive heuristics — TTL + protocol absence pattern
     *
     * Note: MAC-based detection (locally-administered bit) and hostname resolution
     * (gateway DNS, mDNS reverse PTR, nslookup) were attempted but don't work on
     * Android 14 due to SELinux restrictions. See docs/discoverability-improvements.md
     * for the VPN-based approach that would solve both.
     */
    private suspend fun probePortHeuristics() = coroutineScope {
        val targets = discoveredDevices.values
            .filter { it.deviceType == DeviceType.UNKNOWN && !it.isCurrentDevice }
            .toList()

        val semaphore = Semaphore(20)
        targets.map { device ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val ip = device.ipAddress
                    val existing = discoveredDevices[ip] ?: return@withPermit

                    // --- Step 1: TCP port probes ---
                    val portResults = listOf(8008, 8009, 5555, 7000, 7100, 62078).map { port ->
                        async { port to isPortOpen(ip, port) }
                    }.awaitAll().toMap()

                    val has8008 = portResults[8008] == true
                    val has8009 = portResults[8009] == true
                    val has5555 = portResults[5555] == true
                    val has7000 = portResults[7000] == true
                    val has7100 = portResults[7100] == true
                    val has62078 = portResults[62078] == true

                    val portInferred = when {
                        // AirPlay receiver → Apple TV
                        has7000 || has7100 -> DeviceType.TV
                        // Both Cast ports → dedicated Chromecast or Android TV
                        has8008 && has8009 -> DeviceType.TV
                        // Cast HTTP only (no control channel) → phone with Google Home
                        has8008 && !has8009 -> DeviceType.SMARTPHONE
                        // Android ADB WiFi debugging
                        has5555 -> DeviceType.SMARTPHONE
                        // Apple lockdownd → iOS device
                        has62078 -> DeviceType.SMARTPHONE
                        else -> null
                    }

                    if (portInferred != null) {
                        discoveredDevices[ip] = existing.copy(deviceType = portInferred)
                        return@withPermit
                    }

                    // --- Step 2: Passive heuristics ---
                    val passiveInferred = when {
                        // Randomized MAC + no NetBIOS + not Windows TTL
                        // (works on devices where ARP cache is accessible)
                        NetworkUtils.isLocallyAdministeredMac(existing.macAddress)
                            && existing.netBiosInfo == null
                            && existing.ttl != 128 ->
                            DeviceType.SMARTPHONE

                        // TTL=64 + no NetBIOS + no SSDP + no mDNS + no vendor
                        // Real detection using actual network signals:
                        //   TTL=64 → Linux kernel family (Android, iOS, Linux, macOS)
                        //   No NetBIOS → not Windows/Samba
                        //   No SSDP → not a media device/smart TV
                        //   No mDNS services → not a printer/speaker/Apple device
                        //   No vendor → no MAC available or randomized MAC
                        // On home networks this profile matches Android/iOS phones.
                        // Linux servers are caught earlier by hostname/mDNS/SSH.
                        existing.ttl == 64
                            && existing.netBiosInfo == null
                            && existing.ssdpInfo == null
                            && existing.mdnsServices.isEmpty()
                            && existing.vendor == null ->
                            DeviceType.SMARTPHONE

                        else -> null
                    }

                    if (passiveInferred != null) {
                        discoveredDevices[ip] = existing.copy(deviceType = passiveInferred)
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * Clean up an mDNS service instance name for display.
     * Strips trailing hex UUID suffixes (e.g. "DAWLANCE-GSMART-4KTV-6d5e7bc166c22d21c08e111944d3"
     * → "DAWLANCE GSMART 4KTV") and replaces hyphens with spaces.
     */
    private fun sanitizeMdnsName(name: String): String {
        val stripped = name.replace(Regex("-[0-9a-fA-F]{8,}$"), "")
        return stripped.replace('-', ' ').trim()
    }

    private fun updateProgress(
        phase: ScanPhase,
        progress: Float,
        message: String,
        currentTarget: String? = null
    ) {
        _scanProgress.value = ScanProgress(
            phase = phase,
            progress = progress,
            message = message,
            devicesFound = discoveredDevices.size,
            currentTarget = currentTarget
        )
    }

    /**
     * Throttled device count update to avoid flooding UI during ping sweep.
     */
    private fun throttledDeviceCountUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastUiUpdateTime >= UI_UPDATE_INTERVAL_MS) {
            lastUiUpdateTime = now
            updateDeviceCount()
        }
    }

    private fun updateDeviceCount() {
        _scanProgress.value = _scanProgress.value.copy(
            devicesFound = discoveredDevices.size
        )
        _devices.value = discoveredDevices.values.toList()
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("NetworkScanner")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()
        } catch (e: Exception) {
            // Failed to acquire lock
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.release()
            multicastLock = null
        } catch (e: Exception) {
            // Failed to release lock
        }
    }

    /**
     * Get cached devices from previous scans.
     */
    fun getCachedDevices(): List<Device> {
        return deviceCache.values.toList()
    }

    /**
     * Mark all devices as potentially offline (for re-scan).
     */
    fun markAllOffline() {
        for ((ip, device) in deviceCache) {
            deviceCache[ip] = device.copy(isOnline = false)
        }
        _devices.value = deviceCache.values.toList()
    }

    /**
     * Cancel ongoing scan.
     */
    fun cancel() {
        scanJob.cancelChildren()
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        cancel()
        releaseMulticastLock()
    }
}
