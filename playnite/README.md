# OpenNOW Playnite Library

Playnite **library plugin** that detects GeForce NOW-compatible games in your existing Steam/Epic/GOG/Origin/Ubisoft/Battle.net/Bethesda/Xbox libraries and lets you launch them through [OpenNOW](https://github.com/OpenCloudGaming/OpenNOW).

This extension is modeled after [darklinkpower's NVIDIA GeForce NOW Library](https://github.com/darklinkpower/PlayniteExtensionsCollection) (formerly *GeForce NOW Enabler*), but launches via OpenNOW CLI direct launch instead of the official NVIDIA client.

## Features

- Downloads NVIDIA's live GeForce NOW catalog (GraphQL API) and caches it locally.
- Matches games from other Playnite libraries using store IDs or normalized titles (same strategy as darklinkpower's plugin).
- Adds an **OpenNOW** feature tag to compatible games for filtering.
- Injects a **Launch via OpenNOW** play action when starting games from other libraries.
- Optional import of owned cloud-compatible titles as separate **OpenNOW** library entries.
- Optional marking of compatible games as installed.
- Database browser to inspect the cached GeForce NOW catalog.
- Launches OpenNOW with:

  ```text
  OpenNOW.exe --launch-app-id=<cmsId> --launch-title="<title>"
  ```

## Requirements

- Windows
- [Playnite](https://playnite.link/) 10+ (Playnite SDK 6.x)
- [.NET Framework 4.6.2 targeting pack](https://dotnet.microsoft.com/download/dotnet-framework/net462)
- [.NET SDK](https://dotnet.microsoft.com/download) (for building)
- [OpenNOW](https://github.com/OpenCloudGaming/OpenNOW/releases) installed and signed in at least once

## Build

```powershell
# Copy icon once
Copy-Item ..\..\logo.png .\OpenNow.Playnite\icon.png

# Build
cd playnite\OpenNow.Playnite
dotnet build -c Release
```

Output: `playnite/OpenNow.Playnite/bin/Release/`

## Install for development

1. Playnite → **Settings → For developers → External extensions**
2. Add the Release output folder above
3. Restart Playnite
4. Enable the **OpenNOW** library under **Add-ons → Libraries**

## Install packaged extension

Package with [Playnite Toolbox](https://github.com/JosefNemec/PlayniteToolbox):

```powershell
Playnite.Toolbox.exe pack extension "C:\path\to\OpenNOW\playnite\OpenNow.Playnite" "C:\path\to\output"
```

Install the generated `.pext` by opening it or dragging it onto Playnite.

## First-time setup

1. **Add-ons → Extension settings → OpenNOW**
   - Set the OpenNOW executable path if auto-detect fails.
   - Configure startup/library sync behavior.
   - Optionally enable **Import owned cloud-compatible games**.
2. **Main menu → Extensions → OpenNOW → Update OpenNOW-compatible games**
3. Start a game from Steam/Epic/etc. and choose **Launch via OpenNOW**.

## Settings overview

| Setting | Description |
| --- | --- |
| OpenNOW executable path | Override auto-detect (`%LocalAppData%\Programs\OpenNOW\OpenNOW.exe`, etc.) |
| Import as library entries | Creates separate OpenNOW-library games for owned compatible titles |
| Update on startup / library update | Keeps features and cache in sync automatically |
| Show launch action | Adds OpenNOW as a launch option for matched games in other libraries |
| Only for not locally installed | Hides OpenNOW action when the source library reports an install directory |
| Mark compatible as installed | Sets `IsInstalled = true` on matched games during sync |

## Matching notes

- **Steam / GOG / Ubisoft / Battle.net / Bethesda / Rockstar**: matched by store/game ID.
- **Epic / EA app / Xbox**: matched by normalized game title (store IDs often differ from Playnite).
- Name normalization removes punctuation/edition noise similar to the reference plugin.

If a game is on GeForce NOW but not detected, use **Open GeForce NOW database browser** to verify the catalog title/store ID, then adjust the Playnite game name if needed.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| OpenNOW not found | Set executable path in extension settings or install OpenNOW |
| No launch action shown | Run manual sync; confirm the game has the **OpenNOW** feature |
| Wrong game launches | Check title/store match in database browser; rename game or report a matching issue |
| Play time is approximate | OpenNOW single-instance behavior limits precise process tracking |

## Project layout

```text
playnite/OpenNow.Playnite/
  OpenNowLibraryPlugin.cs      # Library plugin + sync/import/play actions
  OpenNowLibraryClient.cs      # Library client (open OpenNOW)
  OpenNowPlayController.cs       # Launch + play-time tracking
  Services/GeforceNowService.cs  # NVIDIA GraphQL catalog fetch
  Services/GameDetectionService.cs
  Views/DatabaseBrowserView.*    # Catalog inspector
  Localization/en_US.xaml
```

## Credits

- Matching/sync design inspired by **[darklinkpower](https://github.com/darklinkpower)**'s [NVIDIA GeForce NOW Library](https://github.com/darklinkpower/PlayniteExtensionsCollection) for Playnite.
- Launch path uses OpenNOW direct launch (`--launch-title`, `--launch-app-id`).

## License

Same license as the OpenNOW repository.
