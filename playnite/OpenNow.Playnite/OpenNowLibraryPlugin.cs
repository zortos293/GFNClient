using OpenNow.Playnite.Common;
using OpenNow.Playnite.Models;
using OpenNow.Playnite.Services;
using OpenNow.Playnite.ViewModels;
using OpenNow.Playnite.Views;
using Playnite.SDK;
using Playnite.SDK.Data;
using Playnite.SDK.Events;
using Playnite.SDK.Models;
using Playnite.SDK.Plugins;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Diagnostics;
using System.Reflection;
using System.Windows;
using System.Windows.Controls;

namespace OpenNow.Playnite
{
    [LoadPlugin]
    public class OpenNowLibraryPlugin : LibraryPlugin
    {
        private static readonly ILogger Logger = LogManager.GetLogger();
        public static readonly Guid PluginGuid = Guid.Parse("a7c3e2b1-4d5f-4a6b-9c8d-1e2f3a4b5c6d");

        private readonly OpenNowSettingsViewModel settings;
        private readonly GameDetectionService detectionService = new GameDetectionService();
        private readonly string databasePath;
        private bool databaseUpdatedOnGetGames;

        public override Guid Id => PluginGuid;
        public override string Name => "OpenNOW";
        public override string LibraryIcon { get; }
        public override LibraryClient Client { get; }

        public OpenNowLibraryPlugin(IPlayniteAPI api) : base(api)
        {
            settings = new OpenNowSettingsViewModel(this);
            Client = new OpenNowLibraryClient(this);
            databasePath = Path.Combine(GetPluginUserDataPath(), "gfnDatabase.json");
            LibraryIcon = Path.Combine(Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? string.Empty, "icon.png");

            detectionService.SetDatabase(LoadDatabaseFromFile(databasePath));

            Properties = new LibraryPluginProperties
            {
                HasSettings = true,
            };
        }

        public string GetResolvedOpenNowPath()
        {
            return OpenNowPath.ResolveExecutablePath(settings.Settings.OpenNowExecutablePath);
        }

        public override ISettings GetSettings(bool firstRunSettings)
        {
            return settings;
        }

        public override UserControl GetSettingsView(bool firstRunSettings)
        {
            return new OpenNowSettingsView();
        }

        public override void OnApplicationStarted(OnApplicationStartedEventArgs args)
        {
            if (settings.Settings.ExecuteOnStartup)
            {
                UpdateDatabaseAndGamesStatus(showDialogs: false);
            }
        }

        public override void OnLibraryUpdated(OnLibraryUpdatedEventArgs args)
        {
            if (settings.Settings.ExecuteOnLibraryUpdate)
            {
                var updateDatabase = !databaseUpdatedOnGetGames;
                UpdateDatabaseAndGamesStatus(showDialogs: false, updateDatabase: updateDatabase);
            }

            databaseUpdatedOnGetGames = false;
        }

        public override IEnumerable<GameMetadata> GetGames(LibraryGetGamesArgs args)
        {
            databaseUpdatedOnGetGames = false;
            var importedGames = new List<GameMetadata>();
            if (!settings.Settings.ImportDatabaseAsLibrary)
            {
                return importedGames;
            }

            databaseUpdatedOnGetGames = DownloadAndRefreshGameList(showDialogs: false);
            if (!detectionService.HasEntries)
            {
                return importedGames;
            }

            var importedIds = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var game in PlayniteApi.Database.Games)
            {
                var variant = detectionService.GetMatchingVariant(game);
                if (variant == null)
                {
                    continue;
                }

                var gameId = variant.LaunchCmsId.ToString();
                if (!importedIds.Add(gameId))
                {
                    continue;
                }

                importedGames.Add(new GameMetadata
                {
                    Name = GameNameMatcher.RemoveTrademarks(variant.Title),
                    GameId = variant.LaunchCmsId.ToString(),
                    Platforms = new HashSet<MetadataProperty> { new MetadataSpecProperty("pc_windows") },
                    Source = new MetadataNameProperty("OpenNOW"),
                    IsInstalled = true,
                });
            }

