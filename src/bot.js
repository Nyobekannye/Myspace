import { Bot, InlineKeyboard, Keyboard } from "grammy";
import { config, isAdmin } from "./config.js";
import { ORDER_STATUS } from "./store.js";
import { escapeHtml, formatCart, formatCatalog, formatOrder, formatProduct, rupiah } from "./format.js";

const MENU = new Keyboard()
  .text("🛍 Katalog").text("🛒 Keranjang").row()
  .text("📦 Pesanan Saya").text("ℹ️ Bantuan")
  .resized()
  .persistent();

const HELP_TEXT = `<b>Cara order:</b>
1. /katalog — lihat produk
2. /beli &lt;ID&gt; [jumlah] — tambah ke keranjang (contoh: <code>/beli P001 2</code>)
3. /keranjang — cek keranjang
4. /checkout — isi data pengiriman, pesanan dibuat otomatis
5. Kirim foto bukti bayar ke bot ini
6. /pesanan — pantau status pesanan

/batal — batalkan proses checkout`;

const ADMIN_HELP = `<b>Perintah admin:</b>
/addproduct Nama | harga | stok | deskripsi
/delproduct &lt;ID&gt;
/stok &lt;ID&gt; &lt;jumlah&gt;
/orders — pesanan yang perlu ditindak
/order &lt;ID_PESANAN&gt; — detail pesanan
/setstatus &lt;ID_PESANAN&gt; &lt;${Object.values(ORDER_STATUS).join("|")}&gt;`;

