#include "../authdata.h"
#include "../sessionstore.h"

#include <QFileInfo>
#include <QTemporaryDir>
#include <QTest>

using namespace OpenNow::Auth;

class AuthDataTest final : public QObject
{
    Q_OBJECT

private slots:
    void parsesAndPreservesRefreshTokens();
    void rejectsInvalidProviders();
    void persistsOwnerOnlySessionState();
};

void AuthDataTest::parsesAndPreservesRefreshTokens()
{
    const Tokens previous{QStringLiteral("old-access"), QStringLiteral("refresh"),
                          QStringLiteral("old-id"), QStringLiteral("client"),
                          QString::fromLatin1(SteamDeckClientId), 1, 2, 3};
    const auto tokens = tokensFromPayload(
        QJsonObject{{QStringLiteral("access_token"), QStringLiteral("new-access")},
                    {QStringLiteral("id_token"), QStringLiteral("new-id")},
                    {QStringLiteral("expires_in"), 60}},
        &previous, 1000);
    QVERIFY(tokens.has_value());
    QCOMPARE(tokens->accessToken, QStringLiteral("new-access"));
    QCOMPARE(tokens->refreshToken, QStringLiteral("refresh"));
    QCOMPARE(tokens->idToken, QStringLiteral("new-id"));
    QCOMPARE(tokens->expiresAt, 61000);
}

void AuthDataTest::rejectsInvalidProviders()
{
    QVERIFY(!providerFromJson(QJsonObject{{QStringLiteral("idpId"), QStringLiteral("id")},
                                          {QStringLiteral("displayName"), QStringLiteral("Bad")},
                                          {QStringLiteral("streamingServiceUrl"),
                                           QStringLiteral("http://nvidiagrid.net")}}));
    QCOMPARE(normalizedServiceUrl(QStringLiteral("https://prod.cloudmatchbeta.nvidiagrid.net")),
             QStringLiteral("https://prod.cloudmatchbeta.nvidiagrid.net/"));
}

void AuthDataTest::persistsOwnerOnlySessionState()
{
    QTemporaryDir directory;
    QVERIFY(directory.isValid());
    const QString path = directory.filePath(QStringLiteral("auth/session.json"));
    const Provider provider{QString::fromLatin1(DefaultIdpId), QStringLiteral("NVIDIA"),
                            QStringLiteral("NVIDIA"),
                            QStringLiteral("https://prod.cloudmatchbeta.nvidiagrid.net/"), 0};
    const Session session{provider,
                          Tokens{QStringLiteral("access"), QStringLiteral("refresh"),
                                 QStringLiteral("id"), QStringLiteral("client"),
                                 QString::fromLatin1(BrowserClientId), 100, 200, 100},
                          User{QStringLiteral("user"), QStringLiteral("Player"), {}, {},
                               QStringLiteral("FREE")}};
    const SessionStore store(path);
    QVERIFY(store.save({provider, session}));
    const auto loaded = store.load();
    QVERIFY(loaded.has_value());
    QVERIFY(loaded->session.has_value());
    QCOMPARE(loaded->session->tokens.refreshToken, QStringLiteral("refresh"));
    QCOMPARE(loaded->session->tokens.authClientId, QString::fromLatin1(BrowserClientId));
    const auto permissions = QFileInfo(path).permissions();
    QVERIFY(permissions.testFlag(QFileDevice::ReadOwner));
    QVERIFY(permissions.testFlag(QFileDevice::WriteOwner));
    QVERIFY(!permissions.testFlag(QFileDevice::ReadGroup));
    QVERIFY(!permissions.testFlag(QFileDevice::ReadOther));
}

QTEST_MAIN(AuthDataTest)
#include "authdata_test.moc"
