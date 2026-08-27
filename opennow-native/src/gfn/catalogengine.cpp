#include "catalogengine.h"

#include <QCryptographicHash>
#include <QDateTime>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonParseError>
#include <QNetworkReply>
#include <QTcpSocket>
#include <QElapsedTimer>
#include <QTimer>
#include <QNetworkRequest>
#include <QRegularExpression>
#include <QUrlQuery>

#include <algorithm>
#include <memory>
#include <utility>

namespace {

constexpr auto kDefaultStreamingUrl = "https://prod.cloudmatchbeta.nvidiagrid.net/";
constexpr auto kGamesGraphqlUrl = "https://games.geforce.com/graphql";
constexpr auto kAppsGraphqlUrl = "https://apps.gxn.nvidia.com/graphql";
constexpr auto kMesUrl = "https://mes.geforcenow.com/v4/subscriptions";
constexpr auto kClientId = "ec7e38d4-03af-4b58-b131-cfb0495903ab";
constexpr auto kClientVersion = "2.0.80.173";
constexpr auto kUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 NVIDIACEFClient/HEAD/debb5919f6 GFN-PC/2.0.80.173";
constexpr int kRequestTimeoutMs = 10000;
constexpr int kMaxRetries = 2;
constexpr int kMaxCatalogPages = 3;
constexpr int kMaxLibraryPages = 25;

constexpr auto kDefinitionsHash = "ef725de5e93b093de1ac7418fed0ffb4f6ae2b9c14f743ab274a791521488eb9";
constexpr auto kAppsWithSearchHash = "ea1b5e417c95ceb5c7d6a65aa4613a417ed80b1a8d6a8c26b6953846da1fc513";
constexpr auto kAppsWithoutSearchHash = "5ae1cfe2e04debdcd81279b5559313abab7d9cfa3ac9d9c048e969b3d445dcb9";
constexpr auto kMainPanelHash = "46ec15f267a056e7d5e46e629efa929529e5e7542a4850faece90b9f8fa5f810";
constexpr auto kStaticAccountHash = "d4117df5319f644c984945715ded9574bb074107eb02e97be17605b5f14c33ba";
constexpr auto kUserAccountHash = "39fa5dbf8c14ac4c873857fd510f337cdc8710d5614038a0625487d41f98986b";

const QString kDefinitionsQuery = QStringLiteral(R"GRAPHQL(
query GetFilterGroupAndSortOrderDefinitions($locale: String!) {
  filterGroupDefinitions(language: $locale) { id label filters { id label filters } }
  sortOrderDefinitions(language: $locale) { id label orderBy }
})GRAPHQL");

const QString kAppFields = QStringLiteral(R"GRAPHQL(
numberReturned
numberSupported
pageInfo { hasNextPage endCursor totalCount }
items {
  id title shortName longDescription developerName publisherName genres supportedControls
  nvidiaTech { PHOTO_MODE FREESTYLE HIGHLIGHTS }
  maxLocalPlayers maxOnlinePlayers
  images { KEY_ART KEY_IMAGE GAME_BOX_ART TV_BANNER HERO_IMAGE MARQUEE_HERO_IMAGE FEATURE_IMAGE GAME_LOGO SCREENSHOTS }
  variants {
    id appStore storeUrl supportedControls
    gfn {
      status
      library { status selected lastPlayedDate }
      features {
        __typename
        ... on GfnSubscriptionFeatureValue { key value }
        ... on GfnSubscriptionFeatureValueList { key values }
      }
    }
  }
  gfn {
    playType playabilityState minimumMembershipTierLabel
    catalogSkuStrings {
      SKU_BASED_TAG SKU_BASED_PLAYABILITY_TEXT SKU_BASED_UNPLAYABLE_DIALOG_HEADER
      SKU_BASED_UNPLAYABLE_DIALOG_BODY_UPGRADE SKU_BASED_UNPLAYABLE_DIALOG_BODY_UPGRADE_ECOMM_RESTRICTED
    }
  }
  itemMetadata { campaignIds }
}
)GRAPHQL");

const QString kCatalogSearchQuery = QStringLiteral(R"GRAPHQL(
query GetSearchFilterResults($vpcId: String!, $locale: String!, $sortString: String!, $fetchCount: Int!, $cursor: String!, $searchString: String!, $filters: AppFilterFields!) {
  apps(vpcId: $vpcId, language: $locale, orderBy: $sortString, first: $fetchCount, after: $cursor, searchQuery: $searchString, filters: $filters) {
%1
  }
})GRAPHQL").arg(kAppFields);

const QString kCatalogQuery = QStringLiteral(R"GRAPHQL(
query GetFilterBrowseResults($vpcId: String!, $locale: String!, $sortString: String!, $fetchCount: Int!, $cursor: String!, $filters: AppFilterFields!) {
  apps(vpcId: $vpcId, language: $locale, orderBy: $sortString, first: $fetchCount, after: $cursor, filters: $filters) {
%1
  }
})GRAPHQL").arg(kAppFields);

const QString kLibraryQuery = QStringLiteral(R"GRAPHQL(
query GetLibraryApps($vpcId: String!, $locale: String!, $sortString: String!, $fetchCount: Int!, $cursor: String!, $filters: AppFilterFields!) {
  apps(vpcId: $vpcId, language: $locale, orderBy: $sortString, first: $fetchCount, after: $cursor, filters: $filters) {
%1
  }
})GRAPHQL").arg(kAppFields);

const QString kPanelQuery = QStringLiteral(R"GRAPHQL(
query GetGameSection($vpcId: String!, $locale: String!, $panelNames: [String]!) {
  panels(vpcId: $vpcId, language: $locale, names: $panelNames) {
    id name
    sections {
      id title renderDirectives
      items {
        __typename
        ... on GameItem {
          app {
            id title
            images { TV_BANNER HERO_IMAGE KEY_ART }
            variants { id appStore storeUrl supportedControls gfn { status library { status selected lastPlayedDate } } }
            gfn { playType playabilityState minimumMembershipTierLabel catalogSkuStrings { SKU_BASED_TAG } }
          }
        }
      }
    }
  }
})GRAPHQL");

QString compactJson(const QJsonValue &value)
{
    if (value.isObject())
        return QString::fromUtf8(QJsonDocument(value.toObject()).toJson(QJsonDocument::Compact));
    return QString::fromUtf8(QJsonDocument(value.toArray()).toJson(QJsonDocument::Compact));
}

QStringList jsonStrings(const QJsonValue &value)
{
    QStringList result;
    const auto append = [&result](const QJsonValue &entry) {
        QString text;
        if (entry.isString()) {
            text = entry.toString().trimmed();
        } else if (entry.isObject()) {
            const auto object = entry.toObject();
            for (const auto &key : {"name", "label", "title", "displayName"}) {
                text = object.value(QLatin1String(key)).toString().trimmed();
                if (!text.isEmpty())
                    break;
            }
            if (text.isEmpty()) {
                for (auto it = object.constBegin(); it != object.constEnd(); ++it) {
                    if (it.value().toBool() && !result.contains(it.key()))
                        result.append(it.key());
                }
            }
        }
        if (!text.isEmpty() && !result.contains(text))
            result.append(text);
    };

    if (value.isArray()) {
        for (const auto &entry : value.toArray())
            append(entry);
    } else {
        append(value);
    }
    return result;
}

QString optimizeImage(QString url, int width)
{
    url = url.trimmed();
    if (url.contains(QStringLiteral("img.nvidiagrid.net")) && !url.isEmpty())
        url += QStringLiteral(";f=webp;w=%1").arg(width);
    return url;
}

QStringList imageValues(const QJsonValue &value, int width)
{
    QStringList result;
    const auto values = value.isArray() ? value.toArray() : QJsonArray{value};
    for (const auto &entry : values) {
        const QString url = optimizeImage(entry.toString(), width);
        if (!url.isEmpty() && !result.contains(url))
            result.append(url);
    }
    return result;
}

QString firstImage(const QJsonObject &images, const QStringList &keys, int width)
{
    for (const auto &key : keys) {
        const auto values = imageValues(images.value(key), width);
        if (!values.isEmpty())
            return values.first();
    }
    return {};
}

bool isOwnedStatus(const QString &status)
{
    return status == QStringLiteral("MANUAL")
        || status == QStringLiteral("PLATFORM_SYNC")
        || status == QStringLiteral("IN_LIBRARY");
}

QString normalizeProvider(QString provider)
{
    provider = provider.trimmed().toUpper();
    provider.replace(QRegularExpression(QStringLiteral("[\\s-]+")), QStringLiteral("_"));
    if (provider == QStringLiteral("UBISOFT") || provider == QStringLiteral("UBISOFT_CONNECT"))
        return QStringLiteral("UPLAY");
    if (provider == QStringLiteral("BATTLE_NET") || provider == QStringLiteral("BLIZZARD"))
        return QStringLiteral("BATTLENET");
    if (provider == QStringLiteral("EPIC_GAMES") || provider == QStringLiteral("EPIC_GAMES_STORE"))
        return QStringLiteral("EPIC");
    return provider;
}

QVariantMap stateMap(bool loading, bool stale, const QString &error, qint64 updatedAt = 0)
{
    return {
        {QStringLiteral("loading"), loading},
        {QStringLiteral("stale"), stale},
        {QStringLiteral("error"), error},
        {QStringLiteral("updatedAt"), updatedAt},
    };
}

bool retryableStatus(int status)
{
    return status == 408 || status == 425 || status == 429 || status == 500
        || status == 502 || status == 503 || status == 504;
}

} // namespace

