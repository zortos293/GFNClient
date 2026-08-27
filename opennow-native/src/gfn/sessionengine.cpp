#include "sessionengine.h"

#include <QJsonArray>
#include <QJsonDocument>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QRegularExpression>
#include <QSettings>
#include <QTimer>
#include <QUrlQuery>
#include <QUuid>

#include <algorithm>

namespace {
constexpr int RequestRetryDelaysMs[] = {250, 750};

bool retryableHttpStatus(int status)
{
    return status == 408 || status == 425 || status == 429 || status == 500 || status == 502
           || status == 503 || status == 504;
}

bool apiSucceeded(const QJsonObject &payload)
{
    return payload.value(QStringLiteral("requestStatus"))
               .toObject()
               .value(QStringLiteral("statusCode"))
               .toInt()
           == 1;
}

QUrl sessionUrl(const QString &baseUrl,
                const QString &sessionId,
                const Gfn::CloudMatch::StreamSettings *settings = nullptr)
{
    QUrl url(QStringLiteral("%1/v2/session/%2").arg(baseUrl, sessionId));
    if (settings) {
        QUrlQuery query;
        query.addQueryItem(QStringLiteral("keyboardLayout"), settings->keyboardLayout);
        query.addQueryItem(QStringLiteral("languageCode"), settings->gameLanguage);
        url.setQuery(query);
    }
    return url;
}
}

SessionEngine::SessionEngine(QObject *parent)
    : QObject(parent)
{
}

QJsonObject SessionEngine::streamerContext() const
{
    QJsonObject session;
    const QStringList keys = {
        QStringLiteral("sessionId"),
        QStringLiteral("appId"),
        QStringLiteral("zone"),
        QStringLiteral("streamingBaseUrl"),
        QStringLiteral("serverIp"),
        QStringLiteral("signalingServer"),
        QStringLiteral("signalingUrl"),
        QStringLiteral("iceServers"),
        QStringLiteral("mediaConnectionInfo"),
        QStringLiteral("rtspsEndpoints"),
        QStringLiteral("connectionInfo"),
        QStringLiteral("sessionControlInfo"),
        QStringLiteral("negotiatedStreamProfile"),
        QStringLiteral("requestedStreamingFeatures"),
        QStringLiteral("finalizedStreamingFeatures"),
        QStringLiteral("clientId"),
        QStringLiteral("deviceId"),
    };
    for (const auto &key : keys) {
        if (m_snapshot.value.contains(key)) {
            session.insert(key, QJsonValue::fromVariant(m_snapshot.value.value(key)));
        }
    }

    const auto &settings = m_context.settings;
    QJsonObject streamSettings{
        {QStringLiteral("resolution"), QStringLiteral("%1x%2").arg(settings.width).arg(settings.height)},
        {QStringLiteral("fps"), settings.framesPerSecond},
        {QStringLiteral("maxBitrateMbps"), std::clamp(settings.maxBitrateKbps / 1000, 5, 150)},
        {QStringLiteral("codec"), settings.codec},
        {QStringLiteral("colorQuality"), settings.colorQuality},
        {QStringLiteral("enableCloudGsync"), settings.enableCloudGsync},
    };
    return {
        {QStringLiteral("session"), session},
        {QStringLiteral("settings"), streamSettings},
        {QStringLiteral("shortcuts"), QJsonObject{}},
    };
}

QString SessionEngine::signalingServer() const
{
    return m_snapshot.value.value(QStringLiteral("signalingServer")).toString();
}

QUrl SessionEngine::signalingUrl() const
{
    return QUrl(m_snapshot.signalingUrl);
}

void SessionEngine::setCredentials(const QString &token, const QString &streamingBaseUrl)
{
    m_token = token;
    m_streamingBaseUrl = streamingBaseUrl;
}

void SessionEngine::clearCredentials()
{
    m_token.clear();
    m_streamingBaseUrl.clear();
}

