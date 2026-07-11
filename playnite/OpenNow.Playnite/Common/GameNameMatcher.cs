using System.Text;
using System.Text.RegularExpressions;

namespace OpenNow.Playnite.Common
{
    internal static class GameNameMatcher
    {
        public static string RemoveTrademarks(string value)
        {
            if (string.IsNullOrWhiteSpace(value))
            {
                return value;
            }

            return Regex.Replace(value, @"[™®©]", string.Empty).Trim();
        }

        public static string Satinize(string value)
        {
            if (string.IsNullOrWhiteSpace(value))
            {
                return string.Empty;
            }

            var builder = new StringBuilder(value.Length);
            foreach (var character in value)
            {
                if (char.IsLetterOrDigit(character))
                {
                    builder.Append(char.ToLowerInvariant(character));
                }
            }

            return builder.ToString();
        }

        public static string SanitizeForMatching(string gameName)
        {
            var sanitized = Satinize(RemoveTrademarks(gameName));
            return sanitized
                .Replace("gameoftheyearedition", string.Empty)
                .Replace("premiumedition", string.Empty)
                .Replace("gameoftheyear", string.Empty)
                .Replace("definitiveedition", string.Empty)
                .Replace("battlefieldvdefinitive", "battlefieldv")
                .Replace("battlefield1revolution", "battlefield1");
        }
    }
}
