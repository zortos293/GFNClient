#include "StreamVideoItem.h"

#include "NativeStreamRuntime.h"
#include "LinuxVulkanGraphics.h"
#include "StreamVideoTextureRenderer.h"

#include <QColor>
#include <QSGRenderNode>
#include <QCursor>
#include <QFocusEvent>
#include <QGuiApplication>
#include <QHoverEvent>
#include <QImage>
#include <QKeyEvent>
#include <QKeySequence>
#include <QMetaObject>
#include <QMouseEvent>
#include <QPixmap>
#include <QPointer>
#include <QQmlEngine>
#include <QQuickWindow>
#include <QThread>
#include <QWheelEvent>
#include <rhi/qrhi.h>
#include <rhi/qrhi_platform.h>

#include <algorithm>
#include <cmath>
#include <utility>

#if defined(Q_OS_WIN)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#endif

namespace {
QPointer<NativeStreamRuntime> nativeRuntime;

class ExternalCommandScope final
{
public:
    explicit ExternalCommandScope(QRhiCommandBuffer *commandBuffer)
        : m_commandBuffer(commandBuffer)
    {
        m_commandBuffer->beginExternal();
    }

    ~ExternalCommandScope()
    {
        m_commandBuffer->endExternal();
    }

    ExternalCommandScope(const ExternalCommandScope &) = delete;
    ExternalCommandScope &operator=(const ExternalCommandScope &) = delete;

private:
    QRhiCommandBuffer *m_commandBuffer;
};

class NativeStreamRenderCallback final : public StreamVideoRenderCallback
{
public:
    explicit NativeStreamRenderCallback(NativeStreamRuntime *runtime)
        : m_runtime(runtime)
    {
    }

    void initialize(QRhi *rhi, QRhiCommandBuffer *commandBuffer,
                    QRhiRenderTarget *renderTarget) override
    {
        if (m_rhi != rhi) releaseResources();
        if (m_runtime && m_presentationGeneration != m_runtime->presentationGeneration()) {
            releaseResources();
            m_presentationGeneration = m_runtime->presentationGeneration();
        }
        m_textures.initialize(rhi, renderTarget);
        if (m_rhi == rhi && m_graphicsReady) return;
        m_rhi = rhi;
        if (!commandBuffer) return;

        OpenNowStreamerGraphicsContext context{};
        context.version = OPENNOW_STREAMER_GRAPHICS_CONTEXT_VERSION;
        context.struct_size = sizeof(context);
        OpenNowStreamerStatus status = OPENNOW_STREAMER_GRAPHICS_UNAVAILABLE;
        {
            // The native streamer creates decoder/video-processor resources on Qt's adopted
            // graphics device. QRhi requires every external native command to be bracketed so it
            // can invalidate and restore its internal backend state.
            ExternalCommandScope externalCommands(commandBuffer);
            switch (rhi->backend()) {
#if defined(Q_OS_WIN)
            case QRhi::D3D11: {
                const auto *handles = static_cast<const QRhiD3D11NativeHandles *>(
                    rhi->nativeHandles());
                if (!handles) return;
                context.graphics_api = OPENNOW_STREAMER_GRAPHICS_API_D3D11;
                context.device = handles->dev;
                context.queue = handles->context;
                break;
            }
#endif
#if QT_CONFIG(vulkan) && __has_include(<vulkan/vulkan.h>)
            case QRhi::Vulkan: {
                const auto *handles = static_cast<const QRhiVulkanNativeHandles *>(
                    rhi->nativeHandles());
                if (!handles || !handles->inst) return;
                context.graphics_api = OPENNOW_STREAMER_GRAPHICS_API_VULKAN;
                context.instance = reinterpret_cast<void *>(handles->inst->vkInstance());
                context.physical_device = reinterpret_cast<void *>(handles->physDev);
                context.device = reinterpret_cast<void *>(handles->dev);
                context.queue = reinterpret_cast<void *>(handles->gfxQueue);
                context.queue_family_index = handles->gfxQueueFamilyIdx;
#if defined(Q_OS_LINUX)
                if (LinuxVulkanGraphics::dmabufImportEnabled(handles->inst, handles->physDev))
                    context.enabled_capabilities = OPENNOW_STREAMER_GRAPHICS_CAP_VULKAN_DMABUF_IMPORT;
#endif
                break;
            }
#endif
#if QT_CONFIG(metal)
            case QRhi::Metal: {
                const auto *handles = static_cast<const QRhiMetalNativeHandles *>(
                    rhi->nativeHandles());
                if (!handles) return;
                context.graphics_api = OPENNOW_STREAMER_GRAPHICS_API_METAL;
                context.device = handles->dev;
                context.queue = handles->cmdQueue;
                break;
            }
#endif
            default:
                reportFailure(QStringLiteral("This graphics backend cannot present native video. Use Auto in Stream settings."));
                return;
            }
            status = m_runtime ? m_runtime->setGraphicsContext(context)
                               : OPENNOW_STREAMER_CLOSED;
        }
        m_graphicsReady = status == OPENNOW_STREAMER_OK;
        if (!m_graphicsReady)
            reportFailure(QStringLiteral("Could not initialize native graphics (status %1). Update the GPU driver and use Auto in Stream settings.").arg(int(status)));
    }