void SessionEngine::launchGame(const QString &zone,
                               const QString &streamingBaseUrl,
                               const QString &appId,
                               const QString &internalTitle,
                               const QVariantMap &settings,
                               bool accountLinked,
                               bool enablePersistingInGameSettings,
                               bool supportsInGameSettingsPersistence)
{
    createSession(m_token,
                  streamingBaseUrl.trimmed().isEmpty() ? m_streamingBaseUrl : streamingBaseUrl,
                  zone,
                  appId,
                  internalTitle,
                  settings,
                  accountLinked,
                  enablePersistingInGameSettings,
                  supportsInGameSettingsPersistence);
}

QString SessionEngine::stableDeviceId()
{
    QSettings settings;
    const auto key = QStringLiteral("gfn/stableDeviceId");
    auto value = settings.value(key).toString().trimmed();
    if (QUuid(value).isNull()) {
        value = QUuid::createUuid().toString(QUuid::WithoutBraces);
        settings.setValue(key, value);
        settings.sync();
    }
    return value;
}

void SessionEngine::beginOperation(const QString &phase, const QString &status)
{
    resetRequests();
    m_context = {};
    m_snapshot = {};
    emit sessionChanged();
    setState(phase, status, true);
}

void SessionEngine::resetRequests()
{
    ++m_generation;
    for (const auto &reply : std::as_const(m_replies)) {
        if (reply) {
            reply->abort();
        }
    }
    m_replies.clear();
}

void SessionEngine::setState(const QString &phase, const QString &status, bool busy)
{
    if (m_phase == phase && m_statusText == status && m_busy == busy) {
        return;
    }
    m_phase = phase;
    m_statusText = status;
    m_busy = busy;
    emit stateChanged();
}

void SessionEngine::fail(const Gfn::CloudMatch::ErrorInfo &error)
{
    resetRequests();
    setState(QStringLiteral("error"), error.message, false);
    emit failed(error.code,
                error.title,
                error.message,
                error.retryable,
                error.needsReauthentication);
}

void SessionEngine::createSession(const QString &token,
                                  const QString &streamingBaseUrl,
                                  const QString &zone,
                                  const QString &appId,
                                  const QString &internalTitle,
                                  const QVariantMap &settings,
                                  bool accountLinked,
                                  bool enablePersistingInGameSettings,
                                  bool supportsInGameSettingsPersistence)
{
    beginOperation(QStringLiteral("resolving"), QStringLiteral("Selecting the nearest GeForce NOW region"));
    if (token.trimmed().isEmpty()) {
        auto error = Gfn::CloudMatch::parseError(401, {});
        error.message = QStringLiteral("A GeForce NOW token is required.");
        fail(error);
        return;
    }
    static const QRegularExpression numericAppId(QStringLiteral("^\\d+$"));
    if (!numericAppId.match(appId).hasMatch()) {
        Gfn::CloudMatch::ErrorInfo error;
        error.code = QStringLiteral("invalid-app-id");
        error.title = QStringLiteral("Invalid game");
        error.message = QStringLiteral("The selected game does not have a valid GeForce NOW app ID.");
        fail(error);
        return;
    }

    const auto requestedBase = streamingBaseUrl.trimmed().isEmpty()
                                   ? QStringLiteral("https://%1.cloudmatchbeta.nvidiagrid.net").arg(zone)
                                   : streamingBaseUrl;
    QString trustError;
    const auto baseUrl = Gfn::CloudMatch::normalizeTrustedBaseUrl(requestedBase, &trustError);
    if (baseUrl.isEmpty()) {
        Gfn::CloudMatch::ErrorInfo error;
        error.code = QStringLiteral("untrusted-endpoint");
        error.title = QStringLiteral("Invalid streaming endpoint");
        error.message = trustError;
        fail(error);
        return;
    }

    m_context.token = token;
    m_context.zone = zone;
    m_context.baseUrl = baseUrl;
    m_context.appId = appId;
    m_context.internalTitle = internalTitle;
    m_context.clientId = QUuid::createUuid().toString(QUuid::WithoutBraces);
    m_context.deviceId = stableDeviceId();
    m_context.settings = Gfn::CloudMatch::StreamSettings::fromVariantMap(settings);
    m_context.accountLinked = accountLinked;
    m_context.persistSettings = enablePersistingInGameSettings && supportsInGameSettingsPersistence;
    resolveCreateBase();
}

