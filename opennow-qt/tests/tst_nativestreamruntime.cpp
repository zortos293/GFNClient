#include "NativeStreamRuntime.h"

#include <QElapsedTimer>
#include <QFile>
#include <QScopeGuard>
#include <QTemporaryDir>
#include <QJsonDocument>
#include <QSignalSpy>
#include <QTest>

#include <chrono>
#include <condition_variable>
#include <mutex>
#include <thread>
#include <utility>

namespace {
struct FakeRuntime {
    OpenNowStreamerConfig config{};
    int destroyDelayMs = 0;
};

int nextDestroyDelayMs = 0;
OpenNowStreamerStatus sendStatus = OPENNOW_STREAMER_OK;
std::mutex graphicsCallMutex;
std::condition_variable graphicsCallChanged;
bool recordCallEntered = false;
bool allowRecordCallToFinish = false;
bool inputCallFinished = false;

OpenNowStreamerStatus fakeCreate(const OpenNowStreamerConfig *config,
                                 OpenNowStreamer **output)
{
    if (!config || !output) return OPENNOW_STREAMER_NULL_POINTER;
    auto *runtime = new FakeRuntime{*config, nextDestroyDelayMs};
    *output = reinterpret_cast<OpenNowStreamer *>(runtime);
    return OPENNOW_STREAMER_OK;
}

OpenNowStreamerStatus fakeSend(const OpenNowStreamer *handle, const std::uint8_t *bytes,
                               std::size_t length)
{
    if (!handle || (!bytes && length)) return OPENNOW_STREAMER_NULL_POINTER;
    if (sendStatus != OPENNOW_STREAMER_OK) return sendStatus;
    const auto *runtime = reinterpret_cast<const FakeRuntime *>(handle);
    const QByteArray command(reinterpret_cast<const char *>(bytes),
                             static_cast<qsizetype>(length));
    std::thread callback([runtime, command] {
        const auto object = QJsonDocument::fromJson(command).object();
        const auto response = QJsonDocument(QJsonObject{{QStringLiteral("id"),
                                                          object.value(QStringLiteral("id"))},
                                                         {QStringLiteral("type"),
                                                          QStringLiteral("ok")}})
                                  .toJson(QJsonDocument::Compact);
        runtime->config.response_callback(
            reinterpret_cast<const std::uint8_t *>(response.constData()),
            static_cast<std::size_t>(response.size()), runtime->config.user_data);
        if (runtime->config.frame_available_callback)
            runtime->config.frame_available_callback(runtime->config.user_data);
        if (runtime->config.cursor_callback) {
            const std::uint8_t cursor[] = {0, 12, 0, 0, 0, 0, 0};
            runtime->config.cursor_callback(cursor, sizeof(cursor), runtime->config.user_data);
        }
    });
    callback.join();
    return OPENNOW_STREAMER_OK;
}

OpenNowStreamerStatus fakeDestroy(OpenNowStreamer *handle)
{
    if (!handle) return OPENNOW_STREAMER_NULL_POINTER;
    auto *runtime = reinterpret_cast<FakeRuntime *>(handle);
    std::this_thread::sleep_for(std::chrono::milliseconds(runtime->destroyDelayMs));
    delete runtime;
    return OPENNOW_STREAMER_OK;
}

NativeStreamRuntime::Api fakeApi()
{
    return {&fakeCreate, &fakeSend, &fakeDestroy};
}

OpenNowStreamerStatus fakeAcquireLatestFrame(
    const OpenNowStreamer *handle, OpenNowStreamerFrame **frame,
    OpenNowStreamerFrameInfo *info)
{
    if (!handle || !frame || !info) return OPENNOW_STREAMER_NULL_POINTER;
    *frame = reinterpret_cast<OpenNowStreamerFrame *>(new int(1));
    *info = OpenNowStreamerFrameInfo{1920, 1080, 1, 0};
    return OPENNOW_STREAMER_OK;
}

OpenNowStreamerStatus fakeRecordFrame(
    const OpenNowStreamer *handle, const OpenNowStreamerFrame *frame,
    const OpenNowStreamerRecordCommand *, OpenNowStreamerRecordedFrame *recorded)
{
    if (!handle || !frame || !recorded) return OPENNOW_STREAMER_NULL_POINTER;
    std::unique_lock lock(graphicsCallMutex);
    recordCallEntered = true;
    graphicsCallChanged.notify_all();
    graphicsCallChanged.wait(lock, [] { return allowRecordCallToFinish; });
    *recorded = OpenNowStreamerRecordedFrame{
        1, 0, OPENNOW_STREAMER_GRAPHICS_API_D3D11,
        OPENNOW_STREAMER_TEXTURE_FORMAT_RGBA8, 1920, 1080, 0, 1, 0};
    return OPENNOW_STREAMER_OK;
}

OpenNowStreamerStatus fakeReleaseFrame(OpenNowStreamerFrame *frame)
{
    if (!frame) return OPENNOW_STREAMER_NULL_POINTER;
    delete reinterpret_cast<int *>(frame);
    return OPENNOW_STREAMER_OK;
}

OpenNowStreamerStatus fakeSubmitKey(
    const OpenNowStreamer *handle, std::uint16_t, std::uint16_t, bool)
{
    if (!handle) return OPENNOW_STREAMER_NULL_POINTER;
    {
        const std::lock_guard lock(graphicsCallMutex);
        inputCallFinished = true;
    }
    graphicsCallChanged.notify_all();
    return OPENNOW_STREAMER_OK;
}

NativeStreamRuntime::Api blockingGraphicsApi()
{
    auto api = fakeApi();
    api.acquireLatestFrame = &fakeAcquireLatestFrame;
    api.recordFrame = &fakeRecordFrame;
    api.releaseFrame = &fakeReleaseFrame;
    api.submitKey = &fakeSubmitKey;
    return api;
}
}

