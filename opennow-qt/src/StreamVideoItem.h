#pragma once

#include <QQuickRhiItem>
#include <QHash>
#include <QSet>
#include <QRect>
#include <QSize>

#include <memory>

class QRhi;
class QRhiCommandBuffer;
class QRhiRenderTarget;
class NativeStreamRuntime;

class StreamVideoRenderCallback
{
public:
    virtual ~StreamVideoRenderCallback() = default;

    virtual void initialize(QRhi *rhi,
                            QRhiCommandBuffer *commandBuffer,
                            QRhiRenderTarget *renderTarget) = 0;
    virtual void prepareFrame(QRhiCommandBuffer *commandBuffer) = 0;
    virtual void recordFrame(QRhiCommandBuffer *commandBuffer,
                             const QRect &videoViewport) = 0;
    virtual void finishFrame() = 0;
    virtual void releaseResources() = 0;
};

class StreamVideoItem : public QQuickRhiItem
{
    Q_OBJECT
    Q_PROPERTY(QSize videoSize READ videoSize WRITE setVideoSize NOTIFY videoSizeChanged)
    Q_PROPERTY(bool renderCallbackAvailable READ renderCallbackAvailable
                   NOTIFY renderCallbackAvailableChanged)
    Q_PROPERTY(bool nativeRuntimeAvailable READ nativeRuntimeAvailable CONSTANT)
    Q_PROPERTY(bool inputEnabled READ inputEnabled WRITE setInputEnabled
                   NOTIFY inputEnabledChanged)
    Q_PROPERTY(bool captureActive READ captureActive NOTIFY captureActiveChanged)
    Q_PROPERTY(bool relativeMouse READ relativeMouse WRITE setRelativeMouse
                   NOTIFY relativeMouseChanged)

public:
    explicit StreamVideoItem(QQuickItem *parent = nullptr);

    [[nodiscard]] QSize videoSize() const;
    void setVideoSize(const QSize &size);

    [[nodiscard]] bool renderCallbackAvailable() const;
    [[nodiscard]] bool nativeRuntimeAvailable() const;
    [[nodiscard]] bool inputEnabled() const;
    void setInputEnabled(bool enabled);
    [[nodiscard]] bool captureActive() const;
    [[nodiscard]] bool relativeMouse() const;
    void setRelativeMouse(bool relative);
    [[nodiscard]] std::shared_ptr<StreamVideoRenderCallback> renderCallback() const;
    void setRenderCallback(std::shared_ptr<StreamVideoRenderCallback> callback);

    static void setNativeStreamRuntime(NativeStreamRuntime *runtime);
    [[nodiscard]] static NativeStreamRuntime *nativeStreamRuntime();

    Q_INVOKABLE void requestFrame();

    [[nodiscard]] static QRect aspectFitRect(const QSize &videoSize,
                                             const QSize &targetSize);
    [[nodiscard]] static quint16 windowsVirtualKey(int key);
    [[nodiscard]] static quint16 inputModifiers(Qt::KeyboardModifiers modifiers, int key);

signals:
    void videoSizeChanged();
    void renderCallbackAvailableChanged();
    void inputEnabledChanged();
    void captureActiveChanged();
    void relativeMouseChanged();

protected:
    QQuickRhiItemRenderer *createRenderer() override;
    void focusInEvent(QFocusEvent *event) override;
    void focusOutEvent(QFocusEvent *event) override;
    void keyPressEvent(QKeyEvent *event) override;
    void keyReleaseEvent(QKeyEvent *event) override;
    void mousePressEvent(QMouseEvent *event) override;
    void mouseReleaseEvent(QMouseEvent *event) override;
    void mouseMoveEvent(QMouseEvent *event) override;
    void wheelEvent(QWheelEvent *event) override;
    void itemChange(ItemChange change, const ItemChangeData &data) override;

private:
    struct PressedKey {
        quint16 virtualKey = 0;
        quint16 modifiers = 0;
    };

    void applyRemoteCursor(const QByteArray &bytes);
    void syncCaptureState();
    void releaseInput();
    void submitAbsoluteMouse(const QPointF &position);
    [[nodiscard]] quint32 keyIdentity(const QKeyEvent *event) const;
    [[nodiscard]] static quint8 mouseButton(Qt::MouseButton button);

    QSize m_videoSize;
    std::shared_ptr<StreamVideoRenderCallback> m_renderCallback;
    QHash<quint32, PressedKey> m_pressedKeys;
    QSet<quint32> m_pressedShortcuts;
    QSet<quint8> m_pressedMouseButtons;
    QPointF m_lastMousePosition;
    bool m_inputEnabled = true;
    bool m_captureActive = false;
    bool m_relativeMouse = false;
    bool m_rawInputActive = false;
};

void registerStreamVideoItemQmlType();
