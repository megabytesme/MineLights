# MineLights Protocol V2

Status: implementation and compatibility notes for the current 2.4.0 candidate.

This specification is shared by MineLights Java clients, the Horizon client, MineLights Server, and future non-Minecraft clients. The server is a general lighting service; Minecraft game state belongs in the Minecraft client.

## Goals

- Authenticate every V2 client and encrypt remote sessions.
- Make the same-machine path automatic after the user permits the mod to run MineLights Server.
- Permit explicit, authenticated connections to servers on other PCs and to multiple servers at once.
- Remove the V2 dependency on fixed UDP ports and separate control/data endpoints.
- Preserve a deliberate local-only compatibility path for older V1 clients.
- Support clients other than MineLights Minecraft mods without Minecraft-specific wire fields.

## Transport and discovery

- V2 uses one bidirectional TCP stream per client/server session. Device discovery, commands, status, and lighting frames share the stream. V2 does not use a fixed UDP port.
- Local-only mode binds to loopback on an OS-selected port. The server writes a per-user endpoint document containing server ID, port, and TLS certificate fingerprint. The document is protected by current-user filesystem permissions. Java clients read this document, so the protocol remains usable on older Java runtimes.
- Remote access is an explicit server setting. The user selects the network interfaces to expose. The listener uses the configured port (default 63215); on conflict it falls back to an OS-selected port and advertises the actual endpoint. It never silently binds every interface.
- The current implementation uses explicit pairing files for remote servers; mDNS/DNS-SD discovery is not implemented. Discovery data is not trust.
- A client may maintain sessions to several server IDs simultaneously. A server ID is stable for that server installation.
- Servers must bound concurrent TLS handshakes and active sessions, apply a short handshake deadline, and reject excess connections before reading large payloads. The initial server caps all local and remote V2 connections at 16, limits remote unauthenticated handshakes to four while leaving loopback handshakes independent, and applies a 30-second handshake timeout. Apply payload-size limits before allocation: eight MiB maximum per message and one MiB maximum per control message.
- No automatic router port forwarding. Internet-range remote access is left to a user-managed VPN or explicitly configured network path.
- The MineLights clients' direct OpenRGB integration has a separate configurable list of `{host, port}` endpoints (default `127.0.0.1:6742`) so OpenRGB servers on other PCs can be selected too. These are OpenRGB protocol connections, not V2 sessions; MineLights credentials and TLS pinning do not apply to them. The UI must make that distinction clear and never silently expose an OpenRGB server to the network.

