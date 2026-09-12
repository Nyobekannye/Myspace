import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Store, ORDER_STATUS } from "../src/store.js";

const freshStore = () => new Store(mkdtempSync(join(tmpdir(), "bot-store-")));

test("addToCart menolak melebihi stok", () => {
  const store = freshStore();
  const p = store.addProduct({ name: "Kopi", price: 10000, stock: 2 });
  assert.equal(store.addToCart(1, p.id, 2).error, undefined);
  assert.match(store.addToCart(1, p.id, 1).error, /Stok/);
  assert.equal(store.cartTotal(1), 20000);
});

test("checkout mengurangi stok, membuat pesanan, mengosongkan keranjang", () => {
  const store = freshStore();
  const p = store.addProduct({ name: "Teh", price: 5000, stock: 5 });
  store.addToCart(7, p.id, 3);
  const { order } = store.checkout(7, { name: "Budi", phone: "0812", address: "Jakarta" });
  assert.equal(order.total, 15000);
  assert.equal(order.status, ORDER_STATUS.PENDING);
  assert.equal(store.getProduct(p.id).stock, 2);
  assert.equal(store.getCart(7).length, 0);
  assert.equal(store.latestUnpaidOrder(7).id, order.id);
});

test("checkout keranjang kosong gagal", () => {
  const store = freshStore();
  assert.match(store.checkout(1, {}).error, /kosong/);
});

test("attachProof mengubah status ke menunggu konfirmasi", () => {
  const store = freshStore();
  const p = store.addProduct({ name: "Gula", price: 1000, stock: 1 });
  store.addToCart(1, p.id, 1);
  const { order } = store.checkout(1, { name: "A", phone: "1", address: "B" });
  store.attachProof(order.id, "file123");
  assert.equal(store.getOrder(order.id).status, ORDER_STATUS.PAID);
  assert.equal(store.latestUnpaidOrder(1), undefined);
  assert.equal(store.pendingOrders().length, 1);
});

test("pembatalan mengembalikan stok sekali saja", () => {
  const store = freshStore();
  const p = store.addProduct({ name: "Roti", price: 1000, stock: 4 });
  store.addToCart(1, p.id, 4);
  const { order } = store.checkout(1, { name: "A", phone: "1", address: "B" });
  assert.equal(store.getProduct(p.id).stock, 0);
  store.setStatus(order.id, ORDER_STATUS.CANCELLED);
  store.setStatus(order.id, ORDER_STATUS.CANCELLED);
  assert.equal(store.getProduct(p.id).stock, 4);
});

test("data tersimpan ke disk dan bisa dimuat ulang", () => {
  const dir = mkdtempSync(join(tmpdir(), "bot-store-"));
  const a = new Store(dir);
  const p = a.addProduct({ name: "Susu", price: 12000, stock: 3 });
  const b = new Store(dir);
  assert.equal(b.getProduct(p.id).name, "Susu");
});
