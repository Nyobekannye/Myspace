import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createHash } from "node:crypto";
import { Store, ORDER_STATUS } from "../src/store.js";
import { MidtransClient, normalizeStatus } from "../src/payment/midtrans.js";
import { PaymentService } from "../src/payment/service.js";
import { createWebhookServer } from "../src/payment/webhook.js";

const SERVER_KEY = "SB-Mid-server-test";

function makeOrder(store, userId = 42) {
  const p = store.addProduct({ name: "Kopi", price: 25000, stock: 5 });
  store.addToCart(userId, p.id, 2);
  return store.checkout(userId, { name: "Budi", phone: "0812", address: "Jakarta" }).order;
}

function fakeApi() {
  const sent = [];
  return {
    sent,
    async sendMessage(chat_id, text, extra) { sent.push({ method: "sendMessage", chat_id, text, extra }); },
    async sendPhoto(chat_id, photo, extra) { sent.push({ method: "sendPhoto", chat_id, caption: extra.caption, extra }); },
  };
}

function fakeFetch(handlers) {
  const calls = [];
  const fn = async (url, init = {}) => {
    calls.push({ url, init });
    for (const [pattern, respond] of handlers) {
      if (url.includes(pattern)) return respond(url, init);
    }
    throw new Error("unexpected fetch " + url);
  };
  fn.calls = calls;
  return fn;
}

const jsonRes = (body, status = 200) => ({
  ok: status < 400,
  status,
  statusText: "x",
  json: async () => body,
});

test("chargeQris mengirim payload QRIS dan mengembalikan URL QR", async () => {
  const store = new Store(mkdtempSync(join(tmpdir(), "pay-")));
  const order = makeOrder(store);
  const fetchFn = fakeFetch([
    ["/v2/charge", () => jsonRes({
      status_code: "201", transaction_id: "trx-1", order_id: order.id, transaction_status: "pending",
      actions: [{ name: "generate-qr-code", method: "GET", url: "https://api.sandbox.midtrans.com/v2/qris/trx-1/qr-code" }],
    })],
  ]);
  const client = new MidtransClient({ serverKey: SERVER_KEY, fetchFn });
  const payment = await client.chargeQris(order);

  const { url, init } = fetchFn.calls[0];
  assert.equal(url, "https://api.sandbox.midtrans.com/v2/charge");
  assert.equal(init.headers.Authorization, "Basic " + Buffer.from(SERVER_KEY + ":").toString("base64"));
  const body = JSON.parse(init.body);
  assert.equal(body.payment_type, "qris");
  assert.equal(body.transaction_details.order_id, order.id);
  assert.equal(body.transaction_details.gross_amount, 50000);
  assert.equal(body.qris.acquirer, "gopay");
  assert.equal(payment.transactionId, "trx-1");
  assert.match(payment.qrUrl, /qr-code$/);
});

test("chargeQris melempar error saat Midtrans balas status_code 4xx", async () => {
  const store = new Store(mkdtempSync(join(tmpdir(), "pay-")));
  const order = makeOrder(store);
  const fetchFn = fakeFetch([["/v2/charge", () => jsonRes({ status_code: "401", status_message: "Access denied" })]]);
  const client = new MidtransClient({ serverKey: SERVER_KEY, fetchFn });
  await assert.rejects(client.chargeQris(order), /401.*Access denied/);
});

test("normalizeStatus memetakan status Midtrans", () => {
  assert.equal(normalizeStatus({ transaction_status: "settlement" }).outcome, "paid");
  assert.equal(normalizeStatus({ transaction_status: "capture", fraud_status: "accept" }).outcome, "paid");
  assert.equal(normalizeStatus({ transaction_status: "capture", fraud_status: "deny" }).outcome, "pending");
  assert.equal(normalizeStatus({ transaction_status: "pending" }).outcome, "pending");
  assert.equal(normalizeStatus({ transaction_status: "expire" }).outcome, "failed");
});

