# SDA++ Pairing Relay

The relay carries only an ECDH/AES-GCM encrypted pairing envelope. It cannot decrypt WebDAV credentials.

- one Durable Object per random 128-bit session id
- independent random 256-bit bearer token
- 64 KiB maximum payload
- two-minute TTL
- payload deleted on first successful download

Deploy with `npm install`, `npx wrangler login`, and `npm run deploy`.
