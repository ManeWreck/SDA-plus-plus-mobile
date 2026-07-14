import { DurableObject } from "cloudflare:workers";

interface Env {
  PAIRING_SESSIONS: DurableObjectNamespace<PairingSession>;
}

const SESSION_PATH = /^\/v1\/pair\/([A-Za-z0-9_-]{22})$/;
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/;
const MAX_ENVELOPE_BYTES = 64 * 1024;
const SESSION_TTL_MS = 2 * 60 * 1000;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "SDA++ pairing relay", version: 1 });
    }

    const match = SESSION_PATH.exec(url.pathname);
    if (!match || !["GET", "PUT", "DELETE"].includes(request.method)) {
      return json({ error: "Not found" }, 404);
    }

    const objectId = env.PAIRING_SESSIONS.idFromName(match[1]);
    return env.PAIRING_SESSIONS.get(objectId).fetch(request);
  },
} satisfies ExportedHandler<Env>;

export class PairingSession extends DurableObject<Env> {
  async fetch(request: Request): Promise<Response> {
    const token = readBearerToken(request);
    if (!token) return json({ error: "Unauthorized" }, 401);

    const now = Date.now();
    const storedTokenHash = await this.ctx.storage.get<string>("tokenHash");
    const tokenHash = await sha256(token);
    if (storedTokenHash && storedTokenHash !== tokenHash) {
      return json({ error: "Unauthorized" }, 401);
    }

    let expiresAt = await this.ctx.storage.get<number>("expiresAt");
    if (!storedTokenHash) {
      expiresAt = now + SESSION_TTL_MS;
      await this.ctx.storage.put({ tokenHash, expiresAt });
      await this.ctx.storage.setAlarm(expiresAt);
    }
    if (!expiresAt || now > expiresAt) {
      await this.clear();
      return json({ error: "Pairing session expired" }, 410);
    }

    if (request.method === "PUT") {
      const contentLength = Number(request.headers.get("content-length") || "0");
      if (contentLength > MAX_ENVELOPE_BYTES) return json({ error: "Payload too large" }, 413);
      if (await this.ctx.storage.get<boolean>("consumed")) return json({ error: "Pairing session already consumed" }, 410);
      if (await this.ctx.storage.get("envelope")) return json({ error: "Payload already submitted" }, 409);

      const envelope = await request.arrayBuffer();
      if (envelope.byteLength === 0 || envelope.byteLength > MAX_ENVELOPE_BYTES) {
        return json({ error: "Invalid payload size" }, 400);
      }
      await this.ctx.storage.put("envelope", envelope);
      return json({ ok: true }, 201);
    }

    if (request.method === "GET") {
      if (await this.ctx.storage.get<boolean>("consumed")) {
        return json({ error: "Pairing session already consumed" }, 410);
      }
      const envelope = await this.ctx.storage.get<ArrayBuffer>("envelope");
      if (!envelope) return new Response(null, { status: 204, headers: noStoreHeaders() });
      await this.ctx.storage.delete("envelope");
      await this.ctx.storage.put("consumed", true);
      return new Response(envelope, {
        status: 200,
        headers: { "content-type": "application/octet-stream", ...noStoreHeaders() },
      });
    }

    await this.clear();
    return json({ ok: true });
  }

  async alarm(): Promise<void> {
    await this.clear();
  }

  private async clear(): Promise<void> {
    await this.ctx.storage.deleteAll();
  }
}

function readBearerToken(request: Request): string | null {
  const header = request.headers.get("authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  return TOKEN_PATTERN.test(token) ? token : null;
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}

function noStoreHeaders(): Record<string, string> {
  return { "cache-control": "no-store", "x-content-type-options": "nosniff" };
}

function json(body: unknown, status = 200): Response {
  return Response.json(body, { status, headers: noStoreHeaders() });
}
