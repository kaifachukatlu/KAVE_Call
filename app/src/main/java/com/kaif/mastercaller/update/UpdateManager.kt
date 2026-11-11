package com.kaif.mastercaller.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer version published on GitHub Releases. */
data class AvailableUpdate(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateState
    data class ReadyToInstall(val update: AvailableUpdate) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Free self-update via GitHub Releases (no Play Store): checks the latest release of the public repo,
 * downloads its .apk with the system DownloadManager, and hands it to Android's installer.
 * Android always shows its own install confirmation; nothing installs silently.
 */
class UpdateManager(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var downloadJob: Job? = null

    val currentVersion: String = try {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** Checks at most every [CHECK_INTERVAL_MS] unless [force] (the "Check for updates" button). */
    fun check(force: Boolean = false) {
        val s = _state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (!force && System.currentTimeMillis() - last < CHECK_INTERVAL_MS) {
            // Re-show a previously found update without hitting the network again.
            restoreCached()
            return
        }
        _state.value = UpdateState.Checking
        scope.launch {
            val result = withContext(Dispatchers.IO) { fetchLatest() }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            _state.value = when {
                result == null -> if (force) UpdateState.Failed("Couldn't reach GitHub. Check your internet.") else UpdateState.Idle
                isNewer(result.version, currentVersion) -> {
                    cache(result)
                    UpdateState.Available(result)
                }
                else -> {
                    clearCache()
                    if (force) UpdateState.UpToDate else UpdateState.Idle
                }
            }
        }
    }

    /** Downloads the APK, then opens the system installer. */
    fun downloadAndInstall(update: AvailableUpdate) {
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            // One-time Android permission: "Install unknown apps" for KAVÉ Call.
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val file = apkFile()
        if (file.exists()) file.delete()
        val dm = appContext.getSystemService(DownloadManager::class.java)
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("KAVÉ Call ${update.version}")
            .setDescription("Downloading update")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, APK_NAME)
        val id = try {
            dm.enqueue(request)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Download couldn't start")
            return
        }
        _state.value = UpdateState.Downloading(update, 0f)
        downloadJob?.cancel()
        downloadJob = scope.launch {
            while (isActive) {
                val (status, progress) = withContext(Dispatchers.IO) { queryDownload(dm, id) }
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _state.value = UpdateState.ReadyToInstall(update)
                        install()
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED, -1 -> {
                        _state.value = UpdateState.Failed("Download failed. Try again.")
                        return@launch
                    }
                    else -> _state.value = UpdateState.Downloading(update, progress)
                }
                delay(500)
            }
        }
    }

    /** Opens Android's installer for the downloaded APK. */
    fun install() {
        val file = apkFile()
        if (!file.exists()) {
            _state.value = UpdateState.Failed("Update file missing. Download again.")
            return
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", file)
        try {
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Couldn't open the installer")
        }
    }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    // --- internals -----------------------------------------------------------------------------

    private fun apkFile() = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_NAME)

    private fun queryDownload(dm: DownloadManager, id: Long): Pair<Int, Float> =
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return@use -1 to 0f
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            status to if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
        } ?: (-1 to 0f)

    private fun fetchLatest(): AvailableUpdate? = try {
        val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "KAVE-Call-Android")
        }
        try {
            when (conn.responseCode) {
                200 -> parseRelease(conn.inputStream.bufferedReader().use { it.readText() }) ?: NO_RELEASE
                404 -> NO_RELEASE // repository has no published release yet
                else -> null
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Update check failed", e)
        null
    }

    private fun cache(u: AvailableUpdate) {
        prefs.edit()
            .putString("v", u.version).putString("n", u.notes).putString("u", u.apkUrl).putLong("s", u.sizeBytes)
            .apply()
    }

    private fun clearCache() = prefs.edit().remove("v").remove("n").remove("u").remove("s").apply()

    private fun restoreCached() {
        val v = prefs.getString("v", null) ?: return
        val url = prefs.getString("u", null) ?: return
        if (isNewer(v, currentVersion)) {
            _state.value = UpdateState.Available(AvailableUpdate(v, prefs.getString("n", "") ?: "", url, prefs.getLong("s", 0)))
        } else {
            clearCache()
        }
    }

    companion object {
        private const val TAG = "UpdateManager"

        /** "Nothing newer": no release yet, or the latest release has no APK attached. */
        private val NO_RELEASE = AvailableUpdate("0", "", "", 0)
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/kaifachukatlu/KAVE_Call/releases/latest"
        private const val KEY_LAST_CHECK = "last_check"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val APK_NAME = "kave-call-update.apk"
        private const val APK_MIME = "application/vnd.android.package-archive"

        /** Parses GitHub's "latest release" JSON; needs a tag like v1.2.0 and an attached .apk. */
        internal fun parseRelease(json: String): AvailableUpdate? {
            val o = JSONObject(json)
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
            val version = o.optString("tag_name").trim().removePrefix("v").removePrefix("V")
            if (version.isEmpty()) return null
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    return AvailableUpdate(
                        version = version,
                        notes = o.optString("body").trim(),
                        apkUrl = a.optString("browser_download_url"),
                        sizeBytes = a.optLong("size"),
                    )
                }
            }
            return null
        }

        /** "1.10.0" > "1.9.2"; missing parts count as 0; non-numeric suffixes are ignored. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.removePrefix("v").split('.', '-', ' ').map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
