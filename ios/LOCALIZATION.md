# iOS localization

The iOS client uses a **String Catalog** at `OpenNOWiOS/OpenNOWiOS/Localizable.xcstrings`.
`SWIFT_EMIT_LOC_STRINGS` is already on for the app target, so every `Text("…")`,
`Label("…", systemImage:)` and `String(localized:)` in the codebase is extracted automatically at
build time — there is no manual key table to maintain.

## Current state

The catalog holds the **English source strings only**. No target languages are registered yet, and
that is deliberate: adding a language to the project makes iOS advertise support for it, and an app
that claims German while rendering English is worse than one that only claims English. Languages go
in once their translations do.

Android ships 13 locales under `android/app/src/main/res/values-*`, and the Electron app has its own
set under `locales/`. Neither is key-compatible with the iOS catalog — iOS keys *are* the English
source strings — so those files are a useful reference for tone and terminology, not an import
source.

## Refreshing the catalog after adding or changing strings

Xcode does this on every build. From the command line it is two steps, because `xcodebuild` writes
`.stringsdata` but does not merge it back:

```sh
cd ios/OpenNOWiOS

xcodebuild -project OpenNOWiOS.xcodeproj -scheme OpenNOWiOS \
  -destination 'platform=iOS Simulator,name=iPad (A16)' \
  -derivedDataPath /tmp/opennow-dd build

OBJ=/tmp/opennow-dd/Build/Intermediates.noindex/OpenNOWiOS.build/Debug-iphonesimulator/OpenNOWiOS.build/Objects-normal/arm64
ARGS=()
while IFS= read -r f; do ARGS+=(--stringsdata "$f"); done < <(find "$OBJ" -name '*.stringsdata')
xcrun xcstringstool sync OpenNOWiOS/Localizable.xcstrings "${ARGS[@]}"
```

`xcstringstool print OpenNOWiOS/Localizable.xcstrings` lists what is in there.

## Adding a language

1. Open the project in Xcode → project settings → Info → Localizations → **+**.
2. Translate in the String Catalog editor, or **Product → Export Localizations…** to produce an
   `.xcloc` for a translator or for Crowdin.
3. **Product → Import Localizations…** to bring the results back.

Do not add a language until its strings are translated.

## Writing localizable strings

- Prefer `Text("Something")`. The literal is the key.
- A string composed at runtime is **not** a translatable string. Use `Text(verbatim:)` so it does
  not land in the catalog as an empty or meaningless key.
- Where a sentence interpolates a value, keep the whole sentence in one string
  (`Text("\(count) games")`) rather than concatenating fragments — word order differs by language.
- Give a string a comment whenever the English is ambiguous out of context:
  `Text("Open", comment: "Button that opens the stream control panel")`.
- Numbers that a person reads as a quantity should use `.monospacedDigit()` and a format style, not
  string interpolation of a rounded value.