            return importedGames;
        }

        public override IEnumerable<MainMenuItem> GetMainMenuItems(GetMainMenuItemsArgs args)
        {
            return new List<MainMenuItem>
            {
                new MainMenuItem
                {
                    Description = ResourceProvider.GetString("LOCOpenNow_MenuItemUpdateStatusDescription"),
                    MenuSection = "@OpenNOW",
                    Action = _ => UpdateDatabaseAndGamesStatus(showDialogs: true),
                },
                new MainMenuItem
                {
                    Description = ResourceProvider.GetString("LOCOpenNow_MenuItemOpenDatabaseBrowserDescription"),
                    MenuSection = "@OpenNOW",
                    Action = _ => OpenDatabaseBrowser(),
                },
            };
        }

        public override IEnumerable<PlayController> GetPlayActions(GetPlayActionsArgs args)
        {
            var game = args.Game;
            var executablePath = GetResolvedOpenNowPath();
            if (string.IsNullOrWhiteSpace(executablePath) || !File.Exists(executablePath))
            {
                NotifyOpenNowMissing(executablePath);
                yield break;
            }

            if (game.PluginId == Id)
            {
                yield return CreateController(game, game.GameId, game.Name, executablePath);
                yield break;
            }

            if (!settings.Settings.ShowPlayActionsOnLaunch)
            {
                yield break;
            }

            if (game.PluginId == Guid.Empty)
            {
                yield break;
            }

            if (settings.Settings.OnlyShowActionsForNotLibInstalledGames
                && !string.IsNullOrWhiteSpace(game.InstallDirectory))
            {
                yield break;
            }

            if (!detectionService.HasEntries)
            {
                yield break;
            }

            var variant = detectionService.GetMatchingVariant(game);
            if (variant == null)
            {
                yield break;
            }

            yield return CreateController(game, variant.LaunchCmsId.ToString(), variant.Title, executablePath);
        }

        private OpenNowPlayController CreateController(Game game, string cmsId, string title, string executablePath)
        {
            int? appId = int.TryParse(cmsId, out var parsed) ? parsed : (int?)null;
            return new OpenNowPlayController(game, title, appId, executablePath);
        }

