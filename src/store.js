import { mkdirSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import { config } from "./config.js";

const PRODUCTS_FILE = "products.json";
const ORDERS_FILE = "orders.json";

const DEFAULT_PRODUCTS = [
  { id: "P001", name: "Contoh Produk A", price: 25000, stock: 10, description: "Ganti lewat /addproduct" },
  { id: "P002", name: "Contoh Produk B", price: 50000, stock: 5, description: "Ganti lewat /addproduct" },
];

export const ORDER_STATUS = {
  PENDING: "MENUNGGU_BAYAR",
  PAID: "MENUNGGU_KONFIRMASI",
  CONFIRMED: "DIPROSES",
  DONE: "SELESAI",
  CANCELLED: "DIBATALKAN",
};

export class Store {
  constructor(dataDir = config.dataDir) {
    this.dataDir = dataDir;
    mkdirSync(dataDir, { recursive: true });
    this.products = this.#load(PRODUCTS_FILE, DEFAULT_PRODUCTS);
    this.orders = this.#load(ORDERS_FILE, []);
    // Keranjang hanya di memori; hilang saat restart, sengaja agar sederhana.
    this.carts = new Map();
  }

  #load(file, fallback) {
    const path = join(this.dataDir, file);
    if (!existsSync(path)) {
      writeFileSync(path, JSON.stringify(fallback, null, 2));
      return structuredClone(fallback);
    }
    return JSON.parse(readFileSync(path, "utf8"));
  }

  #save(file, data) {
    writeFileSync(join(this.dataDir, file), JSON.stringify(data, null, 2));
  }

  // ---- Produk ----
  listProducts() {
    return this.products;
  }

  getProduct(id) {
    return this.products.find((p) => p.id === id.toUpperCase());
  }

  addProduct({ name, price, stock, description = "" }) {
    const nextNum = this.products.reduce((max, p) => {
      const n = Number(p.id.replace(/\D/g, ""));
      return Number.isFinite(n) && n > max ? n : max;
    }, 0) + 1;
    const product = {
      id: `P${String(nextNum).padStart(3, "0")}`,
      name,
      price,
      stock,
      description,
    };
    this.products.push(product);
    this.#save(PRODUCTS_FILE, this.products);
    return product;
  }

  removeProduct(id) {
    const idx = this.products.findIndex((p) => p.id === id.toUpperCase());
    if (idx === -1) return false;
    this.products.splice(idx, 1);
    this.#save(PRODUCTS_FILE, this.products);
    return true;
  }

  setStock(id, stock) {
    const product = this.getProduct(id);
    if (!product) return null;
    product.stock = stock;
    this.#save(PRODUCTS_FILE, this.products);
    return product;
  }

  // ---- Keranjang ----
  getCart(userId) {
    if (!this.carts.has(userId)) this.carts.set(userId, []);
    return this.carts.get(userId);
  }

  addToCart(userId, productId, qty) {
    const product = this.getProduct(productId);
    if (!product) return { error: "Produk tidak ditemukan." };
    const cart = this.getCart(userId);
    const existing = cart.find((i) => i.productId === product.id);
    const totalQty = (existing?.qty ?? 0) + qty;
    if (totalQty > product.stock) {
      return { error: `Stok ${product.name} hanya tersisa ${product.stock}.` };
    }
    if (existing) existing.qty = totalQty;
    else cart.push({ productId: product.id, qty });
    return { cart };
  }

  clearCart(userId) {
    this.carts.delete(userId);
  }

  cartTotal(userId) {
    return this.getCart(userId).reduce((sum, item) => {
      const p = this.getProduct(item.productId);
      return sum + (p ? p.price * item.qty : 0);
    }, 0);
  }

  // ---- Pesanan ----
  checkout(userId, { name, address, phone, username }) {
    const cart = this.getCart(userId);
    if (cart.length === 0) return { error: "Keranjang kosong." };

    const items = [];
    for (const item of cart) {
      const p = this.getProduct(item.productId);
      if (!p) return { error: `Produk ${item.productId} sudah tidak tersedia.` };
      if (p.stock < item.qty) return { error: `Stok ${p.name} tidak cukup (sisa ${p.stock}).` };
      items.push({ productId: p.id, name: p.name, price: p.price, qty: item.qty });
    }

    for (const item of items) this.getProduct(item.productId).stock -= item.qty;
    this.#save(PRODUCTS_FILE, this.products);

    const order = {
      id: `ORD-${Date.now().toString(36).toUpperCase()}`,
      userId,
      username,
      customer: { name, address, phone },
      items,
      total: items.reduce((s, i) => s + i.price * i.qty, 0),
      status: ORDER_STATUS.PENDING,
      createdAt: new Date().toISOString(),
      proofFileId: null,
      payment: null,
    };
    this.orders.push(order);
    this.#save(ORDERS_FILE, this.orders);
    this.clearCart(userId);
    return { order };
  }

  getOrder(id) {
    return this.orders.find((o) => o.id === id.toUpperCase());
  }

  userOrders(userId) {
    return this.orders.filter((o) => o.userId === userId);
  }

  pendingOrders() {
    return this.orders.filter(
      (o) => o.status === ORDER_STATUS.PENDING || o.status === ORDER_STATUS.PAID,
    );
  }

  latestUnpaidOrder(userId) {
    return [...this.userOrders(userId)]
      .reverse()
      .find((o) => o.status === ORDER_STATUS.PENDING);
  }

  attachProof(orderId, fileId) {
    const order = this.getOrder(orderId);
    if (!order) return null;
    order.proofFileId = fileId;
    order.status = ORDER_STATUS.PAID;
    this.#save(ORDERS_FILE, this.orders);
    return order;
  }

  setPayment(orderId, payment) {
    const order = this.getOrder(orderId);
    if (!order) return null;
    order.payment = { ...(order.payment ?? {}), ...payment };
    this.#save(ORDERS_FILE, this.orders);
    return order;
  }

  // Pesanan QRIS yang masih menunggu pembayaran dan belum kedaluwarsa.
  awaitingQrisPayment() {
    return this.orders.filter(
      (o) => o.status === ORDER_STATUS.PENDING && o.payment?.provider && !o.payment.paidAt,
    );
  }

  // Tandai lunas otomatis. Idempoten: pembayaran kedua untuk pesanan yang sama diabaikan.
  markPaid(orderId, { transactionId } = {}) {
    const order = this.getOrder(orderId);
    if (!order) return { error: "Pesanan tidak ditemukan." };
    if (order.payment?.paidAt) return { order, alreadyPaid: true };
    if (order.status === ORDER_STATUS.CANCELLED) return { order, error: "Pesanan sudah dibatalkan." };
    order.payment = { ...(order.payment ?? {}), transactionId, paidAt: new Date().toISOString() };
    order.status = ORDER_STATUS.CONFIRMED;
    this.#save(ORDERS_FILE, this.orders);
    return { order };
  }

  setStatus(orderId, status) {
    const order = this.getOrder(orderId);
    if (!order) return null;
    // Kembalikan stok jika pesanan dibatalkan dari status aktif.
    if (status === ORDER_STATUS.CANCELLED && order.status !== ORDER_STATUS.CANCELLED) {
      for (const item of order.items) {
        const p = this.getProduct(item.productId);
        if (p) p.stock += item.qty;
      }
      this.#save(PRODUCTS_FILE, this.products);
    }
    order.status = status;
    this.#save(ORDERS_FILE, this.orders);
    return order;
  }
}
