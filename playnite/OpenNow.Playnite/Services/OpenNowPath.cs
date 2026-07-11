using System;
using System.Collections.Generic;
using System.IO;

namespace OpenNow.Playnite.Services
{
    internal static class OpenNowPath
    {
        public static IEnumerable<string> GetDefaultCandidatePaths()
        {
            var localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            yield return Path.Combine(localAppData, "Programs", "OpenNOW", "OpenNOW.exe");
            yield return Path.Combine(localAppData, "OpenNOW", "OpenNOW.exe");

            var programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
            yield return Path.Combine(programFiles, "OpenNOW", "OpenNOW.exe");

            var programFilesX86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
            yield return Path.Combine(programFilesX86, "OpenNOW", "OpenNOW.exe");
        }

        public static string ResolveExecutablePath(string configuredPath)
        {
            if (!string.IsNullOrWhiteSpace(configuredPath) && File.Exists(configuredPath))
            {
                return configuredPath;
            }

            foreach (var candidate in GetDefaultCandidatePaths())
            {
                if (File.Exists(candidate))
                {
                    return candidate;
                }
            }

            return null;
        }
    }
}
