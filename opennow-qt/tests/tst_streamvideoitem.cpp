#include "StreamVideoItem.h"
#include "LinuxVulkanGraphics.h"
#include "NativeStreamRuntime.h"
#include "StreamVideoTextureRenderer.h"

#include <QGuiApplication>
#include <QCursor>
#include <QScopeGuard>
#include <QtQml/qqml.h>
#include <QQuickWindow>
#include <QSGSimpleRectNode>
#include <QSignalSpy>
#include <QTest>

#include <atomic>
#include <memory>

#if QT_CONFIG(vulkan) && __has_include(<vulkan/vulkan.h>)
#include <vulkan/vulkan.h>
#endif

#if defined(Q_OS_WIN)
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#endif

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

// Exercise the production import/material with a GPU texture, without a remote
// account or a synthetic alternate presenter. Readbacks are test-only.
class TextureRenderCallback final : public StreamVideoRenderCallback
{
public:
    void initialize(QRhi *rhi, QRhiCommandBuffer *, QRhiRenderTarget *target) override
    {
        if (m_rhi != rhi) releaseResources();
        m_rhi = rhi;
        renderer.initialize(rhi, target);
        directTarget.store(target->resourceType() == QRhiResource::SwapChainRenderTarget);
    }
    void setComposition(const QMatrix4x4 &matrix, const QRectF &bounds,
                        const QRectF &viewport, float opacity) override
    {
        renderer.setComposition(matrix, bounds, viewport, opacity);
    }
    void prepareFrame(QRhiCommandBuffer *cb) override
    {
        if (!showVideo.load()) {
            renderer.clearFrames();
            imported.store(false);
            return;
        }
        if (!renderer.prepare(cb)) return;
        if (!texture) {
            texture.reset(m_rhi->newTexture(QRhiTexture::RGBA8, QSize(4, 4)));
            if (!texture->create()) return;
            QImage image(4, 4, QImage::Format_RGBA8888);
            for (int y = 0; y < 4; ++y)
                for (int x = 0; x < 4; ++x)
                    image.setPixelColor(x, y, y < 2 ? Qt::red : Qt::green);
            auto *updates = m_rhi->nextResourceUpdateBatch();
            updates->uploadTexture(texture.get(), image);
            cb->resourceUpdate(updates);
        }
#if QT_CONFIG(vulkan) && __has_include(<vulkan/vulkan.h>)
        if (textureWasSampled && m_rhi->backend() == QRhi::Vulkan)
            texture->setNativeLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
#endif
        imported.store(renderer.importFrame(m_rhi->currentFrameSlot(), texture->nativeTexture(),
                                            QRhiTexture::RGBA8, QSize(4, 4)));
        importedSlots.store(int(renderer.importedSlotCount()));
    }
    void setClip(bool enabled, int reference) override { stencil = enabled; stencilReference = reference; }
    void recordFrame(QRhiCommandBuffer *cb, const QRect &) override
    {
        renderer.render(cb, stencil, stencilReference);
        if (imported.load()) textureWasSampled = true;
        ++frames;
    }
    void finishFrame() override {}
    void releaseResources() override
    {
        renderer.release();
        texture.reset();
        textureWasSampled = false;
        m_rhi = nullptr;
        ++releases;
    }
    std::atomic_bool imported = false;
    std::atomic_bool directTarget = false;
    std::atomic_int frames = 0;
    std::atomic_int importedSlots = 0;
    std::atomic_bool showVideo = true;
    std::atomic_int releases = 0;
private:
    QRhi *m_rhi = nullptr;
    StreamVideoTextureRenderer renderer;
    std::unique_ptr<QRhiTexture> texture;
    bool textureWasSampled = false;
    bool stencil = false;
    int stencilReference = 0;
};

class WhiteOverlay final : public QQuickItem
{
public:
    explicit WhiteOverlay(QQuickItem *parent) : QQuickItem(parent) { setFlag(ItemHasContents); }
protected:
    QSGNode *updatePaintNode(QSGNode *old, UpdatePaintNodeData *) override
    {
        auto *node = static_cast<QSGSimpleRectNode *>(old);
        if (!node) node = new QSGSimpleRectNode;
        node->setRect(boundingRect());
        node->setColor(Qt::white);
        return node;
    }
};

