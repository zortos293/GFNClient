#include "StreamSurfaceController.h"

#include "AppController.h"
#include "CoreClient.h"

#include <QJsonObject>
#include <QCoreApplication>
#include <QDir>
#include <QQuickItem>
#include <QQuickWindow>
#include <QSignalSpy>
#include <QTest>

using Family = StreamSurfaceController::PlatformFamily;

class StreamSurfaceControllerTest final : public QObject
{
    Q_OBJECT

private slots:
    void reportsHonestCompositionModes()
    {
        const auto windows = StreamSurfaceController::capabilityFor(Family::Windows, {});
        QCOMPARE(windows.mode, QStringLiteral("paired-auxiliary"));
        QVERIFY(windows.presentationSupported);
        QVERIFY(!windows.embedded);

        const auto x11 = StreamSurfaceController::capabilityFor(Family::Linux,
                                                                 QStringLiteral("xcb"));
        QCOMPARE(x11.mode, QStringLiteral("paired-auxiliary"));
        QVERIFY(x11.presentationSupported);
        QVERIFY(!x11.embedded);

        const auto wayland = StreamSurfaceController::capabilityFor(
            Family::Linux, QStringLiteral("wayland"));
        QCOMPARE(wayland.mode, QStringLiteral("paired-auxiliary"));
        QVERIFY(wayland.presentationSupported);
        QVERIFY(!wayland.embedded);

        const auto mac = StreamSurfaceController::capabilityFor(Family::MacOS, {});
        QCOMPARE(mac.mode, QStringLiteral("paired-auxiliary"));
        QVERIFY(mac.presentationSupported);
        QVERIFY(!mac.embedded);

        const auto unsupported = StreamSurfaceController::capabilityFor(Family::Other, {});
        QCOMPARE(unsupported.mode, QStringLiteral("unsupported"));
        QVERIFY(!unsupported.presentationSupported);
    }

    void encodesPhysicalLocalAndScreenRects()
    {
        const auto surface = StreamSurfaceController::encodeSurface({
            .windowRect = QRectF(10.0, 20.0, 640.0, 360.0),
            .screenPosition = QPointF(-90.0, 45.0),
            .devicePixelRatio = 1.5,
            .windowHandle = QStringLiteral("0x1234"),
            .visible = true,
        });
        QCOMPARE(surface.value(QStringLiteral("rect")).toObject(),
                 QJsonObject({{QStringLiteral("x"), 15}, {QStringLiteral("y"), 30},
                              {QStringLiteral("width"), 960},
                              {QStringLiteral("height"), 540}}));
        QCOMPARE(surface.value(QStringLiteral("screenRect")).toObject(),
                 QJsonObject({{QStringLiteral("x"), -135}, {QStringLiteral("y"), 68},
                              {QStringLiteral("width"), 960},
                              {QStringLiteral("height"), 540}}));
        QCOMPARE(surface.value(QStringLiteral("logicalScreenRect")).toObject(),
                 QJsonObject({{QStringLiteral("x"), -90}, {QStringLiteral("y"), 45},
                              {QStringLiteral("width"), 640},
                              {QStringLiteral("height"), 360}}));
        QCOMPARE(surface.value(QStringLiteral("deviceScaleFactor")).toDouble(), 1.5);
        QCOMPARE(surface.value(QStringLiteral("windowHandle")).toString(),
                 QStringLiteral("0x1234"));
        QVERIFY(surface.value(QStringLiteral("visible")).toBool());
    }

    void hiddenSurfaceDropsPotentiallyStaleHandle()
    {
        const auto surface = StreamSurfaceController::encodeSurface({
            .windowRect = QRectF(0, 0, 1920, 1080),
            .screenPosition = QPointF(100, 200),
            .devicePixelRatio = 2.0,
            .windowHandle = QStringLiteral("0xdeadbeef"),
            .visible = false,
        });
        QVERIFY(!surface.value(QStringLiteral("visible")).toBool());
        QVERIFY(!surface.contains(QStringLiteral("windowHandle")));
        QCOMPARE(surface.value(QStringLiteral("rect")).toObject()
                     .value(QStringLiteral("width")).toInt(), 3840);
    }

