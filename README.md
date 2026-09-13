# Dola HD Downloader — Ekstensi Chrome

Ekstensi Manifest V3 untuk:

- mendeteksi URL video dari respons pemutaran Dola dan elemen `<video>`;
- memilih MP4 beresolusi tertinggi yang tersedia;
- mengunduh melalui Chrome Downloads;
- menyembunyikan kontrol filter wajah/portrait agar tidak terpakai tanpa sengaja;
- **mode bypass filter wajah**: mengosongkan pengaturan filter wajah (face filter, beautify, retouch, portrait, dll.) dari body permintaan API Dola di sesi pengguna sendiri, sambil tetap mengirim referensi foto wajah (URL/ID/berkas unggahan). Hasilnya, video generasi yang memakai wajah manusia asli tidak menerapkan efek filter wajah Dola.

Ekstensi ini tidak membuka autentikasi, tidak menghapus watermark secara paksa, dan hanya bekerja dengan media serta sesi Dola pengguna sendiri. Mode bypass adalah preferensi "tanpa efek filter wajah" pada konten milik pengguna — bukan penerobosan izin orang lain. Gerbang persetujuan Dola (mis. `creation_portrait_video_auth_confirm`) tidak dilewati otomatis.

## Pasang untuk uji coba

1. Buka `chrome://extensions`.
2. Aktifkan **Developer mode**.
3. Klik **Load unpacked** dan pilih direktori repositori ini.
4. Buka Dola dan selesaikan atau putar video.
5. Klik **Download HD** pada panel kanan bawah.

Jika Dola hanya memberikan stream HLS (`.m3u8`), ekstensi menolak mengunduh manifest sebagai video palsu dan meminta pengguna memutar video sampai URL MP4 tersedia.

## Mode bypass filter wajah

Di popup ekstensi (ikon Dola HD), aktifkan **Bypass filter wajah** (bawaan: aktif).

Cara kerja: skrip di halaman (`page-bridge.js`) menyadap `fetch` dan `XMLHttpRequest` Dola pada sesi pengguna sendiri, lalu memangkas pengaturan filter wajah di body permintaan yang menuju host `dola.com` — mencakup body JSON, form-urlencoded, dan unggahan multipart. Yang dibuang hanya kunci pengaturan efek: `face_filter`, `beautify`, `beauty_level`, `retouch`, `portrait` (mode), dsb. Referensi foto (mis. `face_url`, `portrait_url`, `photo`, berkas unggahan di multipart) **tetap dipertahankan**, sehingga wajah asli tetap terpakai tanpa efek filter.

Catatan: karena payload generasi Dola tidak mendokumentasikan nama parameter secara publik, pola ini disetel ke istilah wajah/filter yang umum; verifikasi akhir dilakukan langsung di Dola dari wilayah yang tidak dibatasi.

## Pengujian

```bash
npm test
```