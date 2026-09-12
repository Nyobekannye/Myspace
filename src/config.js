const env = process.env;

function parseAdminIds(raw = "") {
  return raw
    .split(",")
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isInteger(n) && n > 0);
}

export const config = {
  botToken: env.BOT_TOKEN ?? "",
  adminIds: parseAdminIds(env.ADMIN_IDS),
  storeName: env.STORE_NAME || "Toko Saya",
  paymentInfo:
    env.PAYMENT_INFO ||
    "Silakan transfer sesuai total pesanan, lalu kirim foto bukti pembayaran ke bot ini.",
  dataDir: env.DATA_DIR || "./data",
};

export function isAdmin(userId) {
  return config.adminIds.includes(userId);
}
