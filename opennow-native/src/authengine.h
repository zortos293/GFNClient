#pragma once

#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QObject>
#include <QTimer>
#include <QVariantList>

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

    Q_INVOKABLE void startLogin();
    Q_INVOKABLE void cancel();
    Q_INVOKABLE void selectProvider(int index);
    Q_INVOKABLE void signOut();

signals:
    void challengeChanged();
    void statusChanged();
    void providersChanged();
    void selectedProviderChanged();
    void signedInChanged();
    void accountChanged();
    void authorized();

private:
    void pollToken();
    void discoverProviders();
    void applyTokenPayload(const QJsonObject &payload);
    void setStatus(const QString &text, bool busy);

    QNetworkAccessManager m_network;
    QTimer m_pollTimer;
    QString m_deviceCode;
    QString m_userCode;
    QString m_verificationUrl;
    QString m_accessToken;
    QString m_accountName = QStringLiteral("Zortos");
    QString m_accountEmail;
    QVariantList m_providers;
    int m_selectedProviderIndex = 0;
    QString m_statusText = QStringLiteral("Ready to pair");
    int m_pollIntervalMs = 5000;
    bool m_busy = false;
    bool m_signedIn = false;
};
