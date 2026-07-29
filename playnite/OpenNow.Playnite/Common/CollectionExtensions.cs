using System.Collections.Generic;
using System.Linq;

namespace OpenNow.Playnite.Common
{
    internal static class CollectionExtensions
    {
        public static bool HasItems<T>(this ICollection<T> source)
        {
            return source != null && source.Count > 0;
        }

        public static bool HasItems<T>(this IEnumerable<T> source)
        {
            return source != null && source.Any();
        }
    }
}
