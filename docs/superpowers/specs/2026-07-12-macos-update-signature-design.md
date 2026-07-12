# macOS update signature fix

The unsigned release hook must replace only the outer `OpenNOW.app` designated
requirement with the stable bundle identifier. Electron Builder remains
responsible for signing nested helpers and frameworks with their own bundle
identifiers.

Release builds must run `codesign --verify --deep --strict` against the packaged
macOS app before uploading artifacts. This prevents another update archive with
invalid nested signatures from being published.

Verification covers the v0.5.2 failure, the corrected hook, a fresh package,
and the repository's TypeScript checks. Existing v0.5.0 installations remain a
manual-update migration because their designated requirement is bound to their
exact code hash.