namespace GfnCatalog {

QString selectJwt(const QString &idToken, const QString &accessToken)
{
    const QString preferred = idToken.trimmed();
    return preferred.isEmpty() ? accessToken.trimmed() : preferred;
}

QUrl trustedStreamingBaseUrl(const QString &candidate, QString *error)
{
    if (error)
        error->clear();
    const QUrl url = QUrl::fromUserInput(candidate.trimmed());
    const QString host = url.host().toLower();
    const bool trustedHost = host == QStringLiteral("nvidiagrid.net")
        || host.endsWith(QStringLiteral(".nvidiagrid.net"));
    const bool rootPath = url.path().isEmpty() || url.path() == QStringLiteral("/");
    if (!url.isValid() || url.scheme() != QStringLiteral("https") || !trustedHost
        || !url.userInfo().isEmpty() || (url.port(-1) != -1 && url.port(-1) != 443)
        || !url.query().isEmpty() || !url.fragment().isEmpty() || !rootPath) {
        if (error)
            *error = QStringLiteral("Untrusted GFN streaming endpoint");
        return {};
    }

    QUrl normalized;
    normalized.setScheme(QStringLiteral("https"));
    normalized.setHost(host);
    normalized.setPath(QStringLiteral("/"));
    return normalized;
}

QUrl buildPersistedQueryUrl(const QUrl &endpoint,
                            const QString &requestType,
                            const QString &sha256Hash,
                            const QJsonObject &variables,
                            const QString &huId)
{
    QJsonObject persisted;
    persisted.insert(QStringLiteral("sha256Hash"), sha256Hash);
    QJsonObject extensions;
    extensions.insert(QStringLiteral("persistedQuery"), persisted);

    QUrlQuery query;
    if (!requestType.isEmpty())
        query.addQueryItem(QStringLiteral("requestType"), requestType);
    query.addQueryItem(QStringLiteral("extensions"), compactJson(extensions));
    if (!huId.isEmpty())
        query.addQueryItem(QStringLiteral("huId"), huId);
    if (!variables.isEmpty())
        query.addQueryItem(QStringLiteral("variables"), compactJson(variables));
    QUrl url(endpoint);
    url.setQuery(query);
    return url;
}

QVariantMap parseServerInfo(const QJsonObject &payload)
{
    QVariantList regions;
    const auto metadata = payload.value(QStringLiteral("metaData")).toArray();
    for (const auto &entryValue : metadata) {
        const auto entry = entryValue.toObject();
        const QString name = entry.value(QStringLiteral("key")).toString().trimmed();
        const QUrl candidate(entry.value(QStringLiteral("value")).toString().trimmed());
        if (name == QStringLiteral("gfn-regions") || name.startsWith(QStringLiteral("gfn-")))
            continue;
        QString validationError;
        QUrl trusted = trustedStreamingBaseUrl(candidate.toString(), &validationError);
        if (trusted.isEmpty())
            continue;
        regions.append(QVariantMap{{QStringLiteral("name"), name},
                                   {QStringLiteral("url"), trusted.toString()}});
    }
    std::sort(regions.begin(), regions.end(), [](const QVariant &left, const QVariant &right) {
        return left.toMap().value(QStringLiteral("name")).toString()
            < right.toMap().value(QStringLiteral("name")).toString();
    });
    return {
        {QStringLiteral("vpcId"), payload.value(QStringLiteral("requestStatus")).toObject()
             .value(QStringLiteral("serverId")).toString()},
        {QStringLiteral("regions"), regions},
    };
}

QVariantMap parseSubscription(const QJsonObject &payload, const QString &vpcId)
{
    const auto number = [&payload](const char *key) {
        const QJsonValue value = payload.value(QLatin1String(key));
        return value.isDouble() ? value.toDouble() : value.toString().toDouble();
    };
    const double allotted = number("allottedTimeInMinutes");
    const double purchased = number("purchasedTimeInMinutes");
    const double rolledOver = number("rolledOverTimeInMinutes");
    const bool hasTotal = payload.contains(QStringLiteral("totalTimeInMinutes"));
    const double total = hasTotal ? number("totalTimeInMinutes") : allotted + purchased + rolledOver;
    const double remaining = number("remainingTimeInMinutes");

    QVariantList resolutions;
    for (const auto &value : payload.value(QStringLiteral("features")).toObject()
                                  .value(QStringLiteral("resolutions")).toArray()) {
        const auto resolution = value.toObject();
        if (!resolution.value(QStringLiteral("isEntitled")).toBool())
            continue;
        resolutions.append(QVariantMap{
            {QStringLiteral("width"), resolution.value(QStringLiteral("widthInPixels")).toInt()},
            {QStringLiteral("height"), resolution.value(QStringLiteral("heightInPixels")).toInt()},
            {QStringLiteral("fps"), resolution.value(QStringLiteral("framesPerSecond")).toInt()},
        });
    }
    std::sort(resolutions.begin(), resolutions.end(), [](const QVariant &left, const QVariant &right) {
        const auto a = left.toMap();
        const auto b = right.toMap();
        if (a.value(QStringLiteral("width")) != b.value(QStringLiteral("width")))
            return a.value(QStringLiteral("width")).toInt() > b.value(QStringLiteral("width")).toInt();
        if (a.value(QStringLiteral("height")) != b.value(QStringLiteral("height")))
            return a.value(QStringLiteral("height")).toInt() > b.value(QStringLiteral("height")).toInt();
        return a.value(QStringLiteral("fps")).toInt() > b.value(QStringLiteral("fps")).toInt();
    });

    QVariantMap storage;
    for (const auto &addonValue : payload.value(QStringLiteral("addons")).toArray()) {
        const auto addon = addonValue.toObject();
        if (addon.value(QStringLiteral("type")) != QStringLiteral("STORAGE")
            || addon.value(QStringLiteral("subType")) != QStringLiteral("PERMANENT_STORAGE")
            || addon.value(QStringLiteral("status")) != QStringLiteral("OK"))
            continue;
        storage.insert(QStringLiteral("type"), QStringLiteral("PERMANENT_STORAGE"));
        for (const auto &attributeValue : addon.value(QStringLiteral("attributes")).toArray()) {
            const auto attribute = attributeValue.toObject();
            const QString key = attribute.value(QStringLiteral("key")).toString();
            const QString value = attribute.value(QStringLiteral("textValue")).toString();
            if (key == QStringLiteral("TOTAL_STORAGE_SIZE_IN_GB"))
                storage.insert(QStringLiteral("sizeGb"), value.toDouble());
            else if (key == QStringLiteral("USED_STORAGE_SIZE_IN_GB"))
                storage.insert(QStringLiteral("usedGb"), value.toDouble());
            else if (key == QStringLiteral("STORAGE_METRO_REGION_NAME"))
                storage.insert(QStringLiteral("regionName"), value);
            else if (key == QStringLiteral("STORAGE_METRO_REGION"))
                storage.insert(QStringLiteral("regionCode"), value);
        }
        break;
    }

    const auto currentState = payload.value(QStringLiteral("currentSubscriptionState")).toObject();
    const auto notifications = payload.value(QStringLiteral("notifications")).toObject();
    QVariantMap result{
        {QStringLiteral("membershipTier"), payload.value(QStringLiteral("membershipTier")).toString(QStringLiteral("FREE"))},
        {QStringLiteral("subscriptionType"), payload.value(QStringLiteral("type")).toString()},
        {QStringLiteral("subscriptionSubType"), payload.value(QStringLiteral("subType")).toString()},
        {QStringLiteral("allottedHours"), allotted / 60.0},
        {QStringLiteral("purchasedHours"), purchased / 60.0},
        {QStringLiteral("rolledOverHours"), rolledOver / 60.0},
        {QStringLiteral("usedHours"), std::max(total - remaining, 0.0) / 60.0},
        {QStringLiteral("remainingHours"), remaining / 60.0},
        {QStringLiteral("totalHours"), total / 60.0},
        {QStringLiteral("firstEntitlementStartDateTime"), payload.value(QStringLiteral("firstEntitlementStartDateTime")).toString()},
        {QStringLiteral("serverRegionId"), vpcId},
        {QStringLiteral("currentSpanStartDateTime"), payload.value(QStringLiteral("currentSpanStartDateTime")).toString()},
        {QStringLiteral("currentSpanEndDateTime"), payload.value(QStringLiteral("currentSpanEndDateTime")).toString()},
        {QStringLiteral("notifyUserWhenTimeRemainingInMinutes"), notifications.value(QStringLiteral("notifyUserWhenTimeRemainingInMinutes")).toDouble()},
        {QStringLiteral("notifyUserOnSessionWhenRemainingTimeInMinutes"), notifications.value(QStringLiteral("notifyUserOnSessionWhenRemainingTimeInMinutes")).toDouble()},
        {QStringLiteral("state"), currentState.value(QStringLiteral("state")).toString()},
        {QStringLiteral("isGamePlayAllowed"), currentState.value(QStringLiteral("isGamePlayAllowed")).toBool()},
        {QStringLiteral("isUnlimited"), payload.value(QStringLiteral("subType")).toString() == QStringLiteral("UNLIMITED")},
        {QStringLiteral("entitledResolutions"), resolutions},
    };
    if (!storage.isEmpty())
        result.insert(QStringLiteral("storageAddon"), storage);
    return result;
}

QVariantMap parseGame(const QJsonObject &app)
{
    const QJsonObject images = app.value(QStringLiteral("images")).toObject();
    QVariantMap imageUrlsByType;
    for (auto it = images.begin(); it != images.end(); ++it) {
        const QStringList values = imageValues(it.value(), 1200);
        if (!values.isEmpty())
            imageUrlsByType.insert(it.key(), values);
    }

    QVariantList variants;
    QStringList stores;
    QString launchAppId;
    QString selectedId;
    QString lastPlayed;
    bool inLibrary = false;
    int selectedIndex = -1;
    const QRegularExpression numeric(QStringLiteral("^\\d+$"));
    const auto appVariants = app.value(QStringLiteral("variants")).toArray();
    for (int index = 0; index < appVariants.size(); ++index) {
        const auto variant = appVariants.at(index).toObject();
        const auto gfn = variant.value(QStringLiteral("gfn")).toObject();
        const auto library = gfn.value(QStringLiteral("library")).toObject();
        const QString id = variant.value(QStringLiteral("id")).toString();
        const QString store = variant.value(QStringLiteral("appStore")).toString();
        const QString status = library.value(QStringLiteral("status")).toString();
        const bool selected = library.value(QStringLiteral("selected")).toBool();
        if (selected) {
            selectedIndex = index;
            selectedId = id;
        }
        if (lastPlayed.isEmpty())
            lastPlayed = library.value(QStringLiteral("lastPlayedDate")).toString();
        if (isOwnedStatus(status))
            inLibrary = true;
        if (!stores.contains(store) && !store.isEmpty())
            stores.append(store);
        variants.append(QVariantMap{
            {QStringLiteral("id"), id},
            {QStringLiteral("store"), store},
            {QStringLiteral("storeUrl"), variant.value(QStringLiteral("storeUrl")).toString()},
            {QStringLiteral("supportedControls"), jsonStrings(variant.value(QStringLiteral("supportedControls")))},
            {QStringLiteral("librarySelected"), selected},
            {QStringLiteral("inLibrary"), isOwnedStatus(status)},
            {QStringLiteral("libraryStatus"), status},
            {QStringLiteral("lastPlayedDate"), library.value(QStringLiteral("lastPlayedDate")).toString()},
            {QStringLiteral("gfnStatus"), gfn.value(QStringLiteral("status")).toString()},
        });
    }
    if (!selectedId.isEmpty() && numeric.match(selectedId).hasMatch())
        launchAppId = selectedId;
    if (launchAppId.isEmpty()) {
        for (const auto &value : appVariants) {
            const QString id = value.toObject().value(QStringLiteral("id")).toString();
            if (numeric.match(id).hasMatch()) {
                launchAppId = id;
                break;
            }
        }
    }
    if (launchAppId.isEmpty() && numeric.match(app.value(QStringLiteral("id")).toString()).hasMatch())
        launchAppId = app.value(QStringLiteral("id")).toString();

    const QString hero = firstImage(images, {QStringLiteral("MARQUEE_HERO_IMAGE"), QStringLiteral("HERO_IMAGE"),
                                             QStringLiteral("TV_BANNER"), QStringLiteral("FEATURE_IMAGE"),
                                             QStringLiteral("KEY_IMAGE"), QStringLiteral("KEY_ART")}, 1200);
    const QString poster = firstImage(images, {QStringLiteral("GAME_BOX_ART"), QStringLiteral("KEY_IMAGE"),
                                               QStringLiteral("KEY_ART")}, 900);
    const QStringList screenshots = imageValues(images.value(QStringLiteral("SCREENSHOTS")), 720);
    const QStringList genres = jsonStrings(app.value(QStringLiteral("genres")));
    QStringList features = jsonStrings(app.value(QStringLiteral("features")));
    const auto sku = app.value(QStringLiteral("gfn")).toObject().value(QStringLiteral("catalogSkuStrings")).toObject();
    for (const auto &tag : jsonStrings(sku.value(QStringLiteral("SKU_BASED_TAG")))) {
        if (!features.contains(tag))
            features.append(tag);
    }
    QStringList searchParts{app.value(QStringLiteral("title")).toString(),
                            app.value(QStringLiteral("publisherName")).toString(),
                            app.value(QStringLiteral("developerName")).toString()};
    searchParts.append(stores);
    searchParts.append(genres);
    searchParts.append(features);
    searchParts.removeAll(QString());

    const auto gfn = app.value(QStringLiteral("gfn")).toObject();
    const QString id = app.value(QStringLiteral("id")).toString();
    return {
        {QStringLiteral("id"), id},
        {QStringLiteral("uuid"), id},
        {QStringLiteral("launchAppId"), launchAppId},
        {QStringLiteral("title"), app.value(QStringLiteral("title")).toString()},
        {QStringLiteral("shortName"), app.value(QStringLiteral("shortName")).toString()},
        {QStringLiteral("description"), app.value(QStringLiteral("description")).toString()},
        {QStringLiteral("longDescription"), app.value(QStringLiteral("longDescription")).toString()},
        {QStringLiteral("developerName"), app.value(QStringLiteral("developerName")).toString()},
        {QStringLiteral("publisherName"), app.value(QStringLiteral("publisherName")).toString()},
        {QStringLiteral("maxLocalPlayers"), app.value(QStringLiteral("maxLocalPlayers")).toInt()},
        {QStringLiteral("maxOnlinePlayers"), app.value(QStringLiteral("maxOnlinePlayers")).toInt()},
        {QStringLiteral("featureLabels"), features},
        {QStringLiteral("genres"), genres},
        {QStringLiteral("supportedControls"), jsonStrings(app.value(QStringLiteral("supportedControls")))},
        {QStringLiteral("nvidiaTech"), jsonStrings(app.value(QStringLiteral("nvidiaTech")))},
        {QStringLiteral("imageUrl"), hero.isEmpty() ? poster : hero},
        {QStringLiteral("heroImageUrl"), hero},
        {QStringLiteral("screenshotUrl"), screenshots.value(0)},
        {QStringLiteral("screenshotUrls"), screenshots},
        {QStringLiteral("imageUrlsByType"), imageUrlsByType},
        {QStringLiteral("playType"), gfn.value(QStringLiteral("playType")).toString()},
        {QStringLiteral("membershipTierLabel"), gfn.value(QStringLiteral("minimumMembershipTierLabel")).toString()},
        {QStringLiteral("catalogSkuStrings"), sku.toVariantMap()},
        {QStringLiteral("playabilityState"), gfn.value(QStringLiteral("playabilityState")).toString()},
        {QStringLiteral("availableStores"), stores},
        {QStringLiteral("searchText"), searchParts.join(QLatin1Char(' ')).toLower()},
        {QStringLiteral("lastPlayed"), lastPlayed},
        {QStringLiteral("isInLibrary"), inLibrary},
        {QStringLiteral("selectedVariantIndex"), selectedIndex < 0 ? 0 : selectedIndex},
        {QStringLiteral("variants"), variants},
    };
}

QVariantList parseAccounts(const QJsonArray &definitions, const QJsonArray &stores, qint64 fetchedAtMs)
{
    QHash<QString, QJsonObject> storesByProvider;
    for (const auto &value : stores) {
        const auto store = value.toObject();
        storesByProvider.insert(normalizeProvider(store.value(QStringLiteral("store")).toString()), store);
    }

    QVariantList result;
    for (const auto &value : definitions) {
        const auto definition = value.toObject();
        const QString provider = normalizeProvider(definition.value(QStringLiteral("store")).toString());
        if (provider.isEmpty() || provider == QStringLiteral("UNKNOWN") || provider == QStringLiteral("NONE"))
            continue;
        bool supportsLinking = false;
        bool supportsSync = false;
        for (const auto &featureValue : definition.value(QStringLiteral("features")).toArray()) {
            const auto feature = featureValue.toObject();
            if (!feature.value(QStringLiteral("supported")).toBool())
                continue;
            supportsLinking |= feature.value(QStringLiteral("__typename")) == QStringLiteral("AccountLinkingSso");
            supportsSync |= feature.value(QStringLiteral("__typename")) == QStringLiteral("AccountGamesSyncing");
        }
        if (!supportsLinking && !supportsSync)
            continue;

        const auto store = storesByProvider.value(provider);
        const bool connected = !store.isEmpty();
        const auto linking = store.value(QStringLiteral("accountLinkingData")).toObject();
        const auto syncing = linking.value(QStringLiteral("accountSyncingData")).toObject();
        bool expiresOk = false;
        const qint64 expiresSeconds = linking.value(QStringLiteral("expiresIn")).toString().toLongLong(&expiresOk);
        const qint64 expiresAt = expiresOk && expiresSeconds >= 0
            ? fetchedAtMs + expiresSeconds * 1000 : 0;
        const QString syncState = syncing.value(QStringLiteral("syncState")).toString();
        const bool expired = connected && supportsLinking && expiresAt > 0 && expiresAt <= fetchedAtMs;
        const bool syncError = connected && supportsSync && !syncState.isEmpty()
            && syncState != QStringLiteral("SYNC_SUCCESS");
        const auto linkingMetadata = definition.value(QStringLiteral("accountLinkingMetadata")).toObject();
        result.append(QVariantMap{
            {QStringLiteral("provider"), provider},
            {QStringLiteral("label"), linkingMetadata.value(QStringLiteral("label")).toString(
                 definition.value(QStringLiteral("label")).toString(provider))},
            {QStringLiteral("sortOrder"), definition.value(QStringLiteral("sortOrder")).toInt(999)},
            {QStringLiteral("iconUrl"), definition.value(QStringLiteral("smallImageUrl")).toString()},
            {QStringLiteral("supportsLinking"), supportsLinking},
            {QStringLiteral("supportsSync"), supportsSync},
            {QStringLiteral("isRequired"), linkingMetadata.value(QStringLiteral("isRequired")).toBool()},
            {QStringLiteral("isConnected"), connected},
            {QStringLiteral("status"), !connected ? QStringLiteral("not_connected")
                 : expired ? QStringLiteral("expired")
                 : syncError ? QStringLiteral("sync_error") : QStringLiteral("connected")},
            {QStringLiteral("displayName"), linking.value(QStringLiteral("userDisplayName")).toString()},
            {QStringLiteral("userIdentifier"), linking.value(QStringLiteral("userIdentifier")).toString()},
            {QStringLiteral("expiresIn"), linking.value(QStringLiteral("expiresIn")).toString()},
            {QStringLiteral("expiresAt"), expiresAt},
            {QStringLiteral("syncState"), syncState},
            {QStringLiteral("syncDate"), syncing.value(QStringLiteral("syncDate")).toString()},
            {QStringLiteral("syncedGames"), syncing.value(QStringLiteral("totalNumberOfSyncedGfnGames")).toInt()},
        });
    }
    std::sort(result.begin(), result.end(), [](const QVariant &left, const QVariant &right) {
        const auto a = left.toMap();
        const auto b = right.toMap();
        const int order = a.value(QStringLiteral("sortOrder")).toInt() - b.value(QStringLiteral("sortOrder")).toInt();
        return order == 0 ? a.value(QStringLiteral("label")).toString() < b.value(QStringLiteral("label")).toString()
                          : order < 0;
    });
    return result;
}

} // namespace GfnCatalog

