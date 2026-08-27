#pragma once

#include "auth/authdata.h"
#include "auth/sessionstore.h"

#include <QNetworkAccessManager>
#include <QObject>
#include <QPointer>
#include <QTcpServer>
#include <QTimer>
#include <QVariantList>

class QNetworkReply;
class QNetworkRequest;

class AuthEngine final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QString userCode READ userCode NOTIFY challengeChanged)
    Q_PROPERTY(QString statusText READ statusText NOTIFY statusChanged)
    Q_PROPERTY(bool busy READ busy NOTIFY statusChanged)
    Q_PROPERTY(QVariantList providers READ providers NOTIFY providersChanged)
    Q_PROPERTY(int selectedProviderIndex READ selectedProviderIndex NOTIFY selectedProviderChanged)
    Q_PROPERTY(QString verificationUrl READ verificationUrl NOTIFY challengeChanged)
    Q_PROPERTY(bool signedIn READ signedIn NOTIFY signedInChanged)
    Q_PROPERTY(QString accountName READ accountName NOTIFY accountChanged)
    Q_PROPERTY(QString accountEmail READ accountEmail NOTIFY accountChanged)
    Q_PROPERTY(qint64 sessionExpiresAt READ sessionExpiresAt NOTIFY sessionChanged)

public:
    explicit AuthEngine(QObject *parent = nullptr);

    QString userCode() const { return m_userCode; }
    QString statusText() const { return m_statusText; }
    bool busy() const { return m_busy; }
    QVariantList providers() const { return m_providers; }
    int selectedProviderIndex() const { return m_selectedProviderIndex; }
    QString verificationUrl() const { return m_verificationUrl; }
    bool signedIn() const { return m_signedIn; }
    QString accountName() const { return m_accountName; }
    QString accountEmail() const { return m_accountEmail; }
    qint64 sessionExpiresAt() const;

    QString accessToken() const;
    QString idToken() const;
    QString clientToken() const;
    QString sessionToken() const;
    QString userId() const { return m_session ? m_session->user.userId : QString{}; }
    OpenNow::Auth::Provider activeProvider() const;
    bool hasUsableSession() const;

    Q_INVOKABLE void startLogin();
    Q_INVOKABLE void startBrowserLogin();
    Q_INVOKABLE void cancel();
    Q_INVOKABLE void selectProvider(int index);
    Q_INVOKABLE void signOut();
    Q_INVOKABLE void refreshSession();

signals:
    void challengeChanged();
    void statusChanged();
    void providersChanged();
    void selectedProviderChanged();
    void signedInChanged();
    void accountChanged();
    void sessionChanged();
    void sessionReady();
    void sessionInvalidated(const QString &reason);
    void authenticationError(const QString &message);
    void authorized();

private:
    void discoverProviders();
    void pollToken();
    void acceptBrowserLoginCallback();
    void exchangeBrowserAuthorizationCode(const QString &code, quint64 attempt);
    void completeDeviceAuthorization(const OpenNow::Auth::Tokens &tokens,
                                     const OpenNow::Auth::Provider &provider, quint64 attempt);
    void fetchLoginUser(OpenNow::Auth::Session session, quint64 attempt);
    void bootstrapLoginClientToken(OpenNow::Auth::Session session, quint64 attempt);
    void finalizeLogin(OpenNow::Auth::Session session, quint64 attempt);

    void ensureValidSession();
    void beginRefresh();
    void refreshWithRefreshToken(quint64 generation);
    void refreshWithClientToken(quint64 generation, const QString &previousError = {});
    void applyRefreshPayload(const QJsonObject &payload, quint64 generation);
    void bootstrapCurrentClientToken(quint64 generation);
    void commitRefreshedSession(OpenNow::Auth::Session session, quint64 generation);
    void handleRefreshFailure(const QString &message, quint64 generation);
    void scheduleRefresh();

    void restoreState();
    bool persistState();
    void applySessionUi(bool restored);
    void invalidateSession(const QString &reason);
    void clearChallenge(bool invalidateAttempt);
    void abortChallengeRequests();
    void abortSessionRequests();
    void setStatus(const QString &text, bool busy);
    void setAuthenticationError(const QString &message);

    OpenNow::Auth::Provider selectedProvider() const;
    static OpenNow::Auth::Provider defaultProvider();
    static QVariantMap providerMap(const OpenNow::Auth::Provider &provider);
    static std::optional<OpenNow::Auth::Provider> providerFromMap(const QVariantMap &provider);
    static void applyAuthHeaders(QNetworkRequest &request, const QString &contentType = {},
                                 const QString &bearerToken = {});

    QNetworkAccessManager m_network;
    QTimer m_pollTimer;
    QTimer m_refreshTimer;
    QTimer m_oauthTimeout;
    QTcpServer m_oauthServer;
    OpenNow::Auth::SessionStore m_store;
    std::optional<OpenNow::Auth::Session> m_session;
    OpenNow::Auth::Provider m_selectedProvider;
    OpenNow::Auth::Provider m_challengeProvider;
    QString m_deviceCode;
    QString m_userCode;
    QString m_verificationUrl;
    QString m_accountName;
    QString m_accountEmail;
    QVariantList m_providers;
    QPointer<QNetworkReply> m_challengeReply;
    QPointer<QNetworkReply> m_pollReply;
    QPointer<QNetworkReply> m_userInfoReply;
    QPointer<QNetworkReply> m_loginClientTokenReply;
    QPointer<QNetworkReply> m_clientTokenReply;
    QPointer<QNetworkReply> m_refreshReply;
    QPointer<QNetworkReply> m_oauthTokenReply;
    QString m_pkceVerifier;
    quint16 m_oauthPort = 0;
    qint64 m_challengeExpiresAt = 0;
    quint64 m_attempt = 0;
    quint64 m_sessionGeneration = 0;
    int m_selectedProviderIndex = 0;
    QString m_statusText = QStringLiteral("Ready to pair");
    int m_pollIntervalMs = 5000;
    bool m_busy = false;
    bool m_signedIn = false;
    bool m_refreshInProgress = false;
};
