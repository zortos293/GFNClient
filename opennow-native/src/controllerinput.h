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
    Q_PROPERTY(bool controllerActive READ controllerActive NOTIFY inputModeChanged)
    Q_PROPERTY(int batteryPercent READ batteryPercent NOTIFY batteryChanged)
    Q_PROPERTY(QString batteryStatus READ batteryStatus NOTIFY batteryChanged)
    Q_PROPERTY(bool batteryAvailable READ batteryAvailable NOTIFY batteryChanged)
    Q_PROPERTY(bool batteryCharging READ batteryCharging NOTIFY batteryChanged)

public:
    explicit ControllerInput(QObject *parent = nullptr);
    ~ControllerInput() override;

    bool connected() const { return m_connected; }
    QString controllerName() const { return m_controllerName; }
    int controllerCount() const { return m_controllerCount; }
    bool controllerActive() const { return m_controllerActive; }
    int batteryPercent() const { return m_batteryPercent; }
    QString batteryStatus() const { return m_batteryStatus; }
    bool batteryAvailable() const { return m_batteryAvailable; }
    bool batteryCharging() const { return m_batteryCharging; }

    void setStreaming(bool streaming, int protocolVersion = 3);

protected:
    bool eventFilter(QObject *watched, QEvent *event) override;

signals:
    void connectedChanged();
    void batteryChanged();
    void inputModeChanged();
    void sectionRequested(int delta);
    void inputPacket(const QByteArray &payload, bool partiallyReliable);

private:
    void pollEvents();
    void postKey(int key, bool pressed);
    void refreshBattery();
    void refreshController();
    void sendGamepadStates();
    void sendKeyboardEvent(class QKeyEvent *event, bool pressed);
    void sendMouseButtonEvent(class QMouseEvent *event, bool pressed);
    void sendMouseMoveEvent(class QMouseEvent *event);
    void sendMouseWheelEvent(class QWheelEvent *event);
    void sendWrappedInput(const QByteArray &payload, bool mouseMove, bool partiallyReliable);
    void setControllerActive(bool active);

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
    bool m_controllerActive = false;
    bool m_batteryAvailable = false;
    bool m_batteryCharging = false;
    int m_batteryPercent = -1;
    quint32 m_primaryGamepadId = 0;
    QPointF m_lastMousePosition;
    bool m_haveMousePosition = false;
    QString m_controllerName = QStringLiteral("Keyboard");
    QString m_batteryStatus = QStringLiteral("Unavailable");
};
