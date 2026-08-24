#include "../catalogengine.h"

#include <QJsonDocument>
#include <QTest>
#include <QUrlQuery>

class CatalogEngineParserTest final : public QObject
{
    Q_OBJECT

private slots:
    void selectsIdTokenBeforeAccessToken();
    void rejectsUntrustedStreamingUrls();
    void buildsPersistedQueryDeterministically();
    void parsesServerInfoAndFiltersMetadata();
    void mapsSelectedOwnedVariantAndImages();
    void parsesOnlyEntitledSubscriptionResolutions();
    void mapsOnlyApiBackedAccountProviders();
};

void CatalogEngineParserTest::selectsIdTokenBeforeAccessToken()
{
    QCOMPARE(GfnCatalog::selectJwt(QStringLiteral(" id-token "), QStringLiteral("access-token")),
             QStringLiteral("id-token"));
    QCOMPARE(GfnCatalog::selectJwt(QString(), QStringLiteral(" access-token ")),
             QStringLiteral("access-token"));
}

void CatalogEngineParserTest::rejectsUntrustedStreamingUrls()
{
    QString error;
    QCOMPARE(GfnCatalog::trustedStreamingBaseUrl(
                 QStringLiteral("https://prod.cloudmatchbeta.nvidiagrid.net"), &error),
             QUrl(QStringLiteral("https://prod.cloudmatchbeta.nvidiagrid.net/")));
    QVERIFY(error.isEmpty());
    QVERIFY(GfnCatalog::trustedStreamingBaseUrl(QStringLiteral("http://prod.cloudmatchbeta.nvidiagrid.net"), &error).isEmpty());
    QVERIFY(!error.isEmpty());
    QVERIFY(GfnCatalog::trustedStreamingBaseUrl(QStringLiteral("https://nvidiagrid.net.attacker.test"), &error).isEmpty());
    QVERIFY(GfnCatalog::trustedStreamingBaseUrl(QStringLiteral("https://user:password@nvidiagrid.net"), &error).isEmpty());
    QVERIFY(GfnCatalog::trustedStreamingBaseUrl(QStringLiteral("https://nvidiagrid.net/path"), &error).isEmpty());
}

void CatalogEngineParserTest::buildsPersistedQueryDeterministically()
{
    const QUrl url = GfnCatalog::buildPersistedQueryUrl(
        QUrl(QStringLiteral("https://games.geforce.com/graphql")),
        QStringLiteral("apps"), QStringLiteral("abc123"),
        QJsonObject{{QStringLiteral("locale"), QStringLiteral("en_US")},
                    {QStringLiteral("cursor"), QString()}},
        QStringLiteral("stable-hu-id"));
    const QUrlQuery query(url);
    QCOMPARE(query.queryItemValue(QStringLiteral("requestType")), QStringLiteral("apps"));
    QCOMPARE(query.queryItemValue(QStringLiteral("huId")), QStringLiteral("stable-hu-id"));
    QCOMPARE(QJsonDocument::fromJson(query.queryItemValue(QStringLiteral("variables")).toUtf8()).object(),
             (QJsonObject{{QStringLiteral("cursor"), QString()},
                          {QStringLiteral("locale"), QStringLiteral("en_US")}}));
    const auto extensions = QJsonDocument::fromJson(
        query.queryItemValue(QStringLiteral("extensions")).toUtf8()).object();
    QCOMPARE(extensions.value(QStringLiteral("persistedQuery")).toObject()
                 .value(QStringLiteral("sha256Hash")).toString(),
             QStringLiteral("abc123"));
}

void CatalogEngineParserTest::parsesServerInfoAndFiltersMetadata()
{
    const auto result = GfnCatalog::parseServerInfo(QJsonObject{
        {QStringLiteral("requestStatus"), QJsonObject{{QStringLiteral("serverId"), QStringLiteral("NP-AMS-08")}}},
        {QStringLiteral("metaData"), QJsonArray{
             QJsonObject{{QStringLiteral("key"), QStringLiteral("US West")},
                         {QStringLiteral("value"), QStringLiteral("https://prod.us-west.geforcenow.nvidiagrid.net")}},
             QJsonObject{{QStringLiteral("key"), QStringLiteral("gfn-regions")},
                         {QStringLiteral("value"), QStringLiteral("US West")}},
             QJsonObject{{QStringLiteral("key"), QStringLiteral("hostile")},
                         {QStringLiteral("value"), QStringLiteral("https://attacker.test")}},
         }},
    });
    QCOMPARE(result.value(QStringLiteral("vpcId")).toString(), QStringLiteral("NP-AMS-08"));
    const auto regions = result.value(QStringLiteral("regions")).toList();
    QCOMPARE(regions.size(), 1);
    QCOMPARE(regions.first().toMap().value(QStringLiteral("name")).toString(), QStringLiteral("US West"));
    QCOMPARE(regions.first().toMap().value(QStringLiteral("url")).toString(),
             QStringLiteral("https://prod.us-west.geforcenow.nvidiagrid.net/"));
}

