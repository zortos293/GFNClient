#include "SingleInstance.h"

#include <QSignalSpy>
#include <QTest>

class SingleInstanceTest final : public QObject
{
    Q_OBJECT

private slots:
    void forwardsArgumentsToPrimary()
    {
        SingleInstance primary;
        QVERIFY(primary.acquire({QStringLiteral("opennow"), QStringLiteral("opennow://launch/42")}));
        QSignalSpy activation(&primary, &SingleInstance::activationRequested);

        SingleInstance secondary;
        QVERIFY(!secondary.acquire({QStringLiteral("opennow"), QStringLiteral("opennow://launch/99")}));
        QTRY_COMPARE_WITH_TIMEOUT(activation.size(), 1, 2'000);
        const auto arguments = activation.first().first().toStringList();
        QCOMPARE(arguments.value(1), QStringLiteral("opennow://launch/99"));
    }
};

QTEST_GUILESS_MAIN(SingleInstanceTest)
#include "tst_singleinstance.moc"