void SessionEngine::resolveCreateBase()
{
    if (!Gfn::CloudMatch::isDefaultStreamingBase(m_context.baseUrl)) {
        createNetworkTestSession();
        return;
    }
    requestJson(QStringLiteral("GET"),
                QUrl(QStringLiteral("%1/v2/serverInfo").arg(m_context.baseUrl)),
                {},
                false,
                [this](const QJsonObject &payload, int) {
                    const auto bases = Gfn::CloudMatch::extractServerInfoRegionBases(payload);
                    if (!bases.isEmpty()) {
                        m_context.baseUrl = bases.first();
                    }
                    createNetworkTestSession();
                },
                [this](const Gfn::CloudMatch::ErrorInfo &) {
                    createNetworkTestSession();
                });
}

void SessionEngine::createNetworkTestSession()
{
    setState(QStringLiteral("testing"), QStringLiteral("Preparing the selected streaming region"), true);
    requestJson(QStringLiteral("POST"),
                QUrl(QStringLiteral("%1/v2/nettestsession").arg(m_context.baseUrl)),
                Gfn::CloudMatch::buildNetworkTestRequest(m_context.settings),
                true,
                [this](const QJsonObject &payload, int) {
                    QString sessionId;
                    if (apiSucceeded(payload)) {
                        sessionId = payload.value(QStringLiteral("netTestSession"))
                                        .toObject()
                                        .value(QStringLiteral("sessionId"))
                                        .toString()
                                        .trimmed();
                    }
                    postCreateSession(sessionId);
                },
                [this](const Gfn::CloudMatch::ErrorInfo &) {
                    postCreateSession({});
                },
                0,
                8000);
}

void SessionEngine::postCreateSession(const QString &networkTestSessionId)
{
    setState(QStringLiteral("creating"), QStringLiteral("Requesting a GeForce NOW gaming rig"), true);
    QUrl url(QStringLiteral("%1/v2/session").arg(m_context.baseUrl));
    QUrlQuery query;
    query.addQueryItem(QStringLiteral("keyboardLayout"), m_context.settings.keyboardLayout);
    query.addQueryItem(QStringLiteral("languageCode"), m_context.settings.gameLanguage);
    url.setQuery(query);
    requestJson(QStringLiteral("POST"),
                url,
                Gfn::CloudMatch::buildCreateRequest(m_context.appId,
                                                    m_context.internalTitle,
                                                    m_context.deviceId,
                                                    networkTestSessionId,
                                                    m_context.settings,
                                                    m_context.accountLinked,
                                                    m_context.persistSettings),
                true,
                [this](const QJsonObject &payload, int status) {
                    if (!apiSucceeded(payload)) {
                        const auto error = Gfn::CloudMatch::parseError(
                            status, QJsonDocument(payload).toJson(QJsonDocument::Compact));
                        if (error.statusCode != 11) {
                            fail(error);
                            return;
                        }
                        setState(QStringLiteral("resuming"),
                                 QStringLiteral("Resuming the existing GeForce NOW session"), true);
                        requestJson(QStringLiteral("GET"),
                                    QUrl(QStringLiteral("%1/v2/session").arg(m_context.baseUrl)),
                                    {}, false,
                                    [this, error](const QJsonObject &activePayload, int) {
                                        const auto sessions = Gfn::CloudMatch::parseActiveSessions(
                                            activePayload, m_context.baseUrl);
                                        for (const auto &value : sessions) {
                                            const auto session = value.toMap();
                                            if (session.value(QStringLiteral("appId")).toString()
                                                != m_context.appId) {
                                                continue;
                                            }
                                            m_context.sessionId = session.value(QStringLiteral("sessionId")).toString();
                                            m_context.recoveryMode = session.value(QStringLiteral("status")).toInt() >= 2;
                                            prepareClaim(session.value(QStringLiteral("serverIp")).toString());
                                            return;
                                        }
                                        fail(error);
                                    },
                                    [this, error](const Gfn::CloudMatch::ErrorInfo &) { fail(error); },
                                    0);
                        return;
                    }
                    processSessionPayload(payload, m_context.baseUrl);
                },
                [this](const Gfn::CloudMatch::ErrorInfo &error) { fail(error); },
                0);
}

