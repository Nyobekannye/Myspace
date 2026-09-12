# Bot Telegram Auto Order

Bot Telegram untuk menerima pesanan otomatis: pembeli lihat katalog → masukkan keranjang → checkout → kirim bukti bayar → admin konfirmasi lewat tombol. Data produk dan pesanan disimpan di file JSON, tanpa database.

## Fitur

**Pembeli**
- `/katalog` — daftar produk dengan tombol ➕ tambah ke keranjang
- `/beli P001 2` — tambah produk dengan jumlah tertentu
- `/keranjang` — lihat keranjang, tombol Checkout / Kosongkan
- `/checkout` — bot menanyakan nama, nomor HP, alamat, lalu pesanan dibuat dan stok dikurangi otomatis
- Kirim **foto** = bukti bayar untuk pesanan terakhir yang belum dibayar
- `/pesanan` — status 5 pesanan terakhir
- `/batal` — batalkan proses checkout

**Admin** (ID di `ADMIN_IDS`)
- Notifikasi otomatis setiap pesanan baru dan bukti bayar masuk, dengan tombol ✅ Konfirmasi / ❌ Tolak
- `/addproduct Nama | harga | stok | deskripsi`
- `/delproduct P001`, `/stok P001 20`
- `/orders` — pesanan yang perlu ditindak
- `/order ORD-XXXX` — detail + tombol Konfirmasi / Selesai / Batalkan
- `/setstatus ORD-XXXX SELESAI`

Status pesanan: `MENUNGGU_BAYAR → MENUNGGU_KONFIRMASI → DIPROSES → SELESAI` (atau `DIBATALKAN`, stok dikembalikan). Pembeli mendapat notifikasi setiap kali status berubah.

## Cara jalankan

1. Buat bot di [@BotFather](https://t.me/BotFather), salin tokennya.
2. Cari ID Telegram kamu lewat [@userinfobot](https://t.me/userinfobot).
3. Siapkan konfigurasi:
   ```bash
   cp .env.example .env
   # isi BOT_TOKEN, ADMIN_IDS, STORE_NAME, PAYMENT_INFO
   ```
4. Install dan jalankan (butuh Node.js 20+):
   ```bash
   npm install
   npm start
   ```
5. Buka bot di Telegram, ketik `/start`. Sebagai admin, tambahkan produk dengan `/addproduct`.

Produk contoh ada di `data/products.json` (dibuat otomatis saat pertama jalan). Pesanan tersimpan di `data/orders.json`.

## Pengembangan

```bash
npm run dev   # auto-restart saat file berubah
npm test      # unit test logika keranjang/pesanan
```

Struktur:
- `src/index.js` — entrypoint, long polling
- `src/bot.js` — semua handler perintah & tombol
- `src/store.js` — produk, keranjang, pesanan (JSON)
- `src/format.js` — format pesan
- `src/config.js` — baca `.env`
