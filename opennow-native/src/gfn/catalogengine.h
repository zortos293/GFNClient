#pragma once

#include <QHash>
#include <QJsonArray>
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QObject>
#include <QSet>
#include <QUrl>
#include <QVariantList>
#include <QVariantMap>

#include <functional>

class QNetworkReply;

namespace GfnCatalog {

QString selectJwt(const QString &idToken, const QString &accessToken);
QUrl trustedStreamingBaseUrl(const QString &candidate, QString *error = nullptr);
QUrl buildPersistedQueryUrl(const QUrl &endpoint,
                            const QString &requestType,
                            const QString &sha256Hash,
                            const QJsonObject &variables,
                            const QString &huId = QString());
QVariantMap parseServerInfo(const QJsonObject &payload);
QVariantMap parseSubscription(const QJsonObject &payload, const QString &vpcId);
QVariantMap parseGame(const QJsonObject &app);
QVariantList parseAccounts(const QJsonArray &definitions,
                           const QJsonArray &stores,
                           qint64 fetchedAtMs);

} // namespace GfnCatalog

class CatalogEngine final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool loading READ loading NOTIFY loadingChanged)
    Q_PROPERTY(bool stale READ stale NOTIFY statesChanged)
    Q_PROPERTY(QString errorString READ errorString NOTIFY errorStringChanged)
    Q_PROPERTY(QString vpcId READ vpcId NOTIFY serverInfoChanged)
    Q_PROPERTY(QVariantList regions READ regions NOTIFY serverInfoChanged)
    Q_PROPERTY(bool probingRegions READ probingRegions NOTIFY regionProbeChanged)
    Q_PROPERTY(QVariantMap regionPings READ regionPings NOTIFY regionProbeChanged)
    Q_PROPERTY(QVariantMap networkTest READ networkTest NOTIFY networkTestChanged)
    Q_PROPERTY(QVariantMap subscription READ subscription NOTIFY subscriptionChanged)
    Q_PROPERTY(QVariantList library READ library NOTIFY libraryChanged)
    Q_PROPERTY(QVariantMap catalog READ catalog NOTIFY catalogChanged)
    Q_PROPERTY(QVariantList panels READ panels NOTIFY panelsChanged)
    Q_PROPERTY(QVariantList connectedAccounts READ connectedAccounts NOTIFY connectedAccountsChanged)
    Q_PROPERTY(QVariantMap states READ states NOTIFY statesChanged)

public:
    explicit CatalogEngine(QObject *parent = nullptr);
    explicit CatalogEngine(QNetworkAccessManager *network, QObject *parent = nullptr);
    ~CatalogEngine() override;

    bool loading() const;
    bool stale() const;
    QString errorString() const;
    QString vpcId() const;
    QVariantList regions() const;
    bool probingRegions() const { return m_probingRegions; }
    QVariantMap regionPings() const { return m_regionPings; }
    QVariantMap networkTest() const { return m_networkTest; }
    QVariantMap subscription() const;
    QVariantList library() const;
    QVariantMap catalog() const;
    QVariantList panels() const;
    QVariantList connectedAccounts() const;
    QVariantMap states() const;

    Q_INVOKABLE void setTokens(const QString &idToken, const QString &accessToken = QString());
    Q_INVOKABLE void setUserId(const QString &userId);
    Q_INVOKABLE bool setProviderStreamingUrl(const QString &url);
    Q_INVOKABLE void setLocale(const QString &locale);
    Q_INVOKABLE void clear();

    Q_INVOKABLE void refreshAll();
    Q_INVOKABLE void refreshServerInfo();
    Q_INVOKABLE void probeRegions();
    Q_INVOKABLE void testConnection(const QString &regionUrl = QString());
    Q_INVOKABLE void refreshSubscription();
    Q_INVOKABLE void refreshLibrary();
    Q_INVOKABLE void browseCatalog(const QString &searchQuery = QString(),
                                   const QString &sortId = QStringLiteral("relevance"),
                                   const QStringList &filterIds = {},
                                   int fetchCount = 120);
    Q_INVOKABLE void refreshPanels();
    Q_INVOKABLE void refreshConnectedAccounts();
    Q_INVOKABLE void cancel();

signals:
    void loadingChanged();
    void errorStringChanged();
    void serverInfoChanged();
    void regionProbeChanged();
    void networkTestChanged();
    void subscriptionChanged();
    void libraryChanged();
    void catalogChanged();
    void panelsChanged();
    void connectedAccountsChanged();
    void statesChanged();
    void requestFailed(const QString &operation, const QString &message);

private:
    struct NetworkResult {
        int status = 0;
        QByteArray body;
        QString error;
    };

    using NetworkCallback = std::function<void(NetworkResult)>;
    using Completion = std::function<void(bool)>;

    quint64 beginGeneration();
    bool credentialsReady(const QString &operation, quint64 generation);
    void startOperation(const QString &operation, bool hasData);
    void finishOperation(const QString &operation, const QString &error = QString());
    QString safeError(QString message) const;
    void withVpc(quint64 generation, const std::function<void(const QString &)> &callback);

    void sendRequest(const QString &operation,
                     quint64 generation,
                     const QNetworkRequest &request,
                     const QByteArray &method,
                     const QByteArray &body,
                     NetworkCallback callback,
                     int attempt = 0);
    QNetworkRequest graphqlRequest(const QUrl &url, const QString &token = QString()) const;
    QNetworkRequest lcarsRequest(const QUrl &url, const QString &token,
                                 bool browserClient = false) const;
    void getPersistedQuery(const QString &operation,
                           quint64 generation,
                           const QUrl &endpoint,
                           const QString &requestType,
                           const QString &hash,
                           const QJsonObject &variables,
                           const QString &fallbackQuery,
                           const std::function<void(NetworkResult)> &callback);

    void loadServerInfo(quint64 generation, Completion completion = {});
    void loadSubscription(quint64 generation);
    void loadLibrary(quint64 generation);
    void loadCatalog(quint64 generation,
                     const QString &searchQuery,
                     const QString &sortId,
                     const QStringList &filterIds,
                     int fetchCount);
    void loadPanels(quint64 generation);
    void loadConnectedAccounts(quint64 generation);

    QJsonObject parseJsonObject(const NetworkResult &result,
                                const QString &context,
                                QString *error) const;
    QString graphQlError(const QJsonObject &payload, const QString &context) const;
    QVariantList gamesFromApps(const QJsonArray &apps) const;
    QVariantList dedupeGames(const QVariantList &games) const;
    void measureTcpLatency(const QUrl &url, const std::function<void(int)> &completion);

    QNetworkAccessManager *m_network = nullptr;
    quint64 m_generation = 0;
    QSet<QNetworkReply *> m_replies;
    QSet<QString> m_loadingOperations;
    QVariantMap m_states;

    QString m_idToken;
    QString m_accessToken;
    QString m_userId;
    QString m_locale = QStringLiteral("en_US");
    QUrl m_streamingBaseUrl;
    QString m_errorString;
    QString m_vpcId;
    QVariantList m_regions;
    bool m_probingRegions = false;
    QVariantMap m_regionPings;
    QVariantMap m_networkTest;
    quint64 m_probeGeneration = 0;
    quint64 m_testGeneration = 0;
    QVariantMap m_subscription;
    QVariantList m_library;
    QVariantMap m_catalog;
    QVariantList m_panels;
    QVariantList m_connectedAccounts;
};