void SessionEngine::claimSession(const QString &token,
                                 const QString &streamingBaseUrl,
                                 const QString &sessionId,
                                 const QString &serverIp,
                                 const QString &appId,
                                 const QVariantMap &settings,
                                 int sessionAppLaunchMode,
                                 bool enablePersistingInGameSettings,
                                 bool recoveryMode)
{
    beginOperation(QStringLiteral("claiming"), QStringLiteral("Reconnecting to the existing session"));
    if (token.trimmed().isEmpty() || sessionId.trimmed().isEmpty()) {
        Gfn::CloudMatch::ErrorInfo error;
        error.code = QStringLiteral("invalid-claim");
        error.title = QStringLiteral("Could not resume session");
        error.message = QStringLiteral("The session ID and GeForce NOW token are required.");
        fail(error);
        return;
    }
    QString trustError;
    const auto baseUrl = Gfn::CloudMatch::normalizeTrustedBaseUrl(streamingBaseUrl, &trustError);
    if (baseUrl.isEmpty()) {
        Gfn::CloudMatch::ErrorInfo error;
        error.code = QStringLiteral("untrusted-endpoint");
        error.title = QStringLiteral("Invalid streaming endpoint");
        error.message = trustError;
        fail(error);
        return;
    }
    m_context.token = token;
    m_context.baseUrl = baseUrl;
    m_context.appId = appId.isEmpty() ? QStringLiteral("0") : appId;
    m_context.sessionId = sessionId;
    m_context.clientId = QUuid::createUuid().toString(QUuid::WithoutBraces);
    m_context.deviceId = stableDeviceId();
    m_context.settings = Gfn::CloudMatch::StreamSettings::fromVariantMap(settings);
    m_context.sessionAppLaunchMode = sessionAppLaunchMode;
    m_context.persistSettings = enablePersistingInGameSettings;
    m_context.recoveryMode = recoveryMode;
    prepareClaim(serverIp);
}

void SessionEngine::prepareClaim(const QString &requestedServerIp)
{
    QString trustError;
    auto claimBase = requestedServerIp.trimmed().isEmpty()
                         ? m_context.baseUrl
                         : Gfn::CloudMatch::normalizeTrustedSessionBaseUrl(
                               QStringLiteral("https://%1").arg(requestedServerIp.trimmed()), &trustError);
    if (claimBase.isEmpty()) {
        Gfn::CloudMatch::ErrorInfo error;
        error.code = QStringLiteral("untrusted-session-host");
        error.title = QStringLiteral("Invalid session host");
        error.message = trustError;
        fail(error);
        return;
    }
    if (!Gfn::CloudMatch::isZoneHostname(QUrl(claimBase).host())) {
        validateAndClaim(claimBase);
        return;
    }

    requestJson(QStringLiteral("GET"),
                sessionUrl(claimBase, m_context.sessionId),
                {},
                false,
                [this, claimBase](const QJsonObject &payload, int) {
                    const auto snapshot = Gfn::CloudMatch::parseSession(payload,
                                                                        m_context.zone,
                                                                        claimBase,
                                                                        m_context.clientId,
                                                                        m_context.deviceId,
                                                                        m_context.appId);
                    if (!snapshot.serverIp.isEmpty()
                        && !Gfn::CloudMatch::isZoneHostname(snapshot.serverIp)) {
                        QString directTrustError;
                        const auto direct = Gfn::CloudMatch::normalizeTrustedSessionBaseUrl(
                            QStringLiteral("https://%1").arg(snapshot.serverIp), &directTrustError);
                        if (!direct.isEmpty()) {
                            validateAndClaim(direct);
                            return;
                        }
                    }
                    validateAndClaim(claimBase);
                },
                [this, claimBase](const Gfn::CloudMatch::ErrorInfo &) {
                    validateAndClaim(claimBase);
                });
}

