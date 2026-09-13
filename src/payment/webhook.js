import { createServer } from "node:http";
import { normalizeStatus } from "./midtrans.js";

// HTTP server kecil untuk menerima notifikasi Midtrans (Payment Notification URL).
export function createWebhookServer({ client, paymentService, path = "/midtrans/notification", logger = console }) {
  return createServer(async (req, res) => {
    if (req.method === "GET" && req.url === "/health") {
      res.writeHead(200).end("ok");
      return;
    }
    if (req.method !== "POST" || req.url !== path) {
      res.writeHead(404).end();
      return;
    }

    let body;
    try {
      body = JSON.parse(await readBody(req, 64 * 1024));
    } catch {
      res.writeHead(400).end("invalid json");
      return;
    }

    if (!client.verifyNotification(body)) {
      logger.warn("Notifikasi Midtrans dengan signature tidak valid untuk", body?.order_id);
      res.writeHead(403).end("invalid signature");
      return;
    }

    try {
      await paymentService.apply(normalizeStatus(body));
      res.writeHead(200).end("ok");
    } catch (err) {
      logger.error("Gagal memproses notifikasi:", err);
      // 500 supaya Midtrans mengulang pengiriman.
      res.writeHead(500).end("error");
    }
  });
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", (chunk) => {
      data += chunk;
      if (data.length > limit) {
        reject(new Error("body too large"));
        req.destroy();
      }
    });
    req.on("end", () => resolve(data));
    req.on("error", reject);
  });
}
