#include "StreamVideoItem.h"
#include "NativeStreamRuntime.h"

#include <QGuiApplication>
#include <QtQml/qqml.h>
#include <QQuickRhiItem>
#include <QQuickWindow>
#include <QSignalSpy>
#include <QTest>

#include <atomic>
#include <memory>

class TestRenderCallback final : public StreamVideoRenderCallback
{
public:
    void initialize(QRhi *rhi,
                    QRhiCommandBuffer *commandBuffer,
                    QRhiRenderTarget *renderTarget) override
    {
        validContext.store(rhi && commandBuffer && renderTarget);
        ++initializeCount;
    }

    void recordFrame(QRhiCommandBuffer *commandBuffer, const QRect &viewport) override
    {
        validContext.store(validContext.load() && commandBuffer);
        viewportWidth.store(viewport.width());
        viewportHeight.store(viewport.height());
        ++frameCount;
    }

    void prepareFrame(QRhiCommandBuffer *commandBuffer) override
    {
        validContext.store(validContext.load() && commandBuffer);
        ++prepareCount;
    }

    void finishFrame() override
    {
        ++finishCount;
    }

    void releaseResources() override
    {
        ++releaseCount;
    }

    std::atomic_bool validContext = false;
    std::atomic_int initializeCount = 0;
    std::atomic_int frameCount = 0;
    std::atomic_int prepareCount = 0;
    std::atomic_int finishCount = 0;
    std::atomic_int releaseCount = 0;
    std::atomic_int viewportWidth = 0;
    std::atomic_int viewportHeight = 0;
};

class StreamVideoItemTest final : public QObject
{
    Q_OBJECT

private slots:
    void initTestCase()
    {
        registerStreamVideoItemQmlType();
    }

    void calculatesCenteredAspectFitViewport()
    {
        QCOMPARE(StreamVideoItem::aspectFitRect(QSize(1920, 1080), QSize(1000, 1000)),
                 QRect(0, 219, 1000, 562));
        QCOMPARE(StreamVideoItem::aspectFitRect(QSize(1000, 1000), QSize(1920, 1080)),
                 QRect(420, 0, 1080, 1080));
        QCOMPARE(StreamVideoItem::aspectFitRect(QSize(1280, 720), QSize(2560, 1440)),
                 QRect(0, 0, 2560, 1440));
    }

    void handlesUnknownAndInvalidSizesPredictably()
    {
        QCOMPARE(StreamVideoItem::aspectFitRect(QSize(), QSize(640, 360)),
                 QRect(0, 0, 640, 360));
        QCOMPARE(StreamVideoItem::aspectFitRect(QSize(1920, 1080), QSize()), QRect());
    }

    void mapsQtKeyboardStateToTypedGfnInputFields()
    {
        QCOMPARE(StreamVideoItem::windowsVirtualKey(Qt::Key_W), quint16(0x57));
        QCOMPARE(StreamVideoItem::windowsVirtualKey(Qt::Key_Escape), quint16(0x1b));
        QCOMPARE(StreamVideoItem::windowsVirtualKey(Qt::Key_F24), quint16(0x87));
        QCOMPARE(StreamVideoItem::windowsVirtualKey(Qt::Key_unknown), quint16(0));
        QCOMPARE(StreamVideoItem::inputModifiers(
                     Qt::ShiftModifier | Qt::ControlModifier, Qt::Key_W), quint16(0x03));
        QCOMPARE(StreamVideoItem::inputModifiers(Qt::ShiftModifier, Qt::Key_Shift), quint16(0));
    }

    void inputEnablementIsExplicitAndObservable()
    {
        StreamVideoItem item;
        QSignalSpy changes(&item, &StreamVideoItem::inputEnabledChanged);
        QVERIFY(item.inputEnabled());
        item.setInputEnabled(false);
        QVERIFY(!item.inputEnabled());
        QCOMPARE(changes.size(), 1);
        item.setInputEnabled(false);
        QCOMPARE(changes.size(), 1);
    }

    void normalizesVideoSizeAndTracksCallbackAvailability()
    {
        StreamVideoItem item;
        QSignalSpy sizeChanges(&item, &StreamVideoItem::videoSizeChanged);
        QSignalSpy callbackChanges(&item, &StreamVideoItem::renderCallbackAvailableChanged);

        item.setVideoSize(QSize(-1, 1080));
        QCOMPARE(item.videoSize(), QSize());
        QCOMPARE(sizeChanges.size(), 0);

        item.setVideoSize(QSize(1920, 1080));
        QCOMPARE(item.videoSize(), QSize(1920, 1080));
        QCOMPARE(sizeChanges.size(), 1);

        const auto callback = std::make_shared<TestRenderCallback>();
        item.setRenderCallback(callback);
        QVERIFY(item.renderCallbackAvailable());
        QVERIFY(item.renderCallback() == callback);
        QCOMPARE(callbackChanges.size(), 1);

        item.setRenderCallback(callback);
        QCOMPARE(callbackChanges.size(), 1);
        item.setRenderCallback({});
        QVERIFY(!item.renderCallbackAvailable());
        QCOMPARE(callbackChanges.size(), 2);
    }

    void registersConcreteQmlSceneGraphType()
    {
        QVERIFY(qmlTypeId("OpenNOW", 1, 0, "StreamVideoItem") >= 0);
        StreamVideoItem item;
        QVERIFY(qobject_cast<QQuickRhiItem *>(&item));
    }

    void createsRenderCallbackFromTheSharedNativeRuntime()
    {
        NativeStreamRuntime runtime;
        StreamVideoItem::setNativeStreamRuntime(&runtime);
        {
            StreamVideoItem item;
            QCOMPARE(StreamVideoItem::nativeStreamRuntime(), &runtime);
            QVERIFY(item.renderCallbackAvailable());
        }
        StreamVideoItem::setNativeStreamRuntime(nullptr);
    }

    void drivesCallbackThroughRhiSceneGraph()
    {
        if (QGuiApplication::platformName() == QStringLiteral("offscreen"))
            QSKIP("The offscreen platform plugin does not create a QRhi.");

        const auto callback = std::make_shared<TestRenderCallback>();
        {
            QQuickWindow window;
            window.resize(640, 480);
            auto *item = new StreamVideoItem(window.contentItem());
            item->setSize(QSizeF(640, 480));
            item->setVideoSize(QSize(1920, 1080));
            item->setRenderCallback(callback);
            window.show();
            item->requestFrame();

            QTRY_VERIFY_WITH_TIMEOUT(callback->initializeCount.load() > 0, 5'000);
            QTRY_VERIFY_WITH_TIMEOUT(callback->frameCount.load() > 0, 5'000);
            QVERIFY(callback->prepareCount.load() > 0);
            QTRY_VERIFY_WITH_TIMEOUT(callback->finishCount.load() > 0, 5'000);
            QVERIFY(callback->validContext.load());
            QCOMPARE(callback->viewportWidth.load(), 640);
            QCOMPARE(callback->viewportHeight.load(), 360);
        }
        QTRY_VERIFY_WITH_TIMEOUT(callback->releaseCount.load() > 0, 5'000);
    }
};

QTEST_MAIN(StreamVideoItemTest)
#include "tst_streamvideoitem.moc"
