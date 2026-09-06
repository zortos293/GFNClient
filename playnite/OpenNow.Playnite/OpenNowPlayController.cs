using OpenNow.Playnite.Services;
using Playnite.SDK;
using Playnite.SDK.Models;
using Playnite.SDK.Plugins;
using System;
using System.Diagnostics;
using System.Threading.Tasks;

namespace OpenNow.Playnite
{
    public sealed class OpenNowPlayController : PlayController
    {
        private static readonly ILogger Logger = LogManager.GetLogger();
        private readonly string launchTitle;
        private readonly long? launchAppId;
        private readonly string openNowExecutablePath;
        private Stopwatch stopWatch;

        public OpenNowPlayController(Game game, string launchTitle, long? launchAppId, string openNowExecutablePath)
            : base(game)
        {
            Name = ResourceProvider.GetString("LOCOpenNow_ControllerLaunchInOpenNow");
            this.launchTitle = launchTitle;
            this.launchAppId = launchAppId;
            this.openNowExecutablePath = openNowExecutablePath;
        }

        public override void Play(PlayActionArgs args)
        {
            stopWatch = Stopwatch.StartNew();
            if (!OpenNowLauncher.TryLaunch(openNowExecutablePath, launchTitle, launchAppId, out var errorMessage))
            {
                Logger.Error(errorMessage);
                return;
            }

            StartWatching();
        }

        private static bool IsOpenNowRunning()
        {
            var processes = Process.GetProcessesByName("OpenNOW");
            try
            {
                return processes.Length > 0;
            }
            finally
            {
                foreach (var process in processes)
                {
                    process.Dispose();
                }
            }
        }

        private async void StartWatching()
        {
            while (stopWatch.Elapsed < TimeSpan.FromSeconds(30))
            {
                if (IsOpenNowRunning())
                {
                    InvokeOnStarted(new GameStartedEventArgs());
                    await WatchUntilStopped();
                    return;
                }

                await Task.Delay(2000);
            }

            Logger.Warn("OpenNOW did not appear within 30 seconds of launch.");
            InvokeOnStopped(new GameStoppedEventArgs(0));
        }

        private async Task WatchUntilStopped()
        {
            while (true)
            {
                if (!IsOpenNowRunning())
                {
                    InvokeOnStopped(new GameStoppedEventArgs(Convert.ToUInt64(stopWatch.Elapsed.TotalSeconds)));
                    break;
                }

                await Task.Delay(5000);
            }
        }
    }
}