        private void NotifyOpenNowMissing(string executablePath)
        {
            PlayniteApi.Notifications.Add(new NotificationMessage(
                "opennow-exe-not-found",
                string.Format(ResourceProvider.GetString("LOCOpenNow_NotificationMessage"), executablePath ?? "(auto-detect failed)"),
                NotificationType.Error,
                () => Process.Start(new ProcessStartInfo("https://github.com/OpenCloudGaming/OpenNOW/releases") { UseShellExecute = true }));
        }

        public bool DownloadAndRefreshGameList(bool showDialogs)
        {
            var databaseUpdated = false;
            PlayniteApi.Dialogs.ActivateGlobalProgress(_ =>
            {
                try
                {
                    var downloadedDatabase = GeforceNowService.GetGeforceNowDatabase();
                    if (downloadedDatabase.Count > 0)
                    {
                        File.WriteAllText(databasePath, Serialization.ToJson(downloadedDatabase));
                        detectionService.SetDatabase(downloadedDatabase);
                        databaseUpdated = true;
                    }
                }
                catch (Exception ex)
                {
                    Logger.Error(ex, "Failed to download GeForce NOW database.");
                    if (showDialogs)
                    {
                        PlayniteApi.Dialogs.ShowErrorMessage(ex.Message, Name);
                    }
                }
            }, new GlobalProgressOptions(ResourceProvider.GetString("LOCOpenNow_DownloadingDatabaseProgressMessage")));

            return databaseUpdated;
        }

        public void UpdateDatabaseAndGamesStatus(bool showDialogs, bool updateDatabase = true)
        {
            var featureName = OpenNowLauncher.FeatureName;
            var feature = PlayniteApi.Database.Features.Add(featureName);

            if (updateDatabase)
            {
                DownloadAndRefreshGameList(showDialogs);
            }

            if (!detectionService.HasEntries)
            {
                Logger.Debug("GeForce NOW detection dictionary is empty; skipping sync.");
                return;
            }

            var enabledGamesCount = 0;
            var featureAddedCount = 0;
            var featureRemovedCount = 0;
            var setAsInstalledCount = 0;

            PlayniteApi.Dialogs.ActivateGlobalProgress(_ =>
            {
                using (PlayniteApi.Database.BufferedUpdate())
                {
                    foreach (var game in PlayniteApi.Database.Games)
                    {
                        if (game.PluginId == Id)
                        {
                            continue;
                        }

                        var variant = detectionService.GetMatchingVariant(game);
                        if (variant == null)
                        {
                            if (PlayniteGameUtilities.RemoveFeatureFromGame(PlayniteApi, game, feature))
                            {
                                featureRemovedCount++;
                            }

                            continue;
                        }

                        enabledGamesCount++;
                        if (PlayniteGameUtilities.AddFeatureToGame(PlayniteApi, game, feature))
                        {
                            featureAddedCount++;
                        }

                        if (settings.Settings.MarkCompatibleGamesAsInstalled && !game.IsInstalled)
                        {
                            game.IsInstalled = true;
                            PlayniteApi.Database.Games.Update(game);
                            setAsInstalledCount++;
                        }
                    }
                }
            }, new GlobalProgressOptions(ResourceProvider.GetString("LOCOpenNow_UpdatingProgressMessage")));

            Logger.Info($"OpenNOW sync complete. Compatible: {enabledGamesCount}, feature added: {featureAddedCount}, removed: {featureRemovedCount}, installed: {setAsInstalledCount}.");

            if (showDialogs)
            {
                var results = string.Format(
                    ResourceProvider.GetString("LOCOpenNow_UpdateResults1Message"),
                    enabledGamesCount,
                    featureName,
                    featureAddedCount,
                    featureName,
                    featureRemovedCount);

                if (settings.Settings.MarkCompatibleGamesAsInstalled)
                {
                    results += string.Format(ResourceProvider.GetString("LOCOpenNow_UpdateResults3Message"), setAsInstalledCount);
                }

                PlayniteApi.Dialogs.ShowMessage(results, Name);
            }
            else if (setAsInstalledCount > 0)
            {
                PlayniteApi.Notifications.Add(new NotificationMessage(
                    Guid.NewGuid().ToString(),
                    string.Format(ResourceProvider.GetString("LOCOpenNow_NotificationMessageMarkedInstalledResults"), setAsInstalledCount),
                    NotificationType.Info));
            }
        }

        private void OpenDatabaseBrowser()
        {
            DownloadAndRefreshGameList(showDialogs: false);
            var window = PlayniteApi.Dialogs.CreateWindow(new WindowCreationOptions
            {
                ShowMinimizeButton = false,
                ShowMaximizeButton = true,
            });

            window.Height = 700;
            window.Width = 900;
            window.Title = ResourceProvider.GetString("LOCOpenNow_DatabaseBrowserWindowTitle");
            window.Content = new DatabaseBrowserView();
            window.DataContext = new DatabaseBrowserViewModel(detectionService.AllVariants.OrderBy(v => v.Title).ToList());
            window.Owner = PlayniteApi.Dialogs.GetCurrentAppWindow();
            window.WindowStartupLocation = WindowStartupLocation.CenterScreen;
            window.ShowDialog();
        }

        private static List<GeforceNowItem> LoadDatabaseFromFile(string filePath)
        {
            if (!File.Exists(filePath))
            {
                return new List<GeforceNowItem>();
            }

            try
            {
                return Serialization.FromJsonFile<List<GeforceNowItem>>(filePath) ?? new List<GeforceNowItem>();
            }
            catch (Exception ex)
            {
                Logger.Error(ex, $"Failed to load cached GeForce NOW database from {filePath}.");
                File.Delete(filePath);
                return new List<GeforceNowItem>();
            }
        }
    }
}
