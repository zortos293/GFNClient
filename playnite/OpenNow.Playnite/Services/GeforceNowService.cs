using OpenNow.Playnite.Models;
using Playnite.SDK;
using Playnite.SDK.Data;
using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text;
using System.Threading;

namespace OpenNow.Playnite.Services
{
    internal static class GeforceNowService
    {
        private static readonly ILogger Logger = LogManager.GetLogger();
        private const string GraphQlEndpoint = "https://api-prod.nvidia.com/services/gfngames/v1/gameList";

        private static string BuildQuery(string after, string country = "US", string language = "en_US")
        {
            return $@"{{
  apps(country:{Serialization.ToJson(country)}, language:{Serialization.ToJson(language)}, after: {Serialization.ToJson(after)}) {{
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
            using (var client = new HttpClient { MaxResponseContentBufferSize = 8 * 1024 * 1024 })
            using (var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(2)))
            {
                return GetGeforceNowDatabase(client, timeout.Token);
            }
        }

        internal static List<GeforceNowItem> GetGeforceNowDatabase(HttpClient client, CancellationToken cancellationToken)
        {
            var afterValue = string.Empty;
            var items = new List<GeforceNowItem>();
            var pageCount = 0;
            var cursors = new HashSet<string>(StringComparer.Ordinal);

            Logger.Info("Fetching GeForce NOW database for OpenNOW Library...");
            while (pageCount < 100)
            {
                cancellationToken.ThrowIfCancellationRequested();
                pageCount++;
                var requestBody = Serialization.ToJson(new GraphQlRequest
                {
                    Query = BuildQuery(afterValue),
                });

                using (var requestContent = new StringContent(requestBody, Encoding.UTF8, "application/json"))
                using (var response = client.PostAsync(GraphQlEndpoint, requestContent, cancellationToken).GetAwaiter().GetResult())
                {
                    response.EnsureSuccessStatusCode();

                    var content = response.Content.ReadAsStringAsync().GetAwaiter().GetResult();
                    var parsed = Serialization.FromJson<GfnGraphQlResponse>(content);
                    var apps = parsed?.Data?.Apps;
                    var pageItems = apps?.Items;
                    if (parsed?.Errors?.Count > 0 || pageItems == null || apps.PageInfo == null)
                    {
                        throw new InvalidOperationException("GeForce NOW returned an incomplete catalog page.");
                    }

                    if (items.Count + pageItems.Count > 50000)
                    {
                        throw new InvalidOperationException("GeForce NOW catalog exceeded the item limit.");
                    }
                    items.AddRange(pageItems);
                    Logger.Debug($"Fetched GeForce NOW page {pageCount}: {pageItems.Count} items");

                    if (!apps.PageInfo.HasNextPage)
                    {
                        Logger.Info($"Finished fetching GeForce NOW database. Pages: {pageCount}, items: {items.Count}");
                        return items;
                    }

                    afterValue = apps.PageInfo.EndCursor;
                    if (pageItems.Count == 0 || string.IsNullOrWhiteSpace(afterValue) || !cursors.Add(afterValue))
                    {
                        throw new InvalidOperationException("GeForce NOW catalog pagination did not advance.");
                    }
                }
            }

            throw new InvalidOperationException("GeForce NOW catalog exceeded the page limit.");
        }

        private sealed class GraphQlRequest
        {
            [SerializationPropertyName("query")]
            public string Query { get; set; }
        }
    }
}
