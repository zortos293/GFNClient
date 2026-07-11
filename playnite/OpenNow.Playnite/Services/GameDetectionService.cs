using OpenNow.Playnite.Common;
using OpenNow.Playnite.Models;
using Playnite.SDK;
using Playnite.SDK.Models;
using System;
using System.Collections.Generic;
using System.Linq;

namespace OpenNow.Playnite.Services
{
    internal sealed class GameDetectionService
    {
        private readonly Dictionary<Tuple<AppStore, string>, GeforceNowItemVariant> detectionDictionary
            = new Dictionary<Tuple<AppStore, string>, GeforceNowItemVariant>();

        private readonly Dictionary<Guid, AppStore> pluginIdToAppStoreMapper;
        private readonly HashSet<AppStore> appStoresToMatchByName;

        public GameDetectionService()
        {
            appStoresToMatchByName = new HashSet<AppStore>
            {
                AppStore.Epic,
                AppStore.EA_APP,
                AppStore.Xbox,
            };

            pluginIdToAppStoreMapper = new Dictionary<Guid, AppStore>
            {
                [Guid.Parse("e3c26a3d-d695-4cb7-a769-5ff7612c7edd")] = AppStore.Battlenet,
                [Guid.Parse("0e2e793e-e0dd-4447-835c-c44a1fd506ec")] = AppStore.Bethesda,
                [Guid.Parse("00000002-dbd1-46c6-b5d0-b1ba559d10e4")] = AppStore.Epic,
                [Guid.Parse("aebe8b7c-6dc3-4a66-af31-e7375c6b5e9e")] = AppStore.Gog,
                [Guid.Parse("85dd7072-2f20-4e76-a007-41035e390724")] = AppStore.EA_APP,
                [Guid.Parse("88409022-088a-4de8-805a-fdbac291f00a")] = AppStore.Rockstar,
                [Guid.Parse("cb91dfc9-b977-43bf-8e70-55f46e410fab")] = AppStore.Steam,
                [Guid.Parse("c2f038e5-8b92-4877-91f1-da9094155fc5")] = AppStore.Uplay,
                [Guid.Parse("7e4fbb5e-2ae3-48d4-8ba0-6b30e7a4e287")] = AppStore.Xbox,
                [BuiltinExtensions.GetIdFromExtension(BuiltinExtension.SteamLibrary)] = AppStore.Steam,
                [BuiltinExtensions.GetIdFromExtension(BuiltinExtension.EpicLibrary)] = AppStore.Epic,
                [BuiltinExtensions.GetIdFromExtension(BuiltinExtension.GogLibrary)] = AppStore.Gog,
                [BuiltinExtensions.GetIdFromExtension(BuiltinExtension.OriginLibrary)] = AppStore.Origin,
                [BuiltinExtensions.GetIdFromExtension(BuiltinExtension.UplayLibrary)] = AppStore.Uplay,
                [BuiltinExtensions.GetIdFromExtension(BuiltinExtension.BattleNetLibrary)] = AppStore.Battlenet,
                [BuiltinExtensions.GetIdFromExtension(BuiltinExtension.BethesdaLibrary)] = AppStore.Bethesda,
                [BuiltinExtensions.GetIdFromExtension(BuiltinExtension.XboxLibrary)] = AppStore.Xbox,
            };
        }

        public bool HasEntries => detectionDictionary.Count > 0;

        public IReadOnlyCollection<GeforceNowItemVariant> AllVariants => detectionDictionary.Values;

        public void SetDatabase(IEnumerable<GeforceNowItem> items)
        {
            detectionDictionary.Clear();
            foreach (var item in items ?? Enumerable.Empty<GeforceNowItem>())
            {
                if (item.Type != AppType.Game || item.Variants == null)
                {
                    continue;
                }

                foreach (var variant in item.Variants)
                {
                    if (variant.OsType != OsType.Windows)
                    {
                        continue;
                    }

                    var key = appStoresToMatchByName.Contains(variant.AppStore)
                        ? Tuple.Create(variant.AppStore, GameNameMatcher.SanitizeForMatching(variant.Title))
                        : Tuple.Create(variant.AppStore, variant.StoreId ?? string.Empty);

                    if (string.IsNullOrWhiteSpace(key.Item2))
                    {
                        continue;
                    }

                    detectionDictionary[key] = variant;
                }
            }
        }

        public GeforceNowItemVariant GetMatchingVariant(Game game)
        {
            if (game == null || !pluginIdToAppStoreMapper.TryGetValue(game.PluginId, out var appStore))
            {
                return null;
            }

            if (appStoresToMatchByName.Contains(appStore))
            {
                var key = Tuple.Create(appStore, GameNameMatcher.SanitizeForMatching(game.Name));
                return detectionDictionary.TryGetValue(key, out var byName) ? byName : null;
            }

            var storeKey = Tuple.Create(appStore, game.GameId ?? string.Empty);
            return detectionDictionary.TryGetValue(storeKey, out var byStoreId) ? byStoreId : null;
        }
    }
}
