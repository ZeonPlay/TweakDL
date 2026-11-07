# TweakDL

**TweakDL** adalah aplikasi Android untuk mengunduh video dan audio melalui mesin [yt-dlp](https://github.com/yt-dlp/yt-dlp).

Aplikasi ini dibuat untuk dua tipe pengguna:
- **Mudah** — tempel tautan, pilih Video atau Audio, lalu tekan tombol unduh.
- **Advanced** — atur batas resolusi, format audio, kualitas audio, ekspresi format yt-dlp, playlist, subtitle, dan thumbnail.

## Arsitektur

- **Bahasa aplikasi:** Kotlin.
- **Antarmuka:** Android Views native. Tidak memakai Compose, Flutter, atau WebView.
- **Mesin unduh:** [youtubedl-android 0.18.1](https://github.com/yausername/youtubedl-android), yang membungkus executable yt-dlp dan runtime Python untuk Android.
- **Pemrosesan media:** FFmpeg melalui modul Android dari proyek wrapper tersebut.
- **Unduhan latar belakang:** foreground service dengan notifikasi dan aksi batal.
- **Penyimpanan:** Download/TweakDL melalui MediaStore pada Android 10+, dan folder Downloads pada versi lebih lama.

TweakDL hanya menyediakan antarmuka. Pengenalan situs dan pemilihan extractor diserahkan ke yt-dlp, sehingga aplikasi tidak perlu memelihara daftar situs hard-coded.

## Ukuran aplikasi

Build awal hanya menyertakan ABI arm64-v8a, menghindari duplikasi runtime Python dan library native untuk arsitektur lain. UI dibuat dengan Android Views bawaan agar tidak menambah runtime UI besar.

Mesin yt-dlp tetap membutuhkan runtime Python, executable yt-dlp, dan FFmpeg. Karena itu ukuran APK final tidak akan sekecil aplikasi catatan biasa. GitHub Actions menghasilkan APK arm64 untuk debug dan release.

## Mulai build

Buka repository ini melalui Android Studio, pastikan Android SDK Platform 36 dan JDK 17 tersedia, lalu jalankan Gradle Sync.

Dari terminal dengan Gradle 8.13 dan Android SDK Platform 36:

~~~sh
gradle assembleDebug
gradle assembleRelease
~~~

APK berada di app/build/outputs/apk/.

GitHub Actions juga membangun APK secara otomatis saat ada push atau pull request. File APK tersedia sebagai workflow artifact.

## Menggunakan aplikasi

1. Tempel tautan, atau bagikan tautan dari aplikasi lain ke TweakDL.
2. Pilih **Video** atau **Audio**.
3. Tekan **Unduh video** atau **Unduh audio**.
4. File yang selesai akan muncul di Download/TweakDL.

Mode Advanced memberi kontrol tambahan. Ekspresi format khusus diteruskan sebagai satu argumen -f ke yt-dlp; aplikasi tidak menjalankan teks tersebut sebagai perintah shell.

## Dukungan situs

Daftar extractor mengikuti versi yt-dlp yang tertanam/terpasang. Lihat [daftar situs resmi yt-dlp](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md).

Daftar extractor bukan jaminan semua tautan selalu berhasil. Situs dapat mengubah sistemnya, sebagian extractor bisa rusak sementara, dan beberapa konten membutuhkan autentikasi atau izin khusus. Gunakan hanya untuk konten yang kamu miliki atau yang memang diizinkan untuk diunduh.

## Kredit

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) — proyek utama yang menangani extractor, pengambilan metadata, dan unduhan. Lisensi upstream: Unlicense.
- [youtubedl-android](https://github.com/yausername/youtubedl-android) — wrapper dan paket runtime Android. Lisensi upstream: GPL-3.0.
- [FFmpeg](https://ffmpeg.org/) — pemrosesan dan penggabungan media. Komponen FFmpeg mengikuti lisensi build upstream masing-masing.

TweakDL menggunakan lisensi **GNU GPL v3**. Lisensi dan ketentuan komponen pihak ketiga tetap berlaku; lihat repository upstream untuk detail dan pemberitahuan lisensinya.

## Privasi

TweakDL tidak membutuhkan akun TweakDL, tidak mengirim URL ke server milik TweakDL, dan tidak menyertakan analitik. URL diproses secara lokal oleh yt-dlp, yang kemudian berkomunikasi langsung dengan situs sumber.

## Catatan rilis awal

- Mode Mudah dan Advanced.
- Unduh video dan ekstraksi audio.
- Opsi kualitas dan format dasar.
- Dukungan playlist dan subtitle.
- Thumbnail/metadata bila didukung situs dan format.
- Unduhan latar belakang dengan notifikasi.
- Riwayat file yang berhasil disimpan.
- Pembaruan yt-dlp stable dari Pengaturan.
