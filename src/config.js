const env = process.env;

function parseAdminIds(raw = "") {
  return raw
    .split(",")
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isInteger(n) && n > 0);
}

const bool = (v, fallback = false) => (v == null || v === "" ? fallback : /^(1|true|yes)$/i.test(v));
const num = (v, fallback) => (Number.isFinite(Number(v)) && v !== "" && v != null ? Number(v) : fallback);

const midtransServerKey = env.MIDTRANS_SERVER_KEY ?? "";

export const config = {
  botToken: env.BOT_TOKEN ?? "",
  adminIds: parseAdminIds(env.ADMIN_IDS),
  storeName: env.STORE_NAME || "Toko Saya",
  paymentInfo:
    env.PAYMENT_INFO ||
    "Silakan transfer sesuai total pesanan, lalu kirim foto bukti pembayaran ke bot ini.",
  dataDir: env.DATA_DIR || "./data",

  // "qris" = QRIS otomatis via Midtrans, "manual" = transfer + upload bukti.
  paymentMode: env.PAYMENT_MODE || (midtransServerKey ? "qris" : "manual"),
  midtrans: {
    serverKey: midtransServerKey,
    isProduction: bool(env.MIDTRANS_IS_PRODUCTION, false),
    acquirer: env.MIDTRANS_ACQUIRER || "gopay",
    expiryMinutes: num(env.QRIS_EXPIRY_MINUTES, 15),
    pollSeconds: num(env.PAYMENT_POLL_SECONDS, 20),
  },
  webhook: {
    port: num(env.WEBHOOK_PORT, 0),
    path: env.WEBHOOK_PATH || "/midtrans/notification",
  },
};

export function isAdmin(userId) {
  return config.adminIds.includes(userId);
}
