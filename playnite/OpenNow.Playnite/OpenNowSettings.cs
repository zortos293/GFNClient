using Playnite.SDK;
using Playnite.SDK.Data;
using System.Collections.Generic;

namespace OpenNow.Playnite
{
    public class OpenNowSettings
    {
        public string OpenNowExecutablePath { get; set; } = string.Empty;
        public bool ExecuteOnStartup { get; set; }
        public bool ExecuteOnLibraryUpdate { get; set; } = true;
        public bool ShowPlayActionsOnLaunch { get; set; } = true;
        public bool OnlyShowActionsForNotLibInstalledGames { get; set; } = true;
        public bool ImportDatabaseAsLibrary { get; set; }
        public bool MarkCompatibleGamesAsInstalled { get; set; } = true;
    }

    public class OpenNowSettingsViewModel : ObservableObject, ISettings
    {
        private readonly OpenNowLibraryPlugin plugin;
        private OpenNowSettings editingClone;

        private OpenNowSettings settings;
        public OpenNowSettings Settings
        {
            get => settings;
            set
            {
                settings = value;
                OnPropertyChanged();
            }
        }

        public OpenNowSettingsViewModel(OpenNowLibraryPlugin plugin)
        {
            this.plugin = plugin;
            var savedSettings = plugin.LoadPluginSettings<OpenNowSettings>();
            Settings = savedSettings ?? new OpenNowSettings();
        }

        public void BeginEdit()
        {
            editingClone = Serialization.GetClone(Settings);
        }

        public void CancelEdit()
        {
            Settings = editingClone;
        }

        public void EndEdit()
        {
            plugin.SavePluginSettings(Settings);
        }

        public bool VerifySettings(out List<string> errors)
        {
            errors = new List<string>();
            return true;
        }
    }
}
