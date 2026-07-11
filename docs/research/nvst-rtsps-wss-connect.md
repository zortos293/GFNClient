# NVST RTSPS `:322` WebSocket connect

Sources: Bifrost2.dll (Poco 1.14.1), geronimo.log, Mall JS, live OpenNOW probes.

## Verdict

| Item | Finding |
|------|---------|
| Transport | Real TLS + HTTP/1.1 WebSocket upgrade (Poco), then RTSP-over-WS |
| Mall JS | **WebRTC-only** — no `:322` / `rtsps://` / `/rtsp` connect path |
| Classic NVST | Entirely in **Bifrost2.dll** |
| Absolute-form | **Falsified** — `rtsps://` / `wss://` / `https://` → 404 |
| Empty path | **Falsified** — → 400 |
| `/` via Node `ws` | 404 (extra Client headers; not Bifrost-shaped) |
| `/` via raw TLS Bifrost headers | **Primary pending re-test** |
| `/v2/session/<id>` | Secondary fallback |

## Official sequence (geronimo)

```
RTSP Scheme set to SECURE WEBSOCKET
Establishing … TAG 'WSS' URL 'rtsps://host:322'
Connecting to host <host>, port 322
WSS client using 1-way SSL
streamingSessionId : <uuid>   ← after upgrade, before OPTIONS
WSS Options: rtsps://host:322 → HTTP/1.0 200 OK
```

## OpenNOW probe

1. Raw TLS to `host:322`
2. `GET / HTTP/1.1` + Bifrost/Poco headers (`Host`, `Connection: Upgrade`, `Upgrade: websocket`, `Sec-WebSocket-Version: 13`, `Sec-WebSocket-Key`, `Content-Length: 0`)
3. On 404 → `GET /v2/session/<CloudMatchSessionId> HTTP/1.1`
4. On 403 → retry with `x-nv-sessionid`

## Next if still 404

Capture official upgrade bytes (Wireshark + SSLKEYLOGFILE). Path guessing from the binary is exhausted.