CatalogEngine::CatalogEngine(QObject *parent)
    : CatalogEngine(new QNetworkAccessManager, parent)
{
    m_network->setParent(this);
}

CatalogEngine::CatalogEngine(QNetworkAccessManager *network, QObject *parent)
    : QObject(parent)
    , m_network(network)
    , m_streamingBaseUrl(QString::fromLatin1(kDefaultStreamingUrl))
{
    Q_ASSERT(m_network);
}

CatalogEngine::~CatalogEngine()
{
    cancel();
}

bool CatalogEngine::loading() const { return !m_loadingOperations.isEmpty(); }
bool CatalogEngine::stale() const
{
    for (auto it = m_states.cbegin(); it != m_states.cend(); ++it) {
        if (it.value().toMap().value(QStringLiteral("stale")).toBool())
            return true;
    }
    return false;
}
QString CatalogEngine::errorString() const { return m_errorString; }
QString CatalogEngine::vpcId() const { return m_vpcId; }
QVariantList CatalogEngine::regions() const { return m_regions; }
QVariantMap CatalogEngine::subscription() const { return m_subscription; }
QVariantList CatalogEngine::library() const { return m_library; }
QVariantMap CatalogEngine::catalog() const { return m_catalog; }
QVariantList CatalogEngine::panels() const { return m_panels; }
QVariantList CatalogEngine::connectedAccounts() const { return m_connectedAccounts; }
QVariantMap CatalogEngine::states() const { return m_states; }

