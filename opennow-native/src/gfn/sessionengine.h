#pragma once

#include "cloudmatchprotocol.h"

#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QObject>
#include <QPointer>
#include <QVariantList>

#include <functional>

class QNetworkReply;

class SessionEngine final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QString phase READ phase NOTIFY stateChanged)
    Q_PROPERTY(QString statusText READ statusText NOTIFY stateChanged)
    Q_PROPERTY(bool busy READ busy NOTIFY stateChanged)
    Q_PROPERTY(int sessionStatus READ sessionStatus NOTIFY sessionChanged)
    Q_PROPERTY(int queuePosition READ queuePosition NOTIFY sessionChanged)
    Q_PROPERTY(int seatSetupStep READ seatSetupStep NOTIFY sessionChanged)

public:
    explicit SessionEngine(QObject *parent = nullptr);

    QString phase() const { return m_phase; }
    QString statusText() const { return m_statusText; }
    bool busy() const { return m_busy; }
    int sessionStatus() const { return m_snapshot.status; }
    int queuePosition() const { return m_snapshot.queuePosition; }
    int seatSetupStep() const { return m_snapshot.seatSetupStep; }
    QJsonObject streamerContext() const;
    QString signalingServer() const;
    QString signalingSessionId() const { return m_snapshot.sessionId; }
    QUrl signalingUrl() const;

    void setCredentials(const QString &token, const QString &streamingBaseUrl);
    void clearCredentials();

    Q_INVOKABLE void launchGame(const QString &zone,
                                const QString &appId,
                                const QString &internalTitle,
                                const QVariantMap &settings,
                                bool accountLinked = true,
                                bool enablePersistingInGameSettings = false,
                                bool supportsInGameSettingsPersistence = false);

    void createSession(const QString &token,
                       const QString &streamingBaseUrl,
                       const QString &zone,
                       const QString &appId,
                       const QString &internalTitle,
                       const QVariantMap &settings,
                       bool accountLinked = true,
                       bool enablePersistingInGameSettings = false,
                       bool supportsInGameSettingsPersistence = false);
    void claimSession(const QString &token,
                      const QString &streamingBaseUrl,
                      const QString &sessionId,
                      const QString &serverIp,
                      const QString &appId,
                      const QVariantMap &settings,
                      int sessionAppLaunchMode = 0,
                      bool enablePersistingInGameSettings = false,
                      bool recoveryMode = false);
    void listActiveSessions(const QString &token, const QString &streamingBaseUrl);
    Q_INVOKABLE void stopSession();
    Q_INVOKABLE void cancel();

signals:
    void stateChanged();
    void sessionChanged();
    void activeSessionsChanged();
    void queueUpdated(int queuePosition, int seatSetupStep);
    void sessionReady();
    void connectionReady();
    void sessionStopped();
    void failed(const QString &code,
                const QString &title,
                const QString &message,
                bool retryable,
                bool needsReauthentication);

private:
    using JsonSuccess = std::function<void(const QJsonObject &, int)>;
    using RequestFailure = std::function<void(const Gfn::CloudMatch::ErrorInfo &)>;

    struct Context
    {
        QString token;
        QString zone;
        QString baseUrl;
        QString directBaseUrl;
        QString appId;
        QString internalTitle;
        QString clientId;
        QString deviceId;
        QString sessionId;
        Gfn::CloudMatch::StreamSettings settings;
        bool accountLinked = true;
        bool persistSettings = false;
        bool recoveryMode = false;
        int sessionAppLaunchMode = 0;
        int pollFailures = 0;
    };

    QString stableDeviceId();
    void beginOperation(const QString &phase, const QString &status);
    void resetRequests();
    void setState(const QString &phase, const QString &status, bool busy);
    void fail(const Gfn::CloudMatch::ErrorInfo &error);

    void resolveCreateBase();
    void createNetworkTestSession();
    void postCreateSession(const QString &networkTestSessionId);
    void prepareClaim(const QString &requestedServerIp);
    void validateAndClaim(const QString &baseUrl);
    void sendClaim(const QString &baseUrl);
    void pollSession(const QString &baseUrl, int delayMs = 0);
    void processSessionPayload(const QJsonObject &payload, const QString &sourceBaseUrl);
    void finishReadySession(const Gfn::CloudMatch::SessionSnapshot &snapshot);
    void fetchActiveSessions(const QString &baseUrl, bool allowDiscoveryFallback);

    void requestJson(const QString &method,
                     const QUrl &url,
                     const QJsonObject &body,
                     bool includeOrigin,
                     JsonSuccess success,
                     RequestFailure failure,
                     int retries = -1,
                     int timeoutMs = 30000,
                     int attempt = 0);

    QNetworkAccessManager m_network;
    QList<QPointer<QNetworkReply>> m_replies;
    Context m_context;
    QString m_token;
    QString m_streamingBaseUrl;
    Gfn::CloudMatch::SessionSnapshot m_snapshot;
    QVariantList m_activeSessions;
    QString m_phase = QStringLiteral("idle");
    QString m_statusText = QStringLiteral("Ready");
    quint64 m_generation = 0;
    bool m_busy = false;
};
