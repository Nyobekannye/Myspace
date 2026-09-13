export function rupiah(n) {
  return "Rp" + Number(n).toLocaleString("id-ID");
}

export function escapeHtml(s = "") {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

export function formatProduct(p) {
  const stok = p.stock > 0 ? `Stok: ${p.stock}` : "❌ Habis";
  const desc = p.description ? `\n<i>${escapeHtml(p.description)}</i>` : "";
  return `<b>${p.id}</b> — ${escapeHtml(p.name)}\n${rupiah(p.price)} · ${stok}${desc}`;
}

export function formatCatalog(products) {
  if (products.length === 0) return "Belum ada produk.";
  return products.map(formatProduct).join("\n\n");
}

export function formatCart(userId, store) {
  const cart = store.getCart(userId);
  if (cart.length === 0) return "🛒 Keranjang kosong.";
  const lines = cart.map((item) => {
    const p = store.getProduct(item.productId);
    if (!p) return `• ${item.productId} (tidak tersedia)`;
    return `• ${escapeHtml(p.name)} x${item.qty} = ${rupiah(p.price * item.qty)}`;
  });
  return `🛒 <b>Keranjang</b>\n${lines.join("\n")}\n\n<b>Total: ${rupiah(store.cartTotal(userId))}</b>`;
}

export function formatOrder(order, { forAdmin = false } = {}) {
  const items = order.items
    .map((i) => `• ${escapeHtml(i.name)} x${i.qty} = ${rupiah(i.price * i.qty)}`)
    .join("\n");
  const c = order.customer;
  const buyer = forAdmin
    ? `\n👤 ${escapeHtml(c.name)}${order.username ? ` (@${escapeHtml(order.username)})` : ""} · ID ${order.userId}`
    : `\n👤 ${escapeHtml(c.name)}`;
  return (
    `🧾 <b>Pesanan ${order.id}</b>\n` +
    `Status: <b>${order.status}</b>\n` +
    `${items}\n` +
    `<b>Total: ${rupiah(order.total)}</b>` +
    buyer +
    `\n📞 ${escapeHtml(c.phone)}\n📍 ${escapeHtml(c.address)}` +
    `\n🕒 ${new Date(order.createdAt).toLocaleString("id-ID")}`
  );
}