void CatalogEngine::setTokens(const QString &idToken, const QString &accessToken)
{
    if (m_idToken == idToken.trimmed() && m_accessToken == accessToken.trimmed())
        return;
    cancel();
    m_idToken = idToken.trimmed();
    m_accessToken = accessToken.trimmed();
    clear();
}

void CatalogEngine::setUserId(const QString &userId)
{
    if (m_userId == userId.trimmed())
        return;
    cancel();
    m_userId = userId.trimmed();
    m_subscription.clear();
    m_connectedAccounts.clear();
    emit subscriptionChanged();
    emit connectedAccountsChanged();
}

bool CatalogEngine::setProviderStreamingUrl(const QString &url)
{
    QString error;
    const QUrl trusted = GfnCatalog::trustedStreamingBaseUrl(url, &error);
    if (trusted.isEmpty()) {
        const QString safe = safeError(error);
        if (m_errorString != safe) {
            m_errorString = safe;
            emit errorStringChanged();
        }
        return false;
    }
    if (m_streamingBaseUrl == trusted)
        return true;
    cancel();
    m_streamingBaseUrl = trusted;
    m_vpcId.clear();
    m_regions.clear();
    ++m_probeGeneration;
    m_regionPings.clear();
    emit serverInfoChanged();
    emit regionProbeChanged();
    return true;
}

void CatalogEngine::setLocale(const QString &locale)
{
    const QString normalized = locale.trimmed().isEmpty() ? QStringLiteral("en_US") : locale.trimmed();
    if (m_locale == normalized)
        return;
    cancel();
    m_locale = normalized;
    m_library.clear();
    m_catalog.clear();
    m_panels.clear();
    emit libraryChanged();
    emit catalogChanged();
    emit panelsChanged();
}

void CatalogEngine::clear()
{
    cancel();
    m_errorString.clear();
    m_vpcId.clear();
    m_regions.clear();
    ++m_probeGeneration;
    ++m_testGeneration;
    m_probingRegions = false;
    m_regionPings.clear();
    m_networkTest.clear();
    m_subscription.clear();
    m_library.clear();
    m_catalog.clear();
    m_panels.clear();
    m_connectedAccounts.clear();
    m_states.clear();
    emit errorStringChanged();
    emit serverInfoChanged();
    emit regionProbeChanged();
    emit networkTestChanged();
    emit subscriptionChanged();
    emit libraryChanged();
    emit catalogChanged();
    emit panelsChanged();
    emit connectedAccountsChanged();
    emit statesChanged();
}

quint64 CatalogEngine::beginGeneration()
{
    cancel();
    return m_generation;
}

void CatalogEngine::cancel()
{
    ++m_generation;
    const bool wasLoading = loading();
    const auto replies = m_replies;
    m_replies.clear();
    for (QNetworkReply *reply : replies) {
        if (reply)
            reply->abort();
    }
    for (const auto &operation : std::as_const(m_loadingOperations)) {
        QVariantMap state = m_states.value(operation).toMap();
        state.insert(QStringLiteral("loading"), false);
        m_states.insert(operation, state);
    }
    m_loadingOperations.clear();
    if (wasLoading)
        emit loadingChanged();
    if (wasLoading)
        emit statesChanged();
}

bool CatalogEngine::credentialsReady(const QString &operation, quint64 generation)
{
    if (generation != m_generation)
        return false;
    if (!GfnCatalog::selectJwt(m_idToken, m_accessToken).isEmpty())
        return true;
    finishOperation(operation, QStringLiteral("No authenticated JWT is available"));
    return false;
}

void CatalogEngine::startOperation(const QString &operation, bool hasData)
{
    const bool wasLoading = loading();
    m_loadingOperations.insert(operation);
    m_states.insert(operation, stateMap(true, hasData, QString(),
                                        m_states.value(operation).toMap()
                                            .value(QStringLiteral("updatedAt")).toLongLong()));
    if (!m_errorString.isEmpty()) {
        m_errorString.clear();
        emit errorStringChanged();
    }
    if (wasLoading != loading())
        emit loadingChanged();
    emit statesChanged();
}

void CatalogEngine::finishOperation(const QString &operation, const QString &error)
{
    const bool wasLoading = loading();
    m_loadingOperations.remove(operation);
    const QVariantMap previous = m_states.value(operation).toMap();
    const QString safe = safeError(error);
    const bool staleData = !safe.isEmpty() && previous.value(QStringLiteral("stale")).toBool();
    m_states.insert(operation, stateMap(false, staleData, safe,
                                        safe.isEmpty() ? QDateTime::currentMSecsSinceEpoch()
                                                       : previous.value(QStringLiteral("updatedAt")).toLongLong()));
    if (!safe.isEmpty()) {
        if (m_errorString != safe) {
            m_errorString = safe;
            emit errorStringChanged();
        }
        emit requestFailed(operation, safe);
    }
    if (wasLoading != loading())
        emit loadingChanged();
    emit statesChanged();
}

QString CatalogEngine::safeError(QString message) const
{
    for (const QString &secret : {m_idToken, m_accessToken}) {
        if (!secret.isEmpty())
            message.replace(secret, QStringLiteral("[redacted]"));
    }
    return message.left(500);
}

