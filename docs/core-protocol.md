# OpenNOW shell/core protocol

The Qt shell and Rust application core communicate over newline-delimited UTF-8
JSON on inherited standard input/output. Standard error is reserved for redacted
diagnostics. A line is limited to 1 MiB. Unknown, malformed or oversized protocol
messages terminate the core connection instead of leaving the shell in an
ambiguous state.

## Handshake

The first shell request is always:

```json
{"type":"request","id":"1","method":"core.hello","params":{"protocolVersion":1,"shell":"qt","shellVersion":"0.5.4"}}
```

The core must return the same protocol version and its capabilities. The shell
does not send product requests before this succeeds. Version mismatches, a
five-second handshake deadline, process exit and invalid data all transition the
transport to `failed` with a credential-free diagnostic.

## Messages

```json
{"type":"request","id":"42","method":"settings.get","params":{}}
{"type":"response","id":"42","ok":true,"result":{"settings":{}}}
{"type":"response","id":"42","ok":false,"error":{"code":"invalid_setting","message":"…"}}
{"type":"event","name":"settings.changed","payload":{"key":"fps","value":120}}
{"type":"cancel","id":"42"}
```

Request IDs are unique within a core process. Every request has a bounded
deadline (100 ms to five minutes). The shell sends cancellation after a timeout
or explicit cancellation. Events are delivered in batches through a queue of at
most 512 items; overflow drops the oldest event and emits a diagnostic counter.

The core admits at most eight RPC workers, with at most four background workers
(`catalog.*`, `artwork.*`, and `network.regions.ping`). The remaining capacity
is reserved for other methods, including session/control operations. Excess
requests receive `busy`; duplicate active IDs are also rejected. Cancellation
only tracks active IDs and never frees a worker slot before that worker exits.
Cancelled requests suppress their response. Store page retries/cache traversal
and region measurement loops stop at cooperative checkpoints. An already-running
blocking HTTP, DNS, or TCP operation is not forcibly interrupted; its existing
timeout still applies. Mutating operations already dispatched are not rolled back.

## Implemented core methods

### Store pagination (`catalog.storePages.v1`)

The protocol-1 envelope and 1 MiB limit are unchanged. This additive capability
advertises cursor pagination and separate storefront presentation. The existing
`catalog.store.list` method now caps `limit` at 100 (default 100), returning one
complete upstream page instead of aggregating thousands of games.

Request: `{ "limit":100, "cursor":"", "searchQuery":"" }`. Cursor is an opaque
string (at most 4096 UTF-8 bytes); search is at most 512 UTF-8 bytes. Response:
`{ "games":[], "count":0, "totalCount":0, "hasNextPage":false,
"nextCursor":"", "source":"store-browse", "fetchedAt":0 }`.
Pass `nextCursor` unchanged to the next call with the same search. A final page
has `hasNextPage:false`; empty non-final pages may advance past unmappable apps.
Missing, repeated or oversized continuation cursors are errors, not completion.

Each result is limited to 768 KiB after JSON encoding, leaving room for the
envelope. Oversized pages are refetched with a smaller count at the same cursor;
they are never truncated while advancing the cursor. A single oversized game
returns `catalog_response_too_large` without disrupting the core connection.

`catalog.store.presentation` accepts `{ "section":"marquee" }` (also `panels`
or `filters`) and returns `{ "section":"marquee", "items":[] }`, independently
bounded to 768 KiB. Failed/oversized optional sections do not fail game pages.

Store pages and presentation results include `cacheHit` (boolean). Successful
responses are persisted under the core data directory in `store-cache-v1`, with
hashed account/provider/membership/proxy/locale and request keys. Credentials
are not stored. Reads remain bounded to 768 KiB; the cache is limited to 64 MiB
and 512 entries. Missing or corrupt entries refetch normally. There is no timed
catalog invalidation: an optional `refresh:true` on a first-page request clears
that account/context's pages and presentation before fetching. Continuations
must omit it or send false. Other accounts' entries are unaffected.

The shell serializes game requests, merges by stable game identity, and retains
loaded games and the failed cursor on error. Retries resume that page.
Search/account changes cancel requests before clearing state; late responses
are ignored.

### Local Store browsing (`catalog.storeLocal.v1`)