On Windows, the endpoint and local-enrollment files live under `%LOCALAPPDATA%\MineLights\v2\`. Apply an ACL for the current user and SYSTEM/Administrators as appropriate. `endpoint.json` has `{schema_version: 1, server_id, server_name, host: "127.0.0.1", port, tls_certificate_sha256, protocol_major: 2, protocol_minor: 0}`. A separate `local-enrollment.key` contains 32 raw random bytes and is readable only by that Windows account. Each mod instance stores `{client_id, client_secret}` in its own config location with current-user-only permissions. Remote client entries additionally store `{server_id, host, port, tls_certificate_sha256, client_id, client_secret}`. On a future non-Windows server, use that platform's per-user data directory and owner-only permissions. The endpoint file is discovery data, not a credential.

## TLS, identity, and pairing

- TLS is required for every V2 TCP session; TLS 1.2 is the minimum for compatibility with Java 8 clients, and TLS 1.3 is preferred. The fingerprint is SHA-256 over the leaf certificate's DER bytes, rendered as 64 lowercase hexadecimal characters without separators. Clients pin the fingerprint learned from the protected local endpoint document or explicitly confirmed during remote pairing. Never trust an arbitrary first certificate silently.
- Each client installation has a random UUID client ID and a 256-bit secret. The client generates and persists its secret; the server stores the corresponding credential in its protected per-user data directory. Credentials are distinct and individually revocable. Secrets and proofs must never be logged.
- After TLS certificate pinning, each connection authenticates with a fresh 256-bit server nonce and client nonce. Nonces and proofs use unpadded base64url. `CLIENT_AUTH` has `{credential_type, credential_id, client_nonce, proof}`. `credential_id` is the client UUID for `client_secret`, `local` for `local_enrollment`, and the pairing UUID for `pairing_code`. The proof is `HMAC-SHA-256(key, ASCII("MineLights Protocol V2") || 0x00 || credential_type_ascii || 0x00 || credential_id_ascii || 0x00 || server_id_ascii || 0x00 || client_id_ascii || server_nonce[32] || client_nonce[32] || major_u16_be || minor_u16_be)`. There is no separator after `client_id_ascii`. The key is the stored client secret, local-enrollment key, or selected pairing code. The server compares proofs in constant time and rejects reused challenges. A credential itself is never sent. TLS provides confidentiality and transport integrity, including for lighting frames.
- Local pairing is automatic after the user authorizes the mod to run the server. The client creates its own ID and secret, then authenticates once using `local-enrollment.key` over the pinned local TLS connection. This credential type is accepted only when the peer address is loopback. The authenticated client then sends `REQUEST` operation `client.register` with its ID, secret, app identity, and requested scopes. The server records the client and returns granted scopes. The client reconnects using its per-install secret. Same-user local processes are within the local trust boundary. Do not put secrets in command-line arguments or logs.
- Remote pairing is a one-time explicit approval. The server tray UI exports a short-lived `.mlpair` JSON file. Fields are `{schema_version: 1, server_id, host, port, tls_certificate_sha256, pairing_id, pairing_code, expires_at_utc}`. IDs are lowercase UUIDs, the fingerprint is 64 lowercase hex characters, the pairing code is 32 random bytes encoded as unpadded base64url, and expiry is RFC 3339 UTC. The user transfers/imports the file using local client settings or a local command that accepts only its path; never place pairing codes or secrets in Minecraft chat, command history, or logs. The client pins the fingerprint, authenticates once using `credential_type=pairing_code` and `credential_id=pairing_id`, sends `client.register` with its generated secret, and reconnects using its per-install secret. Pairing codes expire after five minutes, are rate-limited, and are accepted only on the explicitly enabled remote listener; a successful registration consumes the code. Multiple pairing IDs may be active so several PCs can be paired without invalidating one another.
- The server rejects V2 clients with unknown IDs or invalid proofs. It supports revoke and re-pair actions from the tray UI.

## Framing and version negotiation

Each TCP message begins with a 16-byte big-endian header:

| Offset | Size | Field |
| --- | ---: | --- |
| 0 | 4 | ASCII magic `MLV2` |
| 4 | 2 | Protocol major |
| 6 | 2 | Protocol minor |
| 8 | 2 | Message type |
| 10 | 2 | Flags, initially zero |
| 12 | 4 | Payload byte length |

The first protocol major/minor is `2.0`. `CLIENT_HELLO` carries the client's supported minor range; `SERVER_HELLO` selects the minor. Minor versions add optional capabilities; an unsupported major fails with an actionable error. Enforce an 8 MiB maximum payload before allocation and a 1 MiB maximum for control messages. V2.0 payloads, including lighting frames, are UTF-8 JSON for straightforward Java/Rust/C# interoperability. A later negotiated capability may add a compact binary frame payload if profiling shows JSON is a bottleneck. Do not deserialize arbitrary types.

Required initial message types:

- Message type values: `0x0001 CLIENT_HELLO`, `0x0002 SERVER_HELLO`, `0x0003 CLIENT_AUTH`, `0x0004 AUTH_RESULT`, `0x0010 DEVICE_LIST`, `0x0011 DEVICE_CHANGED`, `0x0020 LIGHTING_FRAME`, `0x0030 PING`, `0x0031 PONG`, `0x0040 REQUEST`, `0x0041 RESPONSE`, `0x00FE ERROR`, `0x00FF GOODBYE`.
- `CLIENT_HELLO`: `{client_id, app_id, app_version, min_minor, max_minor, capabilities[], requested_integrations?}`. The optional list contains canonical provider names; control-only and enrollment connections send an empty list. The server must not apply it until normal client authentication succeeds. An absent list identifies an older V2 client and requests all server-permitted providers for compatibility.
- `SERVER_HELLO`: `{server_id, server_name, server_version, selected_minor, nonce}` where nonce is unpadded base64url for 32 random bytes.
- `CLIENT_AUTH`: `{credential_type, credential_id, client_nonce, proof}` with unpadded base64url for nonce and proof.
- `AUTH_RESULT`: `{ok, scopes[], error_code?}`. Required auth errors include `UNKNOWN_CLIENT`, `BAD_PROOF`, `PAIRING_REQUIRED`, `PAIRING_EXPIRED`, and `VERSION_UNSUPPORTED`.
- `DEVICE_LIST`: `{revision, devices[]}`. The server sends an unsolicited full snapshot immediately after successful normal authentication, then sends `DEVICE_CHANGED` when the snapshot changes. Each device has stable `device_id`, `name`, `vendor`, `integration`, `elements[]`, and `capabilities[]`. Each element's `element_id` is a server-local unsigned 32-bit integer; JSON clients must preserve the full 0..4294967295 range. Optional key/zone metadata may be included.
- `DEVICE_CHANGED`: `{revision, devices[]}` using the same descriptor shape.
- `LIGHTING_FRAME`: `{sequence, ttl_ms, targets[]}`. Each target is `{device_id, colors[]}`; each color is `{element_id, r, g, b}` with channels 0–255. Sequence is a wrapping unsigned 32-bit integer. `ttl_ms` is 1–1000.
- `PING` / `PONG`: `{nonce}`. `REQUEST` / `RESPONSE`: `{request_id, operation, params}` and `{request_id, ok, result?, error_code?}`. `request_id` is a lowercase canonical UUID string and is unique among outstanding requests on that connection. `ERROR`: `{error_code, message}`. `GOODBYE`: `{reason?}`.

The current V2.0 control operations are `devices.get`, `lighting.acquire`, `lighting.release`, `integrations.set`, `server.info`, `server.shutdown`, and `client.register`. The last is accepted only for `local_enrollment` or `pairing_code`, with the temporary `client.enroll` scope. `devices.get` requires `devices.read` and returns `{revision, devices[]}`. `client.register` takes `{client_id, client_secret, app_id, app_version, display_name, requested_scopes[]}` and returns `{granted_scopes[]}`; the secret is 32 random bytes encoded as unpadded base64url. After registration, close the enrollment connection and reconnect with the per-client credential. The server returns `OPERATION_UNSUPPORTED` for unknown operations; clients must handle that response when talking to older V2 servers.

`integrations.set` takes `{providers: [...]}` and updates only the authenticated session's provider interest. The server combines interest from active sessions and permitted V1 users without letting one client disable another client's providers; the owner's global restrictions still apply. Device snapshots can update asynchronously afterward. Older V2 servers may return `OPERATION_UNSUPPORTED`; clients retain their local device filtering and continue safely.

`lighting.acquire` requires `lighting.write` and takes `{device_ids[]}` with 1–128 distinct, nonempty IDs. If at least one device is granted, the success result is `{granted_device_ids[], busy_device_ids[]}`. Busy IDs include unavailable or unknown devices; a partial grant is still successful. If none is granted, the response has `ok: false`, `error_code: "LEASE_BUSY"`, and `details: {device_id}` identifying the first busy ID. Invalid counts return `INVALID_DEVICE_IDS`. `lighting.release` takes `{device_ids[]}` and returns `{released_device_ids[]}` for leases actually held by that session. Clients release removed devices and retry unavailable or preempted leases. A lease lasts five seconds from acquisition or the last valid frame; PING alone does not renew it. It is released on disconnect. The frame TTL separately controls how long the last color frame remains active.

`server.info` has empty parameters and returns `{launch_id: string|null, legacy_v1_enabled: boolean}`. Older V2 servers may omit `legacy_v1_enabled`; clients treat its absence as unknown. `server.shutdown` has empty parameters and returns `{accepted: true}` when the requester is the sole authenticated session. Otherwise its successful response is `{accepted: false, reason: "other_clients", pending_for_ms: 0|5000}`, or `{accepted: false, reason: "legacy_enabled", pending_for_ms: 0}` if legacy local access is enabled. These operations are handled only on an authenticated normal client session; they do not accept enrollment-only sessions. Their lifecycle and security implications are described below.

The application version is descriptive metadata, not the protocol version. A V1 client cannot report its mod or Minecraft version; do not claim to identify it.

## Device and frame model

- IDs are namespaced: `(server_id, device_id, element_id)`. IDs from different PCs never collide.
- Device descriptors expose stable identity, display name, integration, zones/elements, and capabilities. Optional `provider` is the canonical RGB.NET provider name or null; optional `legacy_device_key` is the exact V1 `manufacturer|model` key for migrating per-client exclusions. Older V2 servers can omit both; clients use cautious fallback matching and never alter server-wide provider state. Keyboard key maps are optional capabilities, not required fields.
- The server owns its enabled integration list and hardware configuration. Client handshakes do not reconfigure global hardware or change another application's settings. Client-specific disabled-device choices are local filtering in that client.
- A frame is a complete desired update for the supplied targets, with a monotonically increasing per-session sequence number and a short lifetime. Clients replace unsent frames with the newest frame; they must not build a stale backlog.
- The server validates every target and color value, rejects malformed or out-of-order frames, and restores idle lighting when the last frame's TTL expires.
- A device has one active writer lease. The owner can set each paired client's persistent priority to High (1), Normal (0, the default), or Low (-1) in the server tray. A higher-priority requester can preempt a lower-priority lease. At equal priority the client identity that first authenticated during this server run wins; that order survives reconnects during the run, and session order breaks ties for simultaneous sessions using the same identity. The order resets when the server restarts. Priority changes affect subsequent acquisition attempts. A client whose lease was preempted receives `LEASE_REQUIRED` if it sends a frame for that device and must reacquire before writing. Expiry or disconnect releases the lease and restores idle state.
- Explicitly enabled V1 traffic is lower priority than V2: while a device has an active V2 lease, discard V1 colors for that device. V1 never blocks V2 lease acquisition. This prevents an old local mod from fighting with an authenticated client on the same hardware.

## Authorization and management

- Default client scopes are `devices.read` and `lighting.write` only.
- Remote access requires authentication and an explicitly enabled listener. Authentication never grants administrator privileges.
- Any normally authenticated client may request `server.info` and `server.shutdown`; neither operation currently has a separate management scope. Treat paired client credentials as granting the ability to request shutdown as well as their advertised lighting scopes. There is no raw unauthenticated V2 process-management endpoint. Restart, elevated restart, configuration, credential management, and pairing approval remain tray operations.
- Shutdown is accepted only when the requester is the sole authenticated active session and legacy local access is disabled. Legacy access blocks client-requested shutdown because V1 cannot reliably report active clients; exit via the tray or explicitly disable legacy access once older clients no longer need it. The server atomically rejects new sessions after accepting the request, responds, and performs its normal cleanup. When other sessions exist, it refuses immediate shutdown. If launched with `--client-started`, it also starts a five-second pending window: if all authenticated sessions disconnect during that window, it stops; otherwise it remains running. A manually started server reports `pending_for_ms: 0`. Each authenticated connection counts separately, including multiple sessions using one credential. Unauthenticated V1 clients cannot be counted as authenticated sessions.
- Auto-starting Java and Horizon clients supply a fresh UUID in the child environment variable `MINELIGHTS_LAUNCH_ID` with `--client-started`. The server clears the variable from its environment and exposes the UUID only through authenticated `server.info`. This ID is non-secret correlation data, not a credential or authorization token. On exit, the launching client requests shutdown only after authenticating and matching that ID; missing, mismatched, unsupported, or unreachable identity leaves the server running. The explicit `/minelights server stop` command can use a separate authenticated control connection when lighting is inactive and does not require a launch-ID match. Shutdown is best effort if startup, discovery, or authentication does not complete in time. A disconnect without a shutdown request releases leases and does not itself stop a normally running server.
- OpenRGB and Yeelight connections remain separate integrations with their own addresses and security characteristics. MineLights credentials are never sent to those protocols. Java and Horizon should expose OpenRGB endpoint add/list/remove plus a local default, using local settings/commands rather than V2 credentials.

## V1 compatibility

- V1 is the legacy fixed-endpoint protocol: TCP 63211 handshake, UDP 63212 JSON frames, TCP 63213 raw commands, and UDP 63214 discovery. V1 clients are hard-coded to loopback and have no authentication.
- The V2 service remains usable if any V1 port is unavailable. V1 listeners are optional and loopback-only; their bind failures do not prevent V2 startup.
- Keep TCP 63213 as a loopback-only liveness probe for older clients, which use a connect-only check before attempting the V1 handshake. Accept and immediately close these connections without reading or executing payloads; no V1 process-management command is supported.
- When the server detects a V1 handshake, it presents a rate-limited warning and offers `Allow legacy local clients`. This setting is off by default and does not disable V2 authentication or enable remote V1 access. Do not automatically accept a V1 client or silently change the setting.
- Legacy lighting compatibility does not authorize V1 process-management commands. The raw `shutdown`, `restart`, and `restart_admin` messages are not executed, even when V1 device access is enabled. The tray and authenticated V2 shutdown request manage server lifetime. V1 clients that attempt to replace the server automatically may need a client update or a manual server update.
- If V1 compatibility is enabled but its fixed lighting port cannot bind, explain that old clients cannot use this server instance; V2 continues working. The tray UI offers the same explicit setting when the server has no interactive prompt surface.
- Keep V1 support during the migration period. V1 clients cannot be securely upgraded by a server-side protocol change; they need the explicit local exception until users update them.

## Implementation order

1. Implement shared wire constants, models, parsers, authentication, endpoint publication, and server session handling.
2. Implement V2 in Java common code and use V2 only; older released clients retain their V1 protocol.
3. Implement the same V2 behavior in Horizon Rust; keep mod settings and user-visible behavior aligned.
4. Add multiple-server configuration and authenticated remote pairing in both clients.
5. Add the server tray controls for network scope, paired clients, credential revocation, and V1 compatibility.
6. Integrate other software clients against the generic device/frame schema.
7. Keep future protocol notes and compatibility tests aligned with this document before changing message semantics.

## Initial compatibility matrix

| Client | Local V2 | Remote V2 | Legacy V1 |
| --- | --- | --- | --- |
| Updated Java mod | Automatic local pairing | One-time pair, then automatic reconnect | Not supported |
| Updated Horizon mod | Automatic local pairing | One-time pair, then automatic reconnect | Not supported |
| Older Java/Horizon mod | No V2 support | No remote support | Explicitly enabled loopback only |
| Future non-Minecraft client | Automatic local pairing through supported bootstrap | One-time pair, then automatic reconnect | Not applicable unless implemented |
