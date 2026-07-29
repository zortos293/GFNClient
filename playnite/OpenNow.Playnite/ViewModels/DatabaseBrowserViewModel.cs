using OpenNow.Playnite.Models;
using Playnite.SDK;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Windows;
using System.Windows.Data;

namespace OpenNow.Playnite.ViewModels
{
    public class DatabaseBrowserViewModel : ObservableObject
    {
        private string searchString = string.Empty;
        private string storeSearchString = string.Empty;
        private GeforceNowItemVariant selectedVariant;
        private readonly ICollectionView variantsCollection;

        public string SearchString
        {
            get => searchString;
            set
            {
                searchString = value?.ToLowerInvariant() ?? string.Empty;
                OnPropertyChanged();
                variantsCollection?.Refresh();
            }
        }

        public string StoreSearchString
        {
            get => storeSearchString;
            set
            {
                storeSearchString = value?.ToLowerInvariant() ?? string.Empty;
                OnPropertyChanged();
                variantsCollection?.Refresh();
            }
        }

        public List<GeforceNowItemVariant> VariantsList { get; }

        public GeforceNowItemVariant SelectedVariant
        {
            get => selectedVariant;
            set
            {
                selectedVariant = value;
                OnPropertyChanged();
                OnPropertyChanged(nameof(IsVariantSelected));
            }
        }

        public bool IsVariantSelected => SelectedVariant != null;

        public ICollectionView VariantsCollection => variantsCollection;

        public DatabaseBrowserViewModel(List<GeforceNowItemVariant> variants)
        {
            VariantsList = variants ?? new List<GeforceNowItemVariant>();
            variantsCollection = CollectionViewSource.GetDefaultView(VariantsList);
            variantsCollection.Filter = FilterVariantsCollection;
        }

        public RelayCommand CopyVariantTitleToClipboardCommand => new RelayCommand(() =>
        {
            if (SelectedVariant != null)
            {
                Clipboard.SetText(SelectedVariant.Title);
            }
        });

        private bool FilterVariantsCollection(object item)
        {
            if (item is not GeforceNowItemVariant variant)
            {
                return false;
            }

            if (!string.IsNullOrEmpty(searchString)
                && !(variant.Title?.ToLowerInvariant().Contains(searchString) ?? false))
            {
                return false;
            }

            if (!string.IsNullOrEmpty(storeSearchString)
                && !variant.AppStore.ToString().ToLowerInvariant().Contains(storeSearchString))
            {
                return false;
            }

            return true;
        }
    }
}