    void prepareFrame(QRhiCommandBuffer *commandBuffer) override
    {
        if (!m_runtime || !m_runtime->presentationAllowed()
                || m_presentationGeneration != m_runtime->presentationGeneration()) return;
        if (!m_graphicsReady || !m_rhi || !commandBuffer) return;
        if (!m_textures.prepare(commandBuffer)) {
            reportFailure(QStringLiteral("Could not create the video shaders or GPU resources. Check the packaged shaders and GPU driver."));
            return;
        }

        OpenNowStreamerRecordCommand command{};
        command.version = OPENNOW_STREAMER_RENDER_COMMAND_VERSION;
        command.struct_size = sizeof(command);
        command.frame_slot = static_cast<std::uint32_t>(m_rhi->currentFrameSlot());
        finishFrame();
        OpenNowStreamerFrameInfo info{};
        OpenNowStreamerRecordedFrame recorded{};
        OpenNowStreamerFrame *frame = nullptr;
        OpenNowStreamerStatus status = OPENNOW_STREAMER_GRAPHICS_UNAVAILABLE;
        {
            ExternalCommandScope externalCommands(commandBuffer);
            // Native command-buffer handles can change when QRhi begins an external section, so
            // query them only after beginExternal(), as required by the QRhi contract.
            switch (m_rhi->backend()) {
#if defined(Q_OS_WIN)
            case QRhi::D3D11: {
                const auto *handles = static_cast<const QRhiD3D11NativeHandles *>(
                    m_rhi->nativeHandles());
                command.command_buffer = handles ? handles->context : nullptr;
                break;
            }
#endif
#if QT_CONFIG(vulkan) && __has_include(<vulkan/vulkan.h>)
            case QRhi::Vulkan: {
                const auto *handles = static_cast<const QRhiVulkanCommandBufferNativeHandles *>(
                    commandBuffer->nativeHandles());
                command.command_buffer = handles
                    ? reinterpret_cast<void *>(handles->commandBuffer) : nullptr;
                break;
            }
#endif
#if QT_CONFIG(metal)
            case QRhi::Metal: {
                const auto *handles = static_cast<const QRhiMetalCommandBufferNativeHandles *>(
                    commandBuffer->nativeHandles());
                command.command_buffer = handles ? handles->commandBuffer : nullptr;
                break;
            }
#endif
            default:
                return;
            }
            if (!command.command_buffer) return;
            status = m_runtime->recordLatestFrame(command, &info, &recorded, &frame);
        }

        if (m_presentationGeneration != m_runtime->presentationGeneration()
                || !m_runtime->presentationAllowed()) {
            if (frame) m_runtime->releaseFrame(frame);
            m_textures.clearFrames();
            return;
        }
        if (status == OPENNOW_STREAMER_NO_FRAME) return;
        if (status == OPENNOW_STREAMER_STALE_FRAME) return;
        if (status != OPENNOW_STREAMER_OK || !frame || recorded.resource == 0
            || recorded.width == 0 || recorded.height == 0
            || (recorded.texture_format != OPENNOW_STREAMER_TEXTURE_FORMAT_RGBA8
                && recorded.texture_format != OPENNOW_STREAMER_TEXTURE_FORMAT_RGB10A2)) {
            if (frame) m_runtime->releaseFrame(frame);
            reportFailure(QStringLiteral("Could not present the decoded GPU frame (status %1). Check native-streamer.log for decoder or device errors.").arg(int(status)));
            return;
        }
        m_preparedFrame = frame;


        QRhiTexture::NativeTexture texture{};
        texture.object = recorded.resource;
#if QT_CONFIG(vulkan) && __has_include(<vulkan/vulkan.h>)
        if (recorded.graphics_api == OPENNOW_STREAMER_GRAPHICS_API_VULKAN)
            texture.layout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
#endif
        if (!m_textures.importFrame(command.frame_slot, texture,
            recorded.texture_format == OPENNOW_STREAMER_TEXTURE_FORMAT_RGB10A2
                ? QRhiTexture::RGB10A2 : QRhiTexture::RGBA8,
            QSize(int(recorded.width), int(recorded.height))))
            reportFailure(QStringLiteral("Could not import the decoded video texture into Qt. The decoder and graphics backend must use compatible GPU resources."));
    }

    void setComposition(const QMatrix4x4 &matrix, const QRectF &bounds,
                        const QRectF &videoRect, float opacity) override
    {
        m_textures.setComposition(matrix, bounds, videoRect, opacity);
    }

    void setClip(bool stencil, int reference) override
    {
        m_stencil = stencil;
        m_stencilReference = reference;
    }

    void recordFrame(QRhiCommandBuffer *commandBuffer, const QRect &) override
    {
        if (!m_runtime || !m_runtime->presentationAllowed()
                || m_presentationGeneration != m_runtime->presentationGeneration()) return;
        m_textures.render(commandBuffer, m_stencil, m_stencilReference);
    }

    void finishFrame() override
    {
        if (m_preparedFrame && m_runtime)
            m_runtime->releaseFrame(std::exchange(m_preparedFrame, nullptr));
    }

    void releaseResources() override
    {
        if (m_rhi && m_graphicsReady) m_rhi->finish();
        finishFrame();
        m_textures.release();
        if (m_rhi && m_graphicsReady) m_rhi->finish();
        if (m_graphicsReady && m_runtime) m_runtime->sceneGraphShutdown();
        m_graphicsReady = false;
        m_reportedFailure = false;
        m_rhi = nullptr;
    }

private:
    NativeStreamRuntime *m_runtime;
    QRhi *m_rhi = nullptr;
    StreamVideoTextureRenderer m_textures;
    OpenNowStreamerFrame *m_preparedFrame = nullptr;
    int m_stencilReference = 0;
    bool m_stencil = false;
    bool m_graphicsReady = false;
    bool m_reportedFailure = false;
    quint64 m_presentationGeneration = 0;
    void reportFailure(const QString &message)
    {
        if (m_reportedFailure || !m_runtime) return;
        m_reportedFailure = true;
        m_runtime->reportPresentationError(message, m_presentationGeneration);
    }
};

// Native conversion is recorded in prepare(), before Qt begins its scene pass.
// The converted surface is sampled directly in that pass: no item-sized color
// target, extra blit, CPU readback, or second presentation window.
class StreamVideoNode final : public QSGRenderNode
{
public:
    explicit StreamVideoNode(QQuickWindow *window) : m_window(window) {}
    ~StreamVideoNode() override { releaseResources(); }

