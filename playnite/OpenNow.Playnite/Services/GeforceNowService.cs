using OpenNow.Playnite.Models;
using Playnite.SDK;
using Playnite.SDK.Data;
using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text;

namespace OpenNow.Playnite.Services
{
    internal static class GeforceNowService
    {
        private static readonly ILogger Logger = LogManager.GetLogger();
        private const string GraphQlEndpoint = "https://api-prod.nvidia.com/services/gfngames/v1/gameList";

        private static string BuildQuery(string after, string country = "US", string language = "en_US")
        {
            return $@"{{
  apps(country:""{country}"", language:""{language}"", after: ""{after}"") {{
    numberReturned,
    pageInfo {{
      hasNextPage,
      endCursor
    }},
    items {{
      id,
      cmsId,
      title,
      type,
      variants {{
        id,
        title,
        appStore,
        gfn {{
          releaseDate
        }},
        osType,
        storeId
      }}
    }}
  }}
}}";
        }

        public static List<GeforceNowItem> GetGeforceNowDatabase()
        {
            var afterValue = string.Empty;
            var items = new List<GeforceNowItem>();
            var pageCount = 0;

            Logger.Info("Fetching GeForce NOW database for OpenNOW Library...");
            using (var client = new HttpClient())
            {
                while (true)
                {
                    pageCount++;
                    var requestBody = Serialization.ToJson(new GraphQlRequest
                    {
                        Query = BuildQuery(afterValue),
                    });

                    var response = client.PostAsync(
                        GraphQlEndpoint,
                        new StringContent(requestBody, Encoding.UTF8, "application/json")).GetAwaiter().GetResult();

                    if (!response.IsSuccessStatusCode)
                    {
                        Logger.Warn($"GeForce NOW request failed on page {pageCount}: {(int)response.StatusCode}");
                        break;
                    }

                    var content = response.Content.ReadAsStringAsync().GetAwaiter().GetResult();
                    var parsed = Serialization.FromJson<GfnGraphQlResponse>(content);
                    var pageItems = parsed?.Data?.Apps?.Items;
                    if (pageItems == null || pageItems.Count == 0)
                    {
                        break;
                    }

                    items.AddRange(pageItems);
                    Logger.Debug($"Fetched GeForce NOW page {pageCount}: {pageItems.Count} items");

                    if (parsed.Data.Apps.PageInfo?.HasNextPage != true)
                    {
                        break;
                    }

                    afterValue = parsed.Data.Apps.PageInfo.EndCursor ?? string.Empty;
                    if (string.IsNullOrWhiteSpace(afterValue))
                    {
                        break;
                    }
                }
            }

            Logger.Info($"Finished fetching GeForce NOW database. Pages: {pageCount}, items: {items.Count}");
            return items;
        }

        private sealed class GraphQlRequest
        {
            [SerializationPropertyName("query")]
            public string Query { get; set; }
        }
    }
}
