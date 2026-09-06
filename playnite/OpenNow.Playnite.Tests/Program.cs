using System.Net;
using System.Diagnostics;
using System.Reflection;
using System.Runtime.CompilerServices;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.Json.Serialization.Metadata;
using OpenNow.Playnite.Models;
using OpenNow.Playnite;
using OpenNow.Playnite.Services;
using Playnite.SDK;
using Playnite.SDK.Data;
using Playnite.SDK.Models;
using Playnite.SDK.Plugins;

typeof(Serialization).GetMethod("Init", BindingFlags.Static | BindingFlags.NonPublic)
    .Invoke(null, new object[] { DispatchProxy.Create<IDataSerializer, SerializerProxy>() });
LogManager.Init(new TestLogProvider());

var tests = new (string Name, Action Run)[]
{
    ("Windows argument quoting", () =>
    {
        Equal("--launch-app-id=2147483648 --launch-title=\"Game\"", OpenNowLauncher.BuildLaunchArguments("Game", 2147483648L));
        Equal("--launch-title=\"Game\tName\"", OpenNowLauncher.BuildLaunchArguments("Game\tName", null));
        Equal("""
            --launch-title="Game \\\"Name\"\\"
            """, OpenNowLauncher.BuildLaunchArguments("Game \\\"Name\"\\", 0));
        Equal("--launch-title=\"\"", OpenNowLauncher.BuildLaunchArguments(null, -1));
    }),
    ("Exact edition wins over normalized collision in either order", () =>
    {
        var standard = Variant(1, "Example", AppStore.Epic);
        var premium = Variant(2, "Example Premium Edition", AppStore.Epic);
        foreach (var variants in new[] { new[] { standard, premium }, new[] { premium, standard } })
        {
            var service = Database(variants);
            Equal(1L, Match(service, "Example")?.Id);
            Equal(2L, Match(service, "Example Premium Edition")?.Id);
            Equal(null, Match(service, "Example Game of the Year Edition"));
            Equal(2, service.AllVariants.Count);
        }
    }),
    ("Unambiguous edition fallback and trademarks still match", () =>
    {
        var service = Database(Variant(5, "Example™ Premium Edition", AppStore.Epic));
        Equal(5L, Match(service, "Example")?.Id);
        Equal(5L, Match(service, "Example Premium Edition")?.Id);
    }),
    ("Identical titles with distinct IDs are not arbitrarily selected", () =>
    {
        var service = Database(Variant(1, "Example", AppStore.Epic), Variant(2, "Example", AppStore.Epic));
        Equal(null, Match(service, "Example"));
        Equal(2, service.AllVariants.Count);
    }),
    ("Origin and EA app share title detection", () =>
    {
        foreach (var store in new[] { AppStore.Origin, AppStore.EA_APP })
        {
            var service = Database(Variant(6, "Example", store));
            Equal(6L, service.GetMatchingVariant(new Game { Name = "Example", PluginId = BuiltinExtensions.GetIdFromExtension(BuiltinExtension.OriginLibrary) })?.Id);
            Equal(6L, service.GetMatchingVariant(new Game { Name = "Example", PluginId = Guid.Parse("85dd7072-2f20-4e76-a007-41035e390724") })?.Id);
        }
    }),
    ("Store IDs remain store scoped and database replacement clears indexes", () =>
    {
        var steam = Variant(7, "Example", AppStore.Steam);
        steam.StoreId = "42";
        var service = Database(steam, null);
        var game = new Game { GameId = "42", PluginId = BuiltinExtensions.GetIdFromExtension(BuiltinExtension.SteamLibrary) };
        Equal(7L, service.GetMatchingVariant(game)?.Id);
        Equal(null, Match(service, "Example"));
        service.SetDatabase(new GeforceNowItem[] { null, new() { Type = AppType.Dlc, Variants = new() { steam } } });
        Equal(false, service.HasEntries);
        Equal(null, service.GetMatchingVariant(game));
    }),
    ("Non-Windows variants are excluded", () =>
    {
        var linux = Variant(8, "Example", AppStore.Epic);
        linux.OsType = OsType.Linux;
        Equal(false, Database(linux).HasEntries);
    }),
    ("Complete pages accumulate and cursor is GraphQL escaped", () =>
    {
        var cursor = "cursor\"\\\n";
        using var handler = new CatalogHandler((page, request) =>
        {
            using var body = JsonDocument.Parse(request.Content.ReadAsStringAsync().GetAwaiter().GetResult());
            if (page == 2)
                True(body.RootElement.GetProperty("query").GetString().Contains("after: " + Serialization.ToJson(cursor)));
            return Page(page == 1, cursor);
        });
        using var client = new HttpClient(handler);
        Equal(2, GeforceNowService.GetGeforceNowDatabase(client, CancellationToken.None).Count);
        Equal(2, handler.Count);
        True(handler.Contents.All(content => content.Disposed));
        foreach (var request in handler.Requests)
            Throws<ObjectDisposedException>(() => request.Content.ReadAsStringAsync().GetAwaiter().GetResult());
    }),
    ("HTTP failure never returns a partial catalog", () =>
    {
        using var handler = new CatalogHandler((page, _) => page == 1 ? Page(true, "next") : new HttpResponseMessage(HttpStatusCode.ServiceUnavailable));
        using var client = new HttpClient(handler);
        Throws<HttpRequestException>(() => GeforceNowService.GetGeforceNowDatabase(client, CancellationToken.None));
        True(handler.Contents.All(content => content.Disposed));
    }),
    ("Malformed and GraphQL-error pages reject partial catalogs", () =>
    {
        foreach (var invalid in new[] { "{}", "{\"errors\":[{\"message\":\"failed\"}],\"data\":{\"apps\":{\"items\":[],\"pageInfo\":{\"hasNextPage\":false}}}}", "{\"data\":{\"apps\":{\"items\":[]}}}" })
        {
            using var handler = new CatalogHandler((page, _) => page == 1 ? Page(true, "next") : JsonResponse(invalid));
            using var client = new HttpClient(handler);
            Throws<InvalidOperationException>(() => GeforceNowService.GetGeforceNowDatabase(client, CancellationToken.None));
        }
    }),
    ("Repeated, cyclic, missing and empty-page cursors stop pagination", () =>
    {
        foreach (var cursor in new[] { "same", "", null })
        {
            using var handler = new CatalogHandler((_, _) => Page(true, cursor));
            using var client = new HttpClient(handler);
            Throws<InvalidOperationException>(() => GeforceNowService.GetGeforceNowDatabase(client, CancellationToken.None));
            True(handler.Count <= 2);
        }
        using var cyclic = new CatalogHandler((page, _) => Page(true, (page % 2).ToString()));
        using var cyclicClient = new HttpClient(cyclic);
        Throws<InvalidOperationException>(() => GeforceNowService.GetGeforceNowDatabase(cyclicClient, CancellationToken.None));
        Equal(3, cyclic.Count);
        using var empty = new CatalogHandler((_, _) => Page(true, "next", 0));
        using var emptyClient = new HttpClient(empty);
        Throws<InvalidOperationException>(() => GeforceNowService.GetGeforceNowDatabase(emptyClient, CancellationToken.None));
        Equal(1, empty.Count);
    }),
    ("Page and item limits reject oversized catalogs", () =>
    {
        using var handler = new CatalogHandler((page, _) => Page(true, page.ToString()));
        using var client = new HttpClient(handler);
        Throws<InvalidOperationException>(() => GeforceNowService.GetGeforceNowDatabase(client, CancellationToken.None));
        Equal(100, handler.Count);
        using var oversized = new CatalogHandler((_, _) => Page(false, null, 50001));
        using var oversizedClient = new HttpClient(oversized);
        Throws<InvalidOperationException>(() => GeforceNowService.GetGeforceNowDatabase(oversizedClient, CancellationToken.None));
    }),
    ("Cancellation prevents requests and interrupts in-flight requests", () =>
    {
        using var handler = new CatalogHandler((_, _) => Page(false, null));
        using var client = new HttpClient(handler);
        Throws<OperationCanceledException>(() => GeforceNowService.GetGeforceNowDatabase(client, new CancellationToken(true)));
        Equal(0, handler.Count);
        using var blockedClient = new HttpClient(new BlockingHandler());
        using var timeout = new CancellationTokenSource(TimeSpan.FromMilliseconds(50));
        Throws<OperationCanceledException>(() => GeforceNowService.GetGeforceNowDatabase(blockedClient, timeout.Token));
    }),
    ("Startup watcher stops after its deadline without reporting a started game", () =>
    {
        var processes = Process.GetProcessesByName("OpenNOW");
        foreach (var process in processes) process.Dispose();
        Equal(0, processes.Length);
        using var controller = (OpenNowPlayController)RuntimeHelpers.GetUninitializedObject(typeof(OpenNowPlayController));
        typeof(PlayController).GetField("execContext", BindingFlags.Instance | BindingFlags.NonPublic)
            .SetValue(controller, new SynchronizationContext());
        var stopped = new TaskCompletionSource<ulong>();
        var started = false;
        typeof(PlayController).GetEvent("Started", BindingFlags.Instance | BindingFlags.NonPublic)
            .GetAddMethod(true).Invoke(controller, new object[] { new EventHandler<GameStartedEventArgs>((_, _) => started = true) });
        typeof(PlayController).GetEvent("Stopped", BindingFlags.Instance | BindingFlags.NonPublic)
            .GetAddMethod(true).Invoke(controller, new object[] { new EventHandler<GameStoppedEventArgs>((_, args) => stopped.TrySetResult(args.SessionLength)) });
        typeof(OpenNowPlayController).GetField("stopWatch", BindingFlags.Instance | BindingFlags.NonPublic)
            .SetValue(controller, Stopwatch.StartNew());
        typeof(OpenNowPlayController).GetMethod("StartWatching", BindingFlags.Instance | BindingFlags.NonPublic)
            .Invoke(controller, null);
        Equal(0UL, stopped.Task.WaitAsync(TimeSpan.FromSeconds(35)).GetAwaiter().GetResult());
        Equal(false, started);
    }),
};

