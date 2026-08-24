#pragma once

#include <QNetworkAccessManager>
#include <QObject>
#include <QTimer>

class AuthEngine final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QString userCode READ userCode NOTIFY challengeChanged)
    Q_PROPERTY(QString statusText READ statusText NOTIFY statusChanged)
    Q_PROPERTY(bool busy READ busy NOTIFY statusChanged)

public:
    explicit AuthEngine(QObject *parent = nullptr);

    QString userCode() const { return m_userCode; }
    QString statusText() const { return m_statusText; }
    bool busy() const { return m_busy; }

    Q_INVOKABLE void startLogin();
    Q_INVOKABLE void cancel();

signals:
    void challengeChanged();
    void statusChanged();
    void authorized();

private:
    void pollToken();
    void setStatus(const QString &text, bool busy);

    QNetworkAccessManager m_network;
    QTimer m_pollTimer;
    QString m_deviceCode;
    QString m_userCode;
    QString m_verificationUrl;
    QString m_statusText = QStringLiteral("Ready to pair");
    int m_pollIntervalMs = 5000;
    bool m_busy = false;
};
