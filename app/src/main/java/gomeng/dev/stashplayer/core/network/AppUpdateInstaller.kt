package gomeng.dev.stashplayer.core.network

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import java.io.File
import java.util.UUID

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

internal enum class UpdateDownloadPhase { Pending, Downloading, Waiting, Complete, Failed }

internal data class UpdateDownload(
    val id: Long,
    val version: String,
    val phase: UpdateDownloadPhase,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
)

internal class AppUpdateInstaller(context: Context) {
    private val app = context.applicationContext
    private val manager = requireNotNull(app.getSystemService(DownloadManager::class.java))
    private val prefs = app.getSharedPreferences("phone_update_download", Context.MODE_PRIVATE)

    fun current(currentVersion: String): UpdateDownload? = synchronized(lock) {
        val id = prefs.getLong("id", -1)
        val version = prefs.getString("version", null) ?: return@synchronized null
        if (!isAppUpdateAvailable(currentVersion, version)) {
            runCatching { manager.remove(id) }
            prefs.edit().clear().commit()
            return@synchronized null
        }
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return@synchronized UpdateDownload(id, version, UpdateDownloadPhase.Failed)
            fun column(name: String) = cursor.getColumnIndexOrThrow(name)
            val phase = when (cursor.getInt(column(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_PENDING -> UpdateDownloadPhase.Pending
                DownloadManager.STATUS_PAUSED -> UpdateDownloadPhase.Waiting
                DownloadManager.STATUS_RUNNING -> UpdateDownloadPhase.Downloading
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (manager.getUriForDownloadedFile(id) != null && expectedArchive(version)) {
                        UpdateDownloadPhase.Complete
                    } else UpdateDownloadPhase.Failed
                }
                else -> UpdateDownloadPhase.Failed
            }
            UpdateDownload(
                id, version, phase,
                cursor.getLong(column(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                cursor.getLong(column(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }
    }

    fun enqueue(currentVersion: String, version: String, asset: AppUpdateApkAsset): UpdateDownload =
        synchronized(lock) {
            require(isAppUpdateAvailable(currentVersion, version))
            val previous = current(currentVersion)
            if (previous != null && previous.phase != UpdateDownloadPhase.Failed) return@synchronized previous
            val directory = requireNotNull(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
            val updates = File(directory, "updates")
            check(updates.isDirectory || updates.mkdirs())
            val path = "updates/${UUID.randomUUID()}.apk"
            val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
                .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, path)
                .setTitle(asset.name)
                .setMimeType(APK_MIME_TYPE)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            val id = manager.enqueue(request)
            if (!prefs.edit().putLong("id", id).putString("version", version).putString("path", path).commit()) {
                manager.remove(id)
                error("Could not save the update task")
            }
            if (previous != null) runCatching { manager.remove(previous.id) }
            UpdateDownload(id, version, UpdateDownloadPhase.Pending)
        }

    private fun expectedArchive(version: String): Boolean {
        val path = prefs.getString("path", null) ?: return false
        val directory = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
        val file = File(directory, path)
        if (!file.isFile) return false
        val archive = app.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return false
        return archive.packageName == app.packageName && archive.versionName == version
    }

    fun canRequestPackageInstalls(): Boolean = app.packageManager.canRequestPackageInstalls()

    fun openUnknownAppSourcesSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")))
    }

    fun launchInstaller(context: Context, download: UpdateDownload) {
        check(expectedArchive(download.version))
        val uri = checkNotNull(manager.getUriForDownloadedFile(download.id))
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    private companion object {
        val lock = Any()
    }
}
