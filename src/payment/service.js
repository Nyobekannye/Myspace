import { InlineKeyboard, InputFile } from "grammy";
import { ORDER_STATUS } from "../store.js";
import { formatOrder, rupiah, escapeHtml } from "../format.js";

const html = { parse_mode: "HTML" };

// Menghubungkan gateway QRIS dengan store dan notifikasi Telegram.
export class PaymentService {
  constructor({ client, store, api, adminIds = [], pollSeconds = 20, logger = console }) {
    this.client = client;
    this.store = store;
    this.api = api;
    this.adminIds = adminIds;
    this.pollSeconds = pollSeconds;
    this.logger = logger;
    this.timer = null;
  }

  // Buat QRIS untuk pesanan dan kirim gambarnya ke pembeli.
  async sendQris(order, chatId) {
    const payment = await this.client.chargeQris(order);
    this.store.setPayment(order.id, payment);

    const expires = new Date(payment.expiresAt).toLocaleTimeString("id-ID", { hour: "2-digit", minute: "2-digit" });
    const caption =
      `💳 <b>Bayar dengan QRIS</b>\n` +
      `Pesanan <b>${order.id}</b> · Total <b>${rupiah(order.total)}</b>\n\n` +
      `Scan QR ini lewat GoPay, ShopeePay, OVO, DANA, atau m-banking apa pun.\n` +
      `QR berlaku sampai <b>${expires}</b>. Status akan diperbarui otomatis setelah pembayaran masuk.`;
    const kb = new InlineKeyboard().text("🔄 Cek status", `paycheck:${order.id}`);

    if (payment.qrUrl) {
      try {
        const image = await this.client.fetchQrImage(payment.qrUrl);
        await this.api.sendPhoto(chatId, new InputFile(image, `${order.id}.png`), { caption, ...html, reply_markup: kb });
        return payment;
      } catch (err) {
        this.logger.error("Gagal ambil gambar QR, kirim tautan saja:", err.message);
      }
    }
    const link = payment.qrUrl ? `\n\n<a href="${payment.qrUrl}">Buka QR code</a>` : "";
    const raw = payment.qrString ? `\n\n<code>${escapeHtml(payment.qrString)}</code>` : "";
    await this.api.sendMessage(chatId, caption + link + raw, { ...html, reply_markup: kb });
    return payment;
  }

  // Cek status ke gateway lalu terapkan hasilnya.
  async refresh(orderId) {
    const status = await this.client.getStatus(orderId);
    return this.apply(status);
  }

  // Terapkan status ternormalisasi { orderId, outcome, transactionId }.
  async apply(status) {
    const order = this.store.getOrder(status.orderId);
    if (!order) return { outcome: "unknown" };

    if (status.outcome === "paid") {
      const result = this.store.markPaid(order.id, { transactionId: status.transactionId });
      if (result.error || result.alreadyPaid) return { outcome: "paid", order: result.order, changed: false };
      await this.#notifyPaid(result.order);
      return { outcome: "paid", order: result.order, changed: true };
    }

    if (status.outcome === "failed" && order.status === ORDER_STATUS.PENDING) {
      const cancelled = this.store.setStatus(order.id, ORDER_STATUS.CANCELLED);
      this.store.setPayment(order.id, { failedAt: new Date().toISOString(), reason: status.transactionStatus });
      await this.api
        .sendMessage(
          order.userId,
          `⌛ QRIS pesanan <b>${order.id}</b> ${status.transactionStatus === "expire" ? "kedaluwarsa" : "gagal"}. ` +
            `Pesanan dibatalkan dan stok dikembalikan. Silakan order ulang lewat /katalog.`,
          html,
        )
        .catch(() => {});
      return { outcome: "failed", order: cancelled, changed: true };
    }

    return { outcome: status.outcome, order, changed: false };
  }

  async #notifyPaid(order) {
    await this.api
      .sendMessage(
        order.userId,
        `✅ Pembayaran <b>${rupiah(order.total)}</b> untuk pesanan <b>${order.id}</b> diterima!\n` +
          `Pesanan sedang diproses. Pantau lewat /pesanan.`,
        html,
      )
      .catch(() => {});
    const kb = new InlineKeyboard().text("📦 Tandai Selesai", `done:${order.id}`);
    await Promise.allSettled(
      this.adminIds.map((id) =>
        this.api.sendMessage(id, `💰 <b>Pembayaran QRIS masuk</b>\n\n${formatOrder(order, { forAdmin: true })}`, {
          ...html,
          reply_markup: kb,
        }),
      ),
    );
  }

  // Polling cadangan bila webhook tidak dipasang (atau sebagai pengaman kalau webhook terlewat).
  startPolling() {
    if (this.timer || this.pollSeconds <= 0) return;
    const tick = async () => {
      for (const order of this.store.awaitingQrisPayment()) {
        try {
          await this.refresh(order.id);
        } catch (err) {
          this.logger.error(`Polling ${order.id} gagal:`, err.message);
        }
      }
    };
    this.timer = setInterval(tick, this.pollSeconds * 1000);
    this.timer.unref?.();
  }

  stopPolling() {
    clearInterval(this.timer);
    this.timer = null;
  }
}
