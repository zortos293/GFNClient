# macOS Update Signature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish unsigned macOS updates with a stable outer designated requirement and valid nested code signatures.

**Architecture:** Electron Builder continues signing the complete application tree. The existing post-sign hook re-signs only the outer application bundle with the stable bundle-ID requirement, and release CI rejects any package that fails Apple's deep strict verification.

**Tech Stack:** Electron Builder, Node.js 22, macOS `codesign`, GitHub Actions

## Global Constraints

- Preserve the stable `com.zortos.opennow.stable` outer designated requirement.
- Do not add dependencies or change signed Developer ID builds.
- Verify the final macOS application recursively before release upload.

---

### Task 1: Reproduce and correct unsigned macOS signing

**Files:**
- Modify: `opennow-stable/scripts/after-sign-mac.mjs`
- Test: `opennow-stable/scripts/after-sign-mac.test.mjs`

**Interfaces:**
- Consumes: Electron Builder's `afterSign({ appOutDir, packager })` hook context.
- Produces: an outer application signature with `designated => identifier "<bundleId>"` without replacing nested signatures.

- [ ] **Step 1: Add a regression test that records codesign arguments**

Create a temporary fake `codesign` executable, invoke the hook, and assert that its arguments include the explicit requirement but exclude `--deep`.

- [ ] **Step 2: Run the regression test and confirm it fails**

Run: `node --test scripts/after-sign-mac.test.mjs`

Expected: FAIL because the hook currently passes `--deep`.

- [ ] **Step 3: Remove `--deep` from the hook**

Keep the existing `--force`, ad-hoc identity, and explicit designated requirement unchanged.

- [ ] **Step 4: Run the regression test and confirm it passes**

Run: `node --test scripts/after-sign-mac.test.mjs`

Expected: PASS.

### Task 2: Reject invalid macOS release packages

**Files:**
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: Electron Builder's unpacked `dist-release/mac*/OpenNOW.app` output.
- Produces: a failed macOS build job when `codesign --verify --deep --strict` fails.

- [ ] **Step 1: Confirm the released v0.5.2 archive fails strict verification**

Run: `codesign --verify --deep --strict --verbose=4 /tmp/opennow-update-inspect/OpenNOW.app`

Expected: FAIL with nested code modified or invalid.

- [ ] **Step 2: Add a macOS-only verification step after packaging**

Locate the generated `OpenNOW.app` under `dist-release`, require exactly one result, and run `codesign --verify --deep --strict --verbose=2` against it.

- [ ] **Step 3: Build a fresh unsigned macOS package and verify it**

Run the narrowest available Electron Builder macOS package command for the host architecture, then apply the same strict verification command to the generated app.

Expected: PASS with a stable outer requirement and valid nested signatures.

### Task 3: Repository verification and delivery

**Files:**
- Verify all files changed on this branch.

**Interfaces:**
- Consumes: completed signing and workflow changes.
- Produces: a reviewable draft pull request against `OpenCloudGaming/OpenNOW:main`.

- [ ] **Step 1: Run focused and repository checks**

Run `node --test scripts/after-sign-mac.test.mjs`, `npm run typecheck`, and `git diff --check` from `opennow-stable` where applicable.

- [ ] **Step 2: Review and commit the focused diff**

Use a conventional `fix(updater)` commit explaining the signing boundary.

- [ ] **Step 3: Push the branch and open a draft PR**

Push `fix/macos-update-signature` to the fork and open a brief draft PR against `OpenCloudGaming/OpenNOW:main` with reproduction and verification evidence.
