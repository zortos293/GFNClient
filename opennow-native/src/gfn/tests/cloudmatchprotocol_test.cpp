#include "../cloudmatchprotocol.h"

#include <QTest>

class CloudMatchProtocolTest final : public QObject
{
    Q_OBJECT

private slots:
    void acceptsOnlyTrustedEndpoints();
    void buildsProductionRequestHeaders();
    void validatesLaunchSettings();
};

void CloudMatchProtocolTest::acceptsOnlyTrustedEndpoints()
{
    QString error;
    QCOMPARE(Gfn::CloudMatch::normalizeTrustedBaseUrl(
                 QStringLiteral("https://prod.cloudmatchbeta.nvidiagrid.net"), &error),
             QStringLiteral("https://prod.cloudmatchbeta.nvidiagrid.net"));
    QVERIFY(Gfn::CloudMatch::normalizeTrustedBaseUrl(
                QStringLiteral("http://prod.cloudmatchbeta.nvidiagrid.net"), &error).isEmpty());
    QVERIFY(Gfn::CloudMatch::normalizeTrustedBaseUrl(
                QStringLiteral("https://nvidiagrid.net.attacker.invalid"), &error).isEmpty());
    QVERIFY(Gfn::CloudMatch::normalizeTrustedBaseUrl(
                QStringLiteral("https://user:password@prod.cloudmatchbeta.nvidiagrid.net"), &error).isEmpty());
}

void CloudMatchProtocolTest::buildsProductionRequestHeaders()
{
    const auto headers = Gfn::CloudMatch::requestHeaders(QStringLiteral("token"),
                                                          QStringLiteral("client"),
                                                          QStringLiteral("device"), true);
    QCOMPARE(headers.value(QByteArrayLiteral("Authorization")), QByteArrayLiteral("GFNJWT token"));
    QCOMPARE(headers.value(QByteArrayLiteral("nv-client-id")), QByteArrayLiteral("client"));
    QCOMPARE(headers.value(QByteArrayLiteral("x-device-id")), QByteArrayLiteral("device"));
    QVERIFY(headers.contains(QByteArrayLiteral("Origin")));
}

void CloudMatchProtocolTest::validatesLaunchSettings()
{
    const auto settings = Gfn::CloudMatch::StreamSettings::fromVariantMap({
        {QStringLiteral("resolution"), QStringLiteral("99999x1")},
        {QStringLiteral("fps"), 500},
        {QStringLiteral("maxBitrateKbps"), 1},
        {QStringLiteral("codec"), QStringLiteral("UNKNOWN")},
    });
    QVERIFY(settings.width <= 7680);
    QVERIFY(settings.height >= 480);
    QVERIFY(settings.framesPerSecond <= 240);
    QVERIFY(settings.maxBitrateKbps >= 1000);
    QCOMPARE(settings.codec, QStringLiteral("H264"));
}

QTEST_MAIN(CloudMatchProtocolTest)
#include "cloudmatchprotocol_test.moc"