void CatalogEngineParserTest::mapsSelectedOwnedVariantAndImages()
{
    const auto game = GfnCatalog::parseGame(QJsonObject{
        {QStringLiteral("id"), QStringLiteral("app-uuid")},
        {QStringLiteral("title"), QStringLiteral("Example Game")},
        {QStringLiteral("images"), QJsonObject{
             {QStringLiteral("HERO_IMAGE"), QStringLiteral("https://img.nvidiagrid.net/example")},
             {QStringLiteral("SCREENSHOTS"), QJsonArray{QStringLiteral("https://cdn.example/one.jpg")}},
         }},
        {QStringLiteral("variants"), QJsonArray{
             QJsonObject{{QStringLiteral("id"), QStringLiteral("not-numeric")},
                         {QStringLiteral("appStore"), QStringLiteral("STEAM")},
                         {QStringLiteral("gfn"), QJsonObject{{QStringLiteral("library"), QJsonObject{
                              {QStringLiteral("selected"), false}, {QStringLiteral("status"), QStringLiteral("NOT_OWNED")}}}}}},
             QJsonObject{{QStringLiteral("id"), QStringLiteral("12345")},
                         {QStringLiteral("appStore"), QStringLiteral("EPIC")},
                         {QStringLiteral("gfn"), QJsonObject{{QStringLiteral("status"), QStringLiteral("AVAILABLE")},
                              {QStringLiteral("library"), QJsonObject{{QStringLiteral("selected"), true},
                                   {QStringLiteral("status"), QStringLiteral("PLATFORM_SYNC")},
                                   {QStringLiteral("lastPlayedDate"), QStringLiteral("2026-08-24T00:00:00Z")}}}}}},
         }},
    });
    QCOMPARE(game.value(QStringLiteral("launchAppId")).toString(), QStringLiteral("12345"));
    QCOMPARE(game.value(QStringLiteral("selectedVariantIndex")).toInt(), 1);
    QVERIFY(game.value(QStringLiteral("isInLibrary")).toBool());
    QCOMPARE(game.value(QStringLiteral("heroImageUrl")).toString(),
             QStringLiteral("https://img.nvidiagrid.net/example;f=webp;w=1200"));
    QCOMPARE(game.value(QStringLiteral("screenshotUrls")).toStringList(),
             QStringList{QStringLiteral("https://cdn.example/one.jpg")});
}

void CatalogEngineParserTest::parsesOnlyEntitledSubscriptionResolutions()
{
    const auto subscription = GfnCatalog::parseSubscription(QJsonObject{
        {QStringLiteral("membershipTier"), QStringLiteral("ULTIMATE")},
        {QStringLiteral("allottedTimeInMinutes"), 600},
        {QStringLiteral("remainingTimeInMinutes"), 420},
        {QStringLiteral("features"), QJsonObject{{QStringLiteral("resolutions"), QJsonArray{
             QJsonObject{{QStringLiteral("widthInPixels"), 1920}, {QStringLiteral("heightInPixels"), 1080},
                         {QStringLiteral("framesPerSecond"), 60}, {QStringLiteral("isEntitled"), true}},
             QJsonObject{{QStringLiteral("widthInPixels"), 3840}, {QStringLiteral("heightInPixels"), 2160},
                         {QStringLiteral("framesPerSecond"), 120}, {QStringLiteral("isEntitled"), false}},
         }}}},
    }, QStringLiteral("NP-AMS-08"));
    QCOMPARE(subscription.value(QStringLiteral("totalHours")).toDouble(), 10.0);
    QCOMPARE(subscription.value(QStringLiteral("usedHours")).toDouble(), 3.0);
    QCOMPARE(subscription.value(QStringLiteral("entitledResolutions")).toList().size(), 1);
}

void CatalogEngineParserTest::mapsOnlyApiBackedAccountProviders()
{
    const QJsonArray definitions{
        QJsonObject{{QStringLiteral("store"), QStringLiteral("UBISOFT_CONNECT")},
                    {QStringLiteral("label"), QStringLiteral("Ubisoft Connect")},
                    {QStringLiteral("sortOrder"), 10},
                    {QStringLiteral("features"), QJsonArray{
                         QJsonObject{{QStringLiteral("__typename"), QStringLiteral("AccountLinkingSso")},
                                     {QStringLiteral("supported"), true}},
                         QJsonObject{{QStringLiteral("__typename"), QStringLiteral("AccountGamesSyncing")},
                                     {QStringLiteral("supported"), true}},
                     }}},
        QJsonObject{{QStringLiteral("store"), QStringLiteral("UNSUPPORTED")},
                    {QStringLiteral("features"), QJsonArray{}}},
    };
    const QJsonArray stores{
        QJsonObject{{QStringLiteral("store"), QStringLiteral("UPLAY")},
                    {QStringLiteral("accountLinkingData"), QJsonObject{
                         {QStringLiteral("userDisplayName"), QStringLiteral("Player")},
                         {QStringLiteral("accountSyncingData"), QJsonObject{
                              {QStringLiteral("syncState"), QStringLiteral("SYNC_SUCCESS")},
                              {QStringLiteral("totalNumberOfSyncedGfnGames"), 42},
                          }},
                     }}},
    };
    const auto accounts = GfnCatalog::parseAccounts(definitions, stores, 1000);
    QCOMPARE(accounts.size(), 1);
    const auto account = accounts.first().toMap();
    QCOMPARE(account.value(QStringLiteral("provider")).toString(), QStringLiteral("UPLAY"));
    QCOMPARE(account.value(QStringLiteral("status")).toString(), QStringLiteral("connected"));
    QCOMPARE(account.value(QStringLiteral("syncedGames")).toInt(), 42);
}

QTEST_MAIN(CatalogEngineParserTest)
#include "catalogengine_parsertest.moc"