test("sendQris menyimpan info pembayaran dan mengirim foto QR", async () => {
  const store = new Store(mkdtempSync(join(tmpdir(), "pay-")));
  const order = makeOrder(store);
  const fetchFn = fakeFetch([
    ["/v2/charge", () => jsonRes({
      status_code: "201", transaction_id: "trx-2", order_id: order.id, transaction_status: "pending",
      actions: [{ name: "generate-qr-code", url: "https://api.sandbox.midtrans.com/v2/qris/trx-2/qr-code" }],
    })],
    ["/qr-code", () => ({ ok: true, status: 200, arrayBuffer: async () => new Uint8Array([1, 2, 3]).buffer })],
  ]);
  const client = new MidtransClient({ serverKey: SERVER_KEY, fetchFn });
  const api = fakeApi();
  const svc = new PaymentService({ client, store, api, adminIds: [999], pollSeconds: 0 });

  await svc.sendQris(order, 42);
  assert.equal(store.getOrder(order.id).payment.transactionId, "trx-2");
  assert.equal(api.sent[0].method, "sendPhoto");
  assert.match(api.sent[0].caption, /QRIS/);
  assert.equal(store.awaitingQrisPayment().length, 1);
});

test("apply(paid) menandai lunas sekali, notif pembeli + admin, idempoten", async () => {
  const store = new Store(mkdtempSync(join(tmpdir(), "pay-")));
  const order = makeOrder(store);
  store.setPayment(order.id, { provider: "midtrans", transactionId: "trx-3" });
  const api = fakeApi();
  const svc = new PaymentService({ client: {}, store, api, adminIds: [999, 888], pollSeconds: 0 });

  const first = await svc.apply({ orderId: order.id, outcome: "paid", transactionId: "trx-3" });
  assert.equal(first.changed, true);
  assert.equal(store.getOrder(order.id).status, ORDER_STATUS.CONFIRMED);
  assert.equal(api.sent.filter((m) => m.chat_id === 42).length, 1);
  assert.equal(api.sent.filter((m) => m.chat_id === 999).length, 1);
  assert.equal(api.sent.filter((m) => m.chat_id === 888).length, 1);

  const second = await svc.apply({ orderId: order.id, outcome: "paid", transactionId: "trx-3" });
  assert.equal(second.changed, false);
  assert.equal(api.sent.length, 3);
  assert.equal(store.awaitingQrisPayment().length, 0);
});

test("apply(failed) membatalkan pesanan dan mengembalikan stok", async () => {
  const store = new Store(mkdtempSync(join(tmpdir(), "pay-")));
  const order = makeOrder(store);
  const productId = order.items[0].productId;
  assert.equal(store.getProduct(productId).stock, 3);
  store.setPayment(order.id, { provider: "midtrans" });
  const api = fakeApi();
  const svc = new PaymentService({ client: {}, store, api, adminIds: [], pollSeconds: 0 });

  await svc.apply({ orderId: order.id, outcome: "failed", transactionStatus: "expire" });
  assert.equal(store.getOrder(order.id).status, ORDER_STATUS.CANCELLED);
  assert.equal(store.getProduct(productId).stock, 5);
  assert.match(api.sent[0].text, /kedaluwarsa/);
});

test("webhook menolak signature salah dan memproses yang valid", async () => {
  const store = new Store(mkdtempSync(join(tmpdir(), "pay-")));
  const order = makeOrder(store);
  store.setPayment(order.id, { provider: "midtrans" });
  const client = new MidtransClient({ serverKey: SERVER_KEY, fetchFn: async () => { throw new Error("no"); } });
  const api = fakeApi();
  const svc = new PaymentService({ client, store, api, adminIds: [999], pollSeconds: 0 });
  const server = createWebhookServer({ client, paymentService: svc, logger: { warn() {}, error() {} } });
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;

  const gross = "50000.00";
  const notif = {
    order_id: order.id, status_code: "200", gross_amount: gross, transaction_status: "settlement",
    transaction_id: "trx-4", fraud_status: "accept",
  };
  const post = (body) => fetch(base + "/midtrans/notification", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
  });

  const bad = await post({ ...notif, signature_key: "salah" });
  assert.equal(bad.status, 403);
  assert.equal(store.getOrder(order.id).status, ORDER_STATUS.PENDING);

  const signature_key = createHash("sha512").update(`${order.id}200${gross}${SERVER_KEY}`).digest("hex");
  const good = await post({ ...notif, signature_key });
  assert.equal(good.status, 200);
  assert.equal(store.getOrder(order.id).status, ORDER_STATUS.CONFIRMED);

  assert.equal((await fetch(base + "/health")).status, 200);
  assert.equal((await fetch(base + "/lain")).status, 404);
  server.close();
});