class NativeStreamRuntimeTest final : public QObject
{
    Q_OBJECT

private slots:
    void init()
    {
        nextDestroyDelayMs = 0;
        sendStatus = OPENNOW_STREAMER_OK;
        const std::lock_guard lock(graphicsCallMutex);
        recordCallEntered = false;
        allowRecordCallToFinish = false;
        inputCallFinished = false;
    }

    void presentationFailuresAreMarshalledToTheQtThread()
    {
        NativeStreamRuntime runtime;
        QSignalSpy errors(&runtime, &NativeStreamRuntime::presentationError);
        std::thread render([&runtime] {
            runtime.reportPresentationError(QStringLiteral("Texture import failed"));
        });
        render.join();
        QCOMPARE(errors.size(), 0);
        QTRY_COMPARE(errors.size(), 1);
        QCOMPARE(runtime.lastError(), QStringLiteral("Texture import failed"));
    }

    void presentationIsInvalidatedBetweenNativeSessions()
    {
        NativeStreamRuntime runtime(fakeApi());
        QVERIFY(runtime.start());
        QVERIFY(!runtime.presentationAllowed());
        const auto first = runtime.presentationGeneration();
        QVERIFY(runtime.send({{QStringLiteral("type"), QStringLiteral("start")},
                              {QStringLiteral("id"), QStringLiteral("start-1")}}));
        QVERIFY(runtime.presentationGeneration() > first);
        QVERIFY(!runtime.presentationAllowed());
        QTRY_VERIFY(runtime.presentationAllowed());
        const auto running = runtime.presentationGeneration();
        QVERIFY(runtime.send({{QStringLiteral("type"), QStringLiteral("stop")},
                              {QStringLiteral("id"), QStringLiteral("stop-1")}}));
        QVERIFY(!runtime.presentationAllowed());
        QVERIFY(runtime.presentationGeneration() > running);
        QVERIFY(runtime.send({{QStringLiteral("type"), QStringLiteral("start")},
                              {QStringLiteral("id"), QStringLiteral("start-2")}}));
        QVERIFY(!runtime.presentationAllowed());
        QTRY_VERIFY(runtime.presentationAllowed());
        runtime.reportPresentationError(QStringLiteral("device lost"));
        QTRY_VERIFY(!runtime.presentationAllowed());
        QVERIFY(runtime.shutdown());
    }

