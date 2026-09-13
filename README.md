# Sesi Browser × Introvert Dreams MAX MODE

Satu APK: **Sesi Browser v6.1.3** (multi-sesi, anti-fingerprint, multi-tab, unduhan) digabung dengan
**Introvert Dreams MAX MODE v2.3** (Dola companion: 1 video × 30s, Max HD, tanpa watermark, kunci Seedance 2.5,
akun Google tersimpan & autofill).

## Cara kerja gabungan
- Setiap tab Sesi Browser memuat skrip MAX MODE di `document_start` **hanya** untuk `dola.com` / `seaart.ai`
  (setelah `spoof.js`, jadi identitas sesi tetap konsisten). Situs lain tidak tersentuh dan jembatan JS
  disembunyikan supaya tidak jadi penanda fingerprint.
- `background.js` ekstensi berjalan di WebView tersembunyi (`assets/background.html`); `popup.html` dibuka
  sebagai layar penuh dari tombol MAX MODE (muncul di kanan-bawah saat tab aktif di Dola/Seaart) atau dari
  Pengaturan → *Introvert Dreams MAX MODE* (tahan = buka Dola di tab baru).
- Unduhan dari ekstensi (`chrome.downloads`) masuk ke `Download/Whempy_Videos/` dan ikut tampil di daftar
  unduhan Sesi Browser.
- Akun Google tersimpan terenkripsi (Android Keystore, AES/GCM) — sama seperti MAX MODE asli.

## Build
```
ANDROID_HOME=/path/ke/sdk bash build.sh      # butuh JDK 11+, build-tools 34, platform android-34
```
Hasil: `SesiBrowser-MAXMODE.apk` (ditandatangani dengan keystore `$KEYSTORE` atau `~/sesibrowser.keystore`).
APK siap pakai ada di `dist/`.