    void synchronize(StreamVideoItem *item)
    {
        const auto callback = item->renderCallback();
        if (m_callback != callback) {
            releaseResources();
            m_callback = callback;
        }
        m_bounds = item->boundingRect();
        m_viewport = StreamVideoItem::aspectFitRect(item->videoSize(), m_bounds.size().toSize());
        markDirty(QSGNode::DirtyGeometry | QSGNode::DirtyMaterial);
    }

    void prepare() override
    {
        auto *rhi = m_window->rhi();
        if (!m_callback || !rhi || !renderTarget() || !commandBuffer()) return;
        m_callback->initialize(rhi, commandBuffer(), renderTarget());
        m_initialized = true;
        m_callback->setComposition(*projectionMatrix() * *matrix(), m_bounds, m_viewport,
                                   float(inheritedOpacity()));
        m_callback->prepareFrame(commandBuffer());
    }

    void render(const RenderState *state) override
    {
        if (!m_initialized || !commandBuffer() || !renderTarget()) return;
        auto *cb = commandBuffer();
        const auto size = renderTarget()->pixelSize();
        cb->setViewport(QRhiViewport(0, 0, size.width(), size.height()));
        const auto scissor = state->scissorEnabled()
            ? state->scissorRect() : QRect(QPoint(), size);
        cb->setScissor(QRhiScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height()));
        m_callback->setClip(state->stencilEnabled(), state->stencilValue());
        m_callback->recordFrame(cb, m_viewport);
        m_callback->finishFrame();
    }

    void releaseResources() override
    {
        if (m_initialized && m_callback) m_callback->releaseResources();
        m_initialized = false;
    }

    RenderingFlags flags() const override
    {
        // Conversion brackets its own native commands in prepare(). render()
        // is entirely QRhi: Qt must not open an external/native render section
        // around these draw calls (especially with Vulkan secondary buffers).
        return BoundedRectRendering | DepthAwareRendering | NoExternalRendering;
    }
    StateFlags changedStates() const override { return ViewportState | ScissorState; }
    QRectF rect() const override { return m_bounds; }

private:
    QQuickWindow *m_window;
    std::shared_ptr<StreamVideoRenderCallback> m_callback;
    QRectF m_bounds;
    QRect m_viewport;
    bool m_initialized = false;
};
}

StreamVideoItem::StreamVideoItem(QQuickItem *parent)
    : QQuickItem(parent)
{
    setFlag(ItemHasContents, true);
    setActiveFocusOnTab(true);
    setAcceptedMouseButtons(Qt::AllButtons);
    setKeepMouseGrab(true);
    setAcceptHoverEvents(true);
    if (nativeRuntime) {
        setRenderCallback(std::make_shared<NativeStreamRenderCallback>(nativeRuntime));
        connect(nativeRuntime, &NativeStreamRuntime::frameAvailable,
                this, &StreamVideoItem::requestFrame);
        connect(nativeRuntime, &NativeStreamRuntime::cursorUpdated,
                this, &StreamVideoItem::applyRemoteCursor, Qt::QueuedConnection);
        connect(nativeRuntime, &NativeStreamRuntime::runningChanged, this, [this] {
            if (!nativeRuntime || !nativeRuntime->running()) {
                m_remoteCursorKnown = false;
                m_remoteCursorVisible = false;
                if (m_relativeMouse) setRelativeMouse(false);
                else unsetCursor();
            }
            syncCaptureState();
        });
    }
    connect(this, &QQuickItem::windowChanged, this, [this](QQuickWindow *currentWindow) {
        if (currentWindow) {
            connect(currentWindow, &QWindow::activeChanged,
                    this, &StreamVideoItem::syncCaptureState, Qt::UniqueConnection);
            connect(currentWindow, &QWindow::xChanged,
                    this, &StreamVideoItem::updateCursorConfinement, Qt::UniqueConnection);
            connect(currentWindow, &QWindow::yChanged,
                    this, &StreamVideoItem::updateCursorConfinement, Qt::UniqueConnection);
            connect(currentWindow, &QWindow::widthChanged,
                    this, &StreamVideoItem::updateCursorConfinement, Qt::UniqueConnection);
            connect(currentWindow, &QWindow::heightChanged,
                    this, &StreamVideoItem::updateCursorConfinement, Qt::UniqueConnection);
        }
        syncCaptureState();
    });
}

StreamVideoItem::~StreamVideoItem()
{
    if (nativeRuntime && nativeRuntime->running()) {
        bool rawInput = false;
        nativeRuntime->setCaptureActive(false, false, 0, &rawInput);
    }
    releaseInput();
}

QSize StreamVideoItem::videoSize() const
{
    return m_videoSize;
}

void StreamVideoItem::setVideoSize(const QSize &size)
{
    const auto normalized = size.isValid() && !size.isEmpty() ? size : QSize{};
    if (m_videoSize == normalized) return;
    m_videoSize = normalized;
    emit videoSizeChanged();
    update();
}

bool StreamVideoItem::renderCallbackAvailable() const
{
    return static_cast<bool>(m_renderCallback);
}

bool StreamVideoItem::nativeRuntimeAvailable() const
{
    return nativeRuntime && nativeRuntime->running();
}

bool StreamVideoItem::inputEnabled() const
{
    return m_inputEnabled;
}

void StreamVideoItem::setInputEnabled(bool enabled)
{
    if (m_inputEnabled == enabled) return;
    m_inputEnabled = enabled;
    syncCaptureState();
    emit inputEnabledChanged();
}

bool StreamVideoItem::captureActive() const
{
    return m_captureActive;
}

bool StreamVideoItem::relativeMouse() const
{
    return m_relativeMouse;
}

