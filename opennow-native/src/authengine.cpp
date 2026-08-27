#include "authengine.h"

#include <QCryptographicHash>
#include <QDesktopServices>
#include <QHostAddress>
#include <QJsonArray>
#include <QJsonDocument>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QRandomGenerator>
#include <QTcpSocket>
#include <QUrlQuery>
#include <QUuid>

#include <algorithm>
#include <array>
#include <limits>
#include <memory>

using namespace OpenNow::Auth;

namespace {

constexpr std::array<quint16, 5> BrowserRedirectPorts{2259, 6460, 7119, 8870, 9096};

struct HttpResult
{
    int status = 0;
    QJsonObject payload;
    bool jsonValid = false;
    bool networkSucceeded = false;

    bool ok() const { return networkSucceeded && status >= 200 && status < 300 && jsonValid; }
};

HttpResult readReply(QNetworkReply *reply)
{
    HttpResult result;
    result.status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
    result.networkSucceeded = reply->error() == QNetworkReply::NoError;
    QJsonParseError error;
    const auto responseBody = reply->isOpen() ? reply->readAll() : QByteArray{};
    const auto document = QJsonDocument::fromJson(responseBody, &error);
    result.jsonValid = error.error == QJsonParseError::NoError && document.isObject();
    if (result.jsonValid) {
        result.payload = document.object();
    }
    return result;
}

QString responseError(const HttpResult &result, const QString &fallback)
{
    auto message = result.payload.value(QStringLiteral("error_description")).toString();
    if (message.isEmpty()) {
        message = result.payload.value(QStringLiteral("message")).toString();
    }
    if (message.isEmpty()) {
        message = result.payload.value(QStringLiteral("error")).toString();
    }
    if (message.isEmpty() && result.status > 0) {
        message = QStringLiteral("%1 (HTTP %2)").arg(fallback).arg(result.status);
    }
    return safeErrorText(message, fallback);
}

QString providerMark(const Provider &provider)
{
    QString mark;
    const auto words = provider.displayName.split(QLatin1Char(' '), Qt::SkipEmptyParts);
    for (const auto &word : words) {
        if (!word.isEmpty() && mark.size() < 2) {
            mark.append(word.left(1).toUpper());
        }
    }
    return mark.size() >= 2 ? mark : provider.code.left(2).toUpper();
}

void abortReply(QPointer<QNetworkReply> &reply)
{
    if (reply) {
        reply->abort();
        reply.clear();
    }
}

QByteArray randomBytes(qsizetype size)
{
    QByteArray bytes(size, Qt::Uninitialized);
    for (qsizetype index = 0; index < size; ++index) {
        bytes[index] = static_cast<char>(QRandomGenerator::system()->generate() & 0xff);
    }
    return bytes;
}

QString base64Url(const QByteArray &bytes)
{
    return QString::fromLatin1(bytes.toBase64(QByteArray::Base64UrlEncoding
                                               | QByteArray::OmitTrailingEquals));
}

} // namespace

AuthEngine::AuthEngine(QObject *parent)
    : QObject(parent)
    , m_selectedProvider(defaultProvider())
{
    m_providers.append(providerMap(m_selectedProvider));
    m_pollTimer.setSingleShot(true);
    m_refreshTimer.setSingleShot(true);
    m_oauthTimeout.setSingleShot(true);
    connect(&m_pollTimer, &QTimer::timeout, this, &AuthEngine::pollToken);
    connect(&m_refreshTimer, &QTimer::timeout, this, &AuthEngine::ensureValidSession);
    connect(&m_oauthServer, &QTcpServer::newConnection, this, &AuthEngine::acceptBrowserLoginCallback);
    connect(&m_oauthTimeout, &QTimer::timeout, this, [this] {
        if (!m_oauthServer.isListening()) {
            return;
        }
        clearChallenge(true);
        setAuthenticationError(QStringLiteral("Browser sign-in timed out"));
    });

    restoreState();
    QTimer::singleShot(0, this, [this] {
        discoverProviders();
        if (m_session) {
            emit authorized();
            ensureValidSession();
        }
    });
}

Provider AuthEngine::defaultProvider()
{
    return {QString::fromLatin1(DefaultIdpId), QStringLiteral("NVIDIA"), QStringLiteral("NVIDIA"),
            QStringLiteral("https://prod.cloudmatchbeta.nvidiagrid.net/"), 0};
}

QVariantMap AuthEngine::providerMap(const Provider &provider)
{
    const bool isDefault = provider.idpId == QString::fromLatin1(DefaultIdpId);
    return {{QStringLiteral("idpId"), provider.idpId},
            {QStringLiteral("mark"), providerMark(provider)},
            {QStringLiteral("name"), isDefault ? QStringLiteral("NVIDIA · GeForce NOW")
                                                : provider.displayName},
            {QStringLiteral("detail"), isDefault ? QStringLiteral("Global · default provider")
                                                  : QStringLiteral("Alliance partner · own regional rigs")},
            {QStringLiteral("login"), provider.displayName.toUpper()},
            {QStringLiteral("url"), isDefault ? QStringLiteral("login.nvidia.com/device")
                                               : QStringLiteral("provider sign-in page")},
            {QStringLiteral("streamingServiceUrl"), provider.streamingServiceUrl},
            {QStringLiteral("code"), provider.code},
            {QStringLiteral("priority"), provider.priority}};
}

