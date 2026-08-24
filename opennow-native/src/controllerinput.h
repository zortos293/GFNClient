#pragma once

#include <QObject>
#include <QElapsedTimer>
#include <QHash>
#include <QPointF>
#include <QTimer>

struct SDL_Gamepad;

class ControllerInput final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool connected READ connected NOTIFY connectedChanged)
    Q_PROPERTY(QString controllerName READ controllerName NOTIFY connectedChanged)
    Q_PROPERTY(int controllerCount READ controllerCount NOTIFY connectedChanged)

public:
    explicit ControllerInput(QObject *parent = nullptr);
    ~ControllerInput() override;

    bool connected() const { return m_connected; }
    QString controllerName() const { return m_controllerName; }
    int controllerCount() const { return m_controllerCount; }

    void setStreaming(bool streaming, int protocolVersion = 3);

protected:
    bool eventFilter(QObject *watched, QEvent *event) override;

signals:
    void connectedChanged();
    void sectionRequested(int delta);
    void inputPacket(const QByteArray &payload, bool partiallyReliable);

private:
    void pollEvents();
    void postKey(int key, bool pressed);
    void refreshController();
    void sendGamepadStates();
    void sendKeyboardEvent(class QKeyEvent *event, bool pressed);
    void sendMouseButtonEvent(class QMouseEvent *event, bool pressed);
    void sendMouseMoveEvent(class QMouseEvent *event);
    void sendMouseWheelEvent(class QWheelEvent *event);
    void sendWrappedInput(const QByteArray &payload, bool mouseMove, bool partiallyReliable);

    QTimer m_pollTimer;
    QElapsedTimer m_inputClock;
    QHash<quint32, SDL_Gamepad *> m_gamepads;
    bool m_initialized = false;
    bool m_connected = false;
    int m_controllerCount = 0;
    int m_axisXDirection = 0;
    int m_axisYDirection = 0;
    int m_inputProtocolVersion = 3;
    quint16 m_gamepadSequence = 1;
    qint64 m_lastGamepadSendMs = 0;
    qint64 m_lastHeartbeatMs = 0;
    bool m_streaming = false;
    QPointF m_lastMousePosition;
    bool m_haveMousePosition = false;
    QString m_controllerName = QStringLiteral("Keyboard");
};
