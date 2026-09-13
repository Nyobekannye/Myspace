# Tutorial Lengkap untuk Pemula

Panduan ini mengasumsikan kamu belum pernah menjalankan program apa pun. Ikuti urutannya dari atas ke bawah. Total waktu sekitar 30–45 menit.

Yang kamu butuhkan: laptop/PC (Windows atau Mac), akun Telegram, dan koneksi internet.

---

## Bagian 1 — Pasang Node.js (mesin penjalan bot)

1. Buka https://nodejs.org
2. Klik tombol hijau besar **Download Node.js (LTS)**.
3. Jalankan file yang terunduh, klik **Next** terus sampai **Finish** (biarkan semua pilihan default).
4. Cek apakah berhasil:
   - **Windows:** tekan tombol Windows, ketik `cmd`, tekan Enter. Muncul jendela hitam (Command Prompt).
   - **Mac:** tekan `Cmd + Spasi`, ketik `terminal`, tekan Enter.
   - Ketik `node -v` lalu Enter. Kalau muncul tulisan seperti `v22.11.0`, berhasil.

> Jendela hitam ini disebut **terminal**. Kita akan sering memakainya. "Ketik perintah" berarti ketik di sini lalu tekan Enter.

---

## Bagian 2 — Unduh kode bot

1. Buka https://github.com/Nyobekannye/Myspace/pull/2 dan klik **Merge pull request** → **Confirm merge** (supaya kodenya masuk ke branch utama).
2. Buka https://github.com/Nyobekannye/Myspace, klik tombol hijau **Code** → **Download ZIP**.
3. Ekstrak ZIP-nya. Pindahkan foldernya ke tempat yang mudah, misalnya `Documents\bot-toko` (Windows) atau `~/bot-toko` (Mac).

---

## Bagian 3 — Buat bot di Telegram

1. Buka Telegram, cari **@BotFather** (yang ada centang biru), klik **Start**.
2. Ketik `/newbot` dan kirim.
3. BotFather tanya **nama** bot → ketik misalnya `Toko Sari`.
4. BotFather tanya **username** bot → harus unik dan diakhiri `bot`, misalnya `tokosari_order_bot`.
5. BotFather membalas dengan **token**, bentuknya seperti:
   `7123456789:AAHfz1k3...` — **salin dan simpan**, jangan bagikan ke siapa pun.

Lalu cari **ID Telegram kamu** (supaya bot tahu kamu adminnya):
1. Cari **@userinfobot** di Telegram, klik **Start**.
2. Bot membalas dengan `Id: 123456789`. **Salin angkanya.**

---

## Bagian 4 — Daftar Midtrans (untuk QRIS otomatis)

Midtrans adalah perusahaan pembayaran resmi (terdaftar BI) yang menyediakan QRIS. Semua pembayaran masuk ke akun Midtrans kamu, lalu bisa dicairkan ke rekening bank.

1. Buka https://dashboard.midtrans.com/register dan daftar (gratis).
2. Setelah masuk dashboard, di pojok kiri atas ada pilihan **Sandbox / Production**. Pilih **Sandbox** dulu — ini mode latihan dengan uang bohongan.
3. Klik **Settings → Access Keys**. Salin **Server Key** (bentuknya `SB-Mid-server-xxxxxxxx`).

> Untuk menerima uang sungguhan nanti, kamu perlu melengkapi data usaha di mode **Production** dan menunggu verifikasi Midtrans (biasanya 1–3 hari kerja). Kita lakukan itu di Bagian 8.

---

## Bagian 5 — Isi pengaturan bot

1. Buka folder `bot-toko`. Di dalamnya ada file bernama `.env.example`.
   - **Windows:** kalau file tidak terlihat, klik menu **View** → centang **File name extensions** dan **Hidden items**.
2. Buat salinan file itu dan ubah namanya menjadi `.env` (titik di depan, tanpa `.example`).
3. Buka `.env` dengan Notepad (Windows: klik kanan → Open with → Notepad; Mac: TextEdit).
4. Isi baris-baris berikut dengan data kamu:

```
BOT_TOKEN=7123456789:AAHfz1k3...        ← token dari BotFather
ADMIN_IDS=123456789                     ← ID Telegram kamu
STORE_NAME=Toko Sari                    ← nama toko

PAYMENT_MODE=qris
MIDTRANS_SERVER_KEY=SB-Mid-server-xxxx  ← Server Key sandbox dari Midtrans
MIDTRANS_IS_PRODUCTION=false
```

Baris lain biarkan apa adanya. Simpan (Ctrl+S) dan tutup.

---

## Bagian 6 — Jalankan bot

1. Buka terminal **di dalam folder bot**:
   - **Windows:** buka folder `bot-toko` di File Explorer, klik kolom alamat di atas, ketik `cmd`, Enter.
   - **Mac:** buka Terminal, ketik `cd ` (dengan spasi), lalu seret folder `bot-toko` ke jendela Terminal, Enter.
2. Ketik perintah ini (hanya perlu sekali, untuk mengunduh komponen):
   ```
   npm install
   ```
3. Lalu jalankan bot:
   ```
   npm start
   ```
4. Kalau muncul tulisan `Bot Toko Sari berjalan...`, bot sudah hidup. **Biarkan jendela terminal tetap terbuka** — bot berhenti kalau jendela ditutup.

Untuk mematikan bot: tekan `Ctrl + C` di terminal.

---

## Bagian 7 — Coba bot