void CatalogEngine::sendRequest(const QString &operation,
                                quint64 generation,
                                const QNetworkRequest &request,
                                const QByteArray &method,
                                const QByteArray &body,
                                NetworkCallback callback,
                                int attempt)
{
    if (generation != m_generation)
        return;
    QNetworkReply *reply = nullptr;
    if (method == QByteArrayLiteral("GET"))
        reply = m_network->get(request);
    else if (method == QByteArrayLiteral("POST"))
        reply = m_network->post(request, body);
    else
        reply = m_network->sendCustomRequest(request, method, body);
    m_replies.insert(reply);

    auto *timer = new QTimer(reply);
    timer->setSingleShot(true);
    timer->setInterval(kRequestTimeoutMs);
    connect(timer, &QTimer::timeout, reply, [reply] {
        reply->setProperty("catalogTimedOut", true);
        reply->abort();
    });
    timer->start();

    connect(reply, &QNetworkReply::finished, this,
            [this, operation, generation, request, method, body, callback = std::move(callback), attempt, reply, timer]() mutable {
        timer->stop();
        m_replies.remove(reply);
        const bool timedOut = reply->property("catalogTimedOut").toBool();
        NetworkResult result;
        result.status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        result.body = reply->isOpen() ? reply->readAll() : QByteArray{};
        if (timedOut)
            result.error = QStringLiteral("Request timed out");
        else if (reply->error() != QNetworkReply::NoError && result.status == 0)
            result.error = reply->errorString();
        reply->deleteLater();

        if (generation != m_generation)
            return;
        if (attempt < kMaxRetries && (!result.error.isEmpty() || retryableStatus(result.status))) {
            QTimer::singleShot(300 * (1 << attempt), this,
                               [this, operation, generation, request, method, body,
                                callback = std::move(callback), attempt]() mutable {
                sendRequest(operation, generation, request, method, body,
                            std::move(callback), attempt + 1);
            });
            return;
        }
        callback(std::move(result));
    });
}

QNetworkRequest CatalogEngine::graphqlRequest(const QUrl &url, const QString &token) const
{
    QNetworkRequest request(url);
    request.setRawHeader("Accept", "application/json, text/plain, */*");
    request.setRawHeader("Content-Type", "application/json");
    request.setRawHeader("Origin", "https://play.geforcenow.com");
    request.setRawHeader("Referer", "https://play.geforcenow.com/");
    request.setRawHeader("nv-client-id", kClientId);
    request.setRawHeader("nv-client-type", "NATIVE");
    request.setRawHeader("nv-client-version", kClientVersion);
    request.setRawHeader("nv-client-streamer", "NVIDIA-CLASSIC");
    request.setRawHeader("nv-browser-type", "CHROME");
    request.setRawHeader("nv-device-os", "WINDOWS");
    request.setRawHeader("nv-device-type", "DESKTOP");
    request.setRawHeader("nv-device-make", "NVIDIA");
    request.setRawHeader("nv-device-model", "GFN-PC");
    request.setRawHeader("User-Agent", kUserAgent);
    if (!token.isEmpty())
        request.setRawHeader("Authorization", QByteArrayLiteral("GFNJWT ") + token.toUtf8());
    return request;
}

QNetworkRequest CatalogEngine::lcarsRequest(const QUrl &url, const QString &token, bool browserClient) const
{
    QNetworkRequest request = graphqlRequest(url, token);
    request.setRawHeader("Content-Type", "application/graphql");
    request.setRawHeader("nv-client-type", browserClient ? "BROWSER" : "NATIVE");
    request.setRawHeader("nv-client-streamer", browserClient ? "WEBRTC" : "NVIDIA-CLASSIC");
    return request;
}

void CatalogEngine::getPersistedQuery(const QString &operation,
                                      quint64 generation,
                                      const QUrl &endpoint,
                                      const QString &requestType,
                                      const QString &hash,
                                      const QJsonObject &variables,
                                      const QString &fallbackQuery,
                                      const std::function<void(NetworkResult)> &callback)
{
    const QString huId = QString::number(QDateTime::currentMSecsSinceEpoch(), 16)
        + QString::number(generation, 16);
    const QUrl url = GfnCatalog::buildPersistedQueryUrl(endpoint, requestType, hash, variables, huId);
    sendRequest(operation, generation, lcarsRequest(url, GfnCatalog::selectJwt(m_idToken, m_accessToken)),
                QByteArrayLiteral("GET"), {},
                [this, operation, generation, url, fallbackQuery, callback](NetworkResult result) {
        if (result.status != 400 || fallbackQuery.isEmpty()) {
            callback(std::move(result));
            return;
        }
        QUrl fallbackUrl(url);
        QUrlQuery query(fallbackUrl);
        query.addQueryItem(QStringLiteral("query"), fallbackQuery);
        fallbackUrl.setQuery(query);
        sendRequest(operation, generation,
                    lcarsRequest(fallbackUrl, GfnCatalog::selectJwt(m_idToken, m_accessToken)),
                    QByteArrayLiteral("GET"), {}, callback);
    });
}

QJsonObject CatalogEngine::parseJsonObject(const NetworkResult &result,
                                           const QString &context,
                                           QString *error) const
{
    error->clear();
    if (!result.error.isEmpty()) {
        *error = context + QStringLiteral(": ") + result.error;
        return {};
    }
    if (result.status < 200 || result.status >= 300) {
        *error = QStringLiteral("%1 (HTTP %2): %3")
                     .arg(context).arg(result.status)
                     .arg(QString::fromUtf8(result.body.left(300)));
        return {};
    }
    QJsonParseError parseError;
    const auto document = QJsonDocument::fromJson(result.body, &parseError);
    if (parseError.error != QJsonParseError::NoError || !document.isObject()) {
        *error = context + QStringLiteral(": invalid JSON response");
        return {};
    }
    return document.object();
}

QString CatalogEngine::graphQlError(const QJsonObject &payload, const QString &context) const
{
    QStringList errors;
    for (const auto &value : payload.value(QStringLiteral("errors")).toArray()) {
        const QString message = value.toObject().value(QStringLiteral("message")).toString();
        errors.append(message.isEmpty() ? QStringLiteral("Unknown GraphQL error") : message);
    }
    return errors.isEmpty() ? QString() : context + QStringLiteral(": ") + errors.join(QStringLiteral(", "));
}

void CatalogEngine::withVpc(quint64 generation, const std::function<void(const QString &)> &callback)
{
    if (generation != m_generation)
        return;
    if (!m_vpcId.isEmpty()) {
        callback(m_vpcId);
        return;
    }
    loadServerInfo(generation, [this, generation, callback](bool ok) {
        if (generation == m_generation)
            callback(ok ? m_vpcId : QString());
    });
}

void CatalogEngine::refreshAll()
{
    const quint64 generation = beginGeneration();
    loadConnectedAccounts(generation);
    loadServerInfo(generation, [this, generation](bool ok) {
        if (generation != m_generation)
            return;
        if (!ok) {
            for (const QString &operation : {QStringLiteral("subscription"), QStringLiteral("library"),
                                             QStringLiteral("catalog"), QStringLiteral("panels")}) {
                startOperation(operation, false);
                finishOperation(operation, QStringLiteral("A VPC ID is required and serverInfo failed"));
            }
            return;
        }
        loadSubscription(generation);
        loadLibrary(generation);
        loadCatalog(generation, {}, QStringLiteral("relevance"), {}, 120);
        loadPanels(generation);
    });
}

void CatalogEngine::refreshServerInfo() { loadServerInfo(beginGeneration()); }

void CatalogEngine::measureTcpLatency(const QUrl &url, const std::function<void(int)> &completion)
{
    if (!url.isValid() || url.host().isEmpty()) {
        completion(-1);
        return;
    }

    auto *socket = new QTcpSocket(this);
    auto *timeout = new QTimer(socket);
    timeout->setSingleShot(true);
    auto elapsed = std::make_shared<QElapsedTimer>();
    auto finished = std::make_shared<bool>(false);
    const auto finish = [socket, timeout, elapsed, finished, completion](int latency) {
        if (*finished)
            return;
        *finished = true;
        timeout->stop();
        socket->abort();
        socket->deleteLater();
        completion(latency);
    };
    connect(socket, &QTcpSocket::connected, socket, [elapsed, finish] {
        finish(qMax(1, static_cast<int>(elapsed->elapsed())));
    });
    connect(socket, &QTcpSocket::errorOccurred, socket,
            [finish](QAbstractSocket::SocketError) { finish(-1); });
    connect(timeout, &QTimer::timeout, socket, [finish] { finish(-1); });
    elapsed->start();
    timeout->start(3000);
    socket->connectToHost(url.host(), url.port(url.scheme() == QStringLiteral("https") ? 443 : 80));
}

