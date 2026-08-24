#include "cloudmatchprotocol.h"

#include <QDateTime>
#include <QHostAddress>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonValue>
#include <QRegularExpression>
#include <QUrl>
#include <QUuid>

#include <algorithm>

namespace Gfn::CloudMatch {
namespace {
constexpr auto PlayOrigin = "https://play.geforcenow.com";
constexpr auto PlayReferer = "https://play.geforcenow.com/";
constexpr auto ClientVersion = "2.0.80.173";
constexpr auto UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                           "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 "
                           "NVIDIACEFClient/HEAD/debb5919f6 GFN-PC/2.0.80.173";

QString firstString(const QJsonValue &value)
{
    if (value.isString()) {
        return value.toString().trimmed();
    }
    if (value.isArray()) {
        for (const auto &entry : value.toArray()) {
            const auto candidate = entry.toString().trimmed();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
    }
    return {};
}

int positiveInt(const QJsonValue &value)
{
    if (value.isDouble()) {
        const auto number = value.toInt();
        return number > 0 ? number : 0;
    }
    if (value.isString()) {
        bool ok = false;
        const auto number = value.toString().toInt(&ok);
        return ok && number > 0 ? number : 0;
    }
    return 0;
}

int queuePosition(const QJsonObject &session)
{
    const QJsonValue candidates[] = {
        session.value(QStringLiteral("queuePosition")),
        session.value(QStringLiteral("seatSetupInfo")).toObject().value(QStringLiteral("queuePosition")),
        session.value(QStringLiteral("sessionProgress")).toObject().value(QStringLiteral("queuePosition")),
        session.value(QStringLiteral("progressInfo")).toObject().value(QStringLiteral("queuePosition")),
    };
    for (const auto &candidate : candidates) {
        if (const auto position = positiveInt(candidate); position > 0) {
            return position;
        }
    }
    return 0;
}

QString hostFromResourcePath(const QString &resourcePath)
{
    static const QStringList schemes = {
        QStringLiteral("rtsps://"),
        QStringLiteral("rtsp://"),
        QStringLiteral("wss://"),
        QStringLiteral("https://"),
    };
    for (const auto &scheme : schemes) {
        if (!resourcePath.startsWith(scheme, Qt::CaseInsensitive)) {
            continue;
        }
        auto urlText = resourcePath;
        if (scheme.startsWith(QStringLiteral("rtsp"))) {
            urlText.replace(0, scheme.size(), QStringLiteral("https://"));
        }
        const QUrl url(urlText);
        return url.isValid() ? url.host().trimmed() : QString{};
    }
    return {};
}

int portFromConnection(const QJsonObject &connection)
{
    if (const auto port = connection.value(QStringLiteral("port")).toInt(); port > 0) {
        return port;
    }
    auto path = connection.value(QStringLiteral("resourcePath")).toString();
    if (path.startsWith(QStringLiteral("rtsps://"), Qt::CaseInsensitive)) {
        path.replace(0, 8, QStringLiteral("https://"));
    } else if (path.startsWith(QStringLiteral("rtsp://"), Qt::CaseInsensitive)) {
        path.replace(0, 7, QStringLiteral("http://"));
    }
    return QUrl(path).port();
}

QString connectionHost(const QJsonObject &connection)
{
    const auto direct = firstString(connection.value(QStringLiteral("ip")));
    return direct.isEmpty()
               ? hostFromResourcePath(connection.value(QStringLiteral("resourcePath")).toString())
               : direct;
}

QString streamingServerIp(const QJsonObject &session)
{
    const auto connections = session.value(QStringLiteral("connectionInfo")).toArray();
    for (const auto &entry : connections) {
        const auto connection = entry.toObject();
        if (connection.value(QStringLiteral("usage")).toInt() == 14) {
            const auto host = connectionHost(connection);
            if (!host.isEmpty()) {
                return host;
            }
        }
    }
    return firstString(session.value(QStringLiteral("sessionControlInfo"))
                           .toObject()
                           .value(QStringLiteral("ip")));
}

QVariantMap streamingFeatures(const QJsonObject &features)
{
    QVariantMap result;
    const QStringList booleanKeys = {
        QStringLiteral("reflex"),
        QStringLiteral("cloudGsync"),
        QStringLiteral("enabledL4S"),
        QStringLiteral("trueHdr"),
    };
    for (const auto &key : booleanKeys) {
        if (features.value(key).isBool()) {
            result.insert(key, features.value(key).toBool());
        }
    }
    for (const auto &key : {QStringLiteral("bitDepth"), QStringLiteral("chromaFormat")}) {
        if (features.value(key).isDouble()) {
            result.insert(key, features.value(key).toInt());
        }
    }
    return result;
}

QVariantMap negotiatedProfile(const QJsonObject &session)
{
    QVariantMap result;
    const auto requestData = session.value(QStringLiteral("sessionRequestData")).toObject();
    const auto monitors = requestData.value(QStringLiteral("clientRequestMonitorSettings")).toArray();
    if (!monitors.isEmpty()) {
        const auto monitor = monitors.first().toObject();
        const auto width = monitor.value(QStringLiteral("widthInPixels")).toInt();
        const auto height = monitor.value(QStringLiteral("heightInPixels")).toInt();
        if (width > 0 && height > 0) {
            result.insert(QStringLiteral("resolution"), QStringLiteral("%1x%2").arg(width).arg(height));
        }
        if (const auto fps = monitor.value(QStringLiteral("framesPerSecond")).toInt(); fps > 0) {
            result.insert(QStringLiteral("fps"), fps);
        }
    }

    auto features = requestData.value(QStringLiteral("requestedStreamingFeatures")).toObject();
    const auto finalized = session.value(QStringLiteral("finalizedStreamingFeatures")).toObject();
    for (auto it = finalized.begin(); it != finalized.end(); ++it) {
        features.insert(it.key(), it.value());
    }
    if (features.value(QStringLiteral("bitDepth")).isDouble()
        && features.value(QStringLiteral("chromaFormat")).isDouble()) {
        const auto depth = features.value(QStringLiteral("bitDepth")).toInt() == 1 ? 10 : 8;
        const auto chroma = features.value(QStringLiteral("chromaFormat")).toInt() == 1
                                ? QStringLiteral("444")
                                : QStringLiteral("420");
        result.insert(QStringLiteral("colorQuality"), QStringLiteral("%1bit_%2").arg(depth).arg(chroma));
    }
    for (const auto &[wire, property] : {
             std::pair{QStringLiteral("enabledL4S"), QStringLiteral("enableL4S")},
             std::pair{QStringLiteral("cloudGsync"), QStringLiteral("enableCloudGsync")},
             std::pair{QStringLiteral("reflex"), QStringLiteral("enableReflex")},
         }) {
        if (features.value(wire).isBool()) {
            result.insert(property, features.value(wire).toBool());
        }
    }
    return result;
}

QVariantList iceServers(const QJsonObject &session)
{
    QVariantList result;
    const auto raw = session.value(QStringLiteral("iceServerConfiguration"))
                         .toObject()
                         .value(QStringLiteral("iceServers"))
                         .toArray();
    for (const auto &entry : raw) {
        const auto server = entry.toObject();
        QStringList urls;
        if (server.value(QStringLiteral("urls")).isArray()) {
            for (const auto &url : server.value(QStringLiteral("urls")).toArray()) {
                if (!url.toString().trimmed().isEmpty()) {
                    urls.append(url.toString().trimmed());
                }
            }
        } else if (!server.value(QStringLiteral("urls")).toString().trimmed().isEmpty()) {
            urls.append(server.value(QStringLiteral("urls")).toString().trimmed());
        }
        if (urls.isEmpty()) {
            continue;
        }
        QVariantMap normalized{{QStringLiteral("urls"), urls}};
        if (server.value(QStringLiteral("username")).isString()) {
            normalized.insert(QStringLiteral("username"), server.value(QStringLiteral("username")).toString());
        }
        if (server.value(QStringLiteral("credential")).isString()) {
            normalized.insert(QStringLiteral("credential"), server.value(QStringLiteral("credential")).toString());
        }
        result.append(normalized);
    }
    if (result.isEmpty()) {
        result = {
            QVariantMap{{QStringLiteral("urls"), QStringList{QStringLiteral("stun:s1.stun.gamestream.nvidia.com:19308")}}},
            QVariantMap{{QStringLiteral("urls"), QStringList{QStringLiteral("stun:stun.l.google.com:19302")}}},
            QVariantMap{{QStringLiteral("urls"), QStringList{QStringLiteral("stun:stun1.l.google.com:19302")}}},
        };
    }
    return result;
}

QVariantMap signalingPayload(const QJsonObject &session)
{
    const auto connections = session.value(QStringLiteral("connectionInfo")).toArray();
    const auto serverIp = streamingServerIp(session);
    QJsonObject signalingConnection;
    for (const auto &entry : connections) {
        const auto connection = entry.toObject();
        if (connection.value(QStringLiteral("usage")).toInt() == 14
            && !connectionHost(connection).isEmpty()) {
            signalingConnection = connection;
            break;
        }
    }
    if (signalingConnection.isEmpty()) {
        for (const auto &entry : connections) {
            if (!connectionHost(entry.toObject()).isEmpty()) {
                signalingConnection = entry.toObject();
                break;
            }
        }
    }

    const auto resourcePath = signalingConnection
                                  .value(QStringLiteral("resourcePath"))
                                  .toString(QStringLiteral("/nvst/"));
    QString signalingHost = serverIp;
    QString signalingUrl;
    if (resourcePath.startsWith(QStringLiteral("wss://"), Qt::CaseInsensitive)) {
        signalingUrl = resourcePath;
        signalingHost = QUrl(resourcePath).host();
    } else if (resourcePath.startsWith(QStringLiteral("rtsp"), Qt::CaseInsensitive)) {
        const auto resourceHost = hostFromResourcePath(resourcePath);
        if (!resourceHost.isEmpty()) {
            signalingHost = resourceHost;
        }
        signalingUrl = QStringLiteral("wss://%1/nvst/").arg(signalingHost);
    } else {
        const auto path = resourcePath.startsWith('/') ? resourcePath : QStringLiteral("/nvst/");
        signalingUrl = QStringLiteral("wss://%1:443%2").arg(serverIp, path);
    }

    QVariantMap media;
    auto findMedia = [&](int usage) {
        for (const auto &entry : connections) {
            const auto connection = entry.toObject();
            if (connection.value(QStringLiteral("usage")).toInt() != usage) {
                continue;
            }
            const auto host = connectionHost(connection);
            const auto port = portFromConnection(connection);
            if (!host.isEmpty() && port > 0) {
                media = {
                    {QStringLiteral("ip"), host},
                    {QStringLiteral("port"), port},
                    {QStringLiteral("usage"), usage},
                };
                return true;
            }
        }
        return false;
    };
    if (!findMedia(2) && !findMedia(17)) {
        QList<QJsonObject> alliance;
        for (const auto &entry : connections) {
            const auto connection = entry.toObject();
            if (connection.value(QStringLiteral("usage")).toInt() == 14) {
                alliance.append(connection);
            }
        }
        std::sort(alliance.begin(), alliance.end(), [](const auto &left, const auto &right) {
            return portFromConnection(left) > portFromConnection(right);
        });
        for (const auto &connection : alliance) {
            const auto host = connectionHost(connection).isEmpty() ? serverIp : connectionHost(connection);
            const auto port = portFromConnection(connection);
            if (!host.isEmpty() && port > 0) {
                media = {
                    {QStringLiteral("ip"), host},
                    {QStringLiteral("port"), port},
                    {QStringLiteral("usage"), 14},
                };
                break;
            }
        }
    }

    QStringList rtspsEndpoints;
    for (const auto &entry : connections) {
        const auto connection = entry.toObject();
        if (connection.value(QStringLiteral("usage")).toInt() != 14) {
            continue;
        }
        const auto path = connection.value(QStringLiteral("resourcePath")).toString();
        if (path.startsWith(QStringLiteral("rtsps://"), Qt::CaseInsensitive)) {
            rtspsEndpoints.append(path);
        }
    }
    rtspsEndpoints.removeDuplicates();

    QVariantMap result{
        {QStringLiteral("serverIp"), serverIp},
        {QStringLiteral("signalingServer"), signalingHost.contains(':')
                                                   ? signalingHost
                                                   : QStringLiteral("%1:443").arg(signalingHost)},
        {QStringLiteral("signalingUrl"), signalingUrl},
        {QStringLiteral("iceServers"), iceServers(session)},
        {QStringLiteral("connectionInfo"), connections.toVariantList()},
        {QStringLiteral("sessionControlInfo"), session.value(QStringLiteral("sessionControlInfo")).toObject().toVariantMap()},
    };
    if (!media.isEmpty()) {
        result.insert(QStringLiteral("mediaConnectionInfo"), media);
    }
    if (!rtspsEndpoints.isEmpty()) {
        result.insert(QStringLiteral("rtspsEndpoints"), rtspsEndpoints);
    }
    return result;
}

int codecWireValue(const QString &codec)
{
    if (codec.compare(QStringLiteral("AV1"), Qt::CaseInsensitive) == 0) {
        return 3;
    }
    if (codec.compare(QStringLiteral("H265"), Qt::CaseInsensitive) == 0
        || codec.compare(QStringLiteral("HEVC"), Qt::CaseInsensitive) == 0) {
        return 2;
    }
    return 1;
}

int resolvedCodecWireValue(const StreamSettings &settings)
{
    const auto preferred = codecWireValue(settings.codec);
    if (settings.supportedCodecs.isEmpty()) {
        return preferred;
    }
    QList<int> ladder;
    if (preferred == 3) {
        ladder = {3, 2, 1};
    } else if (preferred == 2) {
        ladder = {2, 1};
    } else {
        ladder = {1};
    }
    QList<int> supported;
    for (const auto &codec : settings.supportedCodecs) {
        supported.append(codecWireValue(codec));
    }
    for (const auto codec : ladder) {
        if (supported.contains(codec)) {
            return codec;
        }
    }
    return preferred;
}

int appLaunchModeWireValue(const QString &mode)
{
    if (mode.compare(QStringLiteral("gamepadFriendly"), Qt::CaseInsensitive) == 0) {
        return 2;
    }
    if (mode.compare(QStringLiteral("touchFriendly"), Qt::CaseInsensitive) == 0) {
        return 3;
    }
    return 1;
}

QJsonArray sessionMetadata(const StreamSettings &settings, bool includePhysicalResolution)
{
    QJsonArray metadata{
        QJsonObject{{QStringLiteral("key"), QStringLiteral("SubSessionId")},
                    {QStringLiteral("value"), QUuid::createUuid().toString(QUuid::WithoutBraces)}},
        QJsonObject{{QStringLiteral("key"), QStringLiteral("wssignaling")},
                    {QStringLiteral("value"), QStringLiteral("1")}},
        QJsonObject{{QStringLiteral("key"), QStringLiteral("GSStreamerType")},
                    {QStringLiteral("value"), QStringLiteral("WebRTC")}},
        QJsonObject{{QStringLiteral("key"), QStringLiteral("networkType")},
                    {QStringLiteral("value"), QStringLiteral("Unknown")}},
        QJsonObject{{QStringLiteral("key"), QStringLiteral("ClientImeSupport")},
                    {QStringLiteral("value"), QStringLiteral("0")}},
        QJsonObject{{QStringLiteral("key"), QStringLiteral("surroundAudioInfo")},
                    {QStringLiteral("value"), QStringLiteral("2")}},
    };
    if (includePhysicalResolution) {
        const auto resolution = QJsonDocument(QJsonObject{
                                                   {QStringLiteral("horizontalPixels"), settings.width},
                                                   {QStringLiteral("verticalPixels"), settings.height},
                                               })
                                    .toJson(QJsonDocument::Compact);
        metadata.insert(5,
                        QJsonObject{{QStringLiteral("key"), QStringLiteral("clientPhysicalResolution")},
                                    {QStringLiteral("value"), QString::fromUtf8(resolution)}});
    }
    return metadata;
}

QJsonObject commonSessionData(const QString &appId,
                              const QString &deviceId,
                              const StreamSettings &settings,
                              bool enablePersistingInGameSettings,
                              bool includePhysicalResolution)
{
    return {
        {QStringLiteral("audioMode"), 2},
        {QStringLiteral("remoteControllersBitmap"), 0},
        {QStringLiteral("sdrHdrMode"), 0},
        {QStringLiteral("networkTestSessionId"), QJsonValue::Null},
        {QStringLiteral("availableSupportedControllers"), QJsonArray{}},
        {QStringLiteral("clientVersion"), QStringLiteral("30.0")},
        {QStringLiteral("deviceHashId"), deviceId},
        {QStringLiteral("internalTitle"), QJsonValue::Null},
        {QStringLiteral("clientPlatformName"), QStringLiteral("windows")},
        {QStringLiteral("metaData"), sessionMetadata(settings, includePhysicalResolution)},
        {QStringLiteral("surroundAudioInfo"), 0},
        {QStringLiteral("clientTimezoneOffset"), QDateTime::currentDateTime().offsetFromUtc() * 1000},
        {QStringLiteral("clientIdentification"), QStringLiteral("GFN-PC")},
        {QStringLiteral("parentSessionId"), QJsonValue::Null},
        {QStringLiteral("appId"), appId},
        {QStringLiteral("streamerVersion"), 1},
        {QStringLiteral("appLaunchMode"), appLaunchModeWireValue(settings.appLaunchMode)},
        {QStringLiteral("sdkVersion"), QStringLiteral("1.0")},
        {QStringLiteral("enhancedStreamMode"), 1},
        {QStringLiteral("useOps"), true},
        {QStringLiteral("clientDisplayHdrCapabilities"), QJsonValue::Null},
        {QStringLiteral("accountLinked"), true},
        {QStringLiteral("partnerCustomData"), QString{}},
        {QStringLiteral("enablePersistingInGameSettings"), enablePersistingInGameSettings},
        {QStringLiteral("secureRTSPSupported"), false},
        {QStringLiteral("userAge"), 26},
    };
}

QString friendlyDescription(int httpStatus, int statusCode, const QString &description)
{
    const auto normalized = description.toUpper();
    if (normalized.contains(QStringLiteral("INSUFFICIENT_PLAYABILITY"))) {
        return QStringLiteral("Your GeForce NOW membership is not high enough to play this game.");
    }
    if (normalized.contains(QStringLiteral("SESSION_LIMIT"))) {
        return QStringLiteral("You have reached your maximum number of concurrent sessions.");
    }
    if (normalized.contains(QStringLiteral("MAINTENANCE"))) {
        return QStringLiteral("GeForce NOW is under maintenance. Try again later.");
    }
    if (normalized.contains(QStringLiteral("AUTH")) || normalized.contains(QStringLiteral("TOKEN"))
        || httpStatus == 401) {
        return QStringLiteral("Your GeForce NOW sign-in expired. Sign in again.");
    }
    if (normalized.contains(QStringLiteral("CAPACITY")) || normalized.contains(QStringLiteral("QUEUE"))) {
        return QStringLiteral("No gaming rig is currently available. Try again later.");
    }
    if (httpStatus == 403) {
        return QStringLiteral("GeForce NOW denied access to this session.");
    }
    if (httpStatus == 429) {
        return QStringLiteral("GeForce NOW is receiving too many requests. Wait a moment and retry.");
    }
    if (httpStatus >= 500 || statusCode == 3 || statusCode == 4) {
        return QStringLiteral("The GeForce NOW session service failed. Try again later.");
    }
    return description.isEmpty() ? QStringLiteral("The GeForce NOW session request failed.") : description;
}

QString normalizeBaseUrl(const QString &input, bool allowIpAddress, QString *error)
{
    const QUrl url(input.trimmed());
    auto host = url.host().toLower();
    if (host.endsWith('.')) {
        host.chop(1);
    }
    QHostAddress address;
    const auto isNvidiaHost = host == QStringLiteral("nvidiagrid.net")
                              || host.endsWith(QStringLiteral(".nvidiagrid.net"));
    const auto trustedHost = isNvidiaHost || (allowIpAddress && address.setAddress(host));
    const auto trusted = url.isValid() && url.scheme() == QStringLiteral("https") && trustedHost
                         && url.userName().isEmpty() && url.password().isEmpty()
                         && (url.port() == -1 || url.port() == 443)
                         && (url.path().isEmpty() || url.path() == QStringLiteral("/"))
                         && url.query().isEmpty() && url.fragment().isEmpty();
    if (!trusted) {
        if (error) {
            *error = allowIpAddress
                         ? QStringLiteral("Session endpoint must be an NVIDIA host or server IP over HTTPS")
                         : QStringLiteral("CloudMatch endpoint must be a clean NVIDIA HTTPS origin");
        }
        return {};
    }
    if (address.setAddress(host) && address.protocol() == QAbstractSocket::IPv6Protocol) {
        return QStringLiteral("https://[%1]").arg(host);
    }
    return QStringLiteral("https://%1").arg(host);
}
}

StreamSettings StreamSettings::fromVariantMap(const QVariantMap &value)
{
    StreamSettings result;
    auto resolution = value.value(QStringLiteral("resolution"), QStringLiteral("1920x1080")).toString();
    resolution.replace(QChar(0x00d7), 'x');
    static const QRegularExpression pattern(QStringLiteral("^\\s*(\\d+)\\s*x\\s*(\\d+)\\s*$"),
                                            QRegularExpression::CaseInsensitiveOption);
    const auto match = pattern.match(resolution);
    if (match.hasMatch()) {
        const auto width = match.captured(1).toInt();
        const auto height = match.captured(2).toInt();
        if (width > 0 && height > 0) {
            result.width = std::clamp(width, 640, 7680);
            result.height = std::clamp(height, 480, 4320);
        }
    }
    result.framesPerSecond = std::clamp(value.value(QStringLiteral("fps"), 60).toInt(), 30, 240);
    if (value.contains(QStringLiteral("maxBitrateKbps"))) {
        result.maxBitrateKbps = std::clamp(value.value(QStringLiteral("maxBitrateKbps")).toInt(), 1000, 200000);
    } else {
        result.maxBitrateKbps = std::clamp(
            qRound(value.value(QStringLiteral("maxBitrateMbps"), 75).toDouble() * 1000.0),
            1000,
            200000);
    }
    const auto codec = value.value(QStringLiteral("codec"), result.codec).toString().toUpper();
    result.codec = QStringList{QStringLiteral("H264"), QStringLiteral("H265"), QStringLiteral("AV1")}.contains(codec)
                       ? codec : QStringLiteral("H264");
    for (const auto &candidate : value.value(QStringLiteral("supportedCodecs")).toStringList()) {
        const auto normalized = candidate.toUpper();
        if (QStringList{QStringLiteral("H264"), QStringLiteral("H265"), QStringLiteral("AV1")}.contains(normalized)
            && !result.supportedCodecs.contains(normalized)) {
            result.supportedCodecs.append(normalized);
        }
    }
    const auto colorQuality = value.value(QStringLiteral("colorQuality"), result.colorQuality).toString();
    result.colorQuality = QStringList{QStringLiteral("8bit_420"), QStringLiteral("10bit_420")}.contains(colorQuality)
                              ? colorQuality : QStringLiteral("8bit_420");
    result.keyboardLayout = value.value(QStringLiteral("keyboardLayout"), result.keyboardLayout).toString();
    result.gameLanguage = value.value(QStringLiteral("gameLanguage"), result.gameLanguage).toString();
    result.appLaunchMode = value.value(QStringLiteral("appLaunchMode"), result.appLaunchMode).toString();
    result.enableL4S = value.value(QStringLiteral("enableL4S"), false).toBool();
    result.enableCloudGsync = value.value(QStringLiteral("enableCloudGsync"), false).toBool();
    result.enableReflex = value.contains(QStringLiteral("enableReflex"))
                              ? value.value(QStringLiteral("enableReflex")).toBool()
                              : result.enableCloudGsync || result.framesPerSecond >= 120;
    return result;
}

QString normalizeTrustedBaseUrl(const QString &input, QString *error)
{
    return normalizeBaseUrl(input, false, error);
}

QString normalizeTrustedSessionBaseUrl(const QString &input, QString *error)
{
    return normalizeBaseUrl(input, true, error);
}

bool isZoneHostname(const QString &host)
{
    auto normalized = host.trimmed().toLower();
    if (normalized.endsWith('.')) {
        normalized.chop(1);
    }
    return normalized == QStringLiteral("cloudmatchbeta.nvidiagrid.net")
           || normalized.endsWith(QStringLiteral(".cloudmatchbeta.nvidiagrid.net"))
           || normalized == QStringLiteral("cloudmatch.nvidiagrid.net")
           || normalized.endsWith(QStringLiteral(".cloudmatch.nvidiagrid.net"));
}

bool isDefaultStreamingBase(const QString &baseUrl)
{
    const auto host = QUrl(baseUrl).host().toLower();
    return host == QStringLiteral("prod.cloudmatchbeta.nvidiagrid.net")
           || (host.startsWith(QStringLiteral("prod."))
               && host.endsWith(QStringLiteral(".nvidiagrid.net")));
}

QStringList extractServerInfoRegionBases(const QJsonObject &payload)
{
    QHash<QString, QString> metadata;
    for (const auto &entry : payload.value(QStringLiteral("metaData")).toArray()) {
        const auto item = entry.toObject();
        metadata.insert(item.value(QStringLiteral("key")).toString(),
                        item.value(QStringLiteral("value")).toString());
    }
    QStringList names;
    if (const auto local = metadata.value(QStringLiteral("local-region")).trimmed(); !local.isEmpty()) {
        names.append(local);
    }
    for (const auto &name : metadata.value(QStringLiteral("gfn-regions")).split(',', Qt::SkipEmptyParts)) {
        names.append(name.trimmed());
    }
    names.removeDuplicates();
    QStringList result;
    for (const auto &name : names) {
        QString trustError;
        const auto base = normalizeTrustedBaseUrl(metadata.value(name), &trustError);
        if (!base.isEmpty() && !result.contains(base)) {
            result.append(base);
        }
    }
    return result;
}

QHash<QByteArray, QByteArray> requestHeaders(const QString &token,
                                             const QString &clientId,
                                             const QString &deviceId,
                                             bool includeOrigin)
{
    QByteArray deviceOs = "LINUX";
#if defined(Q_OS_WIN)
    deviceOs = "WINDOWS";
#elif defined(Q_OS_MACOS)
    deviceOs = "MACOS";
#endif
    QHash<QByteArray, QByteArray> result{
        {"User-Agent", UserAgent},
        {"Authorization", QByteArray("GFNJWT ") + token.toUtf8()},
        {"Content-Type", "application/json"},
        {"Accept", "application/json, text/plain, */*"},
        {"nv-browser-type", "CHROME"},
        {"nv-client-id", clientId.toUtf8()},
        {"nv-client-streamer", "NVIDIA-CLASSIC"},
        {"nv-client-type", "NATIVE"},
        {"nv-client-version", ClientVersion},
        {"nv-device-os", deviceOs},
        {"nv-device-type", "DESKTOP"},
        {"nv-device-make", "UNKNOWN"},
        {"nv-device-model", "UNKNOWN"},
        {"x-device-id", deviceId.toUtf8()},
    };
    if (includeOrigin) {
        result.insert("Origin", PlayOrigin);
        result.insert("Referer", PlayReferer);
    }
    return result;
}

QJsonObject buildNetworkTestRequest(const StreamSettings &settings)
{
    return {{QStringLiteral("netTestRequestData"),
             QJsonObject{{QStringLiteral("clientPlatformName"), QStringLiteral("windows")},
                         {QStringLiteral("netTestProfile"),
                          QJsonObject{{QStringLiteral("widthInPixels"), settings.width},
                                      {QStringLiteral("heightInPixels"), settings.height},
                                      {QStringLiteral("framesPerSecond"), settings.framesPerSecond}}}}}};
}

QJsonObject buildCreateRequest(const QString &appId,
                               const QString &internalTitle,
                               const QString &deviceId,
                               const QString &networkTestSessionId,
                               const StreamSettings &settings,
                               bool accountLinked,
                               bool enablePersistingInGameSettings)
{
    auto data = commonSessionData(appId, deviceId, settings, enablePersistingInGameSettings, true);
    data.insert(QStringLiteral("internalTitle"), internalTitle.isEmpty() ? QJsonValue::Null : QJsonValue(internalTitle));
    data.insert(QStringLiteral("networkTestSessionId"),
                networkTestSessionId.isEmpty() ? QJsonValue::Null : QJsonValue(networkTestSessionId));
    data.insert(QStringLiteral("accountLinked"), accountLinked);
    data.insert(QStringLiteral("clientRequestMonitorSettings"),
                QJsonArray{QJsonObject{
                    {QStringLiteral("monitorId"), 0},
                    {QStringLiteral("positionX"), 0},
                    {QStringLiteral("positionY"), 0},
                    {QStringLiteral("widthInPixels"), settings.width},
                    {QStringLiteral("heightInPixels"), settings.height},
                    {QStringLiteral("framesPerSecond"), settings.framesPerSecond},
                    {QStringLiteral("sdrHdrMode"), 0},
                    {QStringLiteral("displayData"), QJsonObject{}},
                    {QStringLiteral("hdr10PlusGamingData"), QJsonValue::Null},
                    {QStringLiteral("dpi"), 0},
                }});
    const auto tenBit = settings.colorQuality.startsWith(QStringLiteral("10bit"), Qt::CaseInsensitive);
    const auto chroma444 = settings.colorQuality.endsWith(QStringLiteral("444"), Qt::CaseInsensitive);
    data.insert(QStringLiteral("requestedStreamingFeatures"),
                QJsonObject{
                    {QStringLiteral("reflex"), settings.enableReflex},
                    {QStringLiteral("bitDepth"), tenBit ? 1 : 0},
                    {QStringLiteral("cloudGsync"), settings.enableCloudGsync},
                    {QStringLiteral("enabledL4S"), settings.enableL4S},
                    {QStringLiteral("supportedHidDevices"), 0},
                    {QStringLiteral("profile"), 0},
                    {QStringLiteral("fallbackToLogicalResolution"), false},
                    {QStringLiteral("chromaFormat"), chroma444 ? 1 : 0},
                    {QStringLiteral("prefilterMode"), 0},
                    {QStringLiteral("prefilterSharpness"), 0},
                    {QStringLiteral("prefilterNoiseReduction"), 0},
                    {QStringLiteral("hudStreamingMode"), 0},
                    {QStringLiteral("maxBitrateKbps"), settings.maxBitrateKbps},
                    {QStringLiteral("codec"), resolvedCodecWireValue(settings)},
                    {QStringLiteral("vsync"), false},
                    {QStringLiteral("dynamicStreamingMode"), 3},
                    {QStringLiteral("audioChannelCount"), 2},
                });
    return {{QStringLiteral("sessionRequestData"), data}};
}

QJsonObject buildClaimRequest(const QString &appId,
                              const QString &deviceId,
                              const StreamSettings &settings,
                              int sessionAppLaunchMode,
                              bool enablePersistingInGameSettings)
{
    auto data = commonSessionData(appId, deviceId, settings, enablePersistingInGameSettings, false);
    data.insert(QStringLiteral("appId"), appId.toInt());
    if (sessionAppLaunchMode > 0) {
        data.insert(QStringLiteral("appLaunchMode"), sessionAppLaunchMode);
    }
    return {
        {QStringLiteral("action"), 2},
        {QStringLiteral("data"), QStringLiteral("RESUME")},
        {QStringLiteral("sessionRequestData"), data},
        {QStringLiteral("metaData"), QJsonArray{}},
    };
}

SessionSnapshot parseSession(const QJsonObject &payload,
                             const QString &zone,
                             const QString &streamingBaseUrl,
                             const QString &clientId,
                             const QString &deviceId,
                             const QString &fallbackAppId)
{
    SessionSnapshot result;
    const auto session = payload.value(QStringLiteral("session")).toObject();
    const auto requestData = session.value(QStringLiteral("sessionRequestData")).toObject();
    const auto signaling = signalingPayload(session);
    result.sessionId = session.value(QStringLiteral("sessionId")).toString();
    result.appId = requestData.value(QStringLiteral("appId")).toVariant().toString();
    if (result.appId.isEmpty()) {
        result.appId = fallbackAppId;
    }
    result.status = session.value(QStringLiteral("status")).toInt();
    result.queuePosition = queuePosition(session);
    result.seatSetupStep = session.value(QStringLiteral("seatSetupInfo"))
                               .toObject()
                               .value(QStringLiteral("seatSetupStep"))
                               .toInt();
    result.serverIp = signaling.value(QStringLiteral("serverIp")).toString();
    result.signalingUrl = signaling.value(QStringLiteral("signalingUrl")).toString();

    result.value = {
        {QStringLiteral("sessionId"), result.sessionId},
        {QStringLiteral("appId"), result.appId},
        {QStringLiteral("status"), result.status},
        {QStringLiteral("queuePosition"), result.queuePosition},
        {QStringLiteral("seatSetupStep"), result.seatSetupStep},
        {QStringLiteral("zone"), zone},
        {QStringLiteral("streamingBaseUrl"), streamingBaseUrl},
        {QStringLiteral("serverIp"), result.serverIp},
        {QStringLiteral("signalingServer"), signaling.value(QStringLiteral("signalingServer"))},
        {QStringLiteral("signalingUrl"), result.signalingUrl},
        {QStringLiteral("iceServers"), signaling.value(QStringLiteral("iceServers"))},
        {QStringLiteral("connectionInfo"), signaling.value(QStringLiteral("connectionInfo"))},
        {QStringLiteral("sessionControlInfo"), signaling.value(QStringLiteral("sessionControlInfo"))},
        {QStringLiteral("gpuType"), session.value(QStringLiteral("gpuType")).toString()},
        {QStringLiteral("serverLocation"), session.value(QStringLiteral("serverLocation")).toString()},
        {QStringLiteral("requestedStreamingFeatures"), streamingFeatures(requestData.value(QStringLiteral("requestedStreamingFeatures")).toObject())},
        {QStringLiteral("finalizedStreamingFeatures"), streamingFeatures(session.value(QStringLiteral("finalizedStreamingFeatures")).toObject())},
        {QStringLiteral("negotiatedStreamProfile"), negotiatedProfile(session)},
        {QStringLiteral("clientId"), clientId},
        {QStringLiteral("deviceId"), deviceId},
    };
    const auto sessionAdsRequired = session.value(QStringLiteral("sessionAdsRequired")).toBool()
                                    || session.value(QStringLiteral("isAdsRequired")).toBool()
                                    || session.value(QStringLiteral("sessionProgress"))
                                           .toObject()
                                           .value(QStringLiteral("isAdsRequired"))
                                           .toBool()
                                    || session.value(QStringLiteral("progressInfo"))
                                           .toObject()
                                           .value(QStringLiteral("isAdsRequired"))
                                           .toBool();
    if (sessionAdsRequired || session.contains(QStringLiteral("sessionAds"))
        || session.contains(QStringLiteral("opportunity"))) {
        result.value.insert(QStringLiteral("adState"),
                            QVariantMap{
                                {QStringLiteral("isAdsRequired"), sessionAdsRequired},
                                {QStringLiteral("sessionAds"), session.value(QStringLiteral("sessionAds")).toArray().toVariantList()},
                                {QStringLiteral("opportunity"), session.value(QStringLiteral("opportunity")).toObject().toVariantMap()},
                            });
    }
    if (signaling.contains(QStringLiteral("mediaConnectionInfo"))) {
        result.value.insert(QStringLiteral("mediaConnectionInfo"),
                            signaling.value(QStringLiteral("mediaConnectionInfo")));
    }
    if (signaling.contains(QStringLiteral("rtspsEndpoints"))) {
        result.value.insert(QStringLiteral("rtspsEndpoints"),
                            signaling.value(QStringLiteral("rtspsEndpoints")));
    }
    if (requestData.value(QStringLiteral("appLaunchMode")).isDouble()) {
        result.value.insert(QStringLiteral("appLaunchMode"),
                            requestData.value(QStringLiteral("appLaunchMode")).toInt());
    }
    if (requestData.value(QStringLiteral("enablePersistingInGameSettings")).isBool()) {
        result.value.insert(QStringLiteral("enablePersistingInGameSettings"),
                            requestData.value(QStringLiteral("enablePersistingInGameSettings")).toBool());
    }
    return result;
}

QVariantList parseActiveSessions(const QJsonObject &payload, const QString &streamingBaseUrl)
{
    QVariantList result;
    for (const auto &entry : payload.value(QStringLiteral("sessions")).toArray()) {
        const auto session = entry.toObject();
        const auto status = session.value(QStringLiteral("status")).toInt();
        if (status < 1 || status > 3) {
            continue;
        }
        const auto requestData = session.value(QStringLiteral("sessionRequestData")).toObject();
        const auto connections = session.value(QStringLiteral("connectionInfo")).toArray();
        const auto serverIp = streamingServerIp(session);
        QVariantMap value{
            {QStringLiteral("sessionId"), session.value(QStringLiteral("sessionId")).toString()},
            {QStringLiteral("appId"), requestData.value(QStringLiteral("appId")).toVariant()},
            {QStringLiteral("status"), status},
            {QStringLiteral("queuePosition"), queuePosition(session)},
            {QStringLiteral("seatSetupStep"), session.value(QStringLiteral("seatSetupInfo")).toObject().value(QStringLiteral("seatSetupStep")).toInt()},
            {QStringLiteral("gpuType"), session.value(QStringLiteral("gpuType")).toString()},
            {QStringLiteral("streamingBaseUrl"), streamingBaseUrl},
            {QStringLiteral("serverIp"), serverIp},
            {QStringLiteral("signalingUrl"), serverIp.isEmpty() ? QString{} : QStringLiteral("wss://%1:443/nvst/").arg(serverIp)},
            {QStringLiteral("connectionInfo"), connections.toVariantList()},
        };
        if (requestData.value(QStringLiteral("appLaunchMode")).isDouble()) {
            value.insert(QStringLiteral("appLaunchMode"), requestData.value(QStringLiteral("appLaunchMode")).toInt());
        }
        if (requestData.value(QStringLiteral("enablePersistingInGameSettings")).isBool()) {
            value.insert(QStringLiteral("enablePersistingInGameSettings"),
                         requestData.value(QStringLiteral("enablePersistingInGameSettings")).toBool());
        }
        const auto monitors = session.value(QStringLiteral("monitorSettings")).toArray();
        if (!monitors.isEmpty()) {
            const auto monitor = monitors.first().toObject();
            const auto width = monitor.value(QStringLiteral("widthInPixels")).toInt();
            const auto height = monitor.value(QStringLiteral("heightInPixels")).toInt();
            if (width > 0 && height > 0) {
                value.insert(QStringLiteral("resolution"), QStringLiteral("%1x%2").arg(width).arg(height));
            }
            value.insert(QStringLiteral("fps"), monitor.value(QStringLiteral("framesPerSecond")).toInt());
        }
        result.append(value);
    }
    return result;
}

ErrorInfo parseError(int httpStatus, const QByteArray &responseBody, const QString &networkMessage)
{
    const auto document = QJsonDocument::fromJson(responseBody);
    const auto root = document.object();
    const auto requestStatus = root.value(QStringLiteral("requestStatus")).toObject();
    const auto statusCode = requestStatus.value(QStringLiteral("statusCode")).toInt();
    const auto description = requestStatus.value(QStringLiteral("statusDescription")).toString();
    const auto unified = requestStatus.value(QStringLiteral("unifiedErrorCode")).toVariant().toULongLong();
    const auto sessionErrorCode = root.value(QStringLiteral("session"))
                                      .toObject()
                                      .value(QStringLiteral("errorCode"))
                                      .toVariant()
                                      .toULongLong();
    const auto normalized = description.toUpper();

    ErrorInfo result;
    result.httpStatus = httpStatus;
    result.statusCode = statusCode;
    result.unifiedErrorCode = unified;
    result.code = unified > 0 ? QString::number(unified)
                              : sessionErrorCode > 0 ? QString::number(sessionErrorCode)
                              : statusCode > 0 ? QStringLiteral("cloudmatch-%1").arg(statusCode)
                                               : httpStatus > 0 ? QStringLiteral("http-%1").arg(httpStatus)
                                                                : QStringLiteral("network-error");
    result.needsReauthentication = httpStatus == 401 || normalized.contains(QStringLiteral("AUTH"))
                                   || normalized.contains(QStringLiteral("TOKEN"));
    result.retryable = httpStatus == 0 || httpStatus == 408 || httpStatus == 425 || httpStatus == 429
                       || httpStatus >= 500 || statusCode == 3 || statusCode == 4
                       || normalized.contains(QStringLiteral("CAPACITY"));
    if (result.needsReauthentication) {
        result.title = QStringLiteral("Sign-in expired");
    } else if (normalized.contains(QStringLiteral("SESSION_LIMIT"))) {
        result.title = QStringLiteral("Session limit reached");
    } else if (normalized.contains(QStringLiteral("INSUFFICIENT_PLAYABILITY"))) {
        result.title = QStringLiteral("Membership upgrade required");
    } else if (httpStatus >= 500) {
        result.title = QStringLiteral("Session service error");
    } else {
        result.title = QStringLiteral("Could not start session");
    }
    result.message = httpStatus == 0 && !networkMessage.isEmpty()
                         ? networkMessage
                         : friendlyDescription(httpStatus, statusCode, description);
    return result;
}

}
