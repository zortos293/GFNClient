# Discord Rich Presence on native Android

## Decision

Discord Rich Presence is technically supported on Android, but it cannot be added to OpenNOW as a buildable integration until the project owners enable the Discord Social SDK for the OpenNOW Discord application and provide the official Android SDK artifact.

Do not port the Electron app's `discord-rpc` package or connect directly to Discord's legacy localhost/WebSocket RPC endpoints. Discord's supported Android path is the native Discord Social SDK 1.10 or newer.

## Current Discord support

Discord added unauthenticated Android Rich Presence in Social SDK 1.10 on August 3, 2026. It communicates with the installed Discord Android app, so it requires:

- Android 7.0 or newer;
- Discord installed and signed in on the same device;
- activity sharing enabled in the user's Discord privacy settings;
- a Discord application with the Social SDK enabled and a valid application ID; and
- `discord_partner_sdk.aar` from that application's Developer Portal downloads.

No Discord login or OAuth token is required for this limited Rich Presence mode. The application ID identifies OpenNOW and is not a secret. Client secrets, bot tokens, or user tokens must never be placed in the app.

Official references:

- [Setting Rich Presence](https://docs.discord.com/developers/discord-social-sdk/development-guides/setting-rich-presence#rich-presence-without-authentication)
- [Discord Social SDK on Mobile](https://docs.discord.com/developers/discord-social-sdk/core-concepts/mobile)
- [Android standalone C++ setup](https://docs.discord.com/developers/discord-social-sdk/development-guides/account-linking-on-mobile#cpp-standalone-setup)
- [Platform compatibility](https://docs.discord.com/developers/discord-social-sdk/core-concepts/platform-compatibility)

## Repository fit

The native Android app already has the state needed for accurate presence:

- `OpenNowUiState.streamGame.title` is the user-visible game title.
- `streamStatus` distinguishes queueing, connecting, streaming, and idle states.
- `SessionInfo.timerStartedAtMs` provides the stable session start time.
- `streamSession` and `SessionInfo.isReadyForStream()` distinguish a real stream from a selected catalog item.
- `MainActivity` collects `OpenNowViewModel.state` and owns `onResume`/`onStop`, while `stopStream()` and launch failure paths return state to idle.
- The app already has a CMake/JNI library, so the standalone C++ SDK is the appropriate integration rather than a Kotlin RPC reimplementation.

The Android project currently targets API 36, supports API 23+, builds `arm64-v8a`, `armeabi-v7a`, and `x86_64`, and compiles its JNI target as C++17. Discord supports Android 7.0+ and its current standalone SDK requires C++20, so an implementation must guard API 23 devices and update the native target only after confirming the downloaded AAR contains every ABI OpenNOW ships.

The Electron implementation uses application ID `1479944467112001669`. That public identifier may be reusable, but the repository does not prove that its Discord application has Social SDK access or an Android 1.10+ download. The Android SDK is not published as a normal Maven dependency: Discord provides `discord_partner_sdk.aar` through the authenticated Developer Portal after Social SDK enablement. The artifact is absent from this repository, and adding source code that requires it would break local and CI builds.

## Required owner prerequisites

1. In the Discord Developer Portal, confirm that application `1479944467112001669` is owned by OpenNOW and enable its Social SDK integration. Create a separate OpenNOW application instead if that ownership or configuration cannot be confirmed.
2. Download the latest supported Android Social SDK, version 1.10 or newer, and review Discord's redistribution/license terms before committing or hosting the AAR.
3. Confirm the AAR contains `arm64-v8a`, `armeabi-v7a`, and `x86_64`. If it does not, decide whether Discord presence is excluded from unsupported build variants or OpenNOW's ABI support changes independently.
4. Configure OpenNOW's application name and Rich Presence artwork in the Developer Portal. No OAuth redirect is needed while using only unauthenticated Android Rich Presence.
5. Decide where CI obtains the version-pinned artifact without a developer's personal login. Builds must not silently download an unpinned binary or require a Discord credential.

## Minimal implementation after prerequisites

Keep the feature optional and off by default because it publishes the game title to the user's Discord profile.

1. Add a `Share current game on Discord` privacy setting explaining that Discord must be installed and signed in.
2. Add the pinned AAR, enable Gradle Prefab, link `discord_partner_sdk::discord_partner_sdk`, compile the JNI target as C++20, and call `DiscordSocialSdkInit.setEngineActivity(this)` on supported Android versions.
3. Put the Discord bridge in a focused owner (for example, `DiscordRichPresence.kt` plus `discord_rich_presence.cpp`) rather than in streaming or Compose UI code.
4. Create one unauthenticated SDK client, set the application ID, and run Discord callbacks on a single lifecycle-owned worker. SDK errors must remain non-fatal.
5. Publish only when `streamStatus == "streaming"`, `streamSession.isReadyForStream()`, and `streamGame.title` are all valid:
   - name: the configured OpenNOW Discord application name;
   - details: the game title;
   - state: `Streaming via OpenNOW`;
   - start timestamp: `SessionInfo.timerStartedAtMs` converted to Unix seconds.
6. Clear presence immediately when the setting is disabled, the stream becomes idle, a launch/recovery fails, the stream disconnects, the game/session changes, or the activity stops. Restore it on resume only if the same stream is still active. Do not publish session IDs, server addresses, account identifiers, access tokens, queue metadata, or join secrets.
7. Deduplicate identical updates, serialize update/clear calls, and make clear win over stale in-flight updates so reconnects cannot restore an ended game.

## Validation after prerequisites

Automated tests should cover state-to-presence mapping, title/session changes, API-level gating, user opt-out, deduplication, and clear winning over an in-flight update. Then validate on a physical Android 7.0+ device:

1. Install and sign in to Discord, enable activity sharing, and opt in inside OpenNOW.
2. Start a game and wait for `streaming`; confirm Discord shows the exact game title and an elapsed timer.
3. Change/recover the session and confirm the title/timer update without duplicate activities.
4. Background/stop OpenNOW, disconnect, end the stream, and disable the setting; confirm presence clears in every case.
5. Repeat with Discord absent or signed out and on API 23; OpenNOW must continue normally with no presence and no crash.

## Closest available alternative

Until the owner prerequisites are complete, Android users cannot automatically publish the streamed game from OpenNOW. They can set a manual Discord custom status, or use the desktop OpenNOW client, whose local desktop RPC integration already publishes the active game when Discord is running.
