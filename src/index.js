import { config } from "./config.js";
import { Store } from "./store.js";
import { createBot } from "./bot.js";

if (!config.botToken) {
  console.error("BOT_TOKEN belum diisi. Salin .env.example ke .env lalu isi tokennya.");
  process.exit(1);
}
if (config.adminIds.length === 0) {
  console.warn("ADMIN_IDS kosong: notifikasi pesanan tidak akan dikirim ke siapa pun.");
}

const store = new Store();
const bot = createBot(store);

await bot.api.setMyCommands([
  { command: "start", description: "Mulai & menu utama" },
  { command: "katalog", description: "Lihat daftar produk" },
  { command: "beli", description: "Tambah produk ke keranjang: /beli P001 2" },
  { command: "keranjang", description: "Lihat keranjang" },
  { command: "checkout", description: "Buat pesanan" },
  { command: "pesanan", description: "Status pesanan saya" },
  { command: "batal", description: "Batalkan proses checkout" },
  { command: "help", description: "Bantuan" },
]);

const stop = () => bot.stop();
process.once("SIGINT", stop);
process.once("SIGTERM", stop);

console.log(`Bot ${config.storeName} berjalan...`);
await bot.start();
