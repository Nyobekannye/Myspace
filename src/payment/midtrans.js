import { createHash } from "node:crypto";

// Klien Midtrans Core API untuk QRIS (acquirer GoPay/ShopeePay).
// Docs: https://docs.midtrans.com/reference/qris
export class MidtransClient {
  constructor({ serverKey, isProduction = false, acquirer = "gopay", expiryMinutes = 15, fetchFn = fetch }) {
    if (!serverKey) throw new Error("MIDTRANS_SERVER_KEY kosong");
    this.serverKey = serverKey;
    this.acquirer = acquirer;
    this.expiryMinutes = expiryMinutes;
    this.fetch = fetchFn;
    this.baseUrl = isProduction ? "https://api.midtrans.com" : "https://api.sandbox.midtrans.com";
    this.authHeader = "Basic " + Buffer.from(`${serverKey}:`).toString("base64");
  }

  async #request(method, path, body) {
    const res = await this.fetch(this.baseUrl + path, {
      method,
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        Authorization: this.authHeader,
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const data = await res.json().catch(() => ({}));
    // Midtrans mengembalikan HTTP 200 dengan status_code 4xx untuk error validasi.
    const code = Number(data.status_code ?? res.status);
    if (!res.ok || code >= 400) {
      throw new Error(`Midtrans ${code}: ${data.status_message ?? res.statusText}`);
    }
    return data;
  }

  async chargeQris(order) {
    const data = await this.#request("POST", "/v2/charge", {
      payment_type: "qris",
      transaction_details: { order_id: order.id, gross_amount: order.total },
      item_details: order.items.map((i) => ({
        id: i.productId,
        price: i.price,
        quantity: i.qty,
        name: i.name.slice(0, 50),
      })),
      customer_details: {
        first_name: order.customer.name.slice(0, 255),
        phone: order.customer.phone,
        shipping_address: { address: order.customer.address.slice(0, 200) },
      },
      qris: { acquirer: this.acquirer },
      custom_expiry: { expiry_duration: this.expiryMinutes, unit: "minute" },
    });
    const qrAction = data.actions?.find((a) => a.name === "generate-qr-code");
    return {
      provider: "midtrans",
      transactionId: data.transaction_id,
      status: data.transaction_status,
      qrUrl: qrAction?.url ?? null,
      qrString: data.qr_string ?? null,
      expiresAt: new Date(Date.now() + this.expiryMinutes * 60_000).toISOString(),
    };
  }

  async getStatus(orderId) {
    const data = await this.#request("GET", `/v2/${encodeURIComponent(orderId)}/status`);
    return normalizeStatus(data);
  }

  async fetchQrImage(url) {
    const res = await this.fetch(url, { headers: { Authorization: this.authHeader } });
    if (!res.ok) throw new Error(`Gagal ambil QR: HTTP ${res.status}`);
    return Buffer.from(await res.arrayBuffer());
  }

  // signature_key = sha512(order_id + status_code + gross_amount + server_key)
  verifyNotification(body) {
    if (!body?.order_id || !body?.status_code || !body?.gross_amount || !body?.signature_key) return false;
    const expected = createHash("sha512")
      .update(`${body.order_id}${body.status_code}${body.gross_amount}${this.serverKey}`)
      .digest("hex");
    return expected === body.signature_key;
  }
}

// Ubah respons Midtrans menjadi { orderId, outcome: "paid" | "pending" | "failed" }.
export function normalizeStatus(data) {
  const s = data.transaction_status;
  let outcome = "pending";
  if ((s === "settlement" || s === "capture") && data.fraud_status !== "deny") outcome = "paid";
  else if (["deny", "cancel", "expire", "failure"].includes(s)) outcome = "failed";
  return {
    orderId: data.order_id,
    transactionId: data.transaction_id,
    transactionStatus: s,
    outcome,
  };
}