void CatalogEngine::probeRegions()
{
    const quint64 generation = ++m_probeGeneration;
    m_regionPings.clear();
    m_probingRegions = !m_regions.isEmpty();
    emit regionProbeChanged();
    if (m_regions.isEmpty())
        return;

    auto remaining = std::make_shared<int>(m_regions.size());
    for (const QVariant &value : std::as_const(m_regions)) {
        const QVariantMap region = value.toMap();
        const QString regionUrl = region.value(QStringLiteral("url")).toString();
        measureTcpLatency(QUrl(regionUrl), [this, generation, remaining, regionUrl](int latency) {
            if (generation != m_probeGeneration)
                return;
            m_regionPings.insert(regionUrl, latency);
            --*remaining;
            if (*remaining == 0)
                m_probingRegions = false;
            emit regionProbeChanged();
        });
    }
}

void CatalogEngine::testConnection(const QString &regionUrl)
{
    QString target = regionUrl.trimmed();
    if (target.isEmpty() && !m_regions.isEmpty())
        target = m_regions.first().toMap().value(QStringLiteral("url")).toString();
    if (target.isEmpty()) {
        m_networkTest = {{QStringLiteral("status"), QStringLiteral("failed")},
                         {QStringLiteral("message"), QStringLiteral("No GeForce NOW region is available")}};
        emit networkTestChanged();
        return;
    }

    const quint64 generation = ++m_testGeneration;
    m_networkTest = {{QStringLiteral("status"), QStringLiteral("testing")},
                     {QStringLiteral("testedUrl"), target}};
    emit networkTestChanged();

    auto samples = std::make_shared<QList<int>>();
    auto attempts = std::make_shared<int>(0);
    auto next = std::make_shared<std::function<void()>>();
    *next = [this, generation, target, samples, attempts, next] {
        if (generation != m_testGeneration) {
            *next = {};
            return;
        }
        if (*attempts >= 4) {
            const int successful = samples->size();
            int latency = -1;
            int jitter = 0;
            if (successful > 0) {
                int total = 0;
                for (int sample : std::as_const(*samples)) total += sample;
                latency = qRound(static_cast<double>(total) / successful);
                if (successful > 1) {
                    int variation = 0;
                    for (int index = 1; index < successful; ++index)
                        variation += qAbs(samples->at(index) - samples->at(index - 1));
                    jitter = qRound(static_cast<double>(variation) / (successful - 1));
                }
            }
            const int loss = qRound((4 - successful) * 25.0);
            const bool passed = successful >= 3 && latency >= 0 && latency < 120 && jitter < 40;
            m_networkTest = {{QStringLiteral("status"), passed ? QStringLiteral("pass") : QStringLiteral("check")},
                             {QStringLiteral("latencyMs"), latency},
                             {QStringLiteral("jitterMs"), jitter},
                             {QStringLiteral("packetLoss"), loss},
                             {QStringLiteral("samples"), successful},
                             {QStringLiteral("testedUrl"), target}};
            emit networkTestChanged();
            *next = {};
            return;
        }
        ++*attempts;
        measureTcpLatency(QUrl(target), [this, generation, samples, next](int latency) {
            if (generation != m_testGeneration)
                return;
            if (latency >= 0)
                samples->append(latency);
            QTimer::singleShot(80, this, [next] { if (*next) (*next)(); });
        });
    };
    (*next)();
}

void CatalogEngine::refreshSubscription() { loadSubscription(beginGeneration()); }
void CatalogEngine::refreshLibrary() { loadLibrary(beginGeneration()); }
void CatalogEngine::browseCatalog(const QString &searchQuery, const QString &sortId,
                                  const QStringList &filterIds, int fetchCount)
{
    loadCatalog(beginGeneration(), searchQuery, sortId, filterIds, fetchCount);
}
void CatalogEngine::refreshPanels() { loadPanels(beginGeneration()); }
void CatalogEngine::refreshConnectedAccounts() { loadConnectedAccounts(beginGeneration()); }

void CatalogEngine::loadServerInfo(quint64 generation, Completion completion)
{
    const QString operation = QStringLiteral("serverInfo");
    startOperation(operation, !m_regions.isEmpty() || !m_vpcId.isEmpty());
    if (!credentialsReady(operation, generation)) {
        if (completion) completion(false);
        return;
    }
    const QUrl url = m_streamingBaseUrl.resolved(QUrl(QStringLiteral("v2/serverInfo")));
    sendRequest(operation, generation,
                lcarsRequest(url, GfnCatalog::selectJwt(m_idToken, m_accessToken), true),
                QByteArrayLiteral("GET"), {},
                [this, operation, generation, completion](NetworkResult result) {
        if (generation != m_generation)
            return;
        QString error;
        const auto payload = parseJsonObject(result, QStringLiteral("GFN serverInfo failed"), &error);
        if (error.isEmpty()) {
            const auto parsed = GfnCatalog::parseServerInfo(payload);
            const QString vpc = parsed.value(QStringLiteral("vpcId")).toString();
            if (vpc.isEmpty())
                error = QStringLiteral("GFN serverInfo did not include a VPC ID");
            else {
                m_vpcId = vpc;
                m_regions = parsed.value(QStringLiteral("regions")).toList();
                ++m_probeGeneration;
                m_regionPings.clear();
                emit serverInfoChanged();
                emit regionProbeChanged();
            }
        }
        finishOperation(operation, error);
        if (completion) completion(error.isEmpty());
    });
}

void CatalogEngine::loadSubscription(quint64 generation)
{
    const QString operation = QStringLiteral("subscription");
    startOperation(operation, !m_subscription.isEmpty());
    if (!credentialsReady(operation, generation))
        return;
    if (m_userId.isEmpty()) {
        finishOperation(operation, QStringLiteral("Subscription data requires a user ID"));
        return;
    }
    withVpc(generation, [this, operation, generation](const QString &vpc) {
        if (vpc.isEmpty()) {
            finishOperation(operation, QStringLiteral("Subscription data requires a VPC ID"));
            return;
        }
        QUrl url(QString::fromLatin1(kMesUrl));
        QUrlQuery query;
        query.addQueryItem(QStringLiteral("serviceName"), QStringLiteral("gfn_pc"));
        query.addQueryItem(QStringLiteral("languageCode"), m_locale);
        query.addQueryItem(QStringLiteral("vpcId"), vpc);
        query.addQueryItem(QStringLiteral("userId"), m_userId);
        url.setQuery(query);
        sendRequest(operation, generation,
                    lcarsRequest(url, GfnCatalog::selectJwt(m_idToken, m_accessToken)),
                    QByteArrayLiteral("GET"), {},
                    [this, operation, generation, vpc](NetworkResult result) {
            if (generation != m_generation)
                return;
            QString error;
            const auto payload = parseJsonObject(result, QStringLiteral("MES subscription failed"), &error);
            if (error.isEmpty()) {
                m_subscription = GfnCatalog::parseSubscription(payload, vpc);
                emit subscriptionChanged();
            }
            finishOperation(operation, error);
        });
    });
}

QVariantList CatalogEngine::gamesFromApps(const QJsonArray &apps) const
{
    QVariantList games;
    for (const auto &value : apps) {
        const QVariantMap game = GfnCatalog::parseGame(value.toObject());
        if (!game.value(QStringLiteral("id")).toString().isEmpty()
            && !game.value(QStringLiteral("title")).toString().isEmpty()
            && !game.value(QStringLiteral("variants")).toList().isEmpty())
            games.append(game);
    }
    return dedupeGames(games);
}

QVariantList CatalogEngine::dedupeGames(const QVariantList &games) const
{
    QVariantList result;
    QHash<QString, int> indexById;
    for (const auto &value : games) {
        QVariantMap incoming = value.toMap();
        const QString id = incoming.value(QStringLiteral("id")).toString();
        if (!indexById.contains(id)) {
            indexById.insert(id, result.size());
            result.append(incoming);
            continue;
        }
        QVariantMap existing = result.at(indexById.value(id)).toMap();
        QVariantList merged = existing.value(QStringLiteral("variants")).toList();
        QHash<QString, int> variantIndex;
        for (int index = 0; index < merged.size(); ++index)
            variantIndex.insert(merged.at(index).toMap().value(QStringLiteral("id")).toString(), index);
        for (const auto &variantValue : incoming.value(QStringLiteral("variants")).toList()) {
            const auto variant = variantValue.toMap();
            const QString variantId = variant.value(QStringLiteral("id")).toString();
            if (variantIndex.contains(variantId)) {
                QVariantMap prior = merged.at(variantIndex.value(variantId)).toMap();
                for (auto it = variant.cbegin(); it != variant.cend(); ++it) {
                    if (it.value().isValid() && !it.value().toString().isEmpty())
                        prior.insert(it.key(), it.value());
                }
                merged[variantIndex.value(variantId)] = prior;
            } else {
                variantIndex.insert(variantId, merged.size());
                merged.append(variant);
            }
        }
        existing.insert(QStringLiteral("variants"), merged);
        existing.insert(QStringLiteral("isInLibrary"),
                        existing.value(QStringLiteral("isInLibrary")).toBool()
                            || incoming.value(QStringLiteral("isInLibrary")).toBool());
        for (auto it = incoming.cbegin(); it != incoming.cend(); ++it) {
            if (!existing.contains(it.key()) || existing.value(it.key()).toString().isEmpty())
                existing.insert(it.key(), it.value());
        }
        result[indexById.value(id)] = existing;
    }
    return result;
}