std::optional<Provider> AuthEngine::providerFromMap(const QVariantMap &map)
{
    Provider provider{map.value(QStringLiteral("idpId")).toString(),
                      map.value(QStringLiteral("code")).toString(),
                      map.value(QStringLiteral("name")).toString(),
                      normalizedServiceUrl(map.value(QStringLiteral("streamingServiceUrl")).toString()),
                      map.value(QStringLiteral("priority")).toInt()};
    if (provider.idpId == QString::fromLatin1(DefaultIdpId)) {
        provider.displayName = QStringLiteral("NVIDIA");
        provider.code = QStringLiteral("NVIDIA");
    }
    return provider.isValid() ? std::optional(provider) : std::nullopt;
}

void AuthEngine::applyAuthHeaders(QNetworkRequest &request, const QString &contentType,
                                  const QString &bearerToken)
{
    request.setRawHeader("Accept", "application/json, text/plain, */*");
    request.setRawHeader("Origin", PlayOrigin);
    request.setRawHeader("Referer", PlayReferer);
    request.setHeader(QNetworkRequest::UserAgentHeader, QString::fromLatin1(SteamDeckUserAgent));
    request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::NoLessSafeRedirectPolicy);
    if (!contentType.isEmpty()) {
        request.setHeader(QNetworkRequest::ContentTypeHeader, contentType);
    }
    if (!bearerToken.isEmpty()) {
        request.setRawHeader("Authorization", QByteArrayLiteral("Bearer ") + bearerToken.toUtf8());
    }
}

qint64 AuthEngine::sessionExpiresAt() const
{
    return m_session ? m_session->tokens.expiresAt : 0;
}

QString AuthEngine::accessToken() const
{
    return hasUsableSession() ? m_session->tokens.accessToken : QString{};
}

QString AuthEngine::idToken() const
{
    return hasUsableSession() ? m_session->tokens.idToken : QString{};
}

QString AuthEngine::clientToken() const
{
    if (!hasUsableSession() || isExpired(m_session->tokens.clientTokenExpiresAt)) {
        return {};
    }
    return m_session->tokens.clientToken;
}

QString AuthEngine::sessionToken() const
{
    if (!hasUsableSession()) {
        return {};
    }
    return m_session->tokens.idToken.isEmpty() ? m_session->tokens.accessToken : m_session->tokens.idToken;
}

Provider AuthEngine::activeProvider() const
{
    return m_session ? m_session->provider : m_selectedProvider;
}

bool AuthEngine::hasUsableSession() const
{
    return m_session && m_session->isValid() && !isExpired(m_session->tokens.expiresAt);
}

void AuthEngine::restoreState()
{
    const auto state = m_store.load();
    if (!state) {
        persistState();
        return;
    }
    m_selectedProvider = state->selectedProvider;
    m_providers = {providerMap(m_selectedProvider)};
    if (!state->session) {
        return;
    }
    m_session = state->session;
    const auto tokenUser = userFromJwt(m_session->tokens);
    if ((tokenUser.isValid() && tokenUser.userId != m_session->user.userId)
        || (isExpired(m_session->tokens.expiresAt) && !m_session->tokens.hasRefreshMechanism())) {
        wipe(m_session->tokens);
        m_session.reset();
        persistState();
        return;
    }
    m_selectedProvider = m_session->provider;
    applySessionUi(true);
}

bool AuthEngine::persistState()
{
    const bool saved = m_store.save({m_selectedProvider, m_session});
    if (!saved) {
        QTimer::singleShot(0, this, [this] {
            emit authenticationError(QStringLiteral("Authentication state could not be saved securely"));
        });
    }
    return saved;
}

