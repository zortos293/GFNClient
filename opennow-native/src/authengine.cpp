#include "authengine.h"

#include <QDesktopServices>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QUrlQuery>
#include <QUuid>

#include <algorithm>

namespace {
constexpr auto ClientId = "q61ddeJrVt7O90Nl-P-N7I36yctih4Ml6FyXLrb6j-U";
constexpr auto DefaultIdpId = "PDiAhv2kJTFeQ7WOPqiQ2tRZ7lGhR2X11dXvM4TZSxg";
constexpr auto Scopes = "openid consent email tk_client age";
constexpr auto ServiceUrlsEndpoint = "https://pcs.geforcenow.com/v1/serviceUrls";

QVariantMap defaultProvider()
{
    return {
        {QStringLiteral("idpId"), QString::fromLatin1(DefaultIdpId)},
        {QStringLiteral("mark"), QStringLiteral("NV")},
        {QStringLiteral("name"), QStringLiteral("NVIDIA · GeForce NOW")},
        {QStringLiteral("detail"), QStringLiteral("Global · default provider")},
        {QStringLiteral("login"), QStringLiteral("NVIDIA")},
        {QStringLiteral("url"), QStringLiteral("login.nvidia.com/device")},
        {QStringLiteral("streamingServiceUrl"), QStringLiteral("https://prod.cloudmatchbeta.nvidiagrid.net/")},
    };
}
}

AuthEngine::AuthEngine(QObject *parent)
    : QObject(parent)
{
    m_providers.append(defaultProvider());
    m_pollTimer.setSingleShot(true);
    connect(&m_pollTimer, &QTimer::timeout, this, &AuthEngine::pollToken);
    QTimer::singleShot(0, this, &AuthEngine::discoverProviders);
}

void AuthEngine::discoverProviders()
{
    QNetworkRequest request(QUrl(QString::fromLatin1(ServiceUrlsEndpoint)));
    request.setRawHeader("Accept", "application/json");
    request.setHeader(QNetworkRequest::UserAgentHeader,
                      QStringLiteral("Mozilla/5.0 (X11; Linux x86_64; Steam Deck) AppleWebKit/537.36 Chrome/128 Safari/537.36"));
    auto *reply = m_network.get(request);
    connect(reply, &QNetworkReply::finished, this, [this, reply] {
        const auto document = QJsonDocument::fromJson(reply->readAll());
        const auto networkError = reply->error();
        reply->deleteLater();
        if (networkError != QNetworkReply::NoError || !document.isObject()) {
            return;
        }

        const auto endpoints = document.object()
                                   .value(QStringLiteral("gfnServiceInfo"))
                                   .toObject()
                                   .value(QStringLiteral("gfnServiceEndpoints"))
                                   .toArray();
        QVariantList providers;
        for (const auto &value : endpoints) {
            const auto provider = value.toObject();
            const auto idpId = provider.value(QStringLiteral("idpId")).toString();
            const auto code = provider.value(QStringLiteral("loginProviderCode")).toString();
            auto name = provider.value(QStringLiteral("loginProviderDisplayName")).toString();
            if (idpId.isEmpty() || name.isEmpty()) {
                continue;
            }
            if (code == QStringLiteral("BPC")) {
                name = QStringLiteral("bro.game");
            }
            QString mark;
            const auto words = name.split(' ', Qt::SkipEmptyParts);
            for (const auto &word : words) {
                if (!word.isEmpty() && mark.size() < 2) {
                    mark.append(word.left(1).toUpper());
                }
            }
            if (mark.size() < 2) {
                mark = code.left(2).toUpper();
            }
            const auto serviceUrl = provider.value(QStringLiteral("streamingServiceUrl")).toString();
            providers.append(QVariantMap{
                {QStringLiteral("idpId"), idpId},
                {QStringLiteral("mark"), mark},
                {QStringLiteral("name"), idpId == QString::fromLatin1(DefaultIdpId)
                                                   ? QStringLiteral("NVIDIA · GeForce NOW")
                                                   : name},
                {QStringLiteral("detail"), idpId == QString::fromLatin1(DefaultIdpId)
                                                     ? QStringLiteral("Global · default provider")
                                                     : QStringLiteral("Alliance partner · own regional rigs")},
                {QStringLiteral("login"), name.toUpper()},
                {QStringLiteral("url"), QStringLiteral("provider sign-in page")},
                {QStringLiteral("streamingServiceUrl"), serviceUrl},
                {QStringLiteral("priority"), provider.value(QStringLiteral("loginProviderPriority")).toInt()},
            });
        }
        if (providers.isEmpty()) {
            return;
        }
        std::sort(providers.begin(), providers.end(), [](const QVariant &left, const QVariant &right) {
            return left.toMap().value(QStringLiteral("priority")).toInt()
                   < right.toMap().value(QStringLiteral("priority")).toInt();
        });
        m_providers = providers;
        m_selectedProviderIndex = qBound(0, m_selectedProviderIndex, m_providers.size() - 1);
        emit providersChanged();
        emit selectedProviderChanged();
    });
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

void AuthEngine::selectProvider(int index)
{
    if (index < 0 || index >= m_providers.size() || index == m_selectedProviderIndex) {
        return;
    }
    m_selectedProviderIndex = index;
    emit selectedProviderChanged();
    startLogin();
}

void AuthEngine::signOut()
{
    cancel();
    m_accessToken.clear();
    m_accountEmail.clear();
    m_accountName = QStringLiteral("Zortos");
    if (m_signedIn) {
        m_signedIn = false;
        emit signedInChanged();
    }
    emit accountChanged();
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
    const auto selectedProvider = m_providers.value(m_selectedProviderIndex).toMap();
    body.addQueryItem(QStringLiteral("idp_id"),
                      selectedProvider.value(QStringLiteral("idpId"), QString::fromLatin1(DefaultIdpId)).toString());

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
        if (m_verificationUrl.isEmpty()) {
            m_verificationUrl = payload.value(QStringLiteral("verification_uri")).toString();
        }
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
            applyTokenPayload(payload);
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

void AuthEngine::applyTokenPayload(const QJsonObject &payload)
{
    m_accessToken = payload.value(QStringLiteral("access_token")).toString();
    const auto idToken = payload.value(QStringLiteral("id_token")).toString();
    const auto parts = idToken.split('.');
    if (parts.size() >= 2) {
        auto encoded = parts.at(1).toLatin1();
        encoded.append(QByteArray((4 - encoded.size() % 4) % 4, '='));
        const auto claims = QJsonDocument::fromJson(QByteArray::fromBase64(encoded, QByteArray::Base64UrlEncoding))
                                .object();
        const auto displayName = claims.value(QStringLiteral("name")).toString();
        const auto email = claims.value(QStringLiteral("email")).toString();
        if (!displayName.isEmpty()) {
            m_accountName = displayName;
        }
        m_accountEmail = email;
    }
    m_signedIn = true;
    emit signedInChanged();
    emit accountChanged();
}