void SessionEngine::validateAndClaim(const QString &baseUrl)
{
    m_context.directBaseUrl = baseUrl;
    requestJson(QStringLiteral("GET"),
                sessionUrl(baseUrl, m_context.sessionId),
                {},
                false,
                [this, baseUrl](const QJsonObject &payload, int status) {
                    if (!apiSucceeded(payload)) {
                        fail(Gfn::CloudMatch::parseError(status,
                                                        QJsonDocument(payload).toJson(QJsonDocument::Compact)));
                        return;
                    }
                    const auto snapshot = Gfn::CloudMatch::parseSession(payload,
                                                                        m_context.zone,
                                                                        baseUrl,
                                                                        m_context.clientId,
                                                                        m_context.deviceId,
                                                                        m_context.appId);
                    if (snapshot.status == 1) {
                        processSessionPayload(payload, baseUrl);
                        return;
                    }
                    if (m_context.recoveryMode && snapshot.isReady()) {
                        processSessionPayload(payload, baseUrl);
                        return;
                    }
                    sendClaim(baseUrl);
                },
                [this](const Gfn::CloudMatch::ErrorInfo &error) { fail(error); });
}

void SessionEngine::sendClaim(const QString &baseUrl)
{
    requestJson(QStringLiteral("PUT"),
                sessionUrl(baseUrl, m_context.sessionId, &m_context.settings),
                Gfn::CloudMatch::buildClaimRequest(m_context.appId,
                                                   m_context.deviceId,
                                                   m_context.settings,
                                                   m_context.sessionAppLaunchMode,
                                                   m_context.persistSettings),
                true,
                [this, baseUrl](const QJsonObject &payload, int status) {
                    if (!apiSucceeded(payload)) {
                        fail(Gfn::CloudMatch::parseError(status,
                                                        QJsonDocument(payload).toJson(QJsonDocument::Compact)));
                        return;
                    }
                    pollSession(baseUrl);
                },
                [this](const Gfn::CloudMatch::ErrorInfo &error) { fail(error); },
                0);
}

void SessionEngine::pollSession(const QString &baseUrl, int delayMs)
{
    const auto generation = m_generation;
    auto runPoll = [this, generation, baseUrl] {
        if (generation != m_generation) {
            return;
        }
        requestJson(QStringLiteral("GET"),
                    sessionUrl(baseUrl, m_context.sessionId),
                    {},
                    false,
                    [this, baseUrl](const QJsonObject &payload, int status) {
                        if (!apiSucceeded(payload)) {
                            const auto error = Gfn::CloudMatch::parseError(
                                status, QJsonDocument(payload).toJson(QJsonDocument::Compact));
                            if (error.retryable && m_context.pollFailures < 8) {
                                const auto delay = std::min(10000, 500 * (1 << m_context.pollFailures++));
                                pollSession(baseUrl, delay);
                                return;
                            }
                            fail(error);
                            return;
                        }
                        m_context.pollFailures = 0;
                        processSessionPayload(payload, baseUrl);
                    },
                    [this, baseUrl](const Gfn::CloudMatch::ErrorInfo &error) {
                        if (error.retryable && m_context.pollFailures < 8) {
                            const auto delay = std::min(10000, 500 * (1 << m_context.pollFailures++));
                            pollSession(baseUrl, delay);
                            return;
                        }
                        fail(error);
                    });
    };
    if (delayMs <= 0) {
        runPoll();
    } else {
        QTimer::singleShot(delayMs, this, std::move(runPoll));
    }
}