`catalog.store.local` pages and searches the saved catalog, without replaying
every cached page into Qt. It accepts `limit` (capped at 60), `cursor`,
`searchQuery`, and optional `genre`, `store`, and `categoryId` strings (at most
256 UTF-8 bytes each). A cursor belongs to its search/filter context and must
be returned unchanged. The existing 768 KiB result budget still applies.

The result includes `games`, `count`, `totalCount` (matching games),
`catalogTotalCount`, `hasNextPage`, `nextCursor`, `source:"store-local"`,
`cacheHit:true`, and `cacheComplete`. First pages also include `facets` with
all indexed genres, stores, and official categories (`id`, `label`, `count`),
not just those present on the visible page. Subsequent pages omit facet data
by returning `facets:null`. `categoryId:"all"` selects the entire catalog.

The core builds one bounded, account-scoped metadata index from saved pages;
full records remain on disk and only selected results are materialized.
Search ranks exact titles, acronyms, prefixes, reordered words and single
typing errors, with owned games preferred on close matches. Store requests
40 results per page; Ctrl+K uses the same algorithm with a six-result limit
and debounced, cancellable requests.

There is no automatic catalog crawl. Scroll/navigation demand or Load more
requests the next page. A missing/partial cache fetches bounded upstream pages
on demand; `cacheComplete:false` distinguishes this from a complete index.
On this method, `refresh:true` rebuilds the local index without deleting saved
pages. The upstream `catalog.store.list` refresh behavior remains unchanged.

`catalog.store.presentation` also accepts `metadataOnly:true`. For `panels`,
each section returns its title and `totalCount` with an empty `games` array;
the complete panel remains cached in the core. Qt materializes shelf games
and artwork only near the viewport, using the section's local category ID
(`shelf:<panel index>:<section index>`). See all opens that category in Store.

### Method list

- `core.hello`
- `app.status`
- `settings.get`
- `settings.set`
- `settings.reset`
- `auth.providers.list`
- `auth.session.get`
- `auth.device.start`
- `auth.device.poll`
- `auth.device.complete`
- `auth.device.cancel`
- `auth.logout`
- `auth.accounts.logoutAll`
- `auth.accounts.list`
- `auth.accounts.switch`
- `auth.accounts.remove`
- `auth.pin.status`, `auth.pin.set`, `auth.pin.clear`, `auth.pin.verify`
- `catalog.public.list`
- `catalog.library.list`
- `catalog.store.list`, `catalog.store.local`, `catalog.store.presentation`
- `network.regions.list`
- `network.regions.ping`
- `account.subscription.get`
- `account.connections.list`, `account.connections.sync`, `account.connections.unlink`
- `account.connections.link.start`, `account.connections.link.poll`
- `account.storage.locations`, `account.storage.reset`
- `session.create`
- `session.poll`
- `session.stop`
- `session.active.get`
- `session.remote.list`, `session.claim`, `session.ad.report`
- `streamer.detect`
- `streamer.prepare`
- `streamer.start`
- `streamer.status.get`
- `streamer.stop`
- `streamer.input.pause`, `streamer.control`, `streamer.surface.update`
- `streamer.recording.start`, `streamer.recording.stop`
- `diagnostics.snapshot`, `diagnostics.export`, `acceptance.export`
- `media.root.get`, `media.recording.target`, `media.list`, `media.delete`
- `cache.delete`
- `queue.status.get`, `queue.serverMapping.get`
- `thanks.data.get`, `communityProxy.provision`
- `updater.state.get`, `updater.check`, `updater.download`, `updater.install`
- `updater.highlights.get`, `updater.highlights.ack`
- `social.capabilities.get`
- `discord.activity.sync`, `discord.activity.clear`
- `telemetry.sync`, `feedback.submit`, `bug_report.submit`

### Session resume and reconnect

`session.claim` discovers the session's actual control server, sends the minimal
`action: 2, data: "RESUME"` request for a ready/streaming seat, and returns a session
with `resumePending: true` and `phase: "resuming"`. It preserves the stable device
identity and existing launch mode; it does not renegotiate codec, resolution, FPS,
or bitrate. A launching/transitioning seat is polled without repeating the mutation.

The PUT acknowledgement is not stream readiness. Call `session.poll` until a fresh
GET has a successful CloudMatch request status (`statusCode: 1`), session status
`2` or `3`, and nonempty native RTSPS endpoints. Only then does the core clear
`resumePending` and expose the ready/streaming phase. Transient status `6` continues
to report `resuming`. Transport/API failures remain typed RPC errors.