    void copiesAndMarshalsWorkerCallbacksToTheQtThread()
    {
        NativeStreamRuntime runtime(fakeApi());
        QSignalSpy responses(&runtime, &NativeStreamRuntime::responseReceived);
        QSignalSpy frames(&runtime, &NativeStreamRuntime::frameAvailable);
        QSignalSpy cursors(&runtime, &NativeStreamRuntime::cursorUpdated);
        QVERIFY(runtime.start());
        QVERIFY(runtime.send(QJsonObject{{QStringLiteral("id"), QStringLiteral("hello")},
                                         {QStringLiteral("type"), QStringLiteral("hello")}}));

        QTRY_COMPARE_WITH_TIMEOUT(responses.size(), 1, 1'000);
        QCOMPARE(responses.first().first().toJsonObject().value(QStringLiteral("id")).toString(),
                 QStringLiteral("hello"));
        QCOMPARE(responses.first().first().toJsonObject().value(QStringLiteral("type")).toString(),
                 QStringLiteral("ok"));
        QTRY_COMPARE_WITH_TIMEOUT(frames.size(), 1, 1'000);
        QTRY_COMPARE_WITH_TIMEOUT(cursors.size(), 1, 1'000);
        QCOMPARE(cursors.first().first().toByteArray(), QByteArray::fromHex("000c0000000000"));
        QVERIFY(runtime.shutdown());
    }

    void discardsPresentationErrorsFromAnEarlierSession()
    {
        NativeStreamRuntime runtime(fakeApi());
        QSignalSpy errors(&runtime, &NativeStreamRuntime::presentationError);
        QVERIFY(runtime.start());
        runtime.reportPresentationError(QStringLiteral("old device lost"));
        QVERIFY(runtime.send({{QStringLiteral("type"), QStringLiteral("start")},
                              {QStringLiteral("id"), QStringLiteral("new-session")}}));
        QTRY_VERIFY(runtime.presentationAllowed());
        QCOMPARE(errors.size(), 0);
        QVERIFY(runtime.lastError().isEmpty());
        QVERIFY(runtime.shutdown());
    }

    void rejectedSessionCommandsPreservePresentation()
    {
        NativeStreamRuntime runtime(fakeApi());
        QVERIFY(runtime.start());
        QVERIFY(runtime.send({{QStringLiteral("type"), QStringLiteral("start")},
                              {QStringLiteral("id"), QStringLiteral("active-session")}}));
        QTRY_VERIFY(runtime.presentationAllowed());
        const auto generation = runtime.presentationGeneration();
        sendStatus = OPENNOW_STREAMER_QUEUE_FULL;
        for (const auto &type : {QStringLiteral("stop"), QStringLiteral("start")}) {
            QVERIFY(!runtime.send({{QStringLiteral("type"), type},
                                   {QStringLiteral("id"), QStringLiteral("rejected")}}));
            QVERIFY(runtime.presentationAllowed());
            QCOMPARE(runtime.presentationGeneration(), generation);
        }
        const auto oversized = QJsonDocument(QJsonObject{
            {QStringLiteral("type"), QStringLiteral("stop")},
            {QStringLiteral("padding"), QString(NativeStreamRuntime::MaximumCallbackBytes, 'x')}
        }).toJson(QJsonDocument::Compact);
        QVERIFY(!runtime.sendBytes(oversized));
        QVERIFY(runtime.presentationAllowed());
        QCOMPARE(runtime.presentationGeneration(), generation);
        QVERIFY(runtime.shutdown());
    }

    void discardsDrainedCallbacksWhenASignalRestartsTheRuntime()
    {
        NativeStreamRuntime runtime(fakeApi());
        QSignalSpy responses(&runtime, &NativeStreamRuntime::responseReceived);
        QSignalSpy cursors(&runtime, &NativeStreamRuntime::cursorUpdated);
        QVERIFY(runtime.start());
        bool restarted = false;
        connect(&runtime, &NativeStreamRuntime::frameAvailable, &runtime, [&] {
            if (restarted) return;
            restarted = true;
            QVERIFY(runtime.shutdown());
            QVERIFY(runtime.start());
        });
        QVERIFY(runtime.send({{QStringLiteral("type"), QStringLiteral("hello")},
                              {QStringLiteral("id"), QStringLiteral("old-runtime")}}));
        QTRY_VERIFY(restarted);
        QCOMPARE(responses.size(), 0);
        QCOMPARE(cursors.size(), 0);
        QVERIFY(runtime.send({{QStringLiteral("type"), QStringLiteral("hello")},
                              {QStringLiteral("id"), QStringLiteral("new-runtime")}}));
        QTRY_COMPARE(responses.size(), 1);
        QCOMPARE(responses.first().first().toJsonObject().value(QStringLiteral("id")).toString(),
                 QStringLiteral("new-runtime"));
        QTRY_COMPARE(cursors.size(), 1);
        QVERIFY(runtime.shutdown());
    }

    void callbackHandlerCanDeleteTheRuntime()
    {
        auto *runtime = new NativeStreamRuntime(fakeApi());
        QSignalSpy responses(runtime, &NativeStreamRuntime::responseReceived);
        QSignalSpy cursors(runtime, &NativeStreamRuntime::cursorUpdated);
        QVERIFY(runtime->start());
        connect(runtime, &NativeStreamRuntime::responseReceived, this, [&] {
            delete std::exchange(runtime, nullptr);
        });
        QVERIFY(runtime->send({{QStringLiteral("type"), QStringLiteral("hello")},
                               {QStringLiteral("id"), QStringLiteral("delete-runtime")}}));
        QTRY_VERIFY(!runtime);
        QCOMPARE(responses.size(), 1);
        QCOMPARE(cursors.size(), 0);
    }

    void boundsShutdownWhenTheFfiDestroyCallStalls()
    {
        nextDestroyDelayMs = 300;
        NativeStreamRuntime runtime(fakeApi());
        QVERIFY(runtime.start());

        QElapsedTimer timer;
        timer.start();
        QVERIFY(!runtime.shutdown(20));
        QVERIFY2(timer.elapsed() < 200, "shutdown blocked past its bounded deadline");
        QVERIFY(!runtime.running());
        QVERIFY(runtime.lastError().contains(QStringLiteral("Timed out")));
        QTest::qWait(350);
    }

    void inputDoesNotQueueBehindAStalledRenderCall()
    {
        NativeStreamRuntime runtime(blockingGraphicsApi());
        QVERIFY(runtime.start());
        OpenNowStreamerRecordCommand command{};
        OpenNowStreamerFrameInfo info{};
        OpenNowStreamerRecordedFrame recorded{};
        OpenNowStreamerFrame *frame = nullptr;
        OpenNowStreamerStatus recordStatus = OPENNOW_STREAMER_CLOSED;
        std::thread render([&] {
            recordStatus = runtime.recordLatestFrame(command, &info, &recorded, &frame);
        });
        bool recordStarted = false;
        {
            std::unique_lock lock(graphicsCallMutex);
            recordStarted = graphicsCallChanged.wait_for(
                lock, std::chrono::seconds(1), [] { return recordCallEntered; });
            if (!recordStarted) allowRecordCallToFinish = true;
        }
        graphicsCallChanged.notify_all();
        if (!recordStarted) {
            render.join();
            QVERIFY2(recordStarted, "graphics FFI call did not start");
            return;
        }

        OpenNowStreamerStatus inputStatus = OPENNOW_STREAMER_CLOSED;
        std::thread input([&] { inputStatus = runtime.submitKey(0x57, 0, true); });
        bool inputWasConcurrent = false;
        {
            std::unique_lock lock(graphicsCallMutex);
            inputWasConcurrent = graphicsCallChanged.wait_for(
                lock, std::chrono::milliseconds(200), [] { return inputCallFinished; });
            allowRecordCallToFinish = true;
        }
        graphicsCallChanged.notify_all();
        input.join();
        render.join();

        QVERIFY2(inputWasConcurrent, "gameplay input waited behind the graphics FFI call");
        QCOMPARE(inputStatus, OPENNOW_STREAMER_OK);
        QCOMPARE(recordStatus, OPENNOW_STREAMER_OK);
        QCOMPARE(runtime.releaseFrame(frame), OPENNOW_STREAMER_OK);
        QVERIFY(runtime.shutdown());
    }

    void roundTripsThroughTheLinkedRustFfi()
    {
        QTemporaryDir data;
        QVERIFY(data.isValid());
        const bool hadOverride = qEnvironmentVariableIsSet("OPENNOW_DATA_DIR");
        const auto oldOverride = qgetenv("OPENNOW_DATA_DIR");
        const auto restore = qScopeGuard([&] {
            if (hadOverride) qputenv("OPENNOW_DATA_DIR", oldOverride);
            else qunsetenv("OPENNOW_DATA_DIR");
        });
        qputenv("OPENNOW_DATA_DIR", data.path().toUtf8());
        NativeStreamRuntime runtime;
        QSignalSpy responses(&runtime, &NativeStreamRuntime::responseReceived);
        QVERIFY2(runtime.start(), qPrintable(runtime.lastError()));
        QVERIFY2(runtime.send(QJsonObject{{QStringLiteral("id"), QStringLiteral("abi-hello")},
                                          {QStringLiteral("type"), QStringLiteral("hello")},
                                          {QStringLiteral("protocolVersion"), 5}}),
                 qPrintable(runtime.lastError()));
        QTRY_VERIFY_WITH_TIMEOUT(!responses.isEmpty(), 5'000);
        QCOMPARE(responses.first().first().toJsonObject().value(QStringLiteral("id")).toString(),
                 QStringLiteral("abi-hello"));
        QVERIFY2(runtime.shutdown(), qPrintable(runtime.lastError()));
        QFile log(data.filePath(QStringLiteral("diagnostics/native-streamer.log")));
        QVERIFY2(log.open(QIODevice::ReadOnly), "native log must share the core diagnostics directory");
        const auto nativeText = log.readAll();
        QVERIFY(nativeText.contains("file log configured"));
        QVERIFY(nativeText.contains("qt-to-native id=abi-hello type=hello"));
        QVERIFY(nativeText.contains("native-to-qt id=abi-hello"));
        QFile qtLog(data.filePath(QStringLiteral("diagnostics/qt-native.log")));
        QVERIFY(qtLog.open(QIODevice::ReadOnly));
        const auto qtText = qtLog.readAll();
        QVERIFY(qtText.contains("build=diagnostics-v2"));
        QVERIFY(qtText.contains("send id=abi-hello type=hello"));
        QVERIFY(qtText.contains("delivered id=abi-hello"));
    }
};

QTEST_MAIN(NativeStreamRuntimeTest)
#include "tst_nativestreamruntime.moc"
