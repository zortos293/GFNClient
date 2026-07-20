# OpenNOW Native Android

This folder is a standalone Android Studio project for the native Kotlin / Jetpack Compose OpenNOW target.

Open it from Android Studio with **File > Open > `OpenNOW/android`**. Android Studio will install/use the required Gradle, Android SDK, CMake, and NDK components from the project configuration.

## Build Targets

- `:app:assembleDebug` builds a debug APK.
- `:app:assembleRelease` builds the direct-distribution release APK with APK update support.
- `:app:bundleRelease` builds the Google Play Android App Bundle. This task removes `REQUEST_INSTALL_PACKAGES` and disables APK self-updates so Play installs use Google Play's update mechanism.

Release and debug builds include `arm64-v8a`, `armeabi-v7a`, and `x86_64`. The `armeabi-v7a` slice supports 32-bit ARM phones and 32-bit Android TV firmware. OpenNOW recommends 720p/30 FPS/12 Mbps for 32-bit processes and memory-constrained TVs, and warns when a custom profile exceeds that recommendation without overriding the user's selection.

## APK Update Manifest

APK and debug builds check `https://api.printedwaste.com/releases/opennow/latest` and can download the returned APK. App Bundle builds installed from Google Play detect `com.android.vending` as the install source and do not check, download, or install APK updates. The manifest should look like this:

```json
{
  "versionCode": 7,
  "versionName": "0.5.2",
  "apkUrl": "https://api.printedwaste.com/release-files/opennow/app-release.apk",
  "artifactUrl": "https://api.printedwaste.com/release-files/opennow/app-release.apk",
  "sha256": "optional lowercase apk checksum",
  "releaseNotes": "Short notes shown in Settings\nSecond line"
}
```

`apkUrl`, `artifactUrl`, or `url` may point at the APK. `releaseNotes` may use real newlines or literal `\n` separators. The app compares `versionCode` against its installed build and asks Android's package installer to confirm the downloaded APK.

## Runtime Notes

- UI is native Compose.
- GFN auth, catalog, subscription, CloudMatch session creation/polling/claim/stop, signaling, and input packet behavior are implemented in Kotlin from the Electron project contracts.
- Streaming uses Android WebRTC plus hardware MediaCodec probing. The bundled `opennow_native` JNI library exposes native runtime diagnostics and keeps the NDK/CMake path wired for media-sensitive code.
- Queue ad metadata is preserved in `SessionInfo`; ad playback can use the included Media3 dependency when the server returns an ad media URL.