void SessionEngine::processSessionPayload(const QJsonObject &payload, const QString &sourceBaseUrl)
{
    auto snapshot = Gfn::CloudMatch::parseSession(payload,
                                                   m_context.zone,
                                                   sourceBaseUrl,
                                                   m_context.clientId,
                                                   m_context.deviceId,
                                                   m_context.appId);
    if (snapshot.sessionId.isEmpty()) {
        Gfn::CloudMatch::ErrorInfo error;
        error.code = QStringLiteral("invalid-session-response");
        error.title = QStringLiteral("Invalid session response");
        error.message = QStringLiteral("GeForce NOW did not return a session ID.");
        fail(error);
        return;
    }
    m_context.sessionId = snapshot.sessionId;
    m_snapshot = snapshot;
    emit sessionChanged();
    emit queueUpdated(snapshot.queuePosition, snapshot.seatSetupStep);

    if (snapshot.isTerminalFailure()) {
        const auto body = QJsonDocument(payload).toJson(QJsonDocument::Compact);
        fail(Gfn::CloudMatch::parseError(200, body));
        return;
    }
    if (!snapshot.isReady()) {
        const auto queued = snapshot.queuePosition > 1 || snapshot.seatSetupStep == 1;
        setState(queued ? QStringLiteral("queued") : QStringLiteral("launching"),
                 queued && snapshot.queuePosition > 0
                     ? QStringLiteral("Waiting for a gaming rig · %1 ahead").arg(snapshot.queuePosition)
                     : QStringLiteral("Preparing the gaming rig"),
                 true);
        pollSession(sourceBaseUrl, queued ? 2500 : 1000);
        return;
    }

    const auto sourceHost = QUrl(sourceBaseUrl).host();
    if (Gfn::CloudMatch::isZoneHostname(sourceHost) && !snapshot.serverIp.isEmpty()
        && !Gfn::CloudMatch::isZoneHostname(snapshot.serverIp)) {
        QString trustError;
        const auto directBase = Gfn::CloudMatch::normalizeTrustedSessionBaseUrl(
            QStringLiteral("https://%1").arg(snapshot.serverIp), &trustError);
        if (!directBase.isEmpty() && directBase != sourceBaseUrl) {
            requestJson(QStringLiteral("GET"),
                        sessionUrl(directBase, snapshot.sessionId),
                        {},
                        false,
                        [this, directBase](const QJsonObject &directPayload, int) {
                            m_context.directBaseUrl = directBase;
                            processSessionPayload(directPayload, directBase);
                        },
                        [this, snapshot](const Gfn::CloudMatch::ErrorInfo &) {
                            finishReadySession(snapshot);
                        });
            return;
        }
    }
    finishReadySession(snapshot);
}

void SessionEngine::finishReadySession(const Gfn::CloudMatch::SessionSnapshot &snapshot)
{
    if (snapshot.serverIp.isEmpty() || snapshot.signalingUrl.isEmpty()) {
        Gfn::CloudMatch::ErrorInfo error;
        error.code = QStringLiteral("missing-signaling-endpoint");
        error.title = QStringLiteral("Session connection unavailable");
        error.message = QStringLiteral("The gaming rig is ready but did not provide a signaling endpoint.");
        error.retryable = true;
        fail(error);
        return;
    }
    m_snapshot = snapshot;
    emit sessionChanged();
    setState(QStringLiteral("ready"), QStringLiteral("Gaming rig ready"), false);
    emit sessionReady();
    emit connectionReady();
}

void SessionEngine::listActiveSessions(const QString &token, const QString &streamingBaseUrl)
{
    beginOperation(QStringLiteral("loading-sessions"), QStringLiteral("Checking active GeForce NOW sessions"));
    QString trustError;
    const auto baseUrl = Gfn::CloudMatch::normalizeTrustedBaseUrl(streamingBaseUrl, &trustError);
    if (token.trimmed().isEmpty() || baseUrl.isEmpty()) {
        Gfn::CloudMatch::ErrorInfo error;
        error.code = QStringLiteral("invalid-session-list-request");
        error.title = QStringLiteral("Could not load sessions");
        error.message = token.trimmed().isEmpty() ? QStringLiteral("A GeForce NOW token is required.") : trustError;
        fail(error);
        return;
    }
    m_context.token = token;
    m_context.baseUrl = baseUrl;
    m_context.clientId = QUuid::createUuid().toString(QUuid::WithoutBraces);
    m_context.deviceId = stableDeviceId();
    fetchActiveSessions(baseUrl, true);
}

