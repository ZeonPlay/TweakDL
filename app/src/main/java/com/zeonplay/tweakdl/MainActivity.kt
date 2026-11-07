package com.zeonplay.tweakdl

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val preferences by lazy {
        getSharedPreferences("tweakdl_preferences", Context.MODE_PRIVATE)
    }

    private var page = PAGE_DOWNLOAD
    private var advancedMode = false
    private var mediaType = TYPE_VIDEO
    private var urlValue = ""
    private var videoQuality = "best"
    private var audioFormat = "mp3"
    private var audioQuality = "0"
    private var customFormat = ""
    private var includePlaylist = false
    private var includeSubtitles = false
    private var embedThumbnail = true
    private var isBusy = false
    private var pendingStart = false
    private var progressValue = 0
    private var statusMessage = "Siap menerima tautan."

    private var urlField: EditText? = null
    private var statusLabel: TextView? = null
    private var progressBar: ProgressBar? = null
    private var downloadButton: Button? = null

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DownloadService.ACTION_PROGRESS -> {
                    isBusy = true
                    progressValue = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0)
                    statusMessage = intent.getStringExtra(DownloadService.EXTRA_MESSAGE)
                        ?: "Sedang mengunduh…"
                    updateProgressViews()
                }
                DownloadService.ACTION_COMPLETE -> {
                    isBusy = false
                    progressValue = 100
                    statusMessage = intent.getStringExtra(DownloadService.EXTRA_MESSAGE)
                        ?: "Selesai."
                    updateProgressViews()
                    if (page == PAGE_HISTORY) render()
                }
                DownloadService.ACTION_ERROR -> {
                    isBusy = false
                    statusMessage = intent.getStringExtra(DownloadService.EXTRA_MESSAGE)
                        ?: "Unduhan gagal."
                    updateProgressViews()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BACKGROUND
        window.navigationBarColor = COLOR_BACKGROUND
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        advancedMode = preferences.getBoolean("advanced_mode", false)
        mediaType = preferences.getString("media_type", TYPE_VIDEO) ?: TYPE_VIDEO
        videoQuality = preferences.getString("video_quality", "best") ?: "best"
        audioFormat = preferences.getString("audio_format", "mp3") ?: "mp3"
        audioQuality = preferences.getString("audio_quality", "0") ?: "0"
        customFormat = preferences.getString("custom_format", "") ?: ""
        includePlaylist = preferences.getBoolean("include_playlist", false)
        includeSubtitles = preferences.getBoolean("include_subtitles", false)
        embedThumbnail = preferences.getBoolean("embed_thumbnail", true)

        registerDownloadReceiver()
        render()
        acceptSharedText(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptSharedText(intent)
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter().apply {
            addAction(DownloadService.ACTION_PROGRESS)
            addAction(DownloadService.ACTION_COMPLETE)
            addAction(DownloadService.ACTION_ERROR)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadReceiver, filter)
        }
    }

    private fun acceptSharedText(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return
        val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (sharedText.isBlank()) return
        val matchedUrl = Regex("https?://[^\\s]+").find(sharedText)?.value ?: sharedText
        urlValue = matchedUrl.trim().trimEnd('.', ',', ')', ']', '}')
        urlField?.setText(urlValue)
    }

    private fun render() {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
        }
        if (Build.VERSION.SDK_INT >= 35) {
            shell.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(26))
        }

        addHeader(content)
        when (page) {
            PAGE_DOWNLOAD -> renderDownloadPage(content)
            PAGE_HISTORY -> renderHistoryPage(content)
            PAGE_SETTINGS -> renderSettingsPage(content)
        }

        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        shell.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        shell.addView(buildNavigation(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(66)
        ))
        setContentView(shell)
        if (Build.VERSION.SDK_INT >= 35) shell.requestApplyInsets()
    }

    private fun addHeader(parent: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val mark = TextView(this).apply {
            text = "↧"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(COLOR_BACKGROUND)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(COLOR_ACCENT, 17f)
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(46), dp(46)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brand.addView(makeText("TweakDL", 22f, COLOR_TEXT, true))
        brand.addView(makeText("VIDEO  /  AUDIO DOWNLOADER", 9f, COLOR_MUTED, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))

        val settings = makeButton("⚙", false) {
            page = PAGE_SETTINGS
            render()
        }.apply {
            textSize = 18f
            minWidth = dp(46)
            minHeight = dp(46)
            setPadding(0, 0, 0, 0)
        }
        header.addView(settings)
        parent.addView(header, sectionParams(0))
        addText(parent, "Satu tautan. Beres.", 29f, COLOR_TEXT, true, 8)
        addText(
            parent,
            "Simpan video atau audio dari platform yang didukung yt-dlp.",
            14f,
            COLOR_MUTED,
            false,
            22
        )
    }

    private fun renderDownloadPage(parent: LinearLayout) {
        val modePanel = makePanel()
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        modeRow.addView(
            makeModeButton("Mudah", !advancedMode) {
                advancedMode = false
                preferences.edit().putBoolean("advanced_mode", false).apply()
                render()
            },
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )
        modeRow.addView(
            makeModeButton("Advanced", advancedMode) {
                advancedMode = true
                preferences.edit().putBoolean("advanced_mode", true).apply()
                render()
            },
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )
        modePanel.addView(modeRow)
        modePanel.addView(makeText(
            if (advancedMode) "Kontrol kualitas dan opsi yt-dlp."
            else "Tempel tautan, pilih Video atau Audio, lalu unduh.",
            12f,
            COLOR_MUTED
        ).apply { setPadding(dp(8), dp(4), dp(8), dp(5)) })
        parent.addView(modePanel, sectionParams(18))

        val linkPanel = makePanel()
        addCaption(linkPanel, "TAUTAN SUMBER")
        val linkRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val edit = EditText(this).apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(COLOR_TEXT)
            setHintTextColor(COLOR_MUTED)
            hint = "Tempel tautan video atau audio"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            background = rounded(COLOR_FIELD, 14f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setText(urlValue)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    urlValue = s?.toString().orEmpty()
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        urlField = edit
        linkRow.addView(edit, LinearLayout.LayoutParams(0, dp(54), 1f))
        val paste = makeButton("Tempel", false) {
            pasteFromClipboard()
        }.apply {
            textSize = 12f
            minHeight = dp(46)
            setPadding(dp(12), 0, dp(12), 0)
        }
        val pasteParams = LinearLayout.LayoutParams(-2, dp(46))
        pasteParams.marginStart = dp(8)
        linkRow.addView(paste, pasteParams)
        linkPanel.addView(linkRow)
        val shareHint = addText(
            linkPanel,
            "Bisa juga bagikan tautan dari YouTube, browser, atau aplikasi lain ke TweakDL.",
            11f,
            COLOR_MUTED
        )
        shareHint.setPadding(0, dp(10), 0, 0)
        parent.addView(linkPanel, sectionParams(14))

        val typePanel = makePanel()
        addCaption(typePanel, "SIMPAN SEBAGAI")
        val typeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        typeRow.addView(
            makeChoiceButton("▶  Video", mediaType == TYPE_VIDEO) {
                mediaType = TYPE_VIDEO
                preferences.edit().putString("media_type", mediaType).apply()
                render()
            },
            LinearLayout.LayoutParams(0, dp(54), 1f)
        )
        typeRow.addView(
            makeChoiceButton("♫  Audio", mediaType == TYPE_AUDIO) {
                mediaType = TYPE_AUDIO
                preferences.edit().putString("media_type", mediaType).apply()
                render()
            },
            LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                marginStart = dp(10)
            }
        )
        typePanel.addView(typeRow)
        if (!advancedMode) {
            addText(
                typePanel,
                if (mediaType == TYPE_VIDEO) "Kualitas video terbaik yang tersedia akan dipilih otomatis."
                else "Audio akan disimpan sebagai MP3.",
                12f,
                COLOR_MUTED
            ).setPadding(0, dp(10), 0, 0)
        }
        parent.addView(typePanel, sectionParams(14))

        if (advancedMode) renderAdvancedOptions(parent)

        val cta = makeButton(
            if (mediaType == TYPE_VIDEO) "Unduh video" else "Unduh audio",
            true
        ) {
            beginDownload()
        }.apply {
            textSize = 16f
            minHeight = dp(58)
            isEnabled = !isBusy
            alpha = if (isBusy) 0.55f else 1f
        }
        downloadButton = cta
        parent.addView(cta, sectionParams(14))

        val statusPanel = makePanel()
        val statusHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusHeader.addView(makeText("STATUS UNDUHAN", 10f, COLOR_MUTED, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        statusHeader.addView(makeText(
            if (isBusy) "BERJALAN" else "SIAP",
            9f,
            if (isBusy) COLOR_ACCENT else COLOR_SUCCESS,
            true
        ))
        statusPanel.addView(statusHeader)
        statusLabel = makeText(statusMessage, 13f, COLOR_TEXT)
        statusPanel.addView(statusLabel)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = progressValue
            progressTintList = android.content.res.ColorStateList.valueOf(COLOR_ACCENT)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(COLOR_FIELD)
        }
        progressBar = bar
        val barParams = LinearLayout.LayoutParams(-1, dp(5))
        barParams.topMargin = dp(14)
        statusPanel.addView(bar, barParams)
        parent.addView(statusPanel, sectionParams(14))

        addText(parent, "File selesai akan masuk ke Download/TweakDL.", 11f, COLOR_MUTED, false, 4)
    }

    private fun renderAdvancedOptions(parent: LinearLayout) {
        val card = makePanel()
        addCaption(card, "KONTROL LANJUTAN")

        if (mediaType == TYPE_VIDEO) {
            addSettingRow(
                card,
                "Resolusi maksimum",
                "Video di atas batas ini akan dilewati.",
                qualityLabel(videoQuality)
            ) {
                chooseOption(
                    "Resolusi video",
                    arrayOf("Terbaik tersedia", "2160p", "1440p", "1080p", "720p", "480p"),
                    arrayOf("best", "2160", "1440", "1080", "720", "480"),
                    videoQuality
                ) {
                    videoQuality = it
                    preferences.edit().putString("video_quality", it).apply()
                    render()
                }
            }

            val formatEdit = EditText(this).apply {
                setSingleLine(true)
                textSize = 13f
                setTextColor(COLOR_TEXT)
                setHintTextColor(COLOR_MUTED)
                hint = "Format yt-dlp khusus (opsional), contoh: bv*+ba/b"
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                background = rounded(COLOR_FIELD, 13f)
                setPadding(dp(13), dp(12), dp(13), dp(12))
                setText(customFormat)
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        customFormat = s?.toString().orEmpty()
                        preferences.edit().putString("custom_format", customFormat).apply()
                    }
                    override fun afterTextChanged(s: android.text.Editable?) = Unit
                })
            }
            val formatParams = LinearLayout.LayoutParams(-1, dp(50))
            formatParams.topMargin = dp(10)
            card.addView(formatEdit, formatParams)
            addText(card, "Ekspresi ini diteruskan sebagai opsi -f ke yt-dlp.", 10f, COLOR_MUTED, false, 10)
        } else {
            addSettingRow(
                card,
                "Format audio",
                "Format hasil ekstraksi.",
                audioFormat.uppercase()
            ) {
                chooseOption(
                    "Format audio",
                    arrayOf("MP3", "M4A", "OPUS", "WAV"),
                    arrayOf("mp3", "m4a", "opus", "wav"),
                    audioFormat
                ) {
                    audioFormat = it
                    preferences.edit().putString("audio_format", it).apply()
                    render()
                }
            }
            addSettingRow(
                card,
                "Kualitas audio",
                "0 = kualitas tertinggi, 9 = ukuran lebih kecil.",
                audioQualityLabel(audioQuality)
            ) {
                chooseOption(
                    "Kualitas audio",
                    arrayOf("Terbaik (0)", "Seimbang (5)", "Hemat (9)"),
                    arrayOf("0", "5", "9"),
                    audioQuality
                ) {
                    audioQuality = it
                    preferences.edit().putString("audio_quality", it).apply()
                    render()
                }
            }
        }

        addSwitchRow(card, "Unduh seluruh playlist", "Jika tautan berisi playlist.", includePlaylist) {
            includePlaylist = it
            preferences.edit().putBoolean("include_playlist", it).apply()
        }
        if (mediaType == TYPE_VIDEO) {
            addSwitchRow(card, "Sertakan subtitle", "Unduh subtitle yang tersedia.", includeSubtitles) {
                includeSubtitles = it
                preferences.edit().putBoolean("include_subtitles", it).apply()
            }
        }
        addSwitchRow(card, "Simpan thumbnail", "Tambahkan gambar sampul dan metadata jika didukung.", embedThumbnail) {
            embedThumbnail = it
            preferences.edit().putBoolean("embed_thumbnail", it).apply()
        }

        addText(
            card,
            "Tidak semua situs menyediakan semua kualitas atau subtitle. Opsi yang tidak tersedia akan diabaikan oleh situs sumber atau yt-dlp.",
            10f,
            COLOR_MUTED
        ).setPadding(0, dp(8), 0, 0)
        parent.addView(card, sectionParams(14))
    }

    private fun renderHistoryPage(parent: LinearLayout) {
        addText(parent, "Aktivitas unduhan", 23f, COLOR_TEXT, true, 6)
        addText(parent, "File yang berhasil disimpan dari TweakDL.", 13f, COLOR_MUTED, false, 18)

        val history = preferences.getString("history", "").orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (history.isEmpty()) {
            val empty = makePanel()
            addText(empty, "Belum ada unduhan.", 16f, COLOR_TEXT, true, 6)
            addText(empty, "File yang selesai diunduh akan muncul di sini.", 12f, COLOR_MUTED)
            parent.addView(empty)
        } else {
            history.forEachIndexed { index, filename ->
                val item = makePanel()
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val glyph = makeText("↓", 18f, COLOR_ACCENT, true).apply {
                    gravity = Gravity.CENTER
                    background = rounded(COLOR_FIELD, 12f)
                }
                row.addView(glyph, LinearLayout.LayoutParams(dp(42), dp(42)))
                val name = makeText(filename, 12f, COLOR_TEXT, true).apply {
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(12), 0, 0, 0)
                }
                row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
                item.addView(row)
                parent.addView(item, sectionParams(if (index == history.lastIndex) 0 else 10))
            }
        }
        parent.addView(makeButton("Pilih folder unduhan", false) {
            openDownloadsFolder()
        }, sectionParams(12))
    }

    private fun renderSettingsPage(parent: LinearLayout) {
        addText(parent, "Pengaturan", 23f, COLOR_TEXT, true, 6)
        addText(parent, "Mesin unduh, dukungan situs, dan informasi aplikasi.", 13f, COLOR_MUTED, false, 18)

        val engine = makePanel()
        addCaption(engine, "MESIN UNDUH")
        addText(engine, "yt-dlp", 18f, COLOR_TEXT, true, 4)
        addText(
            engine,
            "TweakDL memakai youtubedl-android 0.18.1. Perbarui engine untuk mendapatkan perbaikan extractor terbaru.",
            12f,
            COLOR_MUTED,
            false,
            14
        )
        engine.addView(makeButton("Perbarui yt-dlp (stable)", true) { updateEngine() })
        parent.addView(engine, sectionParams(14))

        val supported = makePanel()
        addCaption(supported, "DAFTAR SITUS")
        addText(supported, "Lihat daftar extractor resmi yt-dlp.", 14f, COLOR_TEXT, true, 5)
        addText(
            supported,
            "Daftar berubah mengikuti versi yt-dlp. Situs yang tercantum pun tidak selalu dapat diunduh setiap saat.",
            11f,
            COLOR_MUTED,
            false,
            12
        )
        supported.addView(makeButton("Buka daftar situs yang didukung", false) {
            openExternal("https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md")
        })
        parent.addView(supported, sectionParams(14))

        val credits = makePanel()
        addCaption(credits, "KREDIT & LISENSI")
        addText(credits, "TweakDL", 16f, COLOR_TEXT, true, 3)
        addText(credits, "Antarmuka Android untuk yt-dlp.", 12f, COLOR_MUTED, false, 10)
        addCreditLink(credits, "yt-dlp · Unlicense", "https://github.com/yt-dlp/yt-dlp")
        addCreditLink(credits, "youtubedl-android · GPL-3.0", "https://github.com/yausername/youtubedl-android")
        addCreditLink(credits, "FFmpeg", "https://ffmpeg.org/")
        addText(
            credits,
            "TweakDL didistribusikan di bawah GNU GPL v3, tanpa garansi. Buka teks lisensi lengkap dari tautan berikut.",
            11f,
            COLOR_MUTED,
            false,
            6
        )
        addCreditLink(
            credits,
            "Baca GNU GPL v3",
            "https://github.com/ZeonPlay/TweakDL/blob/main/LICENSE"
        )
        parent.addView(credits, sectionParams(14))

        val privacy = makePanel()
        addCaption(privacy, "PRIVASI & PENGGUNAAN")
        addText(
            privacy,
            "TweakDL tidak memakai akun, analitik, atau server perantara. Tautan yang kamu masukkan diproses oleh yt-dlp dan situs sumbernya.",
            12f,
            COLOR_TEXT,
            false,
            10
        )
        addText(
            privacy,
            "Unduh hanya konten yang kamu miliki atau yang memang diizinkan untuk diunduh. Batasan akses dari situs tetap berlaku.",
            11f,
            COLOR_MUTED
        )
        parent.addView(privacy)
    }

    private fun addCreditLink(parent: LinearLayout, label: String, url: String) {
        val link = makeText("↗  " + label, 12f, COLOR_ACCENT, true).apply {
            setPadding(0, dp(8), 0, dp(5))
            isClickable = true
            isFocusable = true
            setOnClickListener { openExternal(url) }
        }
        parent.addView(link)
    }

    private fun buildNavigation(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(5), dp(14), dp(5))
            background = rounded(COLOR_SURFACE, 0f, topOnly = true)
        }
        nav.addView(navItem("↓", "Unduh", PAGE_DOWNLOAD), LinearLayout.LayoutParams(0, -1, 1f))
        nav.addView(navItem("≡", "Riwayat", PAGE_HISTORY), LinearLayout.LayoutParams(0, -1, 1f))
        nav.addView(navItem("⚙", "Pengaturan", PAGE_SETTINGS), LinearLayout.LayoutParams(0, -1, 1f))
        return nav
    }

    private fun navItem(icon: String, label: String, target: String): View {
        val selected = page == target
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener {
                page = target
                render()
            }
        }
        item.addView(makeText(icon, 18f, if (selected) COLOR_ACCENT else COLOR_MUTED, true).apply {
            gravity = Gravity.CENTER
        })
        item.addView(makeText(label, 10f, if (selected) COLOR_TEXT else COLOR_MUTED, selected).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        })
        return item
    }

    private fun makeModeButton(label: String, selected: Boolean, action: () -> Unit): Button {
        return makeButton(label, selected, action).apply {
            minHeight = dp(42)
            setPadding(dp(8), 0, dp(8), 0)
        }
    }

    private fun makeChoiceButton(label: String, selected: Boolean, action: () -> Unit): Button {
        return makeButton(label, selected, action).apply {
            minHeight = dp(52)
            textSize = 14f
        }
    }

    private fun addSettingRow(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        value: String,
        action: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, dp(9))
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        copy.addView(makeText(title, 13f, COLOR_TEXT, true))
        copy.addView(makeText(subtitle, 10f, COLOR_MUTED).apply {
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(makeButton(value, false, action).apply {
            textSize = 11f
            minHeight = dp(38)
            setPadding(dp(12), 0, dp(12), 0)
        })
        parent.addView(row)
    }

    private fun addSwitchRow(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, dp(9))
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        copy.addView(makeText(title, 13f, COLOR_TEXT, true))
        copy.addView(makeText(subtitle, 10f, COLOR_MUTED).apply {
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }
        row.addView(toggle)
        parent.addView(row)
    }

    private fun chooseOption(
        title: String,
        labels: Array<String>,
        values: Array<String>,
        currentValue: String,
        onSelected: (String) -> Unit
    ) {
        val selectedIndex = values.indexOf(currentValue).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                onSelected(values[which])
                dialog.dismiss()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun qualityLabel(value: String): String {
        return when (value) {
            "best" -> "Terbaik"
            else -> value + "p"
        }
    }

    private fun audioQualityLabel(value: String): String {
        return when (value) {
            "0" -> "Terbaik"
            "5" -> "Seimbang"
            else -> "Hemat"
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val pasted = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        } else {
            ""
        }
        if (pasted.isBlank()) {
            Toast.makeText(this, "Clipboard kosong.", Toast.LENGTH_SHORT).show()
            return
        }
        val matchedUrl = Regex("https?://[^\\s]+").find(pasted)?.value ?: pasted
        urlValue = matchedUrl.trim().trimEnd('.', ',', ')', ']', '}')
        urlField?.setText(urlValue)
    }

    private fun beginDownload() {
        val parsed = Uri.parse(urlValue.trim())
        if ((parsed.scheme != "https" && parsed.scheme != "http") || parsed.host.isNullOrBlank()) {
            Toast.makeText(this, "Masukkan tautan http atau https yang valid.", Toast.LENGTH_LONG).show()
            return
        }
        if (isBusy) {
            Toast.makeText(this, "Masih ada proses yang berjalan.", Toast.LENGTH_SHORT).show()
            return
        }

        val needed = ArrayList<String>()
        if (Build.VERSION.SDK_INT <= 28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            pendingStart = true
            requestPermissions(needed.toTypedArray(), REQUEST_PERMISSIONS)
            return
        }
        startDownloadService()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS || !pendingStart) return
        pendingStart = false
        val storageDenied = Build.VERSION.SDK_INT <= 28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (storageDenied) {
            Toast.makeText(this, "Izin penyimpanan diperlukan pada Android 9 ke bawah.", Toast.LENGTH_LONG).show()
            return
        }
        startDownloadService()
    }

    private fun startDownloadService() {
        isBusy = true
        progressValue = 0
        statusMessage = "Menyiapkan yt-dlp…"
        updateProgressViews()

        val serviceIntent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_DOWNLOAD
            putExtra(DownloadService.EXTRA_URL, urlValue.trim())
            putExtra(DownloadService.EXTRA_MEDIA_TYPE, mediaType)
            putExtra(DownloadService.EXTRA_VIDEO_QUALITY, videoQuality)
            putExtra(DownloadService.EXTRA_AUDIO_FORMAT, audioFormat)
            putExtra(DownloadService.EXTRA_AUDIO_QUALITY, audioQuality)
            putExtra(DownloadService.EXTRA_CUSTOM_FORMAT, customFormat)
            putExtra(DownloadService.EXTRA_PLAYLIST, includePlaylist)
            putExtra(DownloadService.EXTRA_SUBTITLES, includeSubtitles)
            putExtra(DownloadService.EXTRA_THUMBNAIL, embedThumbnail)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        } catch (error: Exception) {
            isBusy = false
            statusMessage = error.message ?: "Tidak dapat memulai unduhan."
            updateProgressViews()
        }
    }

    private fun updateEngine() {
        if (isBusy) {
            Toast.makeText(this, "Tunggu proses yang sedang berjalan selesai.", Toast.LENGTH_SHORT).show()
            return
        }
        isBusy = true
        progressValue = 0
        statusMessage = "Memeriksa dan memperbarui yt-dlp…"
        updateProgressViews()
        val updateIntent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_UPDATE
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(updateIntent)
            else startService(updateIntent)
        } catch (error: Exception) {
            isBusy = false
            statusMessage = error.message ?: "Pembaruan gagal dimulai."
            updateProgressViews()
        }
    }

    private fun updateProgressViews() {
        statusLabel?.text = statusMessage
        progressBar?.progress = progressValue
        downloadButton?.isEnabled = !isBusy
        downloadButton?.alpha = if (isBusy) 0.55f else 1f
    }

    private fun openDownloadsFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (Build.VERSION.SDK_INT >= 26) {
            val initial = Uri.parse(
                "content://com.android.externalstorage.documents/document/primary%3ADownload%2FTweakDL"
            )
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Buka Download/TweakDL dari aplikasi File.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka tautan.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun makePanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(16), dp(17), dp(16))
            background = rounded(COLOR_SURFACE, 21f)
        }
    }

    private fun addCaption(parent: LinearLayout, value: String) {
        val caption = makeText(value, 10f, COLOR_MUTED, true).apply {
            letterSpacing = 0.08f
            setPadding(0, 0, 0, dp(12))
        }
        parent.addView(caption)
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        bottom: Int = 0
    ): TextView {
        val view = makeText(value, size, color, bold)
        parent.addView(view, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(bottom)
        })
        return view
    }

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            includeFontPadding = true
        }
    }

    private fun makeButton(
        label: String,
        selected: Boolean,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(if (selected) COLOR_BACKGROUND else COLOR_TEXT)
            background = rounded(
                if (selected) COLOR_ACCENT else COLOR_FIELD,
                14f,
                if (!selected) COLOR_STROKE else null
            )
            setPadding(dp(14), dp(8), dp(14), dp(8))
            minHeight = dp(48)
            stateListAnimator = null
            elevation = 0f
            setOnClickListener { action() }
        }
    }

    private fun rounded(
        fill: Int,
        radiusDp: Float,
        stroke: Int? = null,
        topOnly: Boolean = false
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadii = if (topOnly) {
                floatArrayOf(
                    dp(18).toFloat(), dp(18).toFloat(),
                    dp(18).toFloat(), dp(18).toFloat(),
                    0f, 0f, 0f, 0f
                )
            } else {
                FloatArray(8) { dp(radiusDp.toInt()).toFloat() }
            }
            if (stroke != null) setStroke(dp(1), stroke)
        }
    }

    private fun sectionParams(bottom: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(bottom)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {
            // Receiver may already be unregistered during teardown.
        }
        super.onDestroy()
    }

    companion object {
        private const val PAGE_DOWNLOAD = "download"
        private const val PAGE_HISTORY = "history"
        private const val PAGE_SETTINGS = "settings"
        private const val TYPE_VIDEO = "video"
        private const val TYPE_AUDIO = "audio"
        private const val REQUEST_PERMISSIONS = 9401

        private val COLOR_BACKGROUND = Color.rgb(13, 10, 20)
        private val COLOR_SURFACE = Color.rgb(25, 21, 35)
        private val COLOR_FIELD = Color.rgb(35, 29, 49)
        private val COLOR_STROKE = Color.rgb(61, 51, 80)
        private val COLOR_ACCENT = Color.rgb(184, 156, 255)
        private val COLOR_TEXT = Color.rgb(246, 242, 252)
        private val COLOR_MUTED = Color.rgb(168, 158, 185)
        private val COLOR_SUCCESS = Color.rgb(145, 220, 177)
    }
}
