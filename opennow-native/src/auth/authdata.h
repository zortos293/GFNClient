#pragma once

#include <QByteArray>
#include <QDateTime>
#include <QJsonDocument>
#include <QJsonObject>
#include <QString>
#include <QUrl>

#include <optional>

namespace OpenNow::Auth {

inline constexpr auto SteamDeckClientId = "q61ddeJrVt7O90Nl-P-N7I36yctih4Ml6FyXLrb6j-U";
inline constexpr auto DefaultIdpId = "PDiAhv2kJTFeQ7WOPqiQ2tRZ7lGhR2X11dXvM4TZSxg";
inline constexpr auto Scopes = "openid consent email tk_client age";
inline constexpr auto ServiceUrlsEndpoint = "https://pcs.geforcenow.com/v1/serviceUrls";
inline constexpr auto DeviceAuthorizeEndpoint = "https://login.nvidia.com/device/authorize";
inline constexpr auto TokenEndpoint = "https://login.nvidia.com/token";
inline constexpr auto ClientTokenEndpoint = "https://login.nvidia.com/client_token";
inline constexpr auto UserInfoEndpoint = "https://login.nvidia.com/userinfo";
inline constexpr auto SteamDeckUserAgent =
    "Mozilla/5.0 (X11; Linux x86_64; Steam Deck) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
inline constexpr auto GfnUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 NVIDIACEFClient/HEAD/debb5919f6 GFN-PC/2.0.80.173";
inline constexpr auto PlayOrigin = "https://play.geforcenow.com";
inline constexpr auto PlayReferer = "https://play.geforcenow.com/";
inline constexpr qint64 TokenRefreshWindowMs = 10 * 60 * 1000;
inline constexpr qint64 ClientTokenRefreshWindowMs = 5 * 60 * 1000;

struct Provider
{
    QString idpId;
    QString code;
    QString displayName;
    QString streamingServiceUrl;
    int priority = 0;

    bool isValid() const
    {
        const QUrl url(streamingServiceUrl);
        return !idpId.isEmpty() && !displayName.isEmpty() && url.isValid()
               && url.scheme() == QStringLiteral("https") && !url.host().isEmpty();
    }
};

struct Tokens
{
    QString accessToken;
    QString refreshToken;
    QString idToken;
    QString clientToken;
    QString authClientId = QString::fromLatin1(SteamDeckClientId);
    qint64 expiresAt = 0;
    qint64 clientTokenExpiresAt = 0;
    qint64 clientTokenLifetimeMs = 0;

    bool hasRefreshMechanism() const { return !refreshToken.isEmpty() || !clientToken.isEmpty(); }
};

struct User
{
    QString userId;
    QString displayName;
    QString email;
    QString avatarUrl;
    QString membershipTier = QStringLiteral("FREE");

    bool isValid() const { return !userId.isEmpty(); }
};

struct Session
{
    Provider provider;
    Tokens tokens;
    User user;

