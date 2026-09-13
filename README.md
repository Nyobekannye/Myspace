# Dola HD Downloader — Ekstensi Chrome

Ekstensi Manifest V3 untuk:

- mendeteksi URL video dari respons pemutaran Dola dan elemen `<video>`;
- memilih MP4 beresolusi tertinggi yang tersedia;
- mengunduh melalui Chrome Downloads;
- menyembunyikan kontrol filter wajah/portrait agar tidak terpakai tanpa sengaja;
- **mode bypass filter wajah**: menetralkan parameter wajah (`face_filter`, `portrait`, `beautify`, `retouch`, dll.) pada body JSON permintaan API Dola di sesi pengguna sendiri, sehingga hasil generasi video tidak menerapkan filter wajah.

Ekstensi ini tidak membuka autentikasi, tidak mengubah status otorisasi pengguna lain, tidak menghapus watermark secara paksa, dan hanya bekerja dengan media serta sesi Dola pengguna sendiri. Mode bypass adalah preferensi "tanpa efek wajah" pada sesi milik pengguna, bukan penerobosan izin.

## Pasang untuk uji coba

1. Buka `chrome://extensions`.
2. Aktifkan **Developer mode**.
3. Klik **Load unpacked** dan pilih direktori repositori ini.
4. Buka Dola dan selesaikan atau putar video.
5. Klik **Download HD** pada panel kanan bawah.

Jika Dola hanya memberikan stream HLS (`.m3u8`), ekstensi menolak mengunduh manifest sebagai video palsu dan meminta pengguna memutar video sampai URL MP4 tersedia.

## Mode bypass filter wajah

Di popup ekstensi (ikon Dola HD), aktifkan **Bypass filter wajah** (bawaan: aktif).

Cara kerja: skrip di halaman (`page-bridge.js`) menyadap `fetch` dan `XMLHttpRequest` Dola, lalu menghapus atau menonaktifkan parameter terkait wajah pada body JSON yang dikirim ke host `dola.com`. Parameter yang ditargetkan cocok dengan istilah seperti `face`, `face_filter`, `face_swap`, `portrait`, `beautify`, `beauty`, dan `retouch`. Kunci lain seperti `prompt`, `config`, `video`, dan `profile` tidak disentuh. Toggle **Sembunyikan filter wajah** tetap menyembunyikan kontrol UI-nya.

Catatan: karena nama parameter API hasil generasi tidak didokumentasikan publik dan nama pemutar selalu berubah, mode ini disetel agar cocok dengan pola umum; verifikasi akhir dilakukan langsung di Dola dari wilayah yang tidak dibatasi.

## Pengujian

```bash
npm test
```