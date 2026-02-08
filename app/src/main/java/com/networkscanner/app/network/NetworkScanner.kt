package com.networkscanner.app.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import java.util.concurrent.ConcurrentHashMap
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
        private const val SSDP_TIMEOUT_MS = 1500L // Reduced for speed
        private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900

        // Deep scan constants
        private const val PORT_TIMEOUT_MS = 500
        private const val PORT_THREADS = 20
        private const val BANNER_TIMEOUT_MS = 2000
        private const val BANNER_THREADS = 10

        // Throttle UI updates to avoid flooding
        private const val UI_UPDATE_INTERVAL_MS = 200L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
    suspend fun scan(): ScanResult = withContext(Dispatchers.IO) {
        val startTime = Date()
        discoveredDevices.clear()

        val networkInfo = NetworkUtils.getNetworkInfo(context)
            ?: return@withContext ScanResult(
                devices = emptyList(),
                scanStartTime = startTime,
                scanEndTime = Date(),
                networkInfo = NetworkInfo(
                    ssid = null,
                    bssid = null,
                    ipAddress = "0.0.0.0",
                    subnetMask = "255.255.255.0",
                    gateway = null,
                    networkPrefix = 24
                ),
                scanStatus = ScanStatus.ERROR,
                error = "No WiFi connection"
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

            // Phase 5: Identify devices
            updateProgress(ScanPhase.IDENTIFYING_DEVICES, 0.9f, "Identifying devices...")
            identifyDevices()

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
        ports: List<Int> = CommonPorts.TOP_PORTS
    ): DeepScanResult = withContext(Dispatchers.IO) {
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

            // Scan ports in parallel batches
            ports.chunked(PORT_THREADS).forEach { chunk ->
                val results = chunk.map { port ->
                    async {
                        val isOpen = isPortOpen(ipAddress, port)
                        val count = scannedCount.incrementAndGet()

                        if (isOpen) {
                            foundCount.incrementAndGet()
                        }

                        val progress = count.toFloat() / ports.size * 0.6f
                        updateDeepScanProgress(
                            DeepScanPhase.PORT_SCANNING,
                            progress,
                            "Scanning port $port...",
                            currentPort = port,
                            portsScanned = count,
                            portsTotal = ports.size,
                            openPortsFound = foundCount.get()
                        )

                        if (isOpen) port else null
                    }
                }.awaitAll().filterNotNull()

                openPorts.addAll(results.map { port ->
                    PortInfo(
                        port = port,
                        serviceName = CommonPorts.getServiceName(port)
                    )
                })
            }

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
    private fun isPortOpen(ip: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), PORT_TIMEOUT_MS)
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

                // For HTTP ports, send a request
                if (port in listOf(80, 8080, 8000, 8008, 8081, 8888, 443, 8443)) {
                    socket.getOutputStream().write("HEAD / HTTP/1.0\r\n\r\n".toByteArray())
                } else if (port == 22) {
                    // SSH sends banner automatically
                } else {
                    // Try to trigger a response
                    socket.getOutputStream().write("\r\n".toByteArray())
                }

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val banner = StringBuilder()
                var linesRead = 0

                // Use blocking readLine with socket timeout as safeguard
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

        // Windows indicators
        val windowsScore = calculateOsScore(
            portNumbers,
            banners,
            indicatorPorts = setOf(135, 139, 445, 3389, 1433, 5985, 5986),
            keywords = listOf("windows", "microsoft", "iis", "mssql", "msrpc")
        )

        // Linux indicators
        val linuxScore = calculateOsScore(
            portNumbers,
            banners,
            indicatorPorts = setOf(22, 111, 2049),
            keywords = listOf("linux", "ubuntu", "debian", "centos", "fedora", "openssh", "apache", "nginx")
        )

        // macOS indicators
        val macScore = calculateOsScore(
            portNumbers,
            banners,
            indicatorPorts = setOf(22, 548, 5900, 3283, 5000),
            keywords = listOf("darwin", "macos", "apple", "airplay", "afp")
        )

        // Router indicators
        val routerScore = calculateOsScore(
            portNumbers,
            banners,
            indicatorPorts = setOf(23, 80, 443, 161, 53),
            keywords = listOf("router", "mikrotik", "cisco", "netgear", "asus", "tp-link", "dlink", "ubiquiti")
        )

        // Printer indicators
        val printerScore = calculateOsScore(
            portNumbers,
            banners,
            indicatorPorts = setOf(515, 631, 9100),
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
        indicatorPorts: Set<Int>,
        keywords: List<String>
    ): Int {
        var score = 0
        score += ports.intersect(indicatorPorts).size * 2
        keywords.forEach { keyword ->
            if (banners.contains(keyword)) score += 2
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
        val localMac = NetworkUtils.getLocalMacAddress()
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
            val startTime = System.currentTimeMillis()
            val reachable = process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0
            process.destroyForcibly()

            if (reachable) {
                val latency = (System.currentTimeMillis() - startTime).toInt()
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
                vendor = vendor ?: existing.vendor,
                discoveredVia = DiscoveryMethod.ARP_CACHE
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
                    val (reachable, latency) = NetworkUtils.isReachable(ip, PING_TIMEOUT_MS)
                    if (reachable) {
                        val macAddress = ArpReader.getMacForIp(ip)
                        val vendor = MacVendorLookup.lookup(macAddress)
                        val existing = discoveredDevices[ip]

                        val device = existing?.copy(
                            isOnline = true,
                            latencyMs = latency,
                            lastSeen = Date()
                        ) ?: Device(
                            ipAddress = ip,
                            macAddress = macAddress,
                            vendor = vendor,
                            isOnline = true,
                            latencyMs = latency,
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
                        "Scanned $progress/$total IPs",
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
     * Uses the current coroutineScope for the channel consumer to ensure proper cancellation.
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
            "_spotify-connect._tcp.",
            "_printer._tcp.",
            "_ipp._tcp.",
            "_ssh._tcp.",
            "_sftp-ssh._tcp.",
            "_homekit._tcp."
        )

        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

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
                resolveService(serviceInfo)
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
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}

            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.host?.hostAddress?.let { ip ->
                    val existing = discoveredDevices[ip]
                    val services = (existing?.mdnsServices ?: emptyList()) + serviceInfo.serviceType
                    val hostname = serviceInfo.serviceName.takeIf { it.isNotBlank() }

                    val device = existing?.copy(
                        hostname = hostname ?: existing.hostname,
                        mdnsServices = services.distinct(),
                        discoveredVia = if (existing.discoveredVia == DiscoveryMethod.MANUAL)
                            existing.discoveredVia else DiscoveryMethod.MDNS
                    ) ?: Device(
                        ipAddress = ip,
                        hostname = hostname,
                        mdnsServices = services,
                        isOnline = true,
                        discoveredVia = DiscoveryMethod.MDNS
                    )
                    discoveredDevices[ip] = device
                    updateDeviceCount()
                }
            }
        })
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
        } catch (e: Exception) {
            // SSDP discovery failed
        }
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
        val device = existing?.copy(
            ssdpInfo = ssdpInfo,
            discoveredVia = if (existing.discoveredVia == DiscoveryMethod.MANUAL)
                existing.discoveredVia else DiscoveryMethod.SSDP
        ) ?: Device(
            ipAddress = ip,
            ssdpInfo = ssdpInfo,
            isOnline = true,
            discoveredVia = DiscoveryMethod.SSDP
        )
        discoveredDevices[ip] = device
        updateDeviceCount()
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
        scope.coroutineContext.cancelChildren()
    }

    /**
     * Clean up resources.
     */
    fun cleanup() {
        cancel()
        releaseMulticastLock()
    }
}