Qt polls every 1.5 seconds with only one request outstanding, bounded by 60 polls
and a 90-second deadline checked between requests. Native connection recovery first
stops the old media transport, discovers the same session ID through
`session.remote.list`, then claims/polls it and calls `streamer.prepare` for fresh
connection context. It never creates a replacement cloud game or claims a different
session. During recovery, `session.remote.list` accepts the active `sessionId` so the
core can use its remembered regional service rather than the native server IP.
Failed recovery attempts back off up to eight seconds and stop after eight
attempts; only a presented first frame resets this budget. Ending the session cancels
recovery. A native stop stalled for 30 seconds reports an error without launching
another transport over the still-owned resources.

For the embedded Qt client, `session.create` and `streamer.prepare` accept an optional
`runtimeCapabilities` object copied from the in-process streamer's protocol-5 `hello` response.
The core filters its available `videoBackends` by the persisted `nativeVideoBackend` preference
and resolves codec `auto` to AV1, HEVC, then H.264 (subject to requested color mode) before
CloudMatch allocation. This resolution is session-local: the saved preference stays `auto`.
Manual unavailable codecs/backends return `streamer_codec_unavailable` or
`streamer_backend_unavailable`, respectively. `streamer.prepare` also validates the negotiated
codec on resume; it never changes the codec of an already allocated stream. Older callers without
this optional object retain the external-streamer probe path. The additive fields do not change
the JSON protocol version or native FFI ABI.

After an update check, `updater.highlights.get` returns the latest published
release notes for the selected channel, even when that release is equal to or
older than the installed app. Its `version` and `title` identify that published
release, independently of `updater.state.get.availableVersion`. Historical notes
do not emit `updater.highlights.show` or enable downloading a downgrade. Missing
release bodies and empty channels return an explanatory note instead of the
pre-check placeholder.

Settings writes use a temporary file plus recoverable backup and normalize
compatibility-sensitive values. Setting `launchInConsoleMode=false` atomically
sets `switchToConsoleOnPad=false` too, so a manual desktop choice survives restart.
That response and `settings.changed` event additionally contain
`"changes":{"switchToConsoleOnPad":false}`; consumers apply these coupled values
before the primary key. Automatic console switching defaults off. Existing
pre-opt-in settings receive a one-time reset of automatic switching only;
explicit subsequent opt-ins and the independent startup preference are preserved.
Provider discovery falls back to NVIDIA's
default service when discovery is unavailable. Device-login tokens are stored
through the OS credential store (DPAPI/Credential Manager, Keychain or Secret
Service), with an explicit memory-only fallback when that facility is
unavailable; the shell never receives a password. Public catalog results are
cached in the core process and bounded per response.

CloudMatch session methods preserve one client/device identity through create,
poll and stop, retain pending queue responses before signaling is available,
and return the complete ordered connection, ICE and negotiated-feature payload
needed by the native streamer. `streamer.prepare` returns the normalized session
context used by the NVST runtime linked into the Qt shell. The in-process runtime
owns secure NVIDIA signaling, ICE/DTLS/SCTP, RTSPS, Mjolnir, RTCP and native
gameplay input, while Qt owns the graphics device, scene graph, video item and
all top-level windows. Legacy streamer lifecycle methods remain protocol
compatibility routes and are not used by the Qt shell.

`acceptance.export` is available only through the Qt shell's Diagnostics screen. It rejects
headless window systems and writes an atomic, redacted `opennow.live-acceptance` JSON file. The
file records machine-observed ten-minute streaming, first-frame, guide/input ownership, surface,
microphone, recording, hashed media, bounded recovery and terminal-error checks. It contains no
account identifiers, session identifiers, URLs, process identifiers, executable paths or local
media paths. A false check is retained as evidence of an incomplete run; it is never promoted to
a pass by the release verifier.

Network work runs outside the protocol reader with a fixed concurrency ceiling,
connect/request deadlines, one serialized output writer and best-effort response
suppression after cancellation. The full Electron API inventory remains tracked
in [the machine-readable parity manifest](../native/opennow-core/contracts/legacy-open-now-api.json),
validated against its [JSON schema](../native/opennow-core/contracts/legacy-open-now-api.schema.json)
and executable golden-fixture tests. A method is not considered ported until its
owner, wire shape, fixtures and replacement disposition are recorded there.