QVariantMap StreamVideoItem::shortcutBindings() const
{
    return m_shortcutBindings;
}

void StreamVideoItem::setShortcutBindings(const QVariantMap &bindings)
{
    if (m_shortcutBindings == bindings) return;
    m_shortcutBindings = bindings;
    emit shortcutBindingsChanged();
}

std::shared_ptr<StreamVideoRenderCallback> StreamVideoItem::renderCallback() const
{
    return m_renderCallback;
}

void StreamVideoItem::setRenderCallback(std::shared_ptr<StreamVideoRenderCallback> callback)
{
    if (m_renderCallback == callback) return;
    const auto wasAvailable = renderCallbackAvailable();
    m_renderCallback = std::move(callback);
    if (wasAvailable != renderCallbackAvailable()) emit renderCallbackAvailableChanged();
    update();
}

void StreamVideoItem::setNativeStreamRuntime(NativeStreamRuntime *runtime)
{
    nativeRuntime = runtime;
}

NativeStreamRuntime *StreamVideoItem::nativeStreamRuntime()
{
    return nativeRuntime.data();
}

void StreamVideoItem::requestFrame()
{
    if (QThread::currentThread() != thread()) {
        QMetaObject::invokeMethod(this, &StreamVideoItem::requestFrame, Qt::QueuedConnection);
        return;
    }
    update();
}

QRect StreamVideoItem::aspectFitRect(const QSize &videoSize, const QSize &targetSize)
{
    if (!targetSize.isValid() || targetSize.isEmpty()) return {};
    if (!videoSize.isValid() || videoSize.isEmpty()) return QRect(QPoint{}, targetSize);

    const auto scale = std::min(static_cast<double>(targetSize.width()) / videoSize.width(),
                                static_cast<double>(targetSize.height()) / videoSize.height());
    const auto width = std::max(1, static_cast<int>(std::floor(videoSize.width() * scale)));
    const auto height = std::max(1, static_cast<int>(std::floor(videoSize.height() * scale)));
    return QRect((targetSize.width() - width) / 2,
                 (targetSize.height() - height) / 2,
                 width, height);
}

quint16 StreamVideoItem::windowsVirtualKey(int key)
{
    if (key >= Qt::Key_A && key <= Qt::Key_Z) return static_cast<quint16>(key);
    if (key >= Qt::Key_0 && key <= Qt::Key_9) return static_cast<quint16>(key);
    if (key >= Qt::Key_F1 && key <= Qt::Key_F24)
        return static_cast<quint16>(0x70 + key - Qt::Key_F1);
    switch (key) {
    case Qt::Key_Return:
    case Qt::Key_Enter: return 0x0d;
    case Qt::Key_Escape: return 0x1b;
    case Qt::Key_Backspace: return 0x08;
    case Qt::Key_Tab: return 0x09;
    case Qt::Key_Space: return 0x20;
    case Qt::Key_Minus: return 0xbd;
    case Qt::Key_Equal: return 0xbb;
    case Qt::Key_BracketLeft: return 0xdb;
    case Qt::Key_BracketRight: return 0xdd;
    case Qt::Key_Backslash: return 0xdc;
    case Qt::Key_Semicolon: return 0xba;
    case Qt::Key_Apostrophe: return 0xde;
    case Qt::Key_QuoteLeft: return 0xc0;
    case Qt::Key_Comma: return 0xbc;
    case Qt::Key_Period: return 0xbe;
    case Qt::Key_Slash: return 0xbf;
    case Qt::Key_Right: return 0x27;
    case Qt::Key_Left: return 0x25;
    case Qt::Key_Down: return 0x28;
    case Qt::Key_Up: return 0x26;
    case Qt::Key_Control: return 0xa2;
    case Qt::Key_Shift: return 0xa0;
    case Qt::Key_Alt: return 0xa4;
    case Qt::Key_Meta: return 0x5b;
    case Qt::Key_CapsLock: return 0x14;
    case Qt::Key_NumLock: return 0x90;
    case Qt::Key_Insert: return 0x2d;
    case Qt::Key_Delete: return 0x2e;
    case Qt::Key_Home: return 0x24;
    case Qt::Key_End: return 0x23;
    case Qt::Key_PageUp: return 0x21;
    case Qt::Key_PageDown: return 0x22;
    case Qt::Key_Print: return 0x2a;
    case Qt::Key_ScrollLock: return 0x91;
    case Qt::Key_Pause: return 0x13;
    case Qt::Key_Menu: return 0x5d;
    case Qt::Key_Plus: return 0x6b;
    case Qt::Key_Asterisk: return 0x6a;
    default: return 0;
    }
}

quint16 StreamVideoItem::inputModifiers(Qt::KeyboardModifiers modifiers, int key)
{
    quint16 result = 0;
    if (key != Qt::Key_Shift && modifiers.testFlag(Qt::ShiftModifier)) result |= 0x01;
    if (key != Qt::Key_Control && modifiers.testFlag(Qt::ControlModifier)) result |= 0x02;
    if (key != Qt::Key_Alt && modifiers.testFlag(Qt::AltModifier)) result |= 0x04;
    if (key != Qt::Key_Meta && modifiers.testFlag(Qt::MetaModifier)) result |= 0x08;
    return result;
}

