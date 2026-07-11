using Playnite.SDK.Data;
using System;
using System.Collections.Generic;

namespace OpenNow.Playnite.Models
{
    public class GfnGraphQlResponse
    {
        [SerializationPropertyName("data")]
        public GfnGraphQlData Data { get; set; }
    }

    public class GfnGraphQlData
    {
        [SerializationPropertyName("apps")]
        public Apps Apps { get; set; }
    }

    public class Apps
    {
        [SerializationPropertyName("numberReturned")]
        public long NumberReturned { get; set; }

        [SerializationPropertyName("pageInfo")]
        public PageInfo PageInfo { get; set; }

        [SerializationPropertyName("items")]
        public List<GeforceNowItem> Items { get; set; }
    }

    public class GeforceNowItem
    {
        [SerializationPropertyName("id")]
        public Guid Id { get; set; }

        [SerializationPropertyName("cmsId")]
        public long CmsId { get; set; }

        [SerializationPropertyName("title")]
        public string Title { get; set; }

        [SerializationPropertyName("type")]
        public AppType Type { get; set; }

        [SerializationPropertyName("variants")]
        public List<GeforceNowItemVariant> Variants { get; set; }

        public override string ToString()
        {
            return $"{Title} ({Id})";
        }
    }

    public class GeforceNowItemVariant
    {
        [SerializationPropertyName("id")]
        public long Id { get; set; }

        [SerializationPropertyName("title")]
        public string Title { get; set; }

        [SerializationPropertyName("appStore")]
        public AppStore AppStore { get; set; }

        [SerializationPropertyName("gfn")]
        public Gfn Gfn { get; set; }

        [SerializationPropertyName("osType")]
        public OsType OsType { get; set; }

        [SerializationPropertyName("storeId")]
        public string StoreId { get; set; }

        public long LaunchCmsId => Id;
    }

    public class Gfn
    {
        [SerializationPropertyName("releaseDate")]
        public string ReleaseDate { get; set; }
    }

    public class PageInfo
    {
        [SerializationPropertyName("hasNextPage")]
        public bool HasNextPage { get; set; }

        [SerializationPropertyName("endCursor")]
        public string EndCursor { get; set; }
    }

    public enum AppType
    {
        Application = 0,
        Dlc = 1,
        Game = 2,
        Platform_Client = 3,
        Prerequisite = 4,
        Variant = 5,
        Interactive_Content = 6,
    }

    public enum AppStore
    {
        Battlenet = 0,
        Bethesda = 1,
        Digital_Extremes = 2,
        Epic = 3,
        Gazillion = 4,
        Gog = 5,
        None = 6,
        Nvidia = 7,
        Nv_Bundle = 8,
        Origin = 9,
        Rockstar = 10,
        Steam = 11,
        Unknown = 12,
        Uplay = 13,
        Wargaming = 14,
        Stove = 15,
        EA_APP = 16,
        Xbox = 17,
        GAIJIN = 18,
    }

    public enum OsType
    {
        Windows = 0,
        Linux = 1,
    }
}
