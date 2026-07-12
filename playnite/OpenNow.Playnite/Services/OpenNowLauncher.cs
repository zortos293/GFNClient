using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;

namespace OpenNow.Playnite.Services
{
    internal static class OpenNowLauncher
    {
        public const string PlayActionName = "Play via OpenNOW";
        public const string FeatureName = "OpenNOW";

        public static string BuildLaunchArguments(string title, int? appId)
        {
            var builder = new StringBuilder();
            if (appId.HasValue && appId.Value > 0)
            {
                builder.Append("--launch-app-id=");
                builder.Append(appId.Value.ToString(CultureInfo.InvariantCulture));
                builder.Append(' ');
            }

            builder.Append("--launch-title=");
            builder.Append(EscapeArgument(title));
            return builder.ToString().Trim();
        }

        public static bool LooksLikeOpenNowAction(string arguments)
        {
            return !string.IsNullOrWhiteSpace(arguments)
                && arguments.IndexOf("--launch-title", StringComparison.OrdinalIgnoreCase) >= 0;
        }

        public static bool TryLaunch(string executablePath, string title, int? appId, out string errorMessage)
        {
            errorMessage = null;
            if (string.IsNullOrWhiteSpace(executablePath) || !System.IO.File.Exists(executablePath))
            {
                errorMessage = "OpenNOW executable was not found. Set the path in extension settings.";
                return false;
            }

            if (string.IsNullOrWhiteSpace(title))
            {
                errorMessage = "Launch title is empty.";
                return false;
            }

            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = executablePath,
                    Arguments = BuildLaunchArguments(title.Trim(), appId),
                    UseShellExecute = true,
                    WorkingDirectory = System.IO.Path.GetDirectoryName(executablePath),
                });
                return true;
            }
            catch (Exception ex)
            {
                errorMessage = ex.Message;
                return false;
            }
        }

        public static string NormalizeMatchKey(string value)
        {
            if (string.IsNullOrWhiteSpace(value))
            {
                return string.Empty;
            }

            var lettersAndDigits = Regex.Replace(value, @"[^\p{L}\p{Nd}]", string.Empty);
            return lettersAndDigits.ToLowerInvariant();
        }

        private static string EscapeArgument(string value)
        {
            if (value.IndexOf('"') >= 0)
            {
                value = value.Replace("\"", "\\\"");
            }

            if (value.IndexOf(' ') >= 0 || value.IndexOf('"') >= 0)
            {
                return $"\"{value}\"";
            }

            return value;
        }
    }
}