QString StreamVideoItem::shortcutActionForInput(
    const QVariantMap &bindings, int key, Qt::KeyboardModifiers modifiers)
{
    constexpr auto shortcutModifiers = Qt::ControlModifier | Qt::ShiftModifier
        | Qt::AltModifier | Qt::MetaModifier;
    const auto normalizedModifiers = modifiers & shortcutModifiers;
    for (auto binding = bindings.cbegin(); binding != bindings.cend(); ++binding) {
        QStringList sequences;
        if (binding.value().metaType().id() == QMetaType::QString) {
            sequences.push_back(binding.value().toString());
        } else {
            const auto values = binding.value().toList();
            sequences.reserve(values.size());
            for (const auto &value : values) sequences.push_back(value.toString());
        }
        for (const auto &text : std::as_const(sequences)) {
            const QKeySequence sequence(text, QKeySequence::PortableText);
            if (sequence.count() != 1) continue;
            const auto combination = sequence[0];
            if (combination.key() == static_cast<Qt::Key>(key)
                && (combination.keyboardModifiers() & shortcutModifiers)
                    == normalizedModifiers) {
                return binding.key();
            }
        }
    }
    return {};
}

void StreamVideoItem::focusInEvent(QFocusEvent *event)
{
    QQuickItem::focusInEvent(event);
    syncCaptureState();
}

void StreamVideoItem::focusOutEvent(QFocusEvent *event)
{
    releaseInput();
    syncCaptureState();
    QQuickItem::focusOutEvent(event);
}

quint32 StreamVideoItem::keyIdentity(const QKeyEvent *event) const
{
    return event->nativeScanCode() != 0 ? event->nativeScanCode()
                                        : static_cast<quint32>(event->key());
}

void StreamVideoItem::keyPressEvent(QKeyEvent *event)
{
    if (!m_captureActive || event->isAutoRepeat()) {
        event->ignore();
        return;
    }
    const auto identity = keyIdentity(event);
    const auto shortcutAction = shortcutActionForInput(
        m_shortcutBindings, event->key(), event->modifiers());
    if (!shortcutAction.isEmpty()) {
        // A fullscreen transition can prevent Windows from delivering the key-up
        // that belongs to the key which initiated it.  Keep the identity only so
        // the eventual release is consumed; QKeyEvent::isAutoRepeat() already
        // prevents repeats, so a stale identity must never suppress the next
        // deliberate F11 press.
        m_pressedShortcuts.insert(identity);
        emit localShortcutRequested(shortcutAction);
        event->accept();
        return;
    }
    const auto virtualKey = windowsVirtualKey(event->key());
    if (virtualKey == 0) {
        event->ignore();
        return;
    }
    if (!m_pressedKeys.contains(identity)) {
        const auto modifiers = inputModifiers(event->modifiers(), event->key());
        m_pressedKeys.insert(identity, {virtualKey, modifiers});
        nativeRuntime->submitKey(virtualKey, modifiers, true);
    }
    event->accept();
}

void StreamVideoItem::keyReleaseEvent(QKeyEvent *event)
{
    if (event->isAutoRepeat()) {
        event->accept();
        return;
    }
    const auto identity = keyIdentity(event);
    if (m_pressedShortcuts.remove(identity)) {
        event->accept();
        return;
    }
    const auto pressed = m_pressedKeys.take(identity);
    if (pressed.virtualKey == 0) {
        event->ignore();
        return;
    }
    nativeRuntime->submitKey(pressed.virtualKey,
                             inputModifiers(event->modifiers(), event->key()), false);
    event->accept();
}

quint8 StreamVideoItem::mouseButton(Qt::MouseButton button)
{
    switch (button) {
    case Qt::LeftButton: return 1;
    case Qt::MiddleButton: return 2;
    case Qt::RightButton: return 3;
    case Qt::BackButton: return 4;
    case Qt::ForwardButton: return 5;
    default: return 0;
    }
}

void StreamVideoItem::mousePressEvent(QMouseEvent *event)
{
    forceActiveFocus(Qt::MouseFocusReason);
    syncCaptureState();
    const auto button = mouseButton(event->button());
    if (m_captureActive && button != 0) {
        if (!m_pressedMouseButtons.contains(button)) {
            // In absolute cursor mode position and button must have one owner and
            // preserve their queue order.  A move event is not guaranteed before
            // a click (notably after a fullscreen viewport change).
            m_pressedMouseButtons.insert(button);
            if (!m_rawInputActive) {
                if (!m_relativeMouse) submitAbsoluteMouse(event->position());
                nativeRuntime->submitMouseButton(button, true);
            }
        }
        m_lastMousePosition = event->position();
        event->accept();
        return;
    }
    event->ignore();
}

void StreamVideoItem::mouseReleaseEvent(QMouseEvent *event)
{
    const auto button = mouseButton(event->button());
    if (m_captureActive && button != 0) {
        if (m_pressedMouseButtons.remove(button) && !m_rawInputActive) {
            if (!m_relativeMouse) submitAbsoluteMouse(event->position());
            nativeRuntime->submitMouseButton(button, false);
        }
        if (m_pressedMouseButtons.isEmpty() && m_pendingRelativeMouse) {
            const auto relative = *m_pendingRelativeMouse;
            m_pendingRelativeMouse.reset();
            setRelativeMouse(relative);
        }
        event->accept();
        return;
    }
    event->ignore();
}

void StreamVideoItem::mouseMoveEvent(QMouseEvent *event)
{
    if (!m_captureActive) {
        event->ignore();
        return;
    }
    if (m_relativeMouse) {
        if (!m_rawInputActive) {
            const auto delta = event->position() - m_lastMousePosition;
            const auto deltaX = std::clamp(qRound(delta.x()), -32768, 32767);
            const auto deltaY = std::clamp(qRound(delta.y()), -32768, 32767);
            if (deltaX != 0 || deltaY != 0)
                nativeRuntime->submitMouseRelative(static_cast<qint16>(deltaX),
                                                   static_cast<qint16>(deltaY));
            const auto anchor = mapToGlobal(QPointF(width() / 2.0, height() / 2.0)).toPoint();
            QCursor::setPos(anchor);
            m_lastMousePosition = mapFromGlobal(QCursor::pos());
        }
    } else {
        submitAbsoluteMouse(event->position());
        m_lastMousePosition = event->position();
    }
    event->accept();
}

