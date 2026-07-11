using Playnite.SDK;
using Playnite.SDK.Models;
using System;
using System.Collections.Generic;

namespace OpenNow.Playnite.Common
{
    internal static class PlayniteGameUtilities
    {
        public static bool AddFeatureToGame(IPlayniteAPI api, Game game, GameFeature feature)
        {
            if (game.FeatureIds == null)
            {
                game.FeatureIds = new List<Guid> { feature.Id };
                api.Database.Games.Update(game);
                return true;
            }

            if (game.FeatureIds.Contains(feature.Id))
            {
                return false;
            }

            game.FeatureIds.Add(feature.Id);
            api.Database.Games.Update(game);
            return true;
        }

        public static bool RemoveFeatureFromGame(IPlayniteAPI api, Game game, GameFeature feature)
        {
            if (game.FeatureIds == null || !game.FeatureIds.Contains(feature.Id))
            {
                return false;
            }

            game.FeatureIds.Remove(feature.Id);
            api.Database.Games.Update(game);
            return true;
        }
    }
}