    bool isValid() const { return provider.isValid() && !tokens.accessToken.isEmpty() && user.isValid(); }
};

inline qint64 nowMs()
{
    return QDateTime::currentMSecsSinceEpoch();
}

inline bool isExpired(qint64 expiresAt, qint64 now = nowMs())
{
    return expiresAt <= 0 || expiresAt <= now;
}

inline bool isNearExpiry(qint64 expiresAt, qint64 windowMs, qint64 now = nowMs())
{
    return expiresAt <= 0 || expiresAt - now < windowMs;
}

inline qint64 expiresAtFrom(const QJsonObject &payload, qint64 defaultSeconds = 86400,
                            qint64 now = nowMs())
{
    const auto value = payload.value(QStringLiteral("expires_in"));
    const auto seconds = value.isDouble() && value.toDouble() > 0 ? value.toDouble() : defaultSeconds;
    return now + static_cast<qint64>(seconds * 1000.0);
}

inline QString normalizedServiceUrl(const QString &value)
{
    const QUrl url(value);
    if (!url.isValid() || url.scheme() != QStringLiteral("https") || url.host().isEmpty()) {
        return {};
    }
    return value.endsWith(QLatin1Char('/')) ? value : value + QLatin1Char('/');
}

inline QJsonObject decodeJwtPayload(const QString &token)
{
    const auto parts = token.split(QLatin1Char('.'));
    if (parts.size() != 3 || parts.at(1).isEmpty()) {
        return {};
    }
    const auto bytes = QByteArray::fromBase64(parts.at(1).toLatin1(),
                                               QByteArray::Base64UrlEncoding
                                                   | QByteArray::AbortOnBase64DecodingErrors);
    QJsonParseError error;
    const auto document = QJsonDocument::fromJson(bytes, &error);
    return error.error == QJsonParseError::NoError && document.isObject() ? document.object()
                                                                          : QJsonObject{};
}

inline qint64 jwtExpiresAt(const QString &token)
{
    const auto exp = decodeJwtPayload(token).value(QStringLiteral("exp"));
    return exp.isDouble() && exp.toDouble() > 0 ? static_cast<qint64>(exp.toDouble() * 1000.0) : 0;
}

inline User userFromJwt(const Tokens &tokens)
{
    auto claims = decodeJwtPayload(tokens.idToken);
    if (claims.isEmpty()) {
        claims = decodeJwtPayload(tokens.accessToken);
    }
    User user;
    user.userId = claims.value(QStringLiteral("sub")).toString();
    user.email = claims.value(QStringLiteral("email")).toString();
    user.avatarUrl = claims.value(QStringLiteral("picture")).toString();
    user.displayName = claims.value(QStringLiteral("preferred_username")).toString();
    if (user.displayName.isEmpty()) {
        user.displayName = claims.value(QStringLiteral("name")).toString();
    }
    if (user.displayName.isEmpty() && !user.email.isEmpty()) {
        user.displayName = user.email.section(QLatin1Char('@'), 0, 0);
    }
    user.membershipTier = claims.value(QStringLiteral("gfn_tier")).toString(QStringLiteral("FREE"));
    return user;
}

inline QString safeErrorText(QString text, const QString &fallback)
{
    text = text.trimmed();
    text.replace(QChar::Null, QLatin1Char(' '));
    text.replace(QLatin1Char('\n'), QLatin1Char(' '));
    text.replace(QLatin1Char('\r'), QLatin1Char(' '));
    return text.isEmpty() ? fallback : text.left(240);
}

inline std::optional<Tokens> tokensFromPayload(const QJsonObject &payload, const Tokens *base = nullptr,
                                                qint64 now = nowMs())
{
    const auto accessToken = payload.value(QStringLiteral("access_token")).toString();
    if (accessToken.isEmpty()) {
        return std::nullopt;
    }

    Tokens tokens = base ? *base : Tokens{};
    tokens.accessToken = accessToken;
    const auto refreshToken = payload.value(QStringLiteral("refresh_token")).toString();
    const auto idToken = payload.value(QStringLiteral("id_token")).toString();
    const auto clientToken = payload.value(QStringLiteral("client_token")).toString();
    if (!refreshToken.isEmpty()) {
        tokens.refreshToken = refreshToken;
    }
    if (!idToken.isEmpty()) {
        tokens.idToken = idToken;
    }
    if (!clientToken.isEmpty()) {
        const bool rotated = clientToken != tokens.clientToken;
        tokens.clientToken = clientToken;
        if (rotated) {
            tokens.clientTokenExpiresAt = jwtExpiresAt(clientToken);
            tokens.clientTokenLifetimeMs = tokens.clientTokenExpiresAt > now
                                               ? tokens.clientTokenExpiresAt - now
                                               : 0;
        }
    }
    tokens.expiresAt = expiresAtFrom(payload, 86400, now);
    tokens.authClientId = QString::fromLatin1(SteamDeckClientId);
    return tokens;
}

inline QJsonObject providerToJson(const Provider &provider)
{
    return {{QStringLiteral("idpId"), provider.idpId},
            {QStringLiteral("code"), provider.code},
            {QStringLiteral("displayName"), provider.displayName},
            {QStringLiteral("streamingServiceUrl"), provider.streamingServiceUrl},
            {QStringLiteral("priority"), provider.priority}};
}

inline std::optional<Provider> providerFromJson(const QJsonObject &object)
{
    Provider provider{object.value(QStringLiteral("idpId")).toString(),
                      object.value(QStringLiteral("code")).toString(),
                      object.value(QStringLiteral("displayName")).toString(),
                      normalizedServiceUrl(object.value(QStringLiteral("streamingServiceUrl")).toString()),
                      object.value(QStringLiteral("priority")).toInt()};
    return provider.isValid() ? std::optional(provider) : std::nullopt;
}

inline QJsonObject tokensToJson(const Tokens &tokens)
{
    return {{QStringLiteral("accessToken"), tokens.accessToken},
            {QStringLiteral("refreshToken"), tokens.refreshToken},
            {QStringLiteral("idToken"), tokens.idToken},
            {QStringLiteral("clientToken"), tokens.clientToken},
            {QStringLiteral("authClientId"), tokens.authClientId},
            {QStringLiteral("expiresAt"), static_cast<double>(tokens.expiresAt)},
            {QStringLiteral("clientTokenExpiresAt"), static_cast<double>(tokens.clientTokenExpiresAt)},
            {QStringLiteral("clientTokenLifetimeMs"), static_cast<double>(tokens.clientTokenLifetimeMs)}};
}

inline Tokens tokensFromJson(const QJsonObject &object)
{
    return {object.value(QStringLiteral("accessToken")).toString(),
            object.value(QStringLiteral("refreshToken")).toString(),
            object.value(QStringLiteral("idToken")).toString(),
            object.value(QStringLiteral("clientToken")).toString(),
            QString::fromLatin1(SteamDeckClientId),
            static_cast<qint64>(object.value(QStringLiteral("expiresAt")).toDouble()),
            static_cast<qint64>(object.value(QStringLiteral("clientTokenExpiresAt")).toDouble()),
            static_cast<qint64>(object.value(QStringLiteral("clientTokenLifetimeMs")).toDouble())};
}

inline QJsonObject userToJson(const User &user)
{
    return {{QStringLiteral("userId"), user.userId},
            {QStringLiteral("displayName"), user.displayName},
            {QStringLiteral("email"), user.email},
            {QStringLiteral("avatarUrl"), user.avatarUrl},
            {QStringLiteral("membershipTier"), user.membershipTier}};
}

inline User userFromJson(const QJsonObject &object)
{
    return {object.value(QStringLiteral("userId")).toString(),
            object.value(QStringLiteral("displayName")).toString(),
            object.value(QStringLiteral("email")).toString(),
            object.value(QStringLiteral("avatarUrl")).toString(),
            object.value(QStringLiteral("membershipTier")).toString(QStringLiteral("FREE"))};
}

inline QJsonObject sessionToJson(const Session &session)
{
    return {{QStringLiteral("provider"), providerToJson(session.provider)},
            {QStringLiteral("tokens"), tokensToJson(session.tokens)},
            {QStringLiteral("user"), userToJson(session.user)}};
}

inline std::optional<Session> sessionFromJson(const QJsonObject &object)
{
    const auto provider = providerFromJson(object.value(QStringLiteral("provider")).toObject());
    if (!provider) {
        return std::nullopt;
    }
    Session session{*provider, tokensFromJson(object.value(QStringLiteral("tokens")).toObject()),
                    userFromJson(object.value(QStringLiteral("user")).toObject())};
    return session.isValid() ? std::optional(session) : std::nullopt;
}

inline void wipe(QString &value)
{
    value.detach();
    value.fill(QChar::Null);
    value.clear();
    value.squeeze();
}

inline void wipe(Tokens &tokens)
{
    wipe(tokens.accessToken);
    wipe(tokens.refreshToken);
    wipe(tokens.idToken);
    wipe(tokens.clientToken);
    wipe(tokens.authClientId);
    tokens.expiresAt = 0;
    tokens.clientTokenExpiresAt = 0;
    tokens.clientTokenLifetimeMs = 0;
}

} // namespace OpenNow::Auth