void SessionEngine::fetchActiveSessions(const QString &baseUrl, bool allowDiscoveryFallback)
{
    requestJson(QStringLiteral("GET"),
                QUrl(QStringLiteral("%1/v2/session").arg(baseUrl)),
                {},
                false,
                [this, baseUrl, allowDiscoveryFallback](const QJsonObject &payload, int) {
                    if (apiSucceeded(payload)) {
                        m_activeSessions = Gfn::CloudMatch::parseActiveSessions(payload, baseUrl);
                        emit activeSessionsChanged();
                        setState(QStringLiteral("idle"), QStringLiteral("Sessions updated"), false);
                        return;
                    }
                    if (!allowDiscoveryFallback) {
                        m_activeSessions.clear();
                        emit activeSessionsChanged();
                        setState(QStringLiteral("idle"), QStringLiteral("No active sessions"), false);
                        return;
                    }
                    requestJson(QStringLiteral("GET"),
                                QUrl(QStringLiteral("%1/v2/serverInfo").arg(baseUrl)),
                                {},
                                false,
                                [this, baseUrl](const QJsonObject &serverInfo, int) {
                                    const auto bases = Gfn::CloudMatch::extractServerInfoRegionBases(serverInfo);
                                    const auto fallback = std::find_if(bases.begin(), bases.end(), [&](const auto &candidate) {
                                        return candidate != baseUrl;
                                    });
                                    if (fallback == bases.end()) {
                                        m_activeSessions.clear();
                                        emit activeSessionsChanged();
                                        setState(QStringLiteral("idle"), QStringLiteral("No active sessions"), false);
                                        return;
                                    }
                                    fetchActiveSessions(*fallback, false);
                                },
                                [this](const Gfn::CloudMatch::ErrorInfo &) {
                                    m_activeSessions.clear();
                                    emit activeSessionsChanged();
                                    setState(QStringLiteral("idle"), QStringLiteral("No active sessions"), false);
                                });
                },
                [this, baseUrl, allowDiscoveryFallback](const Gfn::CloudMatch::ErrorInfo &error) {
                    if (!allowDiscoveryFallback) {
                        fail(error);
                        return;
                    }
                    requestJson(QStringLiteral("GET"),
                                QUrl(QStringLiteral("%1/v2/serverInfo").arg(baseUrl)),
                                {},
                                false,
                                [this, baseUrl](const QJsonObject &serverInfo, int) {
                                    const auto bases = Gfn::CloudMatch::extractServerInfoRegionBases(serverInfo);
                                    for (const auto &candidate : bases) {
                                        if (candidate != baseUrl) {
                                            fetchActiveSessions(candidate, false);
                                            return;
                                        }
                                    }
                                    m_activeSessions.clear();
                                    emit activeSessionsChanged();
                                    setState(QStringLiteral("idle"), QStringLiteral("No active sessions"), false);
                                },
                                [this, error](const Gfn::CloudMatch::ErrorInfo &) { fail(error); });
                },
                0);
}

void SessionEngine::stopSession()
{
    if (m_context.token.isEmpty() || m_context.sessionId.isEmpty()) {
        return;
    }
    const auto token = m_context.token;
    const auto sessionId = m_context.sessionId;
    const auto baseUrl = m_context.directBaseUrl.isEmpty() ? m_context.baseUrl : m_context.directBaseUrl;
    resetRequests();
    m_context.token = token;
    m_context.sessionId = sessionId;
    m_context.baseUrl = baseUrl;
    setState(QStringLiteral("stopping"), QStringLiteral("Ending the GeForce NOW session"), true);
    requestJson(QStringLiteral("DELETE"),
                sessionUrl(baseUrl, sessionId),
                {},
                false,
                [this](const QJsonObject &payload, int status) {
                    if (!payload.isEmpty() && payload.contains(QStringLiteral("requestStatus"))
                        && !apiSucceeded(payload)) {
                        fail(Gfn::CloudMatch::parseError(status,
                                                        QJsonDocument(payload).toJson(QJsonDocument::Compact)));
                        return;
                    }
                    m_snapshot = {};
                    emit sessionChanged();
                    setState(QStringLiteral("idle"), QStringLiteral("Session ended"), false);
                    emit sessionStopped();
                },
                [this](const Gfn::CloudMatch::ErrorInfo &error) { fail(error); },
                0);
}

