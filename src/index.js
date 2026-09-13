import { config } from "./config.js";
import { Store } from "./store.js";
import { createBot } from "./bot.js";
import { MidtransClient } from "./payment/midtrans.js";
import { PaymentService } from "./payment/service.js";
import { createWebhookServer } from "./payment/webhook.js";

if (!config.botToken) {
  console.error("BOT_TOKEN belum diisi. Salin .env.example ke .env lalu isi tokennya.");
  process.exit(1);
}
if (config.adminIds.length === 0) {
  console.warn("ADMIN_IDS kosong: notifikasi pesanan tidak akan dikirim ke siapa pun.");
}
if (config.paymentMode === "qris" && !config.midtrans.serverKey) {
  console.error("PAYMENT_MODE=qris tapi MIDTRANS_SERVER_KEY kosong.");
  process.exit(1);
}

const store = new Store();

let paymentService = null;
let webhookServer = null;
let client = null;
if (config.paymentMode === "qris") {
  client = new MidtransClient(config.midtrans);
  paymentService = new PaymentService({
    client,
    store,
    api: null, // diisi setelah bot dibuat
    adminIds: config.adminIds,
    pollSeconds: config.midtrans.pollSeconds,
  });
}

const bot = createBot(store, config.botToken, { paymentService });

if (paymentService) {
  paymentService.api = bot.api;
  paymentService.startPolling();

  if (config.webhook.port > 0) {
    webhookServer = createWebhookServer({ client, paymentService, path: config.webhook.path });
    webhookServer.listen(config.webhook.port, () =>
      console.log(`Webhook Midtrans di http://0.0.0.0:${config.webhook.port}${config.webhook.path}`),
    );
  }
  console.log(
    `Pembayaran: QRIS via Midtrans (${config.midtrans.isProduction ? "PRODUCTION" : "sandbox"}, acquirer ${config.midtrans.acquirer}), polling tiap ${config.midtrans.pollSeconds}s`,
  );
} else {
  console.log("Pembayaran: manual (transfer + bukti foto)");
}

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

const stop = () => {
  paymentService?.stopPolling();
  webhookServer?.close();
  bot.stop();
};
process.once("SIGINT", stop);
process.once("SIGTERM", stop);

console.log(`Bot ${config.storeName} berjalan...`);
await bot.start();
