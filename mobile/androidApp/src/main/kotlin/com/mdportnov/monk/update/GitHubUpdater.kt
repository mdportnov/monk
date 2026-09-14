package com.mdportnov.monk.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.mdportnov.monk.shared.platform.UpdateState
import com.mdportnov.monk.shared.platform.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Self-update from GitHub Releases of `mdportnov/monk-cli`.
 *
 * Only releases tagged `mobile-vX.Y.Z` count (the `vX.Y.Z` tags belong to the Rust CLI). Each
 * one carries `monk-android-X.Y.Z.apk` plus a `.sha256` next to it; the APK is streamed into
 * app-private cache while its digest is computed, compared with the published one, and only
 * then handed to [PackageInstaller]. Android's own same-signature check is the final gate, which
 * is why the release keystore must never change.
 */
class GitHubUpdater(private val context: Context) : Updater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("monk_update", Context.MODE_PRIVATE)
    private var job: Job? = null
    private var candidate: Release? = null

    override val currentVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0.0.0"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state

    override fun check(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0) < CHECK_INTERVAL_MS) return
        if (job?.isActive == true) return
        val busy = _state.value
        if (!force && busy !is UpdateState.Idle && busy !is UpdateState.UpToDate) return
        _state.value = UpdateState.Checking
        job = scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            result.onSuccess { release ->
                if (release != null && isNewer(release.version, currentVersion)) {
                    candidate = release
                    _state.value = UpdateState.Available(release.version, release.notes, release.sizeBytes)
                } else {
                    candidate = null
                    _state.value = UpdateState.UpToDate
                }
            }.onFailure { e ->
                Log.w(TAG, "update check failed", e)
                // A failed background check is not worth a red card; a manual one is.
                _state.value = if (force) UpdateState.Failed(null, e.message ?: "network") else UpdateState.Idle
            }
        }
    }

    override fun install() {
        val release = candidate ?: return
        if (job?.isActive == true) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsInstallPermission(release.version)
            return
        }
        job = scope.launch {
            _state.value = UpdateState.Downloading(release.version, 0f)
            val apk = runCatching {
                withContext(Dispatchers.IO) {
                    download(release) { p -> scope.launch { _state.value = UpdateState.Downloading(release.version, p) } }
                }
            }
            apk.onFailure { e ->
                Log.w(TAG, "download failed", e)
                _state.value = UpdateState.Failed(release.version, e.message ?: "download")
            }.onSuccess { file ->
                _state.value = UpdateState.Installing(release.version)
                if (!commit(file)) _state.value = UpdateState.Failed(release.version, "installer session")
            }
        }
    }

    override fun openInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Called by [InstallResultReceiver]. */
    fun onInstallResult(success: Boolean, message: String?) {
        val v = candidate?.version
        _state.value = if (success) UpdateState.UpToDate else UpdateState.Failed(v, message ?: "install")
    }

    // --- GitHub ---

    @Serializable
    private data class GhAsset(val name: String, val browser_download_url: String, val size: Long = 0)

    @Serializable
    private data class GhRelease(
        val tag_name: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GhAsset> = emptyList(),
    )

    private data class Release(val version: String, val notes: String, val apkUrl: String, val shaUrl: String?, val sizeBytes: Long)

    private fun fetchLatest(): Release? {
        val body = get("$API/releases?per_page=30", accept = "application/vnd.github+json")
        val releases = json.decodeFromString<List<GhRelease>>(body)
        return releases.asSequence()
            .filter { !it.draft && !it.prerelease && it.tag_name.startsWith(TAG_PREFIX) }
            .mapNotNull { r ->
                val apk = r.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@mapNotNull null
                val sha = r.assets.firstOrNull { it.name == apk.name + ".sha256" }
                Release(r.tag_name.removePrefix(TAG_PREFIX), r.body.orEmpty(), apk.browser_download_url, sha?.browser_download_url, apk.size)
            }
            .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
    }

    private fun download(release: Release, onProgress: (Float) -> Unit): File {
        if (!release.apkUrl.startsWith("https://github.com/") && !release.apkUrl.startsWith("https://objects.githubusercontent.com/")) {
            error("untrusted url")
        }
        val expected = release.shaUrl?.let { get(it).trim().split(Regex("\\s+")).firstOrNull()?.lowercase() }
            ?: error("no checksum published")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "monk-${release.version}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(release.apkUrl, accept = "application/octet-stream")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            var done = 0L
            var lastReported = -1
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress(pct / 100f)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        scope.launch { _state.value = UpdateState.Verifying(release.version) }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            target.delete()
            error("checksum mismatch")
        }
        return target
    }

    private fun commit(apk: File): Boolean {
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setAppPackageName(context.packageName) }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("monk.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val status = Intent(context, InstallResultReceiver::class.java)
                    .setAction(InstallResultReceiver.ACTION)
                    .setPackage(context.packageName)
                val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
                session.commit(PendingIntent.getBroadcast(context, sessionId, status, flags).intentSender)
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "install session failed", e)
            if (sessionId != -1) runCatching { installer.abandonSession(sessionId) }
            false
        }
    }

    private fun get(url: String, accept: String = "*/*"): String {
        val conn = open(url, accept)
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "monk-android/$currentVersion")
        }

    companion object {
        private const val TAG = "MonkUpdate"
        private const val API = "https://api.github.com/repos/mdportnov/monk-cli"
        private const val TAG_PREFIX = "mobile-v"
        private const val KEY_LAST_CHECK = "last_check"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

        fun isNewer(candidate: String, current: String) = compareVersions(candidate, current) > 0

        /** Numeric dot-separated compare; a suffix like `-dev` sorts below the bare version. */
        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val pa = parts(a)
            val pb = parts(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = (pa.getOrNull(i) ?: 0).compareTo(pb.getOrNull(i) ?: 0)
                if (d != 0) return d
            }
            return (if ('-' in a) 0 else 1).compareTo(if ('-' in b) 0 else 1)
        }
    }
}
