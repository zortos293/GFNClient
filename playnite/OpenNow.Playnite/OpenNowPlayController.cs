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
        private readonly int? launchAppId;
        private readonly string openNowExecutablePath;
        private Stopwatch stopWatch;

        public OpenNowPlayController(Game game, string launchTitle, int? launchAppId, string openNowExecutablePath)
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
            return Process.GetProcessesByName("OpenNOW").Length > 0;
        }

        private async void StartWatching()
        {
            while (true)
            {
                if (IsOpenNowRunning())
                {
                    InvokeOnStarted(new GameStartedEventArgs());
                    break;
                }

                await Task.Delay(2000);
            }

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