void CatalogEngine::loadLibrary(quint64 generation)
{
    const QString operation = QStringLiteral("library");
    startOperation(operation, !m_library.isEmpty());
    if (!credentialsReady(operation, generation))
        return;
    withVpc(generation, [this, operation, generation](const QString &vpc) {
        if (vpc.isEmpty()) {
            finishOperation(operation, QStringLiteral("Library data requires a VPC ID"));
            return;
        }
        struct Paging { int page = 0; QString cursor; QVariantList games; };
        auto state = std::make_shared<Paging>();
        auto next = std::make_shared<std::function<void()>>();
        *next = [this, operation, generation, vpc, state, next] {
            if (generation != m_generation)
                return;
            QJsonObject variables{
                {QStringLiteral("vpcId"), vpc},
                {QStringLiteral("locale"), m_locale},
                {QStringLiteral("sortString"), QStringLiteral("variants.gfn.library.lastPlayedDate:DESC,computedValues.libraryAddedDate:DESC,sortName:ASC")},
                {QStringLiteral("fetchCount"), 200},
                {QStringLiteral("cursor"), state->cursor},
                {QStringLiteral("filters"), QJsonObject{{QStringLiteral("variants"), QJsonObject{{QStringLiteral("gfn"), QJsonObject{{QStringLiteral("library"), QJsonObject{{QStringLiteral("status"), QJsonObject{{QStringLiteral("notEquals"), QStringLiteral("NOT_OWNED")}}}}}}}}}}},
            };
            const QByteArray body = QJsonDocument(QJsonObject{{QStringLiteral("query"), kLibraryQuery},
                                                               {QStringLiteral("variables"), variables}})
                                        .toJson(QJsonDocument::Compact);
            sendRequest(operation, generation,
                        graphqlRequest(QUrl(QString::fromLatin1(kGamesGraphqlUrl)), GfnCatalog::selectJwt(m_idToken, m_accessToken)),
                        QByteArrayLiteral("POST"), body,
                        [this, operation, generation, state, next](NetworkResult result) {
                if (generation != m_generation)
                    return;
                QString error;
                const auto payload = parseJsonObject(result, QStringLiteral("GFN library query failed"), &error);
                if (error.isEmpty())
                    error = graphQlError(payload, QStringLiteral("GFN library query failed"));
                const auto apps = payload.value(QStringLiteral("data")).toObject()
                                      .value(QStringLiteral("apps")).toObject();
                if (error.isEmpty())
                    state->games.append(gamesFromApps(apps.value(QStringLiteral("items")).toArray()));
                const auto pageInfo = apps.value(QStringLiteral("pageInfo")).toObject();
                const bool hasNext = pageInfo.value(QStringLiteral("hasNextPage")).toBool();
                const QString cursor = pageInfo.value(QStringLiteral("endCursor")).toString();
                if (error.isEmpty() && hasNext && cursor.isEmpty())
                    error = QStringLiteral("GFN library pagination returned hasNextPage without endCursor");
                if (!error.isEmpty()) {
                    finishOperation(operation, error);
                    return;
                }
                ++state->page;
                if (hasNext && state->page < kMaxLibraryPages) {
                    state->cursor = cursor;
                    (*next)();
                    return;
                }
                if (hasNext) {
                    finishOperation(operation, QStringLiteral("GFN library pagination exceeded 25 pages"));
                    return;
                }
                m_library = dedupeGames(state->games);
                emit libraryChanged();
                finishOperation(operation);
            });
        };
        (*next)();
    });
}

void CatalogEngine::loadCatalog(quint64 generation, const QString &searchQuery,
                                const QString &sortId, const QStringList &filterIds,
                                int fetchCount)
{
    const QString operation = QStringLiteral("catalog");
    startOperation(operation, !m_catalog.isEmpty());
    if (!credentialsReady(operation, generation))
        return;
    const QString search = searchQuery.trimmed();
    const int count = std::clamp(fetchCount, 24, 200);
    withVpc(generation, [this, operation, generation, search, sortId, filterIds, count](const QString &vpc) {
        if (vpc.isEmpty()) {
            finishOperation(operation, QStringLiteral("Catalog data requires a VPC ID"));
            return;
        }
        getPersistedQuery(operation, generation, QUrl(QString::fromLatin1(kGamesGraphqlUrl)),
                          QStringLiteral("filterGroupAndSortOrderDefinitions"), QString::fromLatin1(kDefinitionsHash),
                          QJsonObject{{QStringLiteral("locale"), m_locale}}, kDefinitionsQuery,
                          [this, operation, generation, search, sortId, filterIds, count, vpc](NetworkResult result) {
            if (generation != m_generation)
                return;
            QString error;
            const auto payload = parseJsonObject(result, QStringLiteral("GFN catalog definitions failed"), &error);
            if (error.isEmpty())
                error = graphQlError(payload, QStringLiteral("GFN catalog definitions failed"));
            if (!error.isEmpty()) {
                finishOperation(operation, error);
                return;
            }

            QVariantList filterGroups;
            QVariantList sortOptions;
            QHash<QString, QJsonObject> filterPayloads;
            const auto data = payload.value(QStringLiteral("data")).toObject();
            for (const auto &groupValue : data.value(QStringLiteral("filterGroupDefinitions")).toArray()) {
                const auto group = groupValue.toObject();
                QVariantList options;
                for (const auto &entryValue : group.value(QStringLiteral("filters")).toArray()) {
                    const auto entry = entryValue.toObject();
                    const auto rawFilters = entry.value(QStringLiteral("filters")).toArray();
                    const QString raw = rawFilters.isEmpty() ? QString() : rawFilters.at(0).toString();
                    QJsonParseError parseError;
                    const auto document = QJsonDocument::fromJson(raw.toUtf8(), &parseError);
                    if (parseError.error != QJsonParseError::NoError || !document.isObject())
                        continue;
                    const QString id = entry.value(QStringLiteral("id")).toString();
                    filterPayloads.insert(id, document.object());
                    options.append(QVariantMap{
                        {QStringLiteral("id"), id}, {QStringLiteral("rawId"), id},
                        {QStringLiteral("label"), entry.value(QStringLiteral("label")).toString()},
                        {QStringLiteral("groupId"), group.value(QStringLiteral("id")).toString()},
                        {QStringLiteral("groupLabel"), group.value(QStringLiteral("label")).toString()},
                    });
                }
                if (!options.isEmpty())
                    filterGroups.append(QVariantMap{{QStringLiteral("id"), group.value(QStringLiteral("id")).toString()},
                                                     {QStringLiteral("label"), group.value(QStringLiteral("label")).toString()},
                                                     {QStringLiteral("options"), options}});
            }
            for (const auto &sortValue : data.value(QStringLiteral("sortOrderDefinitions")).toArray()) {
                const auto sort = sortValue.toObject();
                sortOptions.append(QVariantMap{{QStringLiteral("id"), sort.value(QStringLiteral("id")).toString()},
                                                {QStringLiteral("label"), sort.value(QStringLiteral("label")).toString()},
                                                {QStringLiteral("orderBy"), sort.value(QStringLiteral("orderBy")).toString()}});
            }
            QVariantMap selectedSort;
            for (const auto &value : sortOptions) {
                const auto candidate = value.toMap();
                if (candidate.value(QStringLiteral("id")).toString() == sortId) {
                    selectedSort = candidate;
                    break;
                }
            }
            if (selectedSort.isEmpty()) {
                for (const auto &value : sortOptions) {
                    const auto candidate = value.toMap();
                    if (candidate.value(QStringLiteral("id")).toString() == QStringLiteral("relevance")) {
                        selectedSort = candidate;
                        break;
                    }
                }
            }
            if (selectedSort.isEmpty() && !sortOptions.isEmpty())
                selectedSort = sortOptions.first().toMap();
            if (selectedSort.isEmpty())
                selectedSort = {{QStringLiteral("id"), QStringLiteral("relevance")},
                                {QStringLiteral("label"), QStringLiteral("Relevance")},
                                {QStringLiteral("orderBy"), QStringLiteral("itemMetadata.relevance:DESC,sortName:ASC")}};

            QJsonObject filters;
            QStringList selectedFilters;
            for (const auto &id : filterIds) {
                if (!filterPayloads.contains(id))
                    continue;
                selectedFilters.append(id);
                const auto source = filterPayloads.value(id);
                for (auto it = source.begin(); it != source.end(); ++it)
                    filters.insert(it.key(), it.value());
            }

            struct Paging {
                int page = 0;
                QString cursor;
                QVariantList games;
                int numberReturned = 0;
                int numberSupported = 0;
                int totalCount = 0;
                bool hasNext = false;
                QString endCursor;
            };
            auto state = std::make_shared<Paging>();
            auto next = std::make_shared<std::function<void()>>();
            *next = [this, operation, generation, search, count, vpc, filters, filterGroups,
                     sortOptions, selectedSort, selectedFilters, state, next] {
                QJsonObject variables{
                    {QStringLiteral("vpcId"), vpc}, {QStringLiteral("locale"), m_locale},
                    {QStringLiteral("sortString"), selectedSort.value(QStringLiteral("orderBy")).toString()},
                    {QStringLiteral("fetchCount"), count}, {QStringLiteral("cursor"), state->cursor},
                    {QStringLiteral("filters"), filters},
                };
                if (!search.isEmpty())
                    variables.insert(QStringLiteral("searchString"), search);
                getPersistedQuery(operation, generation, QUrl(QString::fromLatin1(kGamesGraphqlUrl)),
                                  QStringLiteral("apps"), QString::fromLatin1(search.isEmpty() ? kAppsWithoutSearchHash : kAppsWithSearchHash),
                                  variables, search.isEmpty() ? kCatalogQuery : kCatalogSearchQuery,
                                  [this, operation, generation, search, filterGroups, sortOptions, selectedSort,
                                   selectedFilters, state, next](NetworkResult result) {
                    if (generation != m_generation)
                        return;
                    QString error;
                    const auto payload = parseJsonObject(result, QStringLiteral("GFN catalog query failed"), &error);
                    if (error.isEmpty())
                        error = graphQlError(payload, QStringLiteral("GFN catalog query failed"));
                    const auto apps = payload.value(QStringLiteral("data")).toObject().value(QStringLiteral("apps")).toObject();
                    if (error.isEmpty()) {
                        const auto items = apps.value(QStringLiteral("items")).toArray();
                        state->games.append(gamesFromApps(items));
                        state->numberReturned += apps.value(QStringLiteral("numberReturned")).toInt(items.size());
                        state->numberSupported = apps.value(QStringLiteral("numberSupported")).toInt(state->numberSupported);
                        const auto pageInfo = apps.value(QStringLiteral("pageInfo")).toObject();
                        state->hasNext = pageInfo.value(QStringLiteral("hasNextPage")).toBool();
                        state->endCursor = pageInfo.value(QStringLiteral("endCursor")).toString();
                        state->totalCount = pageInfo.value(QStringLiteral("totalCount")).toInt(state->totalCount);
                    }
                    if (!error.isEmpty()) {
                        finishOperation(operation, error);
                        return;
                    }
                    ++state->page;
                    if (state->hasNext && !state->endCursor.isEmpty() && state->page < kMaxCatalogPages) {
                        state->cursor = state->endCursor;
                        (*next)();
                        return;
                    }
                    const QVariantList games = dedupeGames(state->games);
                    m_catalog = {
                        {QStringLiteral("games"), games},
                        {QStringLiteral("numberReturned"), state->numberReturned},
                        {QStringLiteral("numberSupported"), std::max(state->numberSupported, int(games.size()))},
                        {QStringLiteral("totalCount"), std::max(state->totalCount, int(games.size()))},
                        {QStringLiteral("hasNextPage"), state->hasNext},
                        {QStringLiteral("endCursor"), state->endCursor},
                        {QStringLiteral("searchQuery"), search},
                        {QStringLiteral("selectedSortId"), selectedSort.value(QStringLiteral("id"))},
                        {QStringLiteral("selectedFilterIds"), selectedFilters},
                        {QStringLiteral("filterGroups"), filterGroups},
                        {QStringLiteral("sortOptions"), sortOptions},
                    };
                    emit catalogChanged();
                    finishOperation(operation);
                });
            };
            (*next)();
        });
    });
}

