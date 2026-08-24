#include "authengine.h"

#include <QDesktopServices>
#include <QJsonDocument>
#include <QJsonObject>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QUrlQuery>
#include <QUuid>

namespace {
constexpr auto ClientId = "q61ddeJrVt7O90Nl-P-N7I36yctih4Ml6FyXLrb6j-U";
constexpr auto DefaultIdpId = "PDiAhv2kJTFeQ7WOPqiQ2tRZ7lGhR2X11dXvM4TZSxg";
constexpr auto Scopes = "openid consent email tk_client age";
}

AuthEngine::AuthEngine(QObject *parent)
    : QObject(parent)
{
    m_pollTimer.setSingleShot(true);
    connect(&m_pollTimer, &QTimer::timeout, this, &AuthEngine::pollToken);
}

void AuthEngine::setStatus(const QString &text, bool busy)
{
    if (m_statusText == text && m_busy == busy) {
        return;
    }
    m_statusText = text;
    m_busy = busy;
    emit statusChanged();
}

void AuthEngine::cancel()
{
    m_pollTimer.stop();
    m_deviceCode.clear();
    m_userCode.clear();
    m_verificationUrl.clear();
    emit challengeChanged();
    setStatus(QStringLiteral("Ready to pair"), false);
}

void AuthEngine::startLogin()
{
    cancel();
    setStatus(QStringLiteral("Requesting a secure NVIDIA code"), true);

    const auto deviceId = QUuid::createUuid().toString(QUuid::WithoutBraces);
    QUrlQuery body;
    body.addQueryItem(QStringLiteral("client_id"), QString::fromLatin1(ClientId));
    body.addQueryItem(QStringLiteral("scope"), QString::fromLatin1(Scopes));
    body.addQueryItem(QStringLiteral("device_id"), deviceId);
    body.addQueryItem(QStringLiteral("display_name"), QStringLiteral("OpenNOW"));
    body.addQueryItem(QStringLiteral("idp_id"), QString::fromLatin1(DefaultIdpId));

    QNetworkRequest request(QUrl(QStringLiteral("https://login.nvidia.com/device/authorize")));
    request.setHeader(QNetworkRequest::ContentTypeHeader,
                      QStringLiteral("application/x-www-form-urlencoded; charset=UTF-8"));
    request.setHeader(QNetworkRequest::UserAgentHeader,
                      QStringLiteral("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/128 Safari/537.36"));
    request.setRawHeader("x-device-id", deviceId.toUtf8());
    request.setRawHeader("nv-client-id", ClientId);
    request.setRawHeader("nv-client-streamer", "WEBRTC");
    request.setRawHeader("nv-client-type", "BROWSER");
    request.setRawHeader("nv-client-platform-name", "browser");
    request.setRawHeader("nv-browser-type", "CHROME");
    request.setRawHeader("nv-device-os", "STEAMOS");
    request.setRawHeader("nv-device-type", "CONSOLE");
    request.setRawHeader("nv-device-model", "STEAMDECK");
    request.setRawHeader("nv-device-make", "VALVE");

    auto *reply = m_network.post(request, body.query(QUrl::FullyEncoded).toUtf8());
    connect(reply, &QNetworkReply::finished, this, [this, reply] {
        const auto payload = QJsonDocument::fromJson(reply->readAll()).object();
        reply->deleteLater();
        m_deviceCode = payload.value(QStringLiteral("device_code")).toString();
        m_userCode = payload.value(QStringLiteral("user_code")).toString();
        m_verificationUrl = payload.value(QStringLiteral("verification_uri_complete")).toString();
        m_pollIntervalMs = qMax(1000, payload.value(QStringLiteral("interval")).toInt(5) * 1000);

        if (m_deviceCode.isEmpty() || m_userCode.isEmpty() || m_verificationUrl.isEmpty()) {
            setStatus(QStringLiteral("Could not request a device code"), false);
            return;
        }

        emit challengeChanged();
        setStatus(QStringLiteral("Waiting for approval"), true);
        QDesktopServices::openUrl(QUrl(m_verificationUrl));
        m_pollTimer.start(m_pollIntervalMs);
    });
}

void AuthEngine::pollToken()
{
    if (m_deviceCode.isEmpty()) {
        return;
    }

    QUrlQuery body;
    body.addQueryItem(QStringLiteral("grant_type"),
                      QStringLiteral("urn:ietf:params:oauth:grant-type:device_code"));
    body.addQueryItem(QStringLiteral("device_code"), m_deviceCode);
    body.addQueryItem(QStringLiteral("client_id"), QString::fromLatin1(ClientId));

    QNetworkRequest request(QUrl(QStringLiteral("https://login.nvidia.com/token")));
    request.setHeader(QNetworkRequest::ContentTypeHeader,
                      QStringLiteral("application/x-www-form-urlencoded; charset=UTF-8"));
    auto *reply = m_network.post(request, body.query(QUrl::FullyEncoded).toUtf8());
    connect(reply, &QNetworkReply::finished, this, [this, reply] {
        const auto payload = QJsonDocument::fromJson(reply->readAll()).object();
        reply->deleteLater();
        if (!payload.value(QStringLiteral("access_token")).toString().isEmpty()) {
            m_pollTimer.stop();
            m_deviceCode.clear();
            setStatus(QStringLiteral("Device approved"), false);
            emit authorized();
            return;
        }

        const auto error = payload.value(QStringLiteral("error")).toString();
        if (error == QStringLiteral("slow_down")) {
            m_pollIntervalMs += 1000;
        } else if (!error.isEmpty() && error != QStringLiteral("authorization_pending")) {
            setStatus(payload.value(QStringLiteral("error_description")).toString(
                          QStringLiteral("Device authorization failed")),
                      false);
            return;
        }
        m_pollTimer.start(m_pollIntervalMs);
    });
}