void StreamVideoItem::hoverEnterEvent(QHoverEvent *event)
{
    if (!m_captureActive || m_relativeMouse) {
        event->ignore();
        return;
    }
    // QQuickItem sends ordinary no-button movement through hover events once
    // hover delivery is enabled. Publish the entry point as well so the remote
    // cursor cannot retain a stale position when it re-enters the stream item.
    submitAbsoluteMouse(event->position());
    m_lastMousePosition = event->position();
    event->accept();
}

void StreamVideoItem::hoverMoveEvent(QHoverEvent *event)
{
    if (!m_captureActive || m_relativeMouse) {
        event->ignore();
        return;
    }
    // With no button held Qt does not call mouseMoveEvent for this item. Keep
    // the absolute GFN pointer current so remote hover and click hit-testing
    // use the same coordinates.
    submitAbsoluteMouse(event->position());
    m_lastMousePosition = event->position();
    event->accept();
}

void StreamVideoItem::wheelEvent(QWheelEvent *event)
{
    if (!m_captureActive) {
        event->ignore();
        return;
    }
    if (!m_rawInputActive) {
        if (!m_relativeMouse) submitAbsoluteMouse(event->position());
        const auto delta = event->pixelDelta().isNull() ? event->angleDelta()
                                                        : event->pixelDelta();
        nativeRuntime->submitMouseWheel(
            static_cast<qint16>(std::clamp(delta.x(), -32768, 32767)),
            static_cast<qint16>(std::clamp(delta.y(), -32768, 32767)));
    }
    event->accept();
}

void StreamVideoItem::itemChange(ItemChange change, const ItemChangeData &data)
{
    QQuickItem::itemChange(change, data);
    if (change == ItemVisibleHasChanged && !isVisible()) {
        m_remoteCursorKnown = false;
        m_remoteCursorVisible = false;
        if (m_relativeMouse) setRelativeMouse(false);
        else unsetCursor();
    }
    if (change == ItemVisibleHasChanged || change == ItemSceneChange)
        syncCaptureState();
}

void StreamVideoItem::geometryChange(const QRectF &newGeometry, const QRectF &oldGeometry)
{
    QQuickItem::geometryChange(newGeometry, oldGeometry);
    updateCursorConfinement();
}

QRect StreamVideoItem::scaledCaptureRect(const QRectF &itemRect,
                                         const QSizeF &windowSize,
                                         const QRect &clientScreenRect)
{
    if (!itemRect.isValid() || itemRect.isEmpty() || !windowSize.isValid()
        || windowSize.isEmpty() || !clientScreenRect.isValid()
        || clientScreenRect.isEmpty()) {
        return {};
    }
    const auto scaleX = clientScreenRect.width() / windowSize.width();
    const auto scaleY = clientScreenRect.height() / windowSize.height();
    const auto left = clientScreenRect.left()
        + static_cast<int>(std::floor(itemRect.left() * scaleX));
    const auto top = clientScreenRect.top()
        + static_cast<int>(std::floor(itemRect.top() * scaleY));
    const auto right = clientScreenRect.left()
        + static_cast<int>(std::ceil(itemRect.right() * scaleX));
    const auto bottom = clientScreenRect.top()
        + static_cast<int>(std::ceil(itemRect.bottom() * scaleY));
    return QRect(left, top, std::max(0, right - left),
                 std::max(0, bottom - top)).intersected(clientScreenRect);
}

QRect StreamVideoItem::absoluteMouseCoordinates(const QPointF &position,
                                                const QSize &videoSize,
                                                const QSizeF &itemSize)
{
    const auto target = QSize(std::max(1, qRound(itemSize.width())),
                              std::max(1, qRound(itemSize.height())));
    const auto viewport = aspectFitRect(videoSize, target);
    const auto x = std::clamp(qRound(position.x()) - viewport.x(), 0,
                              std::max(0, viewport.width() - 1));
    const auto y = std::clamp(qRound(position.y()) - viewport.y(), 0,
                              std::max(0, viewport.height() - 1));
    return QRect(x, y, viewport.width(), viewport.height());
}

StreamVideoItem::RemoteCursorMetadata StreamVideoItem::remoteCursorMetadata(
    const QByteArray &bytes)
{
    RemoteCursorMetadata result;
    if (bytes.size() < 7) return result;
    const auto messageType = static_cast<quint8>(bytes[0]);
    if (messageType > 1) return result;
    const auto mimeLength = static_cast<qsizetype>(static_cast<quint8>(bytes[4]));
    const auto lengthOffset = qsizetype{5} + mimeLength;
    if (lengthOffset < 5 || lengthOffset + 2 > bytes.size()) return result;
    const auto imageLength = static_cast<qsizetype>(
        static_cast<quint8>(bytes[lengthOffset])
        | (static_cast<quint16>(static_cast<quint8>(bytes[lengthOffset + 1])) << 8));
    const auto imageOffset = lengthOffset + 2;
    if (imageLength < 0 || imageOffset > bytes.size()
        || imageLength > bytes.size() - imageOffset) {
        return result;
    }
    result.imageOffset = imageOffset;
    result.imageLength = imageLength;
    const auto positionOffset = imageOffset + imageLength;
    if (positionOffset + 4 <= bytes.size()) {
        result.normalizedPosition = QPoint(
            static_cast<quint8>(bytes[positionOffset])
                | (static_cast<quint16>(static_cast<quint8>(bytes[positionOffset + 1])) << 8),
            static_cast<quint8>(bytes[positionOffset + 2])
                | (static_cast<quint16>(static_cast<quint8>(bytes[positionOffset + 3])) << 8));
    }
    const auto scaleOffset = positionOffset + 4;
    if (scaleOffset + 2 <= bytes.size()) {
        const auto scalePercent = static_cast<quint16>(
            static_cast<quint8>(bytes[scaleOffset])
            | (static_cast<quint16>(static_cast<quint8>(bytes[scaleOffset + 1])) << 8));
        if (scalePercent > 0) result.scale = scalePercent / 100.0;
    }
    return result;
}