void AuthEngine::discoverProviders()
{
    QNetworkRequest request(QUrl(QString::fromLatin1(ServiceUrlsEndpoint)));
    request.setRawHeader("Accept", "application/json");
    request.setHeader(QNetworkRequest::UserAgentHeader, QString::fromLatin1(GfnUserAgent));
    request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::NoLessSafeRedirectPolicy);
    auto *reply = m_network.get(request);
    connect(reply, &QNetworkReply::finished, this, [this, reply] {
        const auto result = readReply(reply);
        reply->deleteLater();
        if (!result.ok()) {
            return;
        }

        const auto endpoints = result.payload.value(QStringLiteral("gfnServiceInfo"))
                                   .toObject()
                                   .value(QStringLiteral("gfnServiceEndpoints"))
                                   .toArray();
        QList<Provider> discovered;
        for (const auto &value : endpoints) {
            const auto object = value.toObject();
            Provider provider{object.value(QStringLiteral("idpId")).toString(),
                              object.value(QStringLiteral("loginProviderCode")).toString(),
                              object.value(QStringLiteral("loginProviderDisplayName")).toString(),
                              normalizedServiceUrl(
                                  object.value(QStringLiteral("streamingServiceUrl")).toString()),
                              object.value(QStringLiteral("loginProviderPriority")).toInt()};
            if (provider.code == QStringLiteral("BPC")) {
                provider.displayName = QStringLiteral("bro.game");
            }
            if (provider.isValid()) {
                discovered.append(provider);
            }
        }
        if (discovered.isEmpty()) {
            return;
        }
        std::sort(discovered.begin(), discovered.end(), [](const Provider &left, const Provider &right) {
            return left.priority < right.priority;
        });

        QVariantList providers;
        int selectedIndex = -1;
        for (qsizetype index = 0; index < discovered.size(); ++index) {
            providers.append(providerMap(discovered.at(index)));
            if (discovered.at(index).idpId == m_selectedProvider.idpId) {
                selectedIndex = static_cast<int>(index);
            }
        }
        m_providers = providers;
        m_selectedProviderIndex = selectedIndex >= 0 ? selectedIndex : 0;
        if (const auto provider = providerFromMap(m_providers.at(m_selectedProviderIndex).toMap())) {
            m_selectedProvider = *provider;
            persistState();
        }
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

void AuthEngine::setAuthenticationError(const QString &message)
{
    const auto safeMessage = safeErrorText(message, QStringLiteral("Authentication failed"));
    setStatus(safeMessage, false);
    emit authenticationError(safeMessage);
}

void AuthEngine::abortChallengeRequests()
{
    abortReply(m_challengeReply);
    abortReply(m_pollReply);
    abortReply(m_userInfoReply);
    abortReply(m_loginClientTokenReply);
    abortReply(m_oauthTokenReply);
}

void AuthEngine::abortSessionRequests()
{
    abortReply(m_clientTokenReply);
    abortReply(m_refreshReply);
}

void AuthEngine::clearChallenge(bool invalidateAttempt)
{
    m_pollTimer.stop();
    m_oauthTimeout.stop();
    m_oauthServer.close();
    if (invalidateAttempt) {
        ++m_attempt;
    }
    abortChallengeRequests();
    wipe(m_deviceCode);
    wipe(m_userCode);
    wipe(m_pkceVerifier);
    m_verificationUrl.clear();
    m_oauthPort = 0;
    m_challengeExpiresAt = 0;
    m_challengeProvider = {};
    emit challengeChanged();
}

void AuthEngine::cancel()
{
    clearChallenge(true);
    setStatus(QStringLiteral("Ready to pair"), false);
}

Provider AuthEngine::selectedProvider() const
{
    const auto provider = providerFromMap(m_providers.value(m_selectedProviderIndex).toMap());
    return provider.value_or(m_selectedProvider.isValid() ? m_selectedProvider : defaultProvider());
}

void AuthEngine::selectProvider(int index)
{
    if (index < 0 || index >= m_providers.size()) {
        return;
    }
    const bool changed = index != m_selectedProviderIndex;
    m_selectedProviderIndex = index;
    m_selectedProvider = selectedProvider();
    persistState();
    if (changed) {
        emit selectedProviderChanged();
    }
    startLogin();
}

void AuthEngine::signOut()
{
    clearChallenge(true);
    ++m_sessionGeneration;
    m_refreshTimer.stop();
    abortSessionRequests();
    m_refreshInProgress = false;
    if (m_session) {
        wipe(m_session->tokens);
        m_session.reset();
    }
    m_accountEmail.clear();
    m_accountName.clear();
    const bool wasSignedIn = m_signedIn;
    m_signedIn = false;
    persistState();
    if (wasSignedIn) {
        emit signedInChanged();
    }
    emit accountChanged();
    emit sessionChanged();
    emit sessionInvalidated(QStringLiteral("signed_out"));
    setStatus(QStringLiteral("Ready to pair"), false);
}

void AuthEngine::startLogin()
{
    clearChallenge(true);
    const auto attempt = m_attempt;
    m_challengeProvider = selectedProvider();
    m_selectedProvider = m_challengeProvider;
    persistState();
    setStatus(QStringLiteral("Requesting a secure NVIDIA code"), true);

    const auto deviceId = QUuid::createUuid().toString(QUuid::WithoutBraces);
    QUrlQuery body;
    body.addQueryItem(QStringLiteral("client_id"), QString::fromLatin1(SteamDeckClientId));
    body.addQueryItem(QStringLiteral("scope"), QString::fromLatin1(Scopes));
    body.addQueryItem(QStringLiteral("device_id"), deviceId);
    body.addQueryItem(QStringLiteral("display_name"), QStringLiteral("OpenNOW"));
    body.addQueryItem(QStringLiteral("idp_id"), m_challengeProvider.idpId);

    QNetworkRequest request(QUrl(QString::fromLatin1(DeviceAuthorizeEndpoint)));
    applyAuthHeaders(request, QStringLiteral("application/x-www-form-urlencoded; charset=UTF-8"));
    request.setRawHeader("x-device-id", deviceId.toUtf8());
    request.setRawHeader("nv-client-id", SteamDeckClientId);
    request.setRawHeader("nv-client-streamer", "WEBRTC");
    request.setRawHeader("nv-client-type", "BROWSER");
    request.setRawHeader("nv-client-platform-name", "browser");
    request.setRawHeader("nv-browser-type", "CHROME");
    request.setRawHeader("nv-device-os", "STEAMOS");
    request.setRawHeader("nv-device-type", "CONSOLE");
    request.setRawHeader("nv-device-model", "STEAMDECK");
    request.setRawHeader("nv-device-make", "VALVE");

    m_challengeReply = m_network.post(request, body.query(QUrl::FullyEncoded).toUtf8());
    connect(m_challengeReply, &QNetworkReply::finished, this, [this, reply = m_challengeReply, attempt] {
        if (!reply) {
            return;
        }
        const auto result = readReply(reply);
        reply->deleteLater();
        if (m_challengeReply == reply) {
            m_challengeReply.clear();
        }
        if (attempt != m_attempt) {
            return;
        }
        if (!result.ok()) {
            clearChallenge(true);
            setAuthenticationError(responseError(result, QStringLiteral("Could not request a device code")));
            return;
        }

        m_deviceCode = result.payload.value(QStringLiteral("device_code")).toString();
        m_userCode = result.payload.value(QStringLiteral("user_code")).toString();
        const auto verificationUri = result.payload.value(QStringLiteral("verification_uri")).toString();
        m_verificationUrl = result.payload.value(QStringLiteral("verification_uri_complete")).toString();
        m_pollIntervalMs = std::max(1000, result.payload.value(QStringLiteral("interval")).toInt(5) * 1000);
        m_challengeExpiresAt = expiresAtFrom(result.payload, 600);
        if (m_deviceCode.isEmpty() || m_userCode.isEmpty() || verificationUri.isEmpty()
            || m_verificationUrl.isEmpty()) {
            clearChallenge(true);
            setAuthenticationError(QStringLiteral("Device authorization response was incomplete"));
            return;
        }

        emit challengeChanged();
        setStatus(QStringLiteral("Waiting for approval"), true);
        QDesktopServices::openUrl(QUrl(m_verificationUrl));
        m_pollTimer.start(m_pollIntervalMs);
    });
}

void AuthEngine::startBrowserLogin()
{
    if (m_oauthServer.isListening() || m_oauthTokenReply) {
        setStatus(QStringLiteral("Complete sign-in in your browser"), true);
        return;
    }
    clearChallenge(true);
    const auto attempt = m_attempt;
    m_challengeProvider = selectedProvider();
    m_selectedProvider = m_challengeProvider;
    persistState();
    setStatus(QStringLiteral("Opening secure NVIDIA sign-in"), true);

    for (const auto port : BrowserRedirectPorts) {
        if (m_oauthServer.listen(QHostAddress::LocalHost, port)) {
            m_oauthPort = port;
            break;
        }
    }
    if (!m_oauthServer.isListening()) {
        clearChallenge(true);
        setAuthenticationError(QStringLiteral("Could not open a local browser sign-in callback"));
        return;
    }

    m_pkceVerifier = base64Url(randomBytes(64));
    const auto challenge = base64Url(
        QCryptographicHash::hash(m_pkceVerifier.toUtf8(), QCryptographicHash::Sha256));
    const auto deviceId = QUuid::createUuid().toString(QUuid::WithoutBraces);
    const auto redirectUri = QStringLiteral("http://localhost:%1").arg(m_oauthPort);

    QUrl authorizationUrl(QString::fromLatin1(AuthorizeEndpoint));
    QUrlQuery query;
    query.addQueryItem(QStringLiteral("response_type"), QStringLiteral("code"));
    query.addQueryItem(QStringLiteral("device_id"), deviceId);
    query.addQueryItem(QStringLiteral("scope"), QString::fromLatin1(Scopes));
    query.addQueryItem(QStringLiteral("client_id"), QString::fromLatin1(BrowserClientId));
    query.addQueryItem(QStringLiteral("redirect_uri"), redirectUri);
    query.addQueryItem(QStringLiteral("ui_locales"), QStringLiteral("en_US"));
    query.addQueryItem(QStringLiteral("nonce"), QUuid::createUuid().toString(QUuid::WithoutBraces));
    query.addQueryItem(QStringLiteral("prompt"), QStringLiteral("select_account"));
    query.addQueryItem(QStringLiteral("code_challenge"), challenge);
    query.addQueryItem(QStringLiteral("code_challenge_method"), QStringLiteral("S256"));
    query.addQueryItem(QStringLiteral("idp_id"), m_challengeProvider.idpId);
    authorizationUrl.setQuery(query);

    m_verificationUrl = authorizationUrl.toString(QUrl::FullyEncoded);
    emit challengeChanged();
    m_oauthTimeout.start(120000);
    if (!QDesktopServices::openUrl(authorizationUrl)) {
        if (attempt == m_attempt) {
            clearChallenge(true);
            setAuthenticationError(QStringLiteral("Could not open the system browser"));
        }
        return;
    }
    setStatus(QStringLiteral("Complete sign-in in your browser"), true);
}

void AuthEngine::acceptBrowserLoginCallback()
{
    const auto attempt = m_attempt;
    while (m_oauthServer.hasPendingConnections()) {
        auto *socket = m_oauthServer.nextPendingConnection();
        if (!socket) {
            continue;
        }
        auto requestBuffer = std::make_shared<QByteArray>();
        connect(socket, &QTcpSocket::readyRead, this, [this, socket, requestBuffer, attempt] {
            requestBuffer->append(socket->readAll());
            if (!requestBuffer->contains("\r\n\r\n")) {
                return;
            }

            const auto firstLine = requestBuffer->left(requestBuffer->indexOf("\r\n"));
            const auto parts = firstLine.split(' ');
            const auto target = parts.size() >= 2 ? QString::fromUtf8(parts.at(1)) : QString{};
            const QUrl callbackUrl(QStringLiteral("http://localhost:%1%2").arg(m_oauthPort).arg(target));
            const QUrlQuery query(callbackUrl);
            const auto code = query.queryItemValue(QStringLiteral("code"));
            const auto oauthError = query.queryItemValue(QStringLiteral("error_description"));
            const bool valid = attempt == m_attempt && m_oauthServer.isListening() && !code.isEmpty();
            const auto title = valid ? QStringLiteral("OpenNOW sign-in complete")
                                     : QStringLiteral("OpenNOW sign-in failed");
            const auto body = valid
                                  ? QStringLiteral("You can close this tab and return to OpenNOW.")
                                  : QStringLiteral("Return to OpenNOW and try signing in again.");
            const auto html = QStringLiteral(
                                  "<!doctype html><meta charset=utf-8><title>%1</title>"
                                  "<style>body{margin:0;background:#070a08;color:#e8f2ea;font:16px system-ui;"
                                  "display:grid;place-items:center;height:100vh}main{max-width:560px;padding:48px;"
                                  "border:1px solid #1c231e;background:#0e120f}h1{color:#4ce87f}</style>"
                                  "<main><h1>%1</h1><p>%2</p></main>")
                                  .arg(title.toHtmlEscaped(), body.toHtmlEscaped())
                                  .toUtf8();
            const auto response = QByteArrayLiteral("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                                                     "Cache-Control: no-store\r\nConnection: close\r\nContent-Length: ")
                                  + QByteArray::number(html.size()) + QByteArrayLiteral("\r\n\r\n") + html;
            socket->write(response);
            socket->disconnectFromHost();

            if (attempt != m_attempt || !m_oauthServer.isListening()) {
                return;
            }
            if (code.isEmpty()) {
                const auto fallback = query.queryItemValue(QStringLiteral("error"));
                clearChallenge(true);
                setAuthenticationError(oauthError.isEmpty()
                                           ? safeErrorText(fallback, QStringLiteral("Browser sign-in was cancelled"))
                                           : oauthError);
                return;
            }
            m_oauthServer.close();
            m_oauthTimeout.stop();
            exchangeBrowserAuthorizationCode(code, attempt);
        });
    }
}

void AuthEngine::exchangeBrowserAuthorizationCode(const QString &code, quint64 attempt)
{
    const auto verifier = m_pkceVerifier;
    wipe(m_pkceVerifier);
    const auto provider = m_challengeProvider;
    const auto redirectUri = QStringLiteral("http://localhost:%1").arg(m_oauthPort);
    QUrlQuery body;
    body.addQueryItem(QStringLiteral("grant_type"), QStringLiteral("authorization_code"));
    body.addQueryItem(QStringLiteral("code"), code);
    body.addQueryItem(QStringLiteral("redirect_uri"), redirectUri);
    body.addQueryItem(QStringLiteral("code_verifier"), verifier);
    body.addQueryItem(QStringLiteral("client_id"), QString::fromLatin1(BrowserClientId));

    QNetworkRequest request(QUrl(QString::fromLatin1(TokenEndpoint)));
    applyAuthHeaders(request, QStringLiteral("application/x-www-form-urlencoded; charset=UTF-8"));
    m_oauthTokenReply = m_network.post(request, body.query(QUrl::FullyEncoded).toUtf8());
    connect(m_oauthTokenReply, &QNetworkReply::finished, this,
            [this, reply = m_oauthTokenReply, provider, attempt] {
                if (!reply) {
                    return;
                }
                const auto result = readReply(reply);
                reply->deleteLater();
                if (m_oauthTokenReply == reply) {
                    m_oauthTokenReply.clear();
                }
                if (attempt != m_attempt) {
                    return;
                }
                auto tokens = tokensFromPayload(result.payload);
                if (!result.ok() || !tokens) {
                    clearChallenge(true);
                    setAuthenticationError(responseError(
                        result, QStringLiteral("Could not finish browser sign-in")));
                    return;
                }
                tokens->authClientId = QString::fromLatin1(BrowserClientId);
                completeDeviceAuthorization(*tokens, provider, attempt);
            });
}

void AuthEngine::pollToken()
{
    if (m_deviceCode.isEmpty()) {
        return;
    }
    if (isExpired(m_challengeExpiresAt)) {
        clearChallenge(true);
        setAuthenticationError(QStringLiteral("QR login expired"));
        return;
    }

    const auto attempt = m_attempt;
    const auto provider = m_challengeProvider;
    QUrlQuery body;
    body.addQueryItem(QStringLiteral("grant_type"),
                      QStringLiteral("urn:ietf:params:oauth:grant-type:device_code"));
    body.addQueryItem(QStringLiteral("device_code"), m_deviceCode);
    body.addQueryItem(QStringLiteral("client_id"), QString::fromLatin1(SteamDeckClientId));

    QNetworkRequest request(QUrl(QString::fromLatin1(TokenEndpoint)));
    applyAuthHeaders(request, QStringLiteral("application/x-www-form-urlencoded; charset=UTF-8"));
    m_pollReply = m_network.post(request, body.query(QUrl::FullyEncoded).toUtf8());
    connect(m_pollReply, &QNetworkReply::finished, this, [this, reply = m_pollReply, attempt, provider] {
        if (!reply) {
            return;
        }
        const auto result = readReply(reply);
        reply->deleteLater();
        if (m_pollReply == reply) {
            m_pollReply.clear();
        }
        if (attempt != m_attempt) {
            return;
        }
        if (isExpired(m_challengeExpiresAt)) {
            clearChallenge(true);
            setAuthenticationError(QStringLiteral("QR login expired"));
            return;
        }
        if (result.ok()) {
            const auto tokens = tokensFromPayload(result.payload);
            if (!tokens) {
                clearChallenge(true);
                setAuthenticationError(QStringLiteral("Device token response was incomplete"));
                return;
            }
            m_pollTimer.stop();
            completeDeviceAuthorization(*tokens, provider, attempt);
            return;
        }

        const auto error = result.payload.value(QStringLiteral("error")).toString();
        if (error == QStringLiteral("authorization_pending")) {
            m_pollTimer.start(m_pollIntervalMs);
            return;
        }
        if (error == QStringLiteral("slow_down")) {
            m_pollIntervalMs = std::min(m_pollIntervalMs + 1000, 60000);
            m_pollTimer.start(m_pollIntervalMs);
            return;
        }
        const QString fallback = error == QStringLiteral("expired_token")
                                     ? QStringLiteral("QR login expired")
                                     : error == QStringLiteral("access_denied")
                                           ? QStringLiteral("QR login was denied")
                                           : QStringLiteral("Device authorization failed");
        clearChallenge(true);
        setAuthenticationError(responseError(result, fallback));
    });
}

void AuthEngine::completeDeviceAuthorization(const Tokens &tokens, const Provider &provider, quint64 attempt)
{
    Session session{provider, tokens, userFromJwt(tokens)};
    const bool jwtHasProfile = session.user.isValid()
                               && (!session.user.email.isEmpty() || !session.user.avatarUrl.isEmpty());
    if (jwtHasProfile) {
        bootstrapLoginClientToken(std::move(session), attempt);
        return;
    }
    fetchLoginUser(std::move(session), attempt);
}

void AuthEngine::fetchLoginUser(Session session, quint64 attempt)
{
    QNetworkRequest request(QUrl(QString::fromLatin1(UserInfoEndpoint)));
    applyAuthHeaders(request, {}, session.tokens.accessToken);
    m_userInfoReply = m_network.get(request);
    connect(m_userInfoReply, &QNetworkReply::finished, this,
            [this, reply = m_userInfoReply, attempt, session = std::move(session)]() mutable {
                if (!reply) {
                    return;
                }
                const auto result = readReply(reply);
                reply->deleteLater();
                if (m_userInfoReply == reply) {
                    m_userInfoReply.clear();
                }
                if (attempt != m_attempt) {
                    wipe(session.tokens);
                    return;
                }

                if (result.ok()) {
                    User user{result.payload.value(QStringLiteral("sub")).toString(),
                              result.payload.value(QStringLiteral("preferred_username")).toString(),
                              result.payload.value(QStringLiteral("email")).toString(),
                              result.payload.value(QStringLiteral("picture")).toString(),
                              QStringLiteral("FREE")};
                    if (user.displayName.isEmpty() && !user.email.isEmpty()) {
                        user.displayName = user.email.section(QLatin1Char('@'), 0, 0);
                    }
                    if (user.isValid()) {
                        if (!session.user.userId.isEmpty() && session.user.userId != user.userId) {
                            wipe(session.tokens);
                            clearChallenge(true);
                            setAuthenticationError(
                                QStringLiteral("Account details did not match the authorized token"));
                            return;
                        }
                        session.user = std::move(user);
                    }
                }
                if (!session.user.isValid()) {
                    wipe(session.tokens);
                    clearChallenge(true);
                    setAuthenticationError(responseError(result, QStringLiteral("Could not load account details")));
                    return;
                }
                if (session.user.displayName.isEmpty()) {
                    session.user.displayName = QStringLiteral("User");
                }
                bootstrapLoginClientToken(std::move(session), attempt);
            });
}

void AuthEngine::bootstrapLoginClientToken(Session session, quint64 attempt)
{
    if (!session.tokens.clientToken.isEmpty()
        && !isNearExpiry(session.tokens.clientTokenExpiresAt, ClientTokenRefreshWindowMs)) {
        finalizeLogin(std::move(session), attempt);
        return;
    }

    QNetworkRequest request(QUrl(QString::fromLatin1(ClientTokenEndpoint)));
    applyAuthHeaders(request, {}, session.tokens.accessToken);
    m_loginClientTokenReply = m_network.get(request);
    connect(m_loginClientTokenReply, &QNetworkReply::finished, this,
            [this, reply = m_loginClientTokenReply, attempt, session = std::move(session)]() mutable {
                if (!reply) {
                    return;
                }
                const auto result = readReply(reply);
                reply->deleteLater();
                if (m_loginClientTokenReply == reply) {
                    m_loginClientTokenReply.clear();
                }
                if (attempt != m_attempt) {
                    wipe(session.tokens);
                    return;
                }
                const auto token = result.payload.value(QStringLiteral("client_token")).toString();
                if (result.ok() && !token.isEmpty()) {
                    session.tokens.clientToken = token;
                    session.tokens.clientTokenExpiresAt = expiresAtFrom(result.payload);
                    session.tokens.clientTokenLifetimeMs =
                        std::max<qint64>(0, session.tokens.clientTokenExpiresAt - nowMs());
                }
                finalizeLogin(std::move(session), attempt);
            });
}

void AuthEngine::finalizeLogin(Session session, quint64 attempt)
{
    if (attempt != m_attempt || !session.isValid()) {
        wipe(session.tokens);
        return;
    }
    clearChallenge(false);
    abortSessionRequests();
    m_refreshInProgress = false;
    ++m_sessionGeneration;
    if (m_session) {
        wipe(m_session->tokens);
    }
    m_session = std::move(session);
    m_selectedProvider = m_session->provider;
    persistState();
    applySessionUi(false);
    setStatus(QStringLiteral("Device approved"), false);
    scheduleRefresh();
    emit sessionChanged();
    emit sessionReady();
    emit authorized();
}

void AuthEngine::applySessionUi(bool restored)
{
    if (!m_session) {
        return;
    }
    m_accountName = m_session->user.displayName.isEmpty() ? QStringLiteral("User")
                                                          : m_session->user.displayName;
    m_accountEmail = m_session->user.email;
    const bool changed = !m_signedIn;
    m_signedIn = true;
    if (changed) {
        emit signedInChanged();
    }
    emit accountChanged();
    if (restored) {
        emit sessionChanged();
    }
}

void AuthEngine::refreshSession()
{
    beginRefresh();
}

void AuthEngine::ensureValidSession()
{
    if (!m_session) {
        return;
    }
    if (isNearExpiry(m_session->tokens.expiresAt, TokenRefreshWindowMs)) {
        beginRefresh();
        return;
    }
    if (m_session->tokens.clientToken.isEmpty()
        || isNearExpiry(m_session->tokens.clientTokenExpiresAt, ClientTokenRefreshWindowMs)) {
        bootstrapCurrentClientToken(m_sessionGeneration);
        return;
    }
    scheduleRefresh();
}

void AuthEngine::beginRefresh()
{
    if (!m_session || m_refreshInProgress) {
        return;
    }
    m_refreshInProgress = true;
    m_refreshTimer.stop();
    setStatus(QStringLiteral("Refreshing session"), true);
    const auto generation = m_sessionGeneration;
    if (!m_session->tokens.refreshToken.isEmpty()) {
        refreshWithRefreshToken(generation);
    } else if (!m_session->tokens.clientToken.isEmpty() && !m_session->user.userId.isEmpty()) {
        refreshWithClientToken(generation);
    } else {
        handleRefreshFailure(QStringLiteral("No refresh mechanism is available"), generation);
    }
}

void AuthEngine::refreshWithRefreshToken(quint64 generation)
{
    if (!m_session || generation != m_sessionGeneration) {
        return;
    }
    QUrlQuery body;
    body.addQueryItem(QStringLiteral("grant_type"), QStringLiteral("refresh_token"));
    body.addQueryItem(QStringLiteral("refresh_token"), m_session->tokens.refreshToken);
    const auto clientId = m_session->tokens.authClientId.isEmpty()
                              ? QString::fromLatin1(SteamDeckClientId)
                              : m_session->tokens.authClientId;
    body.addQueryItem(QStringLiteral("client_id"), clientId);
    QNetworkRequest request(QUrl(QString::fromLatin1(TokenEndpoint)));
    applyAuthHeaders(request, QStringLiteral("application/x-www-form-urlencoded; charset=UTF-8"));
    m_refreshReply = m_network.post(request, body.query(QUrl::FullyEncoded).toUtf8());
    connect(m_refreshReply, &QNetworkReply::finished, this,
            [this, reply = m_refreshReply, generation] {
                if (!reply) {
                    return;
                }
                const auto result = readReply(reply);
                reply->deleteLater();
                if (m_refreshReply == reply) {
                    m_refreshReply.clear();
                }
                if (generation != m_sessionGeneration || !m_session) {
                    return;
                }
                if (result.ok() && tokensFromPayload(result.payload, &m_session->tokens)) {
                    applyRefreshPayload(result.payload, generation);
                    return;
                }
                const auto message = responseError(result, QStringLiteral("Refresh-token renewal failed"));
                if (!m_session->tokens.clientToken.isEmpty() && !m_session->user.userId.isEmpty()) {
                    refreshWithClientToken(generation, message);
                } else {
                    handleRefreshFailure(message, generation);
                }
            });
}

void AuthEngine::refreshWithClientToken(quint64 generation, const QString &previousError)
{
    if (!m_session || generation != m_sessionGeneration) {
        return;
    }
    QUrlQuery body;
    body.addQueryItem(QStringLiteral("grant_type"),
                      QStringLiteral("urn:ietf:params:oauth:grant-type:client_token"));
    body.addQueryItem(QStringLiteral("client_token"), m_session->tokens.clientToken);
    const auto clientId = m_session->tokens.authClientId.isEmpty()
                              ? QString::fromLatin1(SteamDeckClientId)
                              : m_session->tokens.authClientId;
    body.addQueryItem(QStringLiteral("client_id"), clientId);
    body.addQueryItem(QStringLiteral("sub"), m_session->user.userId);
    QNetworkRequest request(QUrl(QString::fromLatin1(TokenEndpoint)));
    applyAuthHeaders(request, QStringLiteral("application/x-www-form-urlencoded; charset=UTF-8"));
    m_refreshReply = m_network.post(request, body.query(QUrl::FullyEncoded).toUtf8());
    connect(m_refreshReply, &QNetworkReply::finished, this,
            [this, reply = m_refreshReply, generation, previousError] {
                if (!reply) {
                    return;
                }
                const auto result = readReply(reply);
                reply->deleteLater();
                if (m_refreshReply == reply) {
                    m_refreshReply.clear();
                }
                if (generation != m_sessionGeneration || !m_session) {
                    return;
                }
                if (result.ok() && tokensFromPayload(result.payload, &m_session->tokens)) {
                    applyRefreshPayload(result.payload, generation);
                    return;
                }
                auto message = responseError(result, QStringLiteral("Client-token renewal failed"));
                if (!previousError.isEmpty()) {
                    message = previousError + QStringLiteral("; ") + message;
                }
                handleRefreshFailure(message, generation);
            });
}

void AuthEngine::applyRefreshPayload(const QJsonObject &payload, quint64 generation)
{
    if (!m_session || generation != m_sessionGeneration) {
        return;
    }
    const auto tokens = tokensFromPayload(payload, &m_session->tokens);
    if (!tokens) {
        handleRefreshFailure(QStringLiteral("Token renewal response was incomplete"), generation);
        return;
    }
    Session candidate = *m_session;
    candidate.tokens = *tokens;
    const auto refreshedUser = userFromJwt(candidate.tokens);
    if (refreshedUser.isValid()) {
        if (refreshedUser.userId != candidate.user.userId) {
            handleRefreshFailure(QStringLiteral("Token renewal returned a different account"), generation);
            return;
        }
        if (!refreshedUser.displayName.isEmpty()) {
            candidate.user.displayName = refreshedUser.displayName;
        }
        if (!refreshedUser.email.isEmpty()) {
            candidate.user.email = refreshedUser.email;
        }
        if (!refreshedUser.avatarUrl.isEmpty()) {
            candidate.user.avatarUrl = refreshedUser.avatarUrl;
        }
        candidate.user.membershipTier = refreshedUser.membershipTier;
    }

    if (candidate.tokens.clientToken.isEmpty()
        || isNearExpiry(candidate.tokens.clientTokenExpiresAt, ClientTokenRefreshWindowMs)) {
        QNetworkRequest request(QUrl(QString::fromLatin1(ClientTokenEndpoint)));
        applyAuthHeaders(request, {}, candidate.tokens.accessToken);
        m_clientTokenReply = m_network.get(request);
        connect(m_clientTokenReply, &QNetworkReply::finished, this,
                [this, reply = m_clientTokenReply, generation, candidate = std::move(candidate)]() mutable {
                    if (!reply) {
                        return;
                    }
                    const auto result = readReply(reply);
                    reply->deleteLater();
                    if (m_clientTokenReply == reply) {
                        m_clientTokenReply.clear();
                    }
                    if (generation != m_sessionGeneration || !m_session) {
                        wipe(candidate.tokens);
                        return;
                    }
                    const auto token = result.payload.value(QStringLiteral("client_token")).toString();
                    if (result.ok() && !token.isEmpty()) {
                        candidate.tokens.clientToken = token;
                        candidate.tokens.clientTokenExpiresAt = expiresAtFrom(result.payload);
                        candidate.tokens.clientTokenLifetimeMs = std::max<qint64>(
                            0, candidate.tokens.clientTokenExpiresAt - nowMs());
                    }
                    commitRefreshedSession(std::move(candidate), generation);
                });
        return;
    }
    commitRefreshedSession(std::move(candidate), generation);
}

void AuthEngine::bootstrapCurrentClientToken(quint64 generation)
{
    if (!m_session || generation != m_sessionGeneration || m_refreshInProgress
        || isExpired(m_session->tokens.expiresAt)) {
        if (m_session && isExpired(m_session->tokens.expiresAt)) {
            beginRefresh();
        }
        return;
    }
    m_refreshInProgress = true;
    Session candidate = *m_session;
    QNetworkRequest request(QUrl(QString::fromLatin1(ClientTokenEndpoint)));
    applyAuthHeaders(request, {}, candidate.tokens.accessToken);
    m_clientTokenReply = m_network.get(request);
    connect(m_clientTokenReply, &QNetworkReply::finished, this,
            [this, reply = m_clientTokenReply, generation, candidate = std::move(candidate)]() mutable {
                if (!reply) {
                    return;
                }
                const auto result = readReply(reply);
                reply->deleteLater();
                if (m_clientTokenReply == reply) {
                    m_clientTokenReply.clear();
                }
                if (generation != m_sessionGeneration || !m_session) {
                    wipe(candidate.tokens);
                    return;
                }
                const auto token = result.payload.value(QStringLiteral("client_token")).toString();
                if (result.ok() && !token.isEmpty()) {
                    candidate.tokens.clientToken = token;
                    candidate.tokens.clientTokenExpiresAt = expiresAtFrom(result.payload);
                    candidate.tokens.clientTokenLifetimeMs =
                        std::max<qint64>(0, candidate.tokens.clientTokenExpiresAt - nowMs());
                    commitRefreshedSession(std::move(candidate), generation);
                    return;
                }
                m_refreshInProgress = false;
                scheduleRefresh();
            });
}

void AuthEngine::commitRefreshedSession(Session session, quint64 generation)
{
    if (!m_session || generation != m_sessionGeneration || !session.isValid()) {
        wipe(session.tokens);
        return;
    }
    wipe(m_session->tokens);
    m_session = std::move(session);
    m_selectedProvider = m_session->provider;
    m_refreshInProgress = false;
    persistState();
    applySessionUi(false);
    setStatus(QStringLiteral("Session ready"), false);
    scheduleRefresh();
    emit sessionChanged();
    emit sessionReady();
}

void AuthEngine::handleRefreshFailure(const QString &message, quint64 generation)
{
    if (!m_session || generation != m_sessionGeneration) {
        return;
    }
    m_refreshInProgress = false;
    if (isExpired(m_session->tokens.expiresAt)) {
        invalidateSession(QStringLiteral("expired"));
        setAuthenticationError(QStringLiteral("Saved session expired. Please sign in again"));
        return;
    }
    setAuthenticationError(message);
    m_refreshTimer.start(60000);
}

void AuthEngine::scheduleRefresh()
{
    m_refreshTimer.stop();
    if (!m_session || m_refreshInProgress) {
        return;
    }
    const auto now = nowMs();
    qint64 refreshAt = m_session->tokens.expiresAt - TokenRefreshWindowMs;
    if (m_session->tokens.clientToken.isEmpty() || m_session->tokens.clientTokenExpiresAt <= 0) {
        refreshAt = std::min(refreshAt, now + 5 * 60 * 1000);
    } else {
        refreshAt = std::min(refreshAt,
                             m_session->tokens.clientTokenExpiresAt - ClientTokenRefreshWindowMs);
    }
    const auto delay = std::clamp<qint64>(refreshAt - now, 1000,
                                          std::numeric_limits<int>::max());
    m_refreshTimer.start(static_cast<int>(delay));
}

void AuthEngine::invalidateSession(const QString &reason)
{
    ++m_sessionGeneration;
    m_refreshTimer.stop();
    abortSessionRequests();
    m_refreshInProgress = false;
    if (m_session) {
        wipe(m_session->tokens);
        m_session.reset();
    }
    const bool wasSignedIn = m_signedIn;
    m_signedIn = false;
    m_accountName.clear();
    m_accountEmail.clear();
    persistState();
    if (wasSignedIn) {
        emit signedInChanged();
    }
    emit accountChanged();
    emit sessionChanged();
    emit sessionInvalidated(reason);
}
