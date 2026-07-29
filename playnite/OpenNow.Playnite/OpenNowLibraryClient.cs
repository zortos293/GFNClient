using OpenNow.Playnite.Services;
using Playnite.SDK;
using Playnite.SDK.Models;
using Playnite.SDK.Plugins;
using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;

namespace OpenNow.Playnite
{
    public sealed class OpenNowLibraryClient : LibraryClient
    {
        private readonly OpenNowLibraryPlugin plugin;

        public OpenNowLibraryClient(OpenNowLibraryPlugin plugin)
        {
            this.plugin = plugin;
        }

        public override bool IsInstalled
        {
            get
            {
                var path = plugin.GetResolvedOpenNowPath();
                return !string.IsNullOrWhiteSpace(path) && File.Exists(path);
            }
        }

        public override string Icon => Path.Combine(Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? string.Empty, "icon.png");

        public override void Open()
        {
            var path = plugin.GetResolvedOpenNowPath();
            if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
            {
                return;
            }

            Process.Start(new ProcessStartInfo
            {
                FileName = path,
                UseShellExecute = true,
                WorkingDirectory = Path.GetDirectoryName(path),
            });
        }
    }
}