    void rejectsUndersizedVisibleSurface()
    {
        const auto surface = StreamSurfaceController::encodeSurface({
            .windowRect = QRectF(4, 8, 20, 10),
            .screenPosition = QPointF(4, 8),
            .devicePixelRatio = 1.0,
            .windowHandle = QStringLiteral("0x1"),
            .visible = true,
        });
        QVERIFY(!surface.value(QStringLiteral("visible")).toBool());
        QVERIFY(!surface.contains(QStringLiteral("windowHandle")));
        QCOMPARE(surface.value(QStringLiteral("rect")).toObject()
                     .value(QStringLiteral("width")).toInt(), 64);
        QCOMPARE(surface.value(QStringLiteral("rect")).toObject()
                     .value(QStringLiteral("height")).toInt(), 64);
    }

    void surfaceSignalsAreDeduplicatedAndTeardownIsHidden()
    {
        CoreClient core;
        AppController app;
        StreamSurfaceController controller(&core, &app);
        QQuickWindow window;
        window.resize(800, 450);
        QQuickItem host(window.contentItem());
        host.setObjectName(QStringLiteral("streamSurfaceHost"));
        host.setSize(QSizeF(800, 450));
        window.show();
        controller.setWindow(&window);
        QCoreApplication::processEvents();

        QSignalSpy changes(&controller, &StreamSurfaceController::surfaceChanged);
        controller.setWindow(&window);
        QCoreApplication::processEvents();
        QCOMPARE(changes.size(), 0);

        QVERIFY(app.navigate(QStringLiteral("stream")));
        QCoreApplication::processEvents();
        const auto afterRoute = changes.size();
        QVERIFY(afterRoute <= 1);

        host.setWidth(700);
        QCoreApplication::processEvents();
        QCOMPARE(changes.size(), afterRoute + 1);
        QCoreApplication::processEvents();
        QCOMPARE(changes.size(), afterRoute + 1);

        const auto visibleHostSurface = controller.surface();
        host.setVisible(false);
        QCoreApplication::processEvents();
        QVERIFY(!controller.surface().value(QStringLiteral("visible")).toBool());
        QVERIFY(!controller.surface().contains(QStringLiteral("windowHandle")));
        host.setVisible(true);
        QCoreApplication::processEvents();
        QCOMPARE(controller.surface(), visibleHostSurface);

        QVERIFY(app.showOverlay(QStringLiteral("stream-stats")));
        QVERIFY(!controller.surface().value(QStringLiteral("visible")).toBool());
        QVERIFY(!controller.surface().contains(QStringLiteral("windowHandle")));
        QVERIFY(app.showOverlay(QString{}));
        QCOMPARE(controller.surface(), visibleHostSurface);

        controller.teardown();
        QCoreApplication::processEvents();
        QVERIFY(!controller.surface().value(QStringLiteral("visible")).toBool());
        QVERIFY(!controller.surface().contains(QStringLiteral("windowHandle")));
        const auto afterTeardown = changes.size();
        controller.teardown();
        QCOMPARE(changes.size(), afterTeardown);
    }

    void nestedProductionStreamerEventActivatesUpdates()
    {
        auto fakeCore = QDir(QCoreApplication::applicationDirPath())
                            .filePath(QStringLiteral("opennow-fake-core"));
#ifdef Q_OS_WIN
        fakeCore += QStringLiteral(".exe");
#endif
        CoreClient core;
        AppController app;
        StreamSurfaceController controller(&core, &app);
        QSignalSpy responses(&core, &CoreClient::responseReceived);
        QVERIFY(core.start(fakeCore));
        QTRY_COMPARE_WITH_TIMEOUT(core.state(), QStringLiteral("ready"), 2'000);
        QTRY_VERIFY_WITH_TIMEOUT(responses.size() >= 2, 2'000);
        responses.clear();

        const auto eventRequest = core.request(QStringLiteral("test.streamer-nested-event"));
        QVERIFY(!eventRequest.isEmpty());
        QTRY_VERIFY_WITH_TIMEOUT(responses.size() >= 2, 2'000);
        bool sawEventResponse = false;
        bool sawSurfaceResponse = false;
        for (const auto &arguments : responses) {
            const auto id = arguments.at(0).toString();
            sawEventResponse |= id == eventRequest;
            sawSurfaceResponse |= id != eventRequest
                && arguments.at(1).toJsonObject().value(QStringLiteral("applied")).toBool();
        }
        QVERIFY(sawEventResponse);
        QVERIFY(sawSurfaceResponse);
    }
};

QTEST_MAIN(StreamSurfaceControllerTest)
#include "tst_streamsurfacecontroller.moc"