export function createBot(store, token = config.botToken) {
  const bot = new Bot(token);
  // State checkout per user: { step, data }
  const checkoutState = new Map();
  const html = { parse_mode: "HTML" };

  async function notifyAdmins(text, extra = {}) {
    await Promise.allSettled(
      config.adminIds.map((id) => bot.api.sendMessage(id, text, { ...html, ...extra })),
    );
  }

  function adminOnly(handler) {
    return (ctx) => {
      if (!isAdmin(ctx.from?.id)) return ctx.reply("Perintah ini hanya untuk admin.");
      return handler(ctx);
    };
  }

  // ---------- Pembeli ----------
  bot.command("start", (ctx) =>
    ctx.reply(
      `Selamat datang di <b>${escapeHtml(config.storeName)}</b>! 👋\n\n${HELP_TEXT}` +
        (isAdmin(ctx.from.id) ? `\n\n${ADMIN_HELP}` : ""),
      { ...html, reply_markup: MENU },
    ),
  );

  bot.command("help", (ctx) =>
    ctx.reply(HELP_TEXT + (isAdmin(ctx.from.id) ? `\n\n${ADMIN_HELP}` : ""), html),
  );
  bot.hears("ℹ️ Bantuan", (ctx) => ctx.reply(HELP_TEXT, html));

  async function sendCatalog(ctx) {
    const products = store.listProducts();
    if (products.length === 0) return ctx.reply("Belum ada produk.");
    const kb = new InlineKeyboard();
    products
      .filter((p) => p.stock > 0)
      .forEach((p, i) => {
        kb.text(`➕ ${p.name}`, `add:${p.id}`);
        if (i % 2 === 1) kb.row();
      });
    return ctx.reply(`🛍 <b>Katalog</b>\n\n${formatCatalog(products)}`, {
      ...html,
      reply_markup: kb,
    });
  }
  bot.command("katalog", sendCatalog);
  bot.hears("🛍 Katalog", sendCatalog);

  bot.callbackQuery(/^add:(\w+)$/, async (ctx) => {
    const result = store.addToCart(ctx.from.id, ctx.match[1], 1);
    if (result.error) return ctx.answerCallbackQuery({ text: result.error, show_alert: true });
    const p = store.getProduct(ctx.match[1]);
    await ctx.answerCallbackQuery({ text: `${p.name} ditambahkan ke keranjang.` });
  });

  bot.command("beli", (ctx) => {
    const [id, qtyRaw = "1"] = ctx.match.trim().split(/\s+/);
    const qty = Number(qtyRaw);
    if (!id) return ctx.reply("Format: /beli <ID> [jumlah]\nContoh: /beli P001 2");
    if (!Number.isInteger(qty) || qty < 1) return ctx.reply("Jumlah harus angka bulat minimal 1.");
    const result = store.addToCart(ctx.from.id, id, qty);
    if (result.error) return ctx.reply(result.error);
    return ctx.reply(formatCart(ctx.from.id, store), { ...html, reply_markup: cartKeyboard() });
  });

  function cartKeyboard() {
    return new InlineKeyboard().text("✅ Checkout", "checkout").text("🗑 Kosongkan", "clearcart");
  }

  async function sendCart(ctx) {
    const cart = store.getCart(ctx.from.id);
    return ctx.reply(formatCart(ctx.from.id, store), {
      ...html,
      reply_markup: cart.length ? cartKeyboard() : undefined,
    });
  }
  bot.command("keranjang", sendCart);
  bot.hears("🛒 Keranjang", sendCart);

  bot.callbackQuery("clearcart", async (ctx) => {
    store.clearCart(ctx.from.id);
    await ctx.answerCallbackQuery({ text: "Keranjang dikosongkan." });
    await ctx.editMessageText("🛒 Keranjang kosong.");
  });

  async function startCheckout(ctx) {
    if (store.getCart(ctx.from.id).length === 0) return ctx.reply("Keranjang masih kosong. Lihat /katalog dulu.");
    checkoutState.set(ctx.from.id, { step: "name", data: {} });
    return ctx.reply("Siapa nama penerima?\n\n(ketik /batal untuk membatalkan)");
  }
  bot.command("checkout", startCheckout);
  bot.callbackQuery("checkout", async (ctx) => {
    await ctx.answerCallbackQuery();
    await startCheckout(ctx);
  });

  bot.command("batal", (ctx) => {
    if (!checkoutState.delete(ctx.from.id)) return ctx.reply("Tidak ada proses yang berjalan.");
    return ctx.reply("Checkout dibatalkan. Keranjang kamu masih tersimpan.");
  });

  async function sendMyOrders(ctx) {
    const orders = store.userOrders(ctx.from.id);
    if (orders.length === 0) return ctx.reply("Belum ada pesanan.");
    const text = orders.slice(-5).map((o) => formatOrder(o)).join("\n\n———\n\n");
    return ctx.reply(text, html);
  }
  bot.command("pesanan", sendMyOrders);
  bot.hears("📦 Pesanan Saya", sendMyOrders);

  // Foto = bukti bayar untuk pesanan terakhir yang belum dibayar
  bot.on("message:photo", async (ctx) => {
    const order = store.latestUnpaidOrder(ctx.from.id);
    if (!order) return ctx.reply("Tidak ada pesanan yang menunggu pembayaran.");
    const photo = ctx.message.photo.at(-1);
    store.attachProof(order.id, photo.file_id);
    await ctx.reply(
      `Bukti pembayaran untuk <b>${order.id}</b> diterima ✅\nMohon tunggu konfirmasi admin.`,
      html,
    );
    const kb = new InlineKeyboard()
      .text("✅ Konfirmasi", `confirm:${order.id}`)
      .text("❌ Tolak", `reject:${order.id}`);
    await Promise.allSettled(
      config.adminIds.map((id) =>
        bot.api.sendPhoto(id, photo.file_id, {
          caption: `💰 Bukti bayar masuk\n\n${formatOrder(order, { forAdmin: true })}`,
          ...html,
          reply_markup: kb,
        }),
      ),
    );
  });

  // ---------- Admin ----------
  bot.command(
    "addproduct",
    adminOnly((ctx) => {
      const parts = ctx.match.split("|").map((s) => s.trim());
      const [name, priceRaw, stockRaw, description = ""] = parts;
      const price = Number(priceRaw);
      const stock = Number(stockRaw);
      if (!name || !Number.isFinite(price) || price <= 0 || !Number.isInteger(stock) || stock < 0) {
        return ctx.reply("Format: /addproduct Nama | harga | stok | deskripsi\nContoh: /addproduct Kopi Arabika | 45000 | 20 | 250gr");
      }
      const p = store.addProduct({ name, price, stock, description });
      return ctx.reply(`Produk ditambahkan:\n\n${formatProduct(p)}`, html);
    }),
  );

  bot.command(
    "delproduct",
    adminOnly((ctx) => {
      const id = ctx.match.trim();
      if (!id) return ctx.reply("Format: /delproduct <ID>");
      return ctx.reply(store.removeProduct(id) ? `Produk ${id.toUpperCase()} dihapus.` : "Produk tidak ditemukan.");
    }),
  );

  bot.command(
    "stok",
    adminOnly((ctx) => {
      const [id, stockRaw] = ctx.match.trim().split(/\s+/);
      const stock = Number(stockRaw);
      if (!id || !Number.isInteger(stock) || stock < 0) return ctx.reply("Format: /stok <ID> <jumlah>");
      const p = store.setStock(id, stock);
      return p ? ctx.reply(`Stok ${p.name} sekarang ${p.stock}.`) : ctx.reply("Produk tidak ditemukan.");
    }),
  );

  bot.command(
    "orders",
    adminOnly((ctx) => {
      const orders = store.pendingOrders();
      if (orders.length === 0) return ctx.reply("Tidak ada pesanan yang perlu ditindak.");
      const text = orders.map((o) => formatOrder(o, { forAdmin: true })).join("\n\n———\n\n");
      return ctx.reply(text, html);
    }),
  );

  bot.command(
    "order",
    adminOnly((ctx) => {
      const order = store.getOrder(ctx.match.trim());
      if (!order) return ctx.reply("Pesanan tidak ditemukan.");
      return ctx.reply(formatOrder(order, { forAdmin: true }), { ...html, reply_markup: adminOrderKeyboard(order) });
    }),
  );

  function adminOrderKeyboard(order) {
    return new InlineKeyboard()
      .text("✅ Konfirmasi", `confirm:${order.id}`)
      .text("📦 Selesai", `done:${order.id}`)
      .row()
      .text("❌ Batalkan", `cancel:${order.id}`);
  }

  async function updateStatus(ctx, orderId, status, buyerMessage) {
    if (!isAdmin(ctx.from.id)) return ctx.answerCallbackQuery({ text: "Hanya admin.", show_alert: true });
    const order = store.setStatus(orderId, status);
    if (!order) return ctx.answerCallbackQuery({ text: "Pesanan tidak ditemukan.", show_alert: true });
    await ctx.answerCallbackQuery({ text: `Status → ${status}` });
    await ctx.editMessageReplyMarkup({ reply_markup: undefined }).catch(() => {});
    await ctx.reply(`Pesanan <b>${order.id}</b> → <b>${status}</b>`, html);
    await bot.api.sendMessage(order.userId, buyerMessage(order), html).catch(() => {});
  }

  bot.callbackQuery(/^confirm:(.+)$/, (ctx) =>
    updateStatus(ctx, ctx.match[1], ORDER_STATUS.CONFIRMED,
      (o) => `✅ Pembayaran pesanan <b>${o.id}</b> dikonfirmasi. Pesanan sedang diproses.`),
  );
  bot.callbackQuery(/^reject:(.+)$/, (ctx) =>
    updateStatus(ctx, ctx.match[1], ORDER_STATUS.PENDING,
      (o) => `⚠️ Bukti bayar pesanan <b>${o.id}</b> ditolak. Silakan kirim ulang bukti yang benar.`),
  );
  bot.callbackQuery(/^done:(.+)$/, (ctx) =>
    updateStatus(ctx, ctx.match[1], ORDER_STATUS.DONE,
      (o) => `📦 Pesanan <b>${o.id}</b> selesai. Terima kasih sudah berbelanja!`),
  );
  bot.callbackQuery(/^cancel:(.+)$/, (ctx) =>
    updateStatus(ctx, ctx.match[1], ORDER_STATUS.CANCELLED,
      (o) => `❌ Pesanan <b>${o.id}</b> dibatalkan oleh admin.`),
  );

  bot.command(
    "setstatus",
    adminOnly(async (ctx) => {
      const [id, statusRaw] = ctx.match.trim().split(/\s+/);
      const status = statusRaw?.toUpperCase();
      if (!id || !Object.values(ORDER_STATUS).includes(status)) {
        return ctx.reply(`Format: /setstatus <ID_PESANAN> <${Object.values(ORDER_STATUS).join("|")}>`);
      }
      const order = store.setStatus(id, status);
      if (!order) return ctx.reply("Pesanan tidak ditemukan.");
      await ctx.reply(`Pesanan ${order.id} → ${status}`);
      await bot.api
        .sendMessage(order.userId, `Status pesanan <b>${order.id}</b> sekarang: <b>${status}</b>`, html)
        .catch(() => {});
    }),
  );

  // ---------- Alur checkout (teks bebas) ----------
  bot.on("message:text", async (ctx) => {
    const state = checkoutState.get(ctx.from.id);
    if (!state) return ctx.reply("Perintah tidak dikenal. Ketik /help untuk bantuan.", { reply_markup: MENU });

    const text = ctx.message.text.trim();
    if (state.step === "name") {
      state.data.name = text;
      state.step = "phone";
      return ctx.reply("Nomor HP/WhatsApp yang bisa dihubungi?");
    }
    if (state.step === "phone") {
      if (!/^\+?[\d\s-]{8,}$/.test(text)) return ctx.reply("Nomor tidak valid, coba lagi (contoh: 08123456789).");
      state.data.phone = text;
      state.step = "address";
      return ctx.reply("Alamat lengkap pengiriman?");
    }
    if (state.step === "address") {
      state.data.address = text;
      checkoutState.delete(ctx.from.id);
      const result = store.checkout(ctx.from.id, { ...state.data, username: ctx.from.username });
      if (result.error) return ctx.reply(`Checkout gagal: ${result.error}`);
      const { order } = result;
      await ctx.reply(
        `${formatOrder(order)}\n\n💳 <b>Pembayaran</b>\n${escapeHtml(config.paymentInfo)}\n\n` +
          `Total yang harus dibayar: <b>${rupiah(order.total)}</b>\nSetelah transfer, kirim foto bukti bayar ke sini.`,
        html,
      );
      await notifyAdmins(`🆕 Pesanan baru!\n\n${formatOrder(order, { forAdmin: true })}`);
    }
  });

  bot.catch((err) => {
    console.error("Error saat memproses update", err.ctx?.update?.update_id, err.error);
  });

  return bot;
}