var failures = 0;
foreach (var (name, run) in tests)
{
    try { run(); Console.WriteLine($"PASS {name}"); }
    catch (Exception error) { failures++; Console.Error.WriteLine($"FAIL {name}: {error}"); }
}
Console.WriteLine($"{tests.Length - failures}/{tests.Length} tests passed");
return failures == 0 ? 0 : 1;

static void Equal(object expected, object actual)
{
    if (!Equals(expected, actual)) throw new Exception($"Expected [{expected}], got [{actual}]");
}
static void True(bool value) { if (!value) throw new Exception("Expected true"); }
static void Throws<T>(Action action) where T : Exception
{
    try { action(); } catch (T) { return; }
    throw new Exception($"Expected {typeof(T).Name}");
}
static GeforceNowItemVariant Variant(long id, string title, AppStore store) => new() { Id = id, Title = title, AppStore = store };
static GameDetectionService Database(params GeforceNowItemVariant[] variants)
{
    var service = new GameDetectionService();
    service.SetDatabase(new[] { new GeforceNowItem { Type = AppType.Game, Variants = variants.ToList() } });
    return service;
}
static GeforceNowItemVariant Match(GameDetectionService service, string title) => service.GetMatchingVariant(new Game
{
    Name = title, PluginId = BuiltinExtensions.GetIdFromExtension(BuiltinExtension.EpicLibrary)
});
static HttpResponseMessage JsonResponse(string json) => new(HttpStatusCode.OK) { Content = new TrackedContent(json) };
static HttpResponseMessage Page(bool hasNextPage, string cursor, int count = 1) => JsonResponse(JsonSerializer.Serialize(new
{
    data = new { apps = new { items = Enumerable.Range(0, count).Select(_ => new { type = "Game", variants = Array.Empty<object>() }), pageInfo = new { hasNextPage, endCursor = cursor } } }
}));