void SessionEngine::cancel()
{
    resetRequests();
    m_context.token.clear();
    m_snapshot = {};
    emit sessionChanged();
    setState(QStringLiteral("idle"), QStringLiteral("Cancelled"), false);
}

void SessionEngine::requestJson(const QString &method,
                                const QUrl &url,
                                const QJsonObject &body,
                                bool includeOrigin,
                                JsonSuccess success,
                                RequestFailure failure,
                                int retries,
                                int timeoutMs,
                                int attempt)
{
    const auto generation = m_generation;
    const auto normalizedMethod = method.trimmed().toUpper();
    const auto retryCount = retries < 0 ? (normalizedMethod == QStringLiteral("GET") ? 2 : 0) : retries;
    QNetworkRequest request(url);
    request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::ManualRedirectPolicy);
    const auto headers = Gfn::CloudMatch::requestHeaders(m_context.token,
                                                         m_context.clientId,
                                                         m_context.deviceId,
                                                         includeOrigin);
    for (auto it = headers.cbegin(); it != headers.cend(); ++it) {
        request.setRawHeader(it.key(), it.value());
    }
    const auto encodedBody = body.isEmpty() ? QByteArray{} : QJsonDocument(body).toJson(QJsonDocument::Compact);
    auto *reply = m_network.sendCustomRequest(request, normalizedMethod.toUtf8(), encodedBody);
    m_replies.append(reply);

    auto *timeout = new QTimer(reply);
    timeout->setSingleShot(true);
    timeout->start(timeoutMs);
    connect(timeout, &QTimer::timeout, reply, [reply] { reply->abort(); });
    connect(reply, &QNetworkReply::finished, this, [=, this] {
        timeout->stop();
        m_replies.removeAll(reply);
        const auto data = reply->isOpen() ? reply->readAll() : QByteArray{};
        const auto networkError = reply->error();
        const auto networkMessage = reply->errorString();
        const auto status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        const auto redirected = status >= 300 && status < 400;
        reply->deleteLater();
        if (generation != m_generation) {
            return;
        }

        const auto shouldRetry = attempt < retryCount
                                 && (networkError != QNetworkReply::NoError
                                     || retryableHttpStatus(status));
        if (shouldRetry) {
            const auto delay = RequestRetryDelaysMs[std::min(attempt, 1)];
            QTimer::singleShot(delay, this, [=, this] {
                if (generation == m_generation) {
                    requestJson(method,
                                url,
                                body,
                                includeOrigin,
                                success,
                                failure,
                                retryCount,
                                timeoutMs,
                                attempt + 1);
                }
            });
            return;
        }

        if (networkError != QNetworkReply::NoError || redirected || status < 200 || status >= 300) {
            failure(Gfn::CloudMatch::parseError(status, data, networkMessage));
            return;
        }

        if (data.trimmed().isEmpty()) {
            success({}, status);
            return;
        }
        QJsonParseError parseError;
        const auto document = QJsonDocument::fromJson(data, &parseError);
        if (parseError.error != QJsonParseError::NoError || !document.isObject()) {
            Gfn::CloudMatch::ErrorInfo error;
            error.code = QStringLiteral("invalid-json");
            error.title = QStringLiteral("Invalid session response");
            error.message = QStringLiteral("GeForce NOW returned an invalid session response.");
            error.httpStatus = status;
            error.retryable = normalizedMethod == QStringLiteral("GET");
            failure(error);
            return;
        }
        success(document.object(), status);
    });
}
