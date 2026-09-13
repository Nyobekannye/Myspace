# Dola HD Downloader — Ekstensi Chrome

Ekstensi Manifest V3 untuk:

- mendeteksi URL video dari respons pemutaran Dola dan elemen `<video>`;
- memilih MP4 beresolusi tertinggi yang tersedia;
- mengunduh melalui Chrome Downloads;
- menyembunyikan kontrol filter wajah/portrait agar tidak terpakai tanpa sengaja.

Ekstensi ini tidak membuka autentikasi, tidak menghapus watermark secara paksa, dan hanya bekerja dengan media yang tersedia dalam sesi Dola pengguna sendiri.

## Pasang untuk uji coba

1. Buka `chrome://extensions`.
2. Aktifkan **Developer mode**.
3. Klik **Load unpacked** dan pilih direktori repositori ini.
4. Buka Dola dan selesaikan atau putar video.
5. Klik **Download HD** pada panel kanan bawah.

Jika Dola hanya memberikan stream HLS (`.m3u8`), ekstensi menolak mengunduh manifest sebagai video palsu dan meminta pengguna memutar video sampai URL MP4 tersedia.

## Pengujian

```bash
npm test
```