class TrackedContent(string json) : StringContent(json)
{
    public bool Disposed { get; private set; }
    protected override void Dispose(bool disposing) { Disposed = true; base.Dispose(disposing); }
}
class CatalogHandler(Func<int, HttpRequestMessage, HttpResponseMessage> respond) : HttpMessageHandler
{
    public int Count { get; private set; }
    public List<TrackedContent> Contents { get; } = new();
    public List<HttpRequestMessage> Requests { get; } = new();
    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        Requests.Add(request);
        var response = respond(++Count, request);
        if (response.Content is TrackedContent content) Contents.Add(content);
        return Task.FromResult(response);
    }
}
class BlockingHandler : HttpMessageHandler
{
    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        await Task.Delay(Timeout.Infinite, cancellationToken);
        throw new InvalidOperationException();
    }
}
public class SerializerProxy : DispatchProxy
{
    private static readonly JsonSerializerOptions Options = CreateOptions();
    private static JsonSerializerOptions CreateOptions()
    {
        var resolver = new DefaultJsonTypeInfoResolver();
        resolver.Modifiers.Add(info =>
        {
            foreach (var property in info.Properties)
                if (property.AttributeProvider?.GetCustomAttributes(typeof(SerializationPropertyNameAttribute), true).FirstOrDefault() is SerializationPropertyNameAttribute name)
                    property.Name = name.PropertyName;
        });
        var options = new JsonSerializerOptions { TypeInfoResolver = resolver };
        options.Converters.Add(new JsonStringEnumConverter());
        return options;
    }
    protected override object Invoke(MethodInfo method, object[] args) => method.Name switch
    {
        "ToJson" => JsonSerializer.Serialize(args[0], Options),
        "FromJson" => JsonSerializer.Deserialize((string)args[0], method.ReturnType, Options),
        _ => throw new NotSupportedException(method.Name)
    };
}
public class LoggerProxy : DispatchProxy
{
    protected override object Invoke(MethodInfo method, object[] args) => null;
}
class TestLogProvider : ILogProvider
{
    public ILogger GetLogger(string name) => DispatchProxy.Create<ILogger, LoggerProxy>();
}