class StreamVideoItemTest final : public QObject
{
    Q_OBJECT

private slots:
    void initTestCase()
    {
        registerStreamVideoItemQmlType();
    }

    void linuxDmabufRequiresEnabledExtensionsAndVulkanPrerequisites()
    {
#if defined(Q_OS_LINUX) && QT_CONFIG(vulkan) && __has_include(<vulkan/vulkan.h>)
        using namespace LinuxVulkanGraphics;
        const auto required = deviceExtensions();
        QVERIFY(hasDmabufImportContract(QVersionNumber(1, 1), VK_API_VERSION_1_1,
                                        required, required));
        QVERIFY(!hasDmabufImportContract(QVersionNumber(1, 1), VK_API_VERSION_1_1,
                                         {}, required));
        QVERIFY(!hasDmabufImportContract(QVersionNumber(1, 0), VK_API_VERSION_1_1,
                                         required, required));
        QVERIFY(!hasDmabufImportContract(QVersionNumber(1, 1), VK_API_VERSION_1_0,
                                         required, required));
        const QByteArrayList mandatory = {"VK_KHR_external_memory_fd", "VK_EXT_external_memory_dma_buf",
                                         "VK_EXT_image_drm_format_modifier", "VK_KHR_image_format_list"};
        for (const auto &extension : mandatory) {
            auto missing = required;
            missing.removeAll(extension);
            QVERIFY(!hasDmabufImportContract(QVersionNumber(1, 1), VK_API_VERSION_1_1,
                                             missing, required));
            QVERIFY(!hasDmabufImportContract(QVersionNumber(1, 1), VK_API_VERSION_1_1,
                                             required, missing));
        }
        auto promoted = mandatory;
        promoted.removeAll("VK_KHR_image_format_list");
        QVERIFY(hasDmabufImportContract(QVersionNumber(1, 2), VK_API_VERSION_1_2,
                                        promoted, promoted));
        QVERIFY(!hasDmabufImportContract(QVersionNumber(1, 1), VK_API_VERSION_1_2,
                                         promoted, promoted));
        QVERIFY(!dmabufImportEnabled(nullptr, VK_NULL_HANDLE));
#else
        QSKIP("Linux Vulkan capability contract");
#endif
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

    void mapsScaledStreamBoundsIntoNativeClientCoordinates()
    {
        QCOMPARE(StreamVideoItem::scaledCaptureRect(
                     QRectF(80, 0, 1840, 1080), QSizeF(2000, 1080),
                     QRect(100, 50, 2500, 1350)),
                 QRect(200, 50, 2300, 1350));
        QCOMPARE(StreamVideoItem::scaledCaptureRect(
                     QRectF(), QSizeF(1920, 1080), QRect(0, 0, 1920, 1080)),
                 QRect());
    }

    void mapsAbsoluteMouseAgainstTheRenderedViewport()
    {
        QCOMPARE(StreamVideoItem::absoluteMouseCoordinates(
                     QPointF(500, 500), QSize(1920, 1080), QSizeF(1000, 1000)),
                 QRect(500, 281, 1000, 562));
        QCOMPARE(StreamVideoItem::absoluteMouseCoordinates(
                     QPointF(1920, 1080), QSize(1920, 1080), QSizeF(2560, 1440)),
                 QRect(1920, 1080, 2560, 1440));
        QCOMPARE(StreamVideoItem::absoluteMouseCoordinates(
                     QPointF(-50, 2000), QSize(1920, 1080), QSizeF(2560, 1440)),
                 QRect(0, 1439, 2560, 1440));
    }

    void parsesAndMapsRemoteCursorMetadata()
    {
        QByteArray systemCursor;
        systemCursor.append(char(0));
        systemCursor.append(char(12));
        systemCursor.append(char(0));
        systemCursor.append(char(0));
        systemCursor.append(char(0));
        systemCursor.append(char(0));
        systemCursor.append(char(0));
        systemCursor.append(char(0x00));
        systemCursor.append(char(0x80));
        systemCursor.append(char(0xff));
        systemCursor.append(char(0xff));
        const auto system = StreamVideoItem::remoteCursorMetadata(systemCursor);
        QCOMPARE(system.imageOffset, qsizetype(7));
        QCOMPARE(system.imageLength, qsizetype(0));
        QVERIFY(system.normalizedPosition.has_value());
        QCOMPARE(*system.normalizedPosition, QPoint(32768, 65535));
        QCOMPARE(system.scale, 1.0);
        QCOMPARE(StreamVideoItem::mapRemoteCursorPosition(
                     *system.normalizedPosition, QSize(1920, 1080), QSizeF(2560, 1440)),
                 QPoint(1280, 1439));

        QByteArray scaledCursor = systemCursor;
        scaledCursor[0] = char(1);
        scaledCursor.append(char(200));
        scaledCursor.append(char(0));
        const auto scaled = StreamVideoItem::remoteCursorMetadata(scaledCursor);
        QCOMPARE(scaled.scale, 2.0);

        const auto malformed = StreamVideoItem::remoteCursorMetadata(QByteArray::fromHex("01000000000400"));
        QCOMPARE(malformed.imageOffset, qsizetype(-1));
        QVERIFY(!malformed.normalizedPosition.has_value());
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

    void matchesShellShortcutsWithExactModifiersAndAliases()
    {
        const QVariantMap bindings{
            {QStringLiteral("guide"), QVariantList{QStringLiteral("Ctrl+G")}},
            {QStringLiteral("request-exit"), QVariantList{QStringLiteral("Escape")}},
            {QStringLiteral("toggle-fullscreen"), QVariantList{QStringLiteral("F11")}},
            {QStringLiteral("toggle-stats"),
             QVariantList{QStringLiteral("F3"), QStringLiteral("Ctrl+N")}},
        };
        QCOMPARE(StreamVideoItem::shortcutActionForInput(
                     bindings, Qt::Key_F3, Qt::NoModifier), QStringLiteral("toggle-stats"));
        QCOMPARE(StreamVideoItem::shortcutActionForInput(
                     bindings, Qt::Key_N, Qt::ControlModifier), QStringLiteral("toggle-stats"));
        QCOMPARE(StreamVideoItem::shortcutActionForInput(
                     bindings, Qt::Key_F11, Qt::NoModifier), QStringLiteral("toggle-fullscreen"));
        QCOMPARE(StreamVideoItem::shortcutActionForInput(
                     bindings, Qt::Key_G, Qt::ControlModifier), QStringLiteral("guide"));
        QCOMPARE(StreamVideoItem::shortcutActionForInput(
                     bindings, Qt::Key_Escape, Qt::NoModifier), QStringLiteral("request-exit"));
        QVERIFY(StreamVideoItem::shortcutActionForInput(
                    bindings, Qt::Key_F3, Qt::ShiftModifier).isEmpty());
        QVERIFY(StreamVideoItem::shortcutActionForInput(
                    bindings, Qt::Key_G, Qt::NoModifier).isEmpty());
    }

    void shortcutBindingsAreExplicitAndObservable()
    {
        StreamVideoItem item;
        QSignalSpy changes(&item, &StreamVideoItem::shortcutBindingsChanged);
        const QVariantMap bindings{
            {QStringLiteral("toggle-pointer-lock"), QVariantList{QStringLiteral("F8")}},
        };
        item.setShortcutBindings(bindings);
        QCOMPARE(item.shortcutBindings(), bindings);
        QCOMPARE(changes.size(), 1);
        item.setShortcutBindings(bindings);
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
        QVERIFY(qobject_cast<QQuickItem *>(&item));
        QVERIFY(item.flags().testFlag(QQuickItem::ItemHasContents));
        // Direct scene-graph rendering must not reintroduce an offscreen color target.
        QCOMPARE(item.metaObject()->indexOfProperty("colorBufferFormat"), -1);
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

    void rawPointerLockPinsTheCursorWithoutRestrictingAbsoluteInput()
    {
        for (const QRect viewport : {QRect(100, 80, 960, 540), QRect(0, 0, 1920, 1080),
                                    QRect(-2560, 120, 2560, 1440)}) {
            QCOMPARE(StreamVideoItem::cursorConfinementRect(viewport, true),
                     QRect(viewport.center(), QSize(1, 1)));
            QCOMPARE(StreamVideoItem::cursorConfinementRect(viewport, false), viewport);
        }
        QVERIFY(StreamVideoItem::cursorConfinementRect({}, true).isEmpty());
    }

    void nativePointerLockStaysFixedAndReleasesForOverlays()
    {
#if defined(Q_OS_WIN)
        if (QGuiApplication::platformName() == QStringLiteral("offscreen"))
            QSKIP("Requires native Windows cursor confinement.");
        const auto originalPosition = QCursor::pos();
        const auto restore = qScopeGuard([&] { ClipCursor(nullptr); QCursor::setPos(originalPosition); });
        QQuickWindow window;
        window.resize(640, 480);
        auto *item = new StreamVideoItem(window.contentItem());
        for (const bool fullscreen : {false, true}) {
            if (fullscreen) window.showFullScreen();
            else window.showNormal();
            window.requestActivate();
            QTRY_VERIFY(window.isActive());
            item->setSize(window.size());
            item->setInputEnabled(true);
            // Emulate an established native Raw Input capture without a live game.
            item->m_relativeMouse = true;
            item->m_rawInputActive = true;
            item->m_captureActive = true;
            item->updateCursorConfinement();
            RECT clipped{};
            QVERIFY(GetClipCursor(&clipped));
            QCOMPARE(clipped.right - clipped.left, LONG(1));
            QCOMPARE(clipped.bottom - clipped.top, LONG(1));
            QCursor::setPos(clipped.left + 200, clipped.top + 100);
            QCOMPARE(QCursor::pos(), QPoint(clipped.left, clipped.top));
            // Blocking overlays/focus loss disable input; confinement must go too.
            item->setInputEnabled(false);
            QVERIFY(!item->captureActive());
            QVERIFY(GetClipCursor(&clipped));
            QVERIFY(clipped.right - clipped.left > 1);
            QVERIFY(clipped.bottom - clipped.top > 1);
        }
#else
        QSKIP("Windows cursor confinement test.");
#endif
    }

    void cursorModeChangesDoNotReleaseAnActiveDrag()
    {
        StreamVideoItem item;
        QVERIFY(item.keepMouseGrab());
        item.m_pressedMouseButtons.insert(1);
        item.setRelativeMouse(true);
        QVERIFY(!item.relativeMouse());
        QVERIFY(item.m_pressedMouseButtons.contains(1));
        QCOMPARE(item.m_pendingRelativeMouse, std::optional<bool>(true));
        item.setRelativeMouse(false);
        QCOMPARE(item.m_pendingRelativeMouse, std::optional<bool>(false));
        item.releaseInput();
        QVERIFY(item.m_pressedMouseButtons.isEmpty());
        QVERIFY(!item.m_pendingRelativeMouse.has_value());
        item.setRelativeMouse(true);
        QVERIFY(item.relativeMouse());
        item.m_pressedMouseButtons.insert(1);
        item.setRelativeMouse(false);
        QVERIFY(item.relativeMouse());
        QVERIFY(item.m_pressedMouseButtons.contains(1));
        item.m_pressedMouseButtons.clear();
        item.setRelativeMouse(false);
        QVERIFY(!item.relativeMouse());
        item.m_pressedMouseButtons.insert(1);
        item.setRelativeMouse(true);
        item.releaseInput();
        QVERIFY(item.relativeMouse());
        QVERIFY(item.m_pressedMouseButtons.isEmpty());
        QVERIFY(!item.m_pendingRelativeMouse.has_value());
    }

    void directVideoPreservesPixelsClippingOpacityAndOverlays()
    {
        if (QGuiApplication::platformName() == QStringLiteral("offscreen"))
            QSKIP("Requires a native QRhi window.");
        QQuickWindow window;
        window.setColor(Qt::blue);
        window.resize(320, 260);
        auto *clip = new QQuickItem(window.contentItem());
        clip->setPosition(QPointF(20, 20));
        clip->setSize(QSizeF(200, 200));
        clip->setClip(true);
        auto *video = new StreamVideoItem(clip);
        video->setInputEnabled(false);
        video->setSize(QSizeF(200, 200));
        video->setVideoSize(QSize(200, 100));
        const auto callback = std::make_shared<TextureRenderCallback>();
        video->setRenderCallback(callback);
        auto *overlay = new WhiteOverlay(window.contentItem());
        overlay->setPosition(QPointF(80, 80));
        overlay->setSize(QSizeF(20, 20));
        overlay->setZ(10);
        window.show();
        QTRY_VERIFY_WITH_TIMEOUT(callback->imported.load(), 5'000);
        QVERIFY(callback->directTarget.load());
        const auto pixel = [&window](const QImage &image, int x, int y) {
            return image.pixelColor(x * image.width() / window.width(),
                                    y * image.height() / window.height());
        };
        auto image = window.grabWindow();
        QVERIFY(!image.isNull());
        QCOMPARE(pixel(image, 40, 40), QColor(Qt::black)); // letterbox
        QCOMPARE(pixel(image, 40, 80), QColor(Qt::red));
        QCOMPARE(pixel(image, 40, 160), QColor(Qt::green));
        QCOMPARE(pixel(image, 85, 85), QColor(Qt::white)); // overlay remains above video

        callback->showVideo.store(false);
        video->requestFrame();
        QTRY_COMPARE_WITH_TIMEOUT(pixel(window.grabWindow(), 40, 80), QColor(Qt::blue), 5'000);
        QCOMPARE(pixel(window.grabWindow(), 85, 85), QColor(Qt::white));
        callback->showVideo.store(true);
        video->requestFrame();
        QTRY_COMPARE_WITH_TIMEOUT(pixel(window.grabWindow(), 40, 80), QColor(Qt::red), 5'000);

        clip->setWidth(100);
        video->setOpacity(0.5);
        image = window.grabWindow();
        QCOMPARE(pixel(image, 160, 80), QColor(Qt::blue));
        const auto blended = pixel(image, 40, 80);
        QVERIFY(qAbs(blended.red() - 128) <= 2);
        QVERIFY(qAbs(blended.blue() - 127) <= 2);
        QCOMPARE(pixel(image, 85, 85), QColor(Qt::white));

        // Rotated rectangular clips use stencil rather than a simple scissor.
        video->setOpacity(1.0);
        clip->setRotation(15);
        image = window.grabWindow();
        const auto inside = clip->mapToScene(QPointF(40, 70));
        const auto outside = clip->mapToScene(QPointF(150, 70));
        QCOMPARE(pixel(image, int(inside.x()), int(inside.y())), QColor(Qt::red));
        QCOMPARE(pixel(image, int(outside.x()), int(outside.y())), QColor(Qt::blue));

        clip->setRotation(0);
        clip->setWidth(200);
        window.showFullScreen();
        QTRY_VERIFY(window.visibility() == QWindow::FullScreen);
        // Visibility changes synchronously; native resize/swapchain recreation does not.
        QTRY_COMPARE_WITH_TIMEOUT(pixel(window.grabWindow(), 40, 80), QColor(Qt::red), 5'000);
        overlay->setVisible(false);
        window.showNormal();
        window.resize(400, 300);
        QTRY_COMPARE_WITH_TIMEOUT(pixel(window.grabWindow(), 40, 80), QColor(Qt::red), 5'000);
        QVERIFY(callback->importedSlots.load() <= 8);
        const auto releases = callback->releases.load();
        video->setRenderCallback(nullptr);
        window.grabWindow();
        QTRY_VERIFY(callback->releases.load() > releases);
    }
};

QTEST_MAIN(StreamVideoItemTest)
#include "tst_streamvideoitem.moc"
