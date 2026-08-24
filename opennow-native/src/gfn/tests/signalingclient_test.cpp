#include "../signalingclient.h"

#include <QTest>
#include <QUrlQuery>

class SignalingClientTest final : public QObject
{
    Q_OBJECT

private slots:
    void acceptsOnlyNvidiaTlsEndpoints();
    void buildsSessionBoundUrlAndProtocol();
};

void SignalingClientTest::acceptsOnlyNvidiaTlsEndpoints()
{
    QString error;
    QVERIFY(SignalingClient::isTrustedSignalingUrl(
        QUrl(QStringLiteral("wss://stream.nvidiagrid.net:443/nvst/")), &error));
    QVERIFY(!SignalingClient::isTrustedSignalingUrl(
        QUrl(QStringLiteral("ws://stream.nvidiagrid.net/nvst/")), &error));
    QVERIFY(!SignalingClient::isTrustedSignalingUrl(
        QUrl(QStringLiteral("wss://nvidiagrid.net.attacker.invalid/nvst/")), &error));
    QVERIFY(!SignalingClient::isTrustedSignalingUrl(
        QUrl(QStringLiteral("wss://user:password@stream.nvidiagrid.net/nvst/")), &error));
}

void SignalingClientTest::buildsSessionBoundUrlAndProtocol()
{
    QString error;
    const auto url = SignalingClient::buildSignInUrl(
        QStringLiteral("stream.nvidiagrid.net:443"), QStringLiteral("session-value"), {}, &error);
    QVERIFY(error.isEmpty());
    QCOMPARE(url.scheme(), QStringLiteral("wss"));
    QCOMPARE(QUrlQuery(url).queryItemValue(QStringLiteral("pairing_id")),
             QStringLiteral("session-value"));
    QCOMPARE(SignalingClient::sessionProtocol(QStringLiteral("session-value"), &error),
             QStringLiteral("x-nv-sessionid.session-value"));
    QVERIFY(SignalingClient::sessionProtocol(QStringLiteral("bad\r\nvalue"), &error).isEmpty());
}

QTEST_MAIN(SignalingClientTest)
#include "signalingclient_test.moc"
