#include "streaming/rendering/StreamFramePacer.h"

#include <QTest>
#include <limits>

class FramePacerTest : public QObject
{
    Q_OBJECT
private slots:
    void insertsExactlyOneMidpoint()
    {
        StreamFramePacer pacer;
        using Result = StreamFramePacer::Result;
        QCOMPARE(pacer.source(40, 0, 0, 120), Result::WarmingUp);
        QCOMPARE(pacer.source(41, 16'666'667, 16'666'667, 120), Result::Interpolate);
        pacer.midpoint(16'666'667);
        QVERIFY(pacer.pending());
        QCOMPARE(pacer.source(41, 16'666'667, 20'000'000, 120), Result::Duplicate);
        QVERIFY(pacer.takeOriginal(24'000'000, 120));
        QVERIFY(!pacer.pending());
        QVERIFY(!pacer.takeOriginal(25'000'000, 120));
        QCOMPARE(pacer.source(42, 33'333'334, 33'333'334, 120), Result::Interpolate);
    }

    void rejectsMissingOrDiscontinuousSources()
    {
        StreamFramePacer pacer;
        using Result = StreamFramePacer::Result;
        pacer.source(1, 16'666'667, 0, 120);
        QCOMPARE(pacer.source(3, 50'000'000, 33'333'333, 120), Result::Discontinuity);
        QCOMPARE(pacer.source(4, 40'000'000, 40'000'000, 120), Result::Discontinuity);
        QCOMPARE(pacer.source(5, 56'666'667, 200'000'000, 120), Result::Discontinuity);
        pacer.reset();
        QCOMPARE(pacer.source(1, 16'666'667, 0, 120), Result::WarmingUp);
        QCOMPARE(pacer.source(2, std::numeric_limits<std::uint64_t>::max(), 1, 120), Result::Discontinuity);
    }

    void requiresEnoughDisplayRefresh()
    {
        using Result = StreamFramePacer::Result;
        for (double refresh : {0.0, 60.0, 90.0, std::numeric_limits<double>::quiet_NaN()}) {
            StreamFramePacer pacer;
            pacer.source(1, 0, 0, refresh);
            QCOMPARE(pacer.source(2, 16'666'667, 16'666'667, refresh), Result::DisplayTooSlow);
        }
        StreamFramePacer pacer;
        pacer.source(1, 0, 0, 59.94);
        QCOMPARE(pacer.source(2, 33'366'700, 33'366'700, 59.94), Result::Interpolate);
    }

    void overloadDoesNotBuildAQueue()
    {
        using Result = StreamFramePacer::Result;
        StreamFramePacer pacer;
        pacer.source(1, 0, 0, 120);
        pacer.source(2, 16'666'667, 16'666'667, 120);
        pacer.midpoint(16'666'667);
        QCOMPARE(pacer.source(3, 33'333'334, 33'333'334, 120), Result::Overloaded);
        QVERIFY(!pacer.pending());
        QCOMPARE(pacer.source(4, 50'000'001, 50'000'001, 120), Result::Overloaded);
        for (std::uint64_t frame = 5; frame < 130; ++frame)
            pacer.source(frame, (frame - 1) * 16'666'667, (frame - 1) * 16'666'667, 120);
        QCOMPARE(pacer.source(130, 129 * 16'666'667ULL, 129 * 16'666'667LL, 120), Result::Interpolate);
    }

    void highRefreshDoesNotCompressTheOriginalPair()
    {
        StreamFramePacer pacer;
        pacer.source(1, 0, 0, 360);
        pacer.source(2, 16'666'667, 16'666'667, 360);
        pacer.midpoint(16'666'667);
        QVERIFY(!pacer.takeOriginal(17'000'000, 360));
        QVERIFY(pacer.takeOriginal(22'000'000, 360));
    }
};

QTEST_APPLESS_MAIN(FramePacerTest)
#include "tst_framepacer.moc"
