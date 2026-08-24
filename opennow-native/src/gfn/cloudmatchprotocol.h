#pragma once

#include <QHash>
#include <QJsonObject>
#include <QStringList>
#include <QVariantMap>

namespace Gfn::CloudMatch {

struct StreamSettings
{
    int width = 1920;
    int height = 1080;
    int framesPerSecond = 60;
    int maxBitrateKbps = 75000;
    QString codec = QStringLiteral("H264");
    QStringList supportedCodecs;
    QString colorQuality = QStringLiteral("8bit_420");
    QString keyboardLayout = QStringLiteral("en-US");
    QString gameLanguage = QStringLiteral("en_US");
    QString appLaunchMode = QStringLiteral("default");
    bool enableL4S = false;
    bool enableCloudGsync = false;
    bool enableReflex = false;

    static StreamSettings fromVariantMap(const QVariantMap &value);
};

struct SessionSnapshot
{
    QString sessionId;
    QString appId;
    QString serverIp;
    QString signalingUrl;
    int status = 0;
    int queuePosition = 0;
    int seatSetupStep = 0;
    QVariantMap value;

    bool isReady() const { return status == 2 || status == 3; }
    bool isTerminalFailure() const { return status > 3 && status != 6; }
};

struct ErrorInfo
{
    QString code;
    QString title;
    QString message;
    int httpStatus = 0;
    int statusCode = 0;
    quint64 unifiedErrorCode = 0;
    bool retryable = false;
    bool needsReauthentication = false;
};

QString normalizeTrustedBaseUrl(const QString &input, QString *error = nullptr);
QString normalizeTrustedSessionBaseUrl(const QString &input, QString *error = nullptr);
bool isZoneHostname(const QString &host);
bool isDefaultStreamingBase(const QString &baseUrl);
QStringList extractServerInfoRegionBases(const QJsonObject &payload);

QHash<QByteArray, QByteArray> requestHeaders(const QString &token,
                                             const QString &clientId,
                                             const QString &deviceId,
                                             bool includeOrigin);
QJsonObject buildNetworkTestRequest(const StreamSettings &settings);
QJsonObject buildCreateRequest(const QString &appId,
                               const QString &internalTitle,
                               const QString &deviceId,
                               const QString &networkTestSessionId,
                               const StreamSettings &settings,
                               bool accountLinked,
                               bool enablePersistingInGameSettings);
QJsonObject buildClaimRequest(const QString &appId,
                              const QString &deviceId,
                              const StreamSettings &settings,
                              int sessionAppLaunchMode,
                              bool enablePersistingInGameSettings);

SessionSnapshot parseSession(const QJsonObject &payload,
                             const QString &zone,
                             const QString &streamingBaseUrl,
                             const QString &clientId,
                             const QString &deviceId,
                             const QString &fallbackAppId = {});
QVariantList parseActiveSessions(const QJsonObject &payload, const QString &streamingBaseUrl);
ErrorInfo parseError(int httpStatus, const QByteArray &responseBody, const QString &networkMessage = {});

}
