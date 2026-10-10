package com.networkscanner.app.util

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.preference.PreferenceManager
import com.networkscanner.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.system.exitProcess

/**
 * Reads the kernel neighbor table with shell or root privileges.
 *
 * Since Android 10 SELinux hides other hosts' ARP entries from apps, so their
 * MAC addresses are only visible to a privileged identity: a Shizuku user
 * service (shell or root) or a `su` shell. Both backends run the same
 * `ip -4 neigh show` and feed [ArpReader]'s parser. Nothing is requested until
 * the user picks a backend in Settings.
 */
object PrivilegedNeighborSource {

    enum class Mode { NONE, SHIZUKU, ROOT }

    const val PREF_KEY = "privileged_neighbor_source"

    private val COMMAND = arrayOf("ip", "-4", "neigh", "show")
    private const val SU_TIMEOUT_S = 20L
    private const val SHIZUKU_BIND_TIMEOUT_MS = 10_000L
    private const val SHIZUKU_PERMISSION_TIMEOUT_MS = 120_000L
    private const val SHIZUKU_REQUEST_CODE = 0x4E45

    fun mode(context: Context): Mode {
        val name = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY, null)
        return Mode.entries.firstOrNull { it.name == name } ?: Mode.NONE
    }

    /** Valid neighbor entries as seen by [mode]; empty when it is off or unavailable. */
    suspend fun read(mode: Mode): List<ArpReader.ArpEntry> = withContext(Dispatchers.IO) {
        val lines = try {
            when (mode) {
                Mode.NONE -> emptyList()
                Mode.ROOT -> runSu(COMMAND.joinToString(" "))
                Mode.SHIZUKU -> shizukuService()?.neighbors().orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        lines.mapNotNull(ArpReader::parseIpNeighLine).filter { it.isValid }
    }

    /**
     * Checks that [mode] is usable, which asks the user for consent through
     * the backend's own prompt (Shizuku dialog or su manager) when needed.
     */
    suspend fun probe(mode: Mode): Boolean = withContext(Dispatchers.IO) {
        when (mode) {
            Mode.NONE -> true
            Mode.ROOT -> runSu("id").any { "uid=0" in it }
            Mode.SHIZUKU -> try {
                requestShizukuPermission()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }
    }

    // The command goes through stdin because `su -c` is not universal (AOSP's su lacks it).
    private fun runSu(command: String): List<String> = try {
        val process = ProcessBuilder("su").redirectErrorStream(true).start()
        // The su manager may wait for the user; cap the wait to SU_TIMEOUT_S to avoid hanging indefinitely.
        thread(isDaemon = true) {
            if (!process.waitFor(SU_TIMEOUT_S, TimeUnit.SECONDS)) process.destroy()
        }
        process.outputStream.bufferedWriter().use { it.write(command + "\n") }
        process.inputStream.bufferedReader().use { it.readLines() }
    } catch (e: Exception) {
        emptyList()
    }

    /** Runs in the Shizuku process; instantiated there by class name. */
    class ShizukuService : INeighborService.Stub() {
        override fun neighbors(): List<String> = try {
            ProcessBuilder(*COMMAND).redirectErrorStream(true).start()
                .inputStream.bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            emptyList()
        }

        override fun destroy() {
            exitProcess(0)
        }
    }

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, ShizukuService::class.java.name)
    )
        .tag("neighbors")
        .daemon(false)
        .processNameSuffix("neigh")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    @Volatile
    private var service: INeighborService? = null

    private suspend fun shizukuService(): INeighborService? {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return withTimeoutOrNull(SHIZUKU_BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        val connected = INeighborService.Stub.asInterface(binder)
                        service = connected
                        if (continuation.isActive) continuation.resume(connected)
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        service = null
                    }
                }
                // On timeout drop the callback but leave the service running for the next scan.
                continuation.invokeOnCancellation { Shizuku.unbindUserService(serviceArgs, connection, false) }
                Shizuku.bindUserService(serviceArgs, connection)
            }
        }
    }

    private suspend fun requestShizukuPermission(): Boolean {
        if (!Shizuku.pingBinder() || Shizuku.isPreV11()) return false
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
        if (Shizuku.shouldShowRequestPermissionRationale()) return false
        return withTimeoutOrNull(SHIZUKU_PERMISSION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Shizuku.OnRequestPermissionResultListener {
                    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                        if (requestCode != SHIZUKU_REQUEST_CODE) return
                        Shizuku.removeRequestPermissionResultListener(this)
                        if (continuation.isActive) {
                            continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                        }
                    }
                }
                Shizuku.addRequestPermissionResultListener(listener)
                continuation.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
                try {
                    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                } catch (e: Exception) {
                    Shizuku.removeRequestPermissionResultListener(listener)
                    throw e
                }
            }
        } ?: false
    }
}