### Sebagai admin (kamu)
1. Buka bot kamu di Telegram (cari usernamenya), klik **Start**.
2. Tambahkan produk, formatnya `Nama | harga | stok | keterangan`:
   ```
   /addproduct Kopi Arabika 250gr | 45000 | 20 | Sangrai medium
   /addproduct Keripik Singkong | 15000 | 50 | Rasa balado
   ```
3. Hapus produk contoh bawaan:
   ```
   /delproduct P001
   /delproduct P002
   ```

### Sebagai pembeli (uji coba)
Pakai akun Telegram lain kalau ada, atau akun kamu sendiri juga bisa:
1. `/katalog` → tekan tombol **➕ Kopi Arabika**.
2. `/keranjang` → tekan **✅ Checkout**.
3. Jawab pertanyaan bot: nama, nomor HP, alamat.
4. Bot mengirim **gambar QRIS** dan admin menerima notifikasi pesanan baru.

### Simulasikan pembayaran (mode sandbox)
Karena masih sandbox, QR itu tidak bisa dibayar dari aplikasi asli. Cara pura-pura bayarnya:
1. Buka https://simulator.sandbox.midtrans.com/ → pilih **QRIS**.
2. Pilih **Scan QR** dan unggah screenshot QR dari bot, atau tempel `qr_string`.
3. Klik **Pay**.
4. Dalam ≤20 detik bot mengirim "✅ Pembayaran diterima" ke pembeli dan "💰 Pembayaran QRIS masuk" ke admin. Status pesanan otomatis `DIPROSES`. Bisa juga tekan tombol **🔄 Cek status** agar langsung.
5. Setelah barang dikirim, admin tekan **📦 Tandai Selesai**.

Perintah admin lain: `/orders` (pesanan aktif), `/order ORD-XXXX` (detail), `/stok P003 10` (ubah stok).

---

## Bagian 8 — Terima uang sungguhan (Production)

1. Di dashboard Midtrans, ganti ke **Production**, lengkapi data usaha (KTP, rekening bank, dsb) dan ajukan aktivasi metode **GoPay / QRIS**. Tunggu disetujui.
2. Setelah disetujui, buka **Settings → Access Keys** di mode **Production**, salin Server Key (bentuknya `Mid-server-xxxx`, tanpa `SB-`).
3. Ubah `.env`:
   ```
   MIDTRANS_SERVER_KEY=Mid-server-xxxx
   MIDTRANS_IS_PRODUCTION=true
   ```
4. Matikan bot (`Ctrl+C`) lalu jalankan lagi `npm start`.

Sekarang QR yang dikirim bot bisa dibayar sungguhan lewat GoPay, ShopeePay, OVO, DANA, atau m-banking mana pun. Uang masuk ke saldo Midtrans dan dicairkan ke rekeningmu sesuai jadwal Midtrans.

---

## Bagian 9 — Supaya bot hidup 24 jam

Kalau dijalankan di laptop, bot mati saat laptop mati. Pilihan agar selalu hidup:

**Paling mudah — sewa VPS kecil** (Rp 30–60 ribu/bulan, misal IDCloudHost, Biznet Gio, DigitalOcean):
1. Pilih sistem Ubuntu. Masuk lewat SSH (penyedia VPS biasanya menyediakan tombol "Console" di web).
2. Jalankan berurutan:
   ```
   curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
   sudo apt install -y nodejs git
   git clone https://github.com/Nyobekannye/Myspace.git bot-toko
   cd bot-toko
   nano .env        # isi seperti Bagian 5, simpan dengan Ctrl+O, Enter, keluar Ctrl+X
   npm install
   sudo npm install -g pm2
   pm2 start npm --name bot-toko -- start
   pm2 save && pm2 startup
   ```
3. Bot kini jalan terus, otomatis hidup lagi kalau server restart. Lihat log dengan `pm2 logs bot-toko`.

Alternatif gratis/murah: Railway, Render, atau Fly.io — unggah repo, set variabel `.env` di menu Environment, perintah start `npm start`.

---

## Bagian 10 — Kalau ada masalah

| Gejala | Penyebab & solusi |
|---|---|
| `BOT_TOKEN belum diisi` | File masih bernama `.env.example`, atau `.env` belum diisi. |
| `401 Unauthorized` saat start | Token BotFather salah/terpotong. Salin ulang. |
| Bot tidak balas sama sekali | Terminal `npm start` sudah tertutup, atau ada dua bot dengan token yang sama jalan bersamaan. |
| `Midtrans 401: Access denied` | Server Key salah, atau key sandbox dipakai dengan `MIDTRANS_IS_PRODUCTION=true` (atau sebaliknya). |
| `Midtrans 402` / payment channel not activated | Metode GoPay/QRIS belum diaktifkan di dashboard Midtrans. |
| QRIS gagal, bot kirim instruksi transfer manual | Ini fallback otomatis. Cek pesan error di terminal. |
| Pembayaran sudah tapi status belum berubah | Tunggu 20 detik atau tekan 🔄 Cek status. Kalau tetap, cek terminal ada error "Polling ... gagal" atau tidak. |
| Perintah admin dibalas "hanya untuk admin" | `ADMIN_IDS` salah. Cek ulang di @userinfobot. Beberapa admin dipisah koma: `123,456`. |

Data produk ada di `data/products.json` dan pesanan di `data/orders.json` — bisa dibuka dengan Notepad kalau ingin melihat riwayat.