void CatalogEngine::loadPanels(quint64 generation)
{
    const QString operation = QStringLiteral("panels");
    startOperation(operation, !m_panels.isEmpty());
    if (!credentialsReady(operation, generation))
        return;
    withVpc(generation, [this, operation, generation](const QString &vpc) {
        if (vpc.isEmpty()) {
            finishOperation(operation, QStringLiteral("Panel data requires a VPC ID"));
            return;
        }
        getPersistedQuery(operation, generation, QUrl(QString::fromLatin1(kGamesGraphqlUrl)),
                          QStringLiteral("panels/MainV2"), QString::fromLatin1(kMainPanelHash),
                          QJsonObject{{QStringLiteral("vpcId"), vpc},
                                      {QStringLiteral("locale"), m_locale},
                                      {QStringLiteral("panelNames"), QJsonArray{QStringLiteral("MAIN")}}},
                          kPanelQuery,
                          [this, operation, generation](NetworkResult result) {
            if (generation != m_generation)
                return;
            QString error;
            const auto payload = parseJsonObject(result, QStringLiteral("GFN panels query failed"), &error);
            if (error.isEmpty())
                error = graphQlError(payload, QStringLiteral("GFN panels query failed"));
            if (error.isEmpty()) {
                QVariantList panels;
                for (const auto &panelValue : payload.value(QStringLiteral("data")).toObject()
                                                  .value(QStringLiteral("panels")).toArray()) {
                    const auto panel = panelValue.toObject();
                    QVariantList sections;
                    for (const auto &sectionValue : panel.value(QStringLiteral("sections")).toArray()) {
                        const auto section = sectionValue.toObject();
                        QVariantList games;
                        for (const auto &itemValue : section.value(QStringLiteral("items")).toArray()) {
                            const auto item = itemValue.toObject();
                            if (item.value(QStringLiteral("__typename")) != QStringLiteral("GameItem"))
                                continue;
                            const auto game = GfnCatalog::parseGame(item.value(QStringLiteral("app")).toObject());
                            if (!game.value(QStringLiteral("id")).toString().isEmpty()
                                && !game.value(QStringLiteral("variants")).toList().isEmpty())
                                games.append(game);
                        }
                        if (!games.isEmpty())
                            sections.append(QVariantMap{{QStringLiteral("id"), section.value(QStringLiteral("id")).toString(
                                                                         section.value(QStringLiteral("title")).toString())},
                                                        {QStringLiteral("title"), section.value(QStringLiteral("title")).toString()},
                                                        {QStringLiteral("games"), dedupeGames(games)}});
                    }
                    if (!sections.isEmpty())
                        panels.append(QVariantMap{{QStringLiteral("id"), panel.value(QStringLiteral("id")).toString(
                                                                 panel.value(QStringLiteral("name")).toString())},
                                                  {QStringLiteral("title"), panel.value(QStringLiteral("name")).toString()},
                                                  {QStringLiteral("sections"), sections}});
                }
                m_panels = panels;
                emit panelsChanged();
            }
            finishOperation(operation, error);
        });
    });
}

void CatalogEngine::loadConnectedAccounts(quint64 generation)
{
    const QString operation = QStringLiteral("accounts");
    startOperation(operation, !m_connectedAccounts.isEmpty());
    if (!credentialsReady(operation, generation))
        return;
    if (m_userId.isEmpty()) {
        finishOperation(operation, QStringLiteral("Connected account data requires a user ID"));
        return;
    }
    const QJsonObject staticVariables{{QStringLiteral("locale"), m_locale},
                                      {QStringLiteral("stringsKey"), QJsonArray{QString()}}};
    const QUrl staticUrl = GfnCatalog::buildPersistedQueryUrl(QUrl(QString::fromLatin1(kAppsGraphqlUrl)),
                                                              QStringLiteral("staticAppData"),
                                                              QString::fromLatin1(kStaticAccountHash), staticVariables);
    sendRequest(operation, generation, lcarsRequest(staticUrl, QString()), QByteArrayLiteral("GET"), {},
                [this, operation, generation](NetworkResult staticResult) {
        if (generation != m_generation)
            return;
        QString error;
        const auto staticPayload = parseJsonObject(staticResult, QStringLiteral("Account provider definitions failed"), &error);
        if (error.isEmpty())
            error = graphQlError(staticPayload, QStringLiteral("Account provider definitions failed"));
        if (!error.isEmpty()) {
            finishOperation(operation, error);
            return;
        }

        const QString stableHuId = QString::fromLatin1(QCryptographicHash::hash(m_userId.toUtf8(), QCryptographicHash::Sha256).toHex());
        const QUrl accountUrl = GfnCatalog::buildPersistedQueryUrl(QUrl(QString::fromLatin1(kAppsGraphqlUrl)),
                                                                   QStringLiteral("userAccount"),
                                                                   QString::fromLatin1(kUserAccountHash), {}, stableHuId);
        sendRequest(operation, generation,
                    lcarsRequest(accountUrl, GfnCatalog::selectJwt(m_idToken, m_accessToken)),
                    QByteArrayLiteral("GET"), {},
                    [this, operation, generation, staticPayload](NetworkResult accountResult) {
            if (generation != m_generation)
                return;
            QString error;
            const auto accountPayload = parseJsonObject(accountResult, QStringLiteral("Connected accounts failed"), &error);
            if (error.isEmpty())
                error = graphQlError(accountPayload, QStringLiteral("Connected accounts failed"));
            if (error.isEmpty()) {
                const auto definitions = staticPayload.value(QStringLiteral("data")).toObject()
                                             .value(QStringLiteral("appStoreDefinitions")).toArray();
                const auto stores = accountPayload.value(QStringLiteral("data")).toObject()
                                        .value(QStringLiteral("userAccount")).toObject()
                                        .value(QStringLiteral("storesData")).toArray();
                m_connectedAccounts = GfnCatalog::parseAccounts(definitions, stores,
                                                                 QDateTime::currentMSecsSinceEpoch());
                emit connectedAccountsChanged();
            }
            finishOperation(operation, error);
        });
    });
}
