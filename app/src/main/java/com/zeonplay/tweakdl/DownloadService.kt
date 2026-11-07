package com.zeonplay.tweakdl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class DownloadService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var jobActive = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_DOWNLOAD
        val updateChannel = intent?.getStringExtra(EXTRA_UPDATE_CHANNEL) ?: "stable"

        if (action == ACTION_STOP) {
            try {
                YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
            } catch (_: Exception) {
                // The downloader may not have started yet.
            }
            notifyUi(ACTION_ERROR, "Unduhan dibatalkan.")
            updateNotification("Unduhan dibatalkan", "Kamu menghentikan proses.", 0, false)
            if (!jobActive) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        if (jobActive) {
            notifyUi(ACTION_ERROR, "TweakDL sedang menjalankan proses lain.")
            return START_NOT_STICKY
        }

        jobActive = true
        val message = if (action == ACTION_UPDATE) {
            "Menyiapkan pembaruan yt-dlp…"
        } else {
            "Menyiapkan unduhan…"
        }
        startAsForeground(message)

        executor.execute {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)

                if (action == ACTION_UPDATE) updateEngine(updateChannel)
                else runDownload(intent ?: throw IllegalArgumentException("Permintaan unduhan kosong."))
            } catch (_: YoutubeDL.CanceledException) {
                notifyUi(ACTION_ERROR, "Unduhan dibatalkan.")
                updateNotification("Unduhan dibatalkan", "Kamu menghentikan proses.", 0, false)
            } catch (error: Exception) {
                val detail = (error.message ?: "Terjadi kesalahan saat memproses tautan.")
                    .replace('\n', ' ')
                    .take(360)
                val message = if (detail.contains("HTTP Error 403", ignoreCase = true)) {
                    detail + " | Situs menolak permintaan. Perbarui yt-dlp dari Pengaturan; bila masih gagal di YouTube, coba Nightly."
                } else {
                    detail
                }
                notifyUi(ACTION_ERROR, message)
                updateNotification("Proses gagal", message, 0, false)
            } finally {
                jobActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun updateEngine(channel: String) {
        val isNightly = channel == "nightly"
        val label = if (isNightly) "Nightly" else "Stable"
        val updateSource = if (isNightly) {
            YoutubeDL.UpdateChannel.NIGHTLY
        } else {
            YoutubeDL.UpdateChannel.STABLE
        }

        notifyUi(ACTION_PROGRESS, "Memeriksa versi yt-dlp " + label + "…", 0)
        val result = YoutubeDL.getInstance().updateYoutubeDL(this, updateSource)
        val version = YoutubeDL.versionName(this) ?: "versi terbaru"
        val message = when (result) {
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE ->
                "yt-dlp " + label + " sudah terbaru (" + version + ")."
            else ->
                "Engine yt-dlp " + label + " berhasil diperbarui (" + version + ")."
        }
        notifyUi(ACTION_COMPLETE, message, 100)
        updateNotification("Engine siap", message, 100, false)
    }

    private fun runDownload(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL)?.trim().orEmpty()
        if (url.isBlank()) throw IllegalArgumentException("Tautan kosong.")

        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "video"
        val quality = intent.getStringExtra(EXTRA_VIDEO_QUALITY) ?: "best"
        val audioFormat = intent.getStringExtra(EXTRA_AUDIO_FORMAT) ?: "mp3"
        val audioQuality = intent.getStringExtra(EXTRA_AUDIO_QUALITY) ?: "0"
        val customFormat = intent.getStringExtra(EXTRA_CUSTOM_FORMAT)?.trim().orEmpty()
        val playlist = intent.getBooleanExtra(EXTRA_PLAYLIST, false)
        val subtitles = intent.getBooleanExtra(EXTRA_SUBTITLES, false)
        val thumbnail = intent.getBooleanExtra(EXTRA_THUMBNAIL, true)

        val stagingRoot = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val stagingDir = File(stagingRoot, "TweakDL-staging")
        if (!stagingDir.exists() && !stagingDir.mkdirs()) {
            throw IOException("Tidak dapat membuat folder sementara.")
        }

        val startedAt = System.currentTimeMillis()
        val request = YoutubeDLRequest(url)
        request.addOption("--no-mtime")
        request.addOption("--newline")
        request.addOption("--no-warnings")
        request.addOption(
            "-o",
            File(stagingDir, "%(title).160s [%(id)s].%(ext)s").absolutePath
        )

        if (mediaType == "audio") {
            request.addOption("-x")
            request.addOption("--audio-format", audioFormat)
            request.addOption("--audio-quality", audioQuality)
        } else {
            val format = if (customFormat.isNotBlank()) {
                customFormat
            } else if (quality == "best") {
                "bestvideo*+bestaudio/best"
            } else {
                "bestvideo[height<=" + quality + "]+bestaudio/best[height<=" + quality + "]"
            }
            request.addOption("-f", format)
            request.addOption("--merge-output-format", "mp4")
        }

        if (playlist) request.addOption("--yes-playlist")
        else request.addOption("--no-playlist")

        if (thumbnail) {
            request.addOption("--embed-thumbnail")
            request.addOption("--embed-metadata")
        }
        if (subtitles && mediaType == "video") {
            request.addOption("--write-subs")
            request.addOption("--write-auto-subs")
            request.addOption("--sub-langs", "id,en.*,en")
            request.addOption("--embed-subs")
        }

        notifyUi(ACTION_PROGRESS, "Membaca informasi dari situs sumber…", 0)
        updateNotification("Mengunduh media", "Menghubungi situs sumber…", 0, true)

        YoutubeDL.getInstance().execute(request, PROCESS_ID, false) { progress, eta, line ->
            val percent = progress.toInt().coerceIn(0, 100)
            val detail = if (line.isNotBlank()) line
            else if (eta > 0) "Perkiraan waktu tersisa: " + eta + " detik"
            else "Sedang mengunduh…"
            notifyUi(ACTION_PROGRESS, detail, percent)
            updateNotification("Mengunduh media", detail, percent, true)
        }

        val outputFiles = stagingDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.lastModified() >= startedAt - 3_000 &&
                    isMediaOutput(file)
            }
            ?.sortedBy { it.lastModified() }
            .orEmpty()

        if (outputFiles.isEmpty()) {
            throw IOException("yt-dlp selesai, tetapi file media tidak ditemukan.")
        }

        val savedNames = ArrayList<String>()
        outputFiles.forEachIndexed { index, file ->
            notifyUi(
                ACTION_PROGRESS,
                "Menyimpan file " + (index + 1) + " dari " + outputFiles.size + "…",
                98
            )
            savedNames.add(copyToDownloads(file))
            file.delete()
        }

        // Remove sidecar subtitle and temporary metadata files after embedding/copying.
        stagingDir.listFiles()?.forEach { it.delete() }
        rememberHistory(savedNames)
        val summary = if (savedNames.size == 1) {
            "Selesai: " + savedNames.first() + " · Download/TweakDL"
        } else {
            savedNames.size.toString() + " file berhasil disimpan ke Download/TweakDL."
        }
        notifyUi(ACTION_COMPLETE, summary, 100)
        updateNotification("Unduhan selesai", summary, 100, false)
    }

    private fun isMediaOutput(file: File): Boolean {
        if (file.name.endsWith(".part") || file.name.endsWith(".ytdl")) return false
        val extension = file.extension.lowercase()
        return extension in setOf(
            "mp4", "mkv", "webm", "mov", "avi", "flv",
            "mp3", "m4a", "aac", "opus", "ogg", "flac", "wav"
        )
    }

    private fun copyToDownloads(source: File): String {
        val extension = source.extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"

        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/TweakDL"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw IOException("Tidak dapat membuat file di folder Download.")

            try {
                val output = contentResolver.openOutputStream(uri)
                    ?: throw IOException("Tidak dapat membuka file tujuan.")
                output.use { destination ->
                    source.inputStream().use { input -> input.copyTo(destination) }
                }
                val completed = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                contentResolver.update(uri, completed, null, null)
            } catch (error: Exception) {
                contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val publicDownloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val destinationDir = File(publicDownloads, "TweakDL")
            if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                throw IOException("Tidak dapat membuat folder Download/TweakDL.")
            }
            val destination = File(destinationDir, source.name)
            source.inputStream().use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return source.name
    }

    private fun rememberHistory(names: List<String>) {
        val preferences = getSharedPreferences("tweakdl_preferences", MODE_PRIVATE)
        val old = preferences.getString("history", "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
        val combined = (names.asReversed() + old).distinct().take(30)
        preferences.edit().putString("history", combined.joinToString("\n")).apply()
    }

    private fun notifyUi(action: String, message: String, progress: Int? = null) {
        val event = Intent(action).setPackage(packageName)
            .putExtra(EXTRA_MESSAGE, message)
        if (progress != null) event.putExtra(EXTRA_PROGRESS, progress)
        sendBroadcast(event)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Unduhan TweakDL",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status unduhan video dan audio."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun startAsForeground(message: String) {
        val notification = buildNotification("TweakDL", message, 0, true)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(
        title: String,
        message: String,
        progress: Int,
        ongoing: Boolean = true
    ) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(title, message, progress, ongoing)
        )
    }

    private fun buildNotification(
        title: String,
        message: String,
        progress: Int,
        ongoing: Boolean
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val stopIntent = Intent(this, DownloadService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this,
            711,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(message.take(110))
            .setStyle(Notification.BigTextStyle().bigText(message.take(350)))
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setProgress(100, progress.coerceIn(0, 100), ongoing && progress == 0)
            .setCategory(Notification.CATEGORY_PROGRESS)

        if (ongoing) {
            builder.addAction(
                Notification.Action.Builder(
                    R.drawable.ic_stat_download,
                    "Batal",
                    stopPending
                ).build()
            )
        }
        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_DOWNLOAD = "com.zeonplay.tweakdl.DOWNLOAD"
        const val ACTION_UPDATE = "com.zeonplay.tweakdl.UPDATE"
        const val ACTION_STOP = "com.zeonplay.tweakdl.STOP"
        const val ACTION_PROGRESS = "com.zeonplay.tweakdl.PROGRESS"
        const val ACTION_COMPLETE = "com.zeonplay.tweakdl.COMPLETE"
        const val ACTION_ERROR = "com.zeonplay.tweakdl.ERROR"

        const val EXTRA_URL = "url"
        const val EXTRA_MEDIA_TYPE = "media_type"
        const val EXTRA_VIDEO_QUALITY = "video_quality"
        const val EXTRA_AUDIO_FORMAT = "audio_format"
        const val EXTRA_AUDIO_QUALITY = "audio_quality"
        const val EXTRA_CUSTOM_FORMAT = "custom_format"
        const val EXTRA_PLAYLIST = "playlist"
        const val EXTRA_SUBTITLES = "subtitles"
        const val EXTRA_THUMBNAIL = "thumbnail"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_UPDATE_CHANNEL = "update_channel"

        private const val CHANNEL_ID = "tweakdl_downloads"
        private const val NOTIFICATION_ID = 7401
        private const val PROCESS_ID = "tweakdl_active_download"
    }
}