QPoint StreamVideoItem::mapRemoteCursorPosition(const QPoint &normalizedPosition,
                                                const QSize &videoSize,
                                                const QSizeF &itemSize)
{
    const auto target = QSize(std::max(1, qRound(itemSize.width())),
                              std::max(1, qRound(itemSize.height())));
    const auto viewport = aspectFitRect(videoSize, target);
    const auto coordinate = [](int value, int extent) {
        const auto safeExtent = std::max(1, extent);
        return static_cast<int>(std::min<qint64>(
            (static_cast<qint64>(std::clamp(value, 0, 65535)) * safeExtent) / 65535,
            safeExtent - 1));
    };
    return QPoint(viewport.x() + coordinate(normalizedPosition.x(), viewport.width()),
                  viewport.y() + coordinate(normalizedPosition.y(), viewport.height()));
}

void StreamVideoItem::resynchronizeInput()
{
    syncCaptureState();
    if (m_captureActive && m_relativeMouse && !m_rawInputActive) {
        const auto anchor = mapToGlobal(QPointF(width() / 2.0, height() / 2.0)).toPoint();
        QCursor::setPos(anchor);
    } else if (m_captureActive && !m_relativeMouse) {
        // Fullscreen changes the absolute viewport dimensions without requiring
        // the physical cursor to move. Re-publish the current point immediately.
        submitAbsoluteMouse(mapFromGlobal(QCursor::pos()));
    }
    m_lastMousePosition = mapFromGlobal(QCursor::pos());
    updateCursorConfinement();
}

void StreamVideoItem::submitAbsoluteMouse(const QPointF &position)
{
    const auto coordinates = absoluteMouseCoordinates(
        position, m_videoSize, QSizeF(width(), height()));
    nativeRuntime->submitMouseAbsolute(
        static_cast<quint16>(std::min(coordinates.x(), 65535)),
        static_cast<quint16>(std::min(coordinates.y(), 65535)),
        static_cast<quint16>(std::min(coordinates.width(), 65535)),
        static_cast<quint16>(std::min(coordinates.height(), 65535)));
}

void StreamVideoItem::syncCaptureState()
{
    const auto desired = m_inputEnabled && isVisible() && hasActiveFocus()
        && window() && window()->isActive() && nativeRuntime && nativeRuntime->running();
    if (!desired && m_captureActive) releaseInput();
    bool rawInput = false;
    if (nativeRuntime && nativeRuntime->running()) {
        nativeRuntime->setCaptureActive(
            desired, m_relativeMouse,
            window() ? static_cast<std::uintptr_t>(window()->winId()) : 0,
            &rawInput);
    }
    m_rawInputActive = desired && rawInput;
    const auto changed = m_captureActive != desired;
    m_captureActive = desired;
    if (m_captureActive) {
        m_lastMousePosition = mapFromGlobal(QCursor::pos());
        if (m_relativeMouse) grabMouse();
    } else {
        ungrabMouse();
    }
    updateCursorConfinement();
    if (changed) emit captureActiveChanged();
}

void StreamVideoItem::releaseInput()
{
    const auto pendingRelativeMouse = m_pendingRelativeMouse;
    m_pendingRelativeMouse.reset();
    if (nativeRuntime) {
        for (const auto &pressed : std::as_const(m_pressedKeys))
            nativeRuntime->submitKey(pressed.virtualKey, 0, false);
    }
    m_pressedKeys.clear();
    m_pressedShortcuts.clear();
    if (!m_rawInputActive) releaseQtMouseButtons();
    else m_pressedMouseButtons.clear();
    ungrabMouse();
    releaseCursorConfinement();
    // Remember the newest server mode across focus loss without re-entering
    // syncCaptureState() or acquiring a new grab while releasing the old one.
    if (pendingRelativeMouse && m_relativeMouse != *pendingRelativeMouse) {
        m_relativeMouse = *pendingRelativeMouse;
        if (m_relativeMouse) setCursor(Qt::BlankCursor);
        else unsetCursor();
        emit relativeMouseChanged();
    }
}

void StreamVideoItem::releaseQtMouseButtons()
{
    if (nativeRuntime) {
        for (const auto button : std::as_const(m_pressedMouseButtons))
            nativeRuntime->submitMouseButton(button, false);
    }
    m_pressedMouseButtons.clear();
}

QRect StreamVideoItem::cursorConfinementRect(const QRect &viewport, bool rawRelative)
{
    // Raw Input supplies unaccelerated deltas independently of the OS cursor.
    // Pin that hidden cursor so Qt cannot hover chrome as the player looks around.
    // The non-raw relative fallback still needs room for its move/recenter events.
    return rawRelative && !viewport.isEmpty() ? QRect(viewport.center(), QSize(1, 1)) : viewport;
}

