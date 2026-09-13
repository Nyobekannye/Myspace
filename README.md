# Bot Telegram Auto Order

Bot Telegram untuk menerima pesanan otomatis: pembeli lihat katalog → masukkan keranjang → checkout → **bayar QRIS, status lunas otomatis** → admin tinggal kirim barang. Data produk dan pesanan disimpan di file JSON, tanpa database.

## Fitur

**Pembeli**
- `/katalog` — daftar produk dengan tombol ➕ tambah ke keranjang
- `/beli P001 2` — tambah produk dengan jumlah tertentu
- `/keranjang` — lihat keranjang, tombol Checkout / Kosongkan
- `/checkout` — bot menanyakan nama, nomor HP, alamat, lalu pesanan dibuat dan stok dikurangi otomatis
- **QRIS otomatis:** bot mengirim gambar QR (bisa dibayar GoPay/ShopeePay/OVO/DANA/m-banking); begitu dibayar, status berubah ke `DIPROSES` otomatis dan pembeli + admin dapat notifikasi. Tombol 🔄 Cek status untuk cek manual.
- Mode manual (tanpa Midtrans): kirim **foto** = bukti bayar, admin konfirmasi
- `/pesanan` — status 5 pesanan terakhir
- `/batal` — batalkan proses checkout

**Admin** (ID di `ADMIN_IDS`)
- Notifikasi otomatis setiap pesanan baru dan pembayaran masuk (QRIS: tombol 📦 Tandai Selesai; manual: tombol ✅ Konfirmasi / ❌ Tolak)
- `/addproduct Nama | harga | stok | deskripsi`
- `/delproduct P001`, `/stok P001 20`
- `/orders` — pesanan yang perlu ditindak
- `/order ORD-XXXX` — detail + tombol Konfirmasi / Selesai / Batalkan
- `/setstatus ORD-XXXX SELESAI`

Status pesanan: `MENUNGGU_BAYAR → DIPROSES → SELESAI` untuk QRIS (`MENUNGGU_BAYAR → MENUNGGU_KONFIRMASI → DIPROSES → SELESAI` untuk manual), atau `DIBATALKAN` (stok dikembalikan; QR kedaluwarsa otomatis membatalkan pesanan). Pembeli mendapat notifikasi setiap kali status berubah.

## Cara jalankan

1. Buat bot di [@BotFather](https://t.me/BotFather), salin tokennya.
2. Cari ID Telegram kamu lewat [@userinfobot](https://t.me/userinfobot).
3. Siapkan konfigurasi:
   ```bash
   cp .env.example .env
   # isi BOT_TOKEN, ADMIN_IDS, STORE_NAME, dan MIDTRANS_SERVER_KEY (lihat bawah)
   ```
4. Install dan jalankan (butuh Node.js 20+):
   ```bash
   npm install
   npm start
   ```
5. Buka bot di Telegram, ketik `/start`. Sebagai admin, tambahkan produk dengan `/addproduct`.

Produk contoh ada di `data/products.json` (dibuat otomatis saat pertama jalan). Pesanan tersimpan di `data/orders.json`.

## Setup QRIS (Midtrans)

1. Daftar di [midtrans.com](https://midtrans.com), aktifkan metode pembayaran **GoPay / QRIS** di dashboard.
2. Ambil **Server Key** di *Settings → Access Keys*. Pakai key **sandbox** (`SB-Mid-server-...`) dulu untuk uji coba, lalu ganti ke key production dan set `MIDTRANS_IS_PRODUCTION=true`.
3. Isi `.env`:
   ```
   PAYMENT_MODE=qris
   MIDTRANS_SERVER_KEY=SB-Mid-server-xxxx
   MIDTRANS_IS_PRODUCTION=false
   ```
4. Selesai — bot akan mengecek status pembayaran ke Midtrans setiap `PAYMENT_POLL_SECONDS` (default 20 detik), jadi tidak wajib punya domain/webhook.

**Webhook (opsional, notifikasi instan):** isi `WEBHOOK_PORT=3000`, expose ke internet (domain/VPS/ngrok), lalu di dashboard Midtrans *Settings → Configuration → Payment Notification URL* isi `https://domain-kamu.com/midtrans/notification`. Signature setiap notifikasi diverifikasi (SHA-512), dan polling tetap berjalan sebagai cadangan.

**Uji coba di sandbox:** setelah QR terkirim, buka [simulator Midtrans](https://simulator.sandbox.midtrans.com/) → QRIS, tempel `qr_string` atau scan QR untuk mensimulasikan pembayaran. Status di bot akan berubah otomatis dalam ≤20 detik (atau langsung dengan tombol 🔄 Cek status).

Kalau `MIDTRANS_SERVER_KEY` kosong, bot otomatis jalan di mode manual (transfer + foto bukti). Bila pembuatan QRIS gagal (misalnya Midtrans down), bot otomatis menampilkan instruksi transfer manual dari `PAYMENT_INFO`.

## Pengembangan

```bash
npm run dev   # auto-restart saat file berubah
npm test      # unit test logika keranjang/pesanan/pembayaran
```

Struktur:
- `src/index.js` — entrypoint, long polling, wiring pembayaran & webhook
- `src/bot.js` — semua handler perintah & tombol
- `src/store.js` — produk, keranjang, pesanan (JSON)
- `src/format.js` — format pesan
- `src/config.js` — baca `.env`
- `src/payment/midtrans.js` — klien Midtrans Core API (charge QRIS, cek status, verifikasi signature)
- `src/payment/service.js` — kirim QR, terapkan status bayar, polling
- `src/payment/webhook.js` — HTTP server penerima notifikasi Midtrans
