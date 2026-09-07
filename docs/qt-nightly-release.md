# Unsigned Qt nightly releases

Use `qt-ci` on `dev` for the current Qt/native application. The old release workflows on `main`
do not build this product. A nightly needs no Windows signing certificate, protected signing
environment, or self-hosted update signer.

## Build and publish

After the release changes are merged into `dev`, run:

```sh
gh workflow run qt-ci --repo OpenCloudGaming/OpenNOW --ref dev -f publish_nightly=false
```

This builds and tests all four targets without publishing anything. Download the four unsigned
artifact groups from that workflow run and perform the relevant checks in
[`qt-acceptance.md`](qt-acceptance.md), especially Windows ARM64, which is cross-compiled and
cannot run its application tests on the x64 CI worker.

To publish a nightly from the selected branch after all CI jobs pass:

```sh
gh workflow run qt-ci --repo OpenCloudGaming/OpenNOW --ref dev -f publish_nightly=true
```

Use the registered `qt-ci` workflow name and an explicit `--ref dev`. The Actions web UI may not
show the dispatch control until this workflow is also present on the default branch. Do not use
the legacy `release` workflow instead. Publishing is opt-in: pushes, pull requests, and a manual
run with the default input only upload validation artifacts.

The version comes from `project(OpenNOWQt VERSION ...)` and the workflow run identity:
`1.0.0-nightly.<run-number>.<run-attempt>`. The exact checked-out SHA is shared by every build and
recorded in `RELEASE-INFO.json`. Rerunning **all jobs** creates a distinct nightly version; rerunning
only failed jobs retains the identity from the original metadata job. A successful
publisher first uploads a draft, then makes it a prerelease without changing the latest stable
release. A failed upload can leave a draft; it does not expose an incomplete public release.
After a failed publication, rerun all jobs to produce a fresh candidate rather than overwriting
an existing tag or asset. Remove abandoned drafts separately if needed.

## Download formats

Each release contains six packages with distinct version/platform/architecture filenames:

- `OpenNOW-Qt-<version>-Windows-x64.zip` and `...-Windows-arm64.zip` are unsigned portable builds.
  Extract the entire archive, then launch `bin/OpenNOW.exe`. Windows may display SmartScreen or
  unknown-publisher warnings. These nightlies do not include MSI installers.
- `OpenNOW-Qt-<version>-Linux-x64.AppImage` and `...-Linux-arm64.AppImage` are the recommended
  portable Linux downloads. Make the downloaded file executable before starting it.
- `OpenNOW-Qt-<version>-Linux-x64.deb` and `...-Linux-arm64.deb` require distribution-provided
  Qt 6.8+ and SDL3. Stock Ubuntu 24.04 does not provide those versions; use the AppImage there.
  The internal Debian version uses `1.0.0~nightly.<run>.<attempt>` so a later stable `1.0.0`
  correctly supersedes it.

macOS is currently disabled. `SHA256SUMS` covers the six packages and `RELEASE-INFO.json`.
Checksums detect corruption; they do not replace a publisher signature. The inventory rejects
missing platforms, duplicate basenames, wrong versions, empty files, and unexpected assets
before any release upload.

## Updates and signed candidates

These nightlies deliberately have no pinned Ed25519 update-signing key. Users download updates
manually; the client does not bypass signature verification to install unsigned update payloads.
The updater compares complete semantic versions, so nightly runs order numerically and stable
`1.0.0` sorts after its nightlies when signed updates are configured in future builds.

Authenticode signing and Ed25519 update signing are different mechanisms. Adding an update key
later does not require buying a Windows certificate, but it does require a separately reviewed
key-management and manifest-signing setup. Never distribute the private key in a build.

The separate [`qt-release-candidate`](qt-release-candidate.md) workflow remains a signed,
numeric-version production-candidate path. It still requires its documented certificates,
environments, and isolated signer. It is not the unsigned nightly publishing workflow.
