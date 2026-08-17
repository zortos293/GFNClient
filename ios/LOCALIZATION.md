# iOS localization

The iOS client uses a **String Catalog** at `OpenNOWiOS/OpenNOWiOS/Localizable.xcstrings`.
`SWIFT_EMIT_LOC_STRINGS` is already on for the app target, so every `Text("…")`,
`Label("…", systemImage:)` and `String(localized:)` in the codebase is extracted automatically at
build time — there is no manual key table to maintain.

## Current state

The catalog holds the **English source strings only**, and no target languages are registered in the
Xcode project yet. Translation happens through Crowdin (below).

Android ships 13 locales under `android/app/src/main/res/values-*`, and the Electron app has its own
set under `locales/`. Neither is key-compatible with the iOS catalog — iOS keys *are* the English
source strings — so those files are a terminology reference, not an import source. Worth consulting
them so both platforms use the same word for "queue", "rig", "codec" and so on.

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

## Where translations come from

Crowdin, like everything else in this repo. `AGENTS.md` is explicit that Crowdin owns generated
translations and that they are not hand-edited, and the iOS catalog is wired into `crowdin.yml`
alongside `locales/en.json`:

```yaml
  - source: /ios/OpenNOWiOS/OpenNOWiOS/Localizable.xcstrings
    translation: /ios/OpenNOWiOS/OpenNOWiOS/Localizable.xcstrings
    update_option: update_as_unapproved
```

A String Catalog holds its source strings *and* every translation in one file, which is why source
and translation point at the same path. Crowdin edits it in place, so a sync pull request will show
up as a diff to `Localizable.xcstrings` and nothing else.

The loop is:

1. Push a change that adds or edits English strings (after running the sync above).
2. Crowdin picks up the new source strings.
3. Translators work in Crowdin; the sync PR brings the results back.
4. Add the language to the Xcode project — project settings → Info → Localizations → **+** — once
   its strings are actually translated, and not before.

**Do not hand-write translations into the catalog.** Same rule as `locales/*.json`.

## Adding a language to the project

Only after step 3 above. Adding a language makes iOS advertise support for it, and an app that
claims German while rendering English is worse than one that only claims English.

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