void StreamVideoItem::updateCursorConfinement()
{
#if defined(Q_OS_WIN)
    if (!m_captureActive || !window() || !window()->isActive()) {
        releaseCursorConfinement();
        return;
    }
    const auto handle = reinterpret_cast<HWND>(window()->winId());
    RECT client{};
    POINT topLeft{};
    if (!handle || !GetClientRect(handle, &client)
        || !ClientToScreen(handle, &topLeft)) {
        releaseCursorConfinement();
        return;
    }
    POINT bottomRight{client.right, client.bottom};
    if (!ClientToScreen(handle, &bottomRight)) {
        releaseCursorConfinement();
        return;
    }
    const auto *content = window()->contentItem();
    if (!content) {
        releaseCursorConfinement();
        return;
    }
    const auto first = mapToItem(content, QPointF(0, 0));
    const auto second = mapToItem(content, QPointF(width(), height()));
    const QRectF itemRect(QPointF(std::min(first.x(), second.x()),
                                 std::min(first.y(), second.y())),
                          QPointF(std::max(first.x(), second.x()),
                                  std::max(first.y(), second.y())));
    const auto captureRect = scaledCaptureRect(
        itemRect, QSizeF(window()->width(), window()->height()),
        QRect(topLeft.x, topLeft.y, bottomRight.x - topLeft.x,
              bottomRight.y - topLeft.y));
    if (captureRect.isEmpty()) {
        releaseCursorConfinement();
        return;
    }
    const auto confinement = cursorConfinementRect(captureRect, m_relativeMouse && m_rawInputActive);
    const RECT screenRect{confinement.left(), confinement.top(),
                          confinement.left() + confinement.width(),
                          confinement.top() + confinement.height()};
    m_cursorConfined = ClipCursor(&screenRect) != FALSE;
#else
    m_cursorConfined = false;
#endif
}

void StreamVideoItem::releaseCursorConfinement()
{
#if defined(Q_OS_WIN)
    if (m_cursorConfined) ClipCursor(nullptr);
#endif
    m_cursorConfined = false;
}

void StreamVideoItem::setRelativeMouse(bool relative)
{
    // Server cursor visibility can change during remote window dragging. Keep
    // the button owner and pointer coordinates stable until the physical release.
    // Focus loss/overlays still release everything through releaseInput().
    if (!m_pressedMouseButtons.isEmpty()) {
        m_pendingRelativeMouse = relative;
        return;
    }
    m_pendingRelativeMouse.reset();
    if (m_relativeMouse == relative) return;
    // Qt owns buttons while the remote cursor is visible; Windows Raw Input
    // owns them in relative mode. A raw release cannot match a button that Qt
    // pressed, so close the old ownership epoch before enabling Raw Input.
    if (relative && !m_rawInputActive) releaseQtMouseButtons();
    m_relativeMouse = relative;
    if (relative) {
        setCursor(Qt::BlankCursor);
        if (m_captureActive) {
            grabMouse();
            const auto anchor = mapToGlobal(QPointF(width() / 2.0, height() / 2.0)).toPoint();
            QCursor::setPos(anchor);
            m_lastMousePosition = mapFromGlobal(QCursor::pos());
        }
    } else {
        ungrabMouse();
        releaseCursorConfinement();
        unsetCursor();
    }
    syncCaptureState();
    emit relativeMouseChanged();
}

void StreamVideoItem::applyRemoteCursor(const QByteArray &bytes)
{
    if (bytes.size() < 2) return;
    const auto messageType = static_cast<quint8>(bytes[0]);
    const auto cursorId = static_cast<quint8>(bytes[1]);
    if (messageType > 1) return;
    const auto hidden = messageType == 0 && cursorId == 0;
    const auto reposition = !m_remoteCursorKnown || !m_remoteCursorVisible;
    const auto metadata = remoteCursorMetadata(bytes);
    m_remoteCursorKnown = true;
    m_remoteCursorVisible = !hidden;
    setRelativeMouse(hidden);
    if (hidden) return;

    if (reposition && metadata.normalizedPosition && m_pressedMouseButtons.isEmpty()
        && m_captureActive) {
        const auto local = mapRemoteCursorPosition(
            *metadata.normalizedPosition, m_videoSize, QSizeF(width(), height()));
        QCursor::setPos(mapToGlobal(local).toPoint());
        m_lastMousePosition = local;
    }

    if (messageType == 1 && metadata.imageOffset >= 0 && metadata.imageLength > 0) {
        QPixmap pixmap;
        const auto image = QByteArray::fromBase64(
            bytes.mid(metadata.imageOffset, metadata.imageLength));
        if (pixmap.loadFromData(image) && pixmap.width() <= 256 && pixmap.height() <= 256) {
            const auto scaledSize = QSize(
                std::clamp(qRound(pixmap.width() / metadata.scale), 1, 256),
                std::clamp(qRound(pixmap.height() / metadata.scale), 1, 256));
            if (scaledSize != pixmap.size())
                pixmap = pixmap.scaled(scaledSize, Qt::IgnoreAspectRatio,
                                       Qt::SmoothTransformation);
            const auto hotspot = QPoint(
                std::clamp(qRound(static_cast<quint8>(bytes[2]) / metadata.scale),
                           0, pixmap.width() - 1),
                std::clamp(qRound(static_cast<quint8>(bytes[3]) / metadata.scale),
                           0, pixmap.height() - 1));
            setCursor(QCursor(pixmap, hotspot.x(), hotspot.y()));
            return;
        }
    }
    switch (cursorId) {
    case 2: setCursor(Qt::IBeamCursor); break;
    case 3: setCursor(Qt::WaitCursor); break;
    case 4: setCursor(Qt::CrossCursor); break;
    case 6: setCursor(Qt::SizeFDiagCursor); break;
    case 7: setCursor(Qt::SizeBDiagCursor); break;
    case 8: setCursor(Qt::SizeHorCursor); break;
    case 9: setCursor(Qt::SizeVerCursor); break;
    case 10: setCursor(Qt::SizeAllCursor); break;
    case 12: setCursor(Qt::PointingHandCursor); break;
    default: setCursor(Qt::ArrowCursor); break;
    }
}

QSGNode *StreamVideoItem::updatePaintNode(QSGNode *oldNode, UpdatePaintNodeData *)
{
    auto *node = static_cast<StreamVideoNode *>(oldNode);
    if (!node) node = new StreamVideoNode(window());
    node->synchronize(this);
    return node;
}

void registerStreamVideoItemQmlType()
{
    qmlRegisterType<StreamVideoItem>("OpenNOW", 1, 0, "StreamVideoItem");
}
