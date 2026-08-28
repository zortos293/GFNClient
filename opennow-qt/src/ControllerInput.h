#pragma once

#include <QObject>
#include <QElapsedTimer>
#include <QHash>
#include <QTimer>
#include <QVariantList>

#include <SDL3/SDL.h>

class ControllerInput final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(int controllerCount READ controllerCount NOTIFY controllerCountChanged)
    Q_PROPERTY(QVariantList controllers READ controllers NOTIFY controllersChanged)
    Q_PROPERTY(bool shellCaptureEnabled READ shellCaptureEnabled WRITE setShellCaptureEnabled NOTIFY shellCaptureEnabledChanged)

public:
    static constexpr quint32 syntheticControllerScanCode = 0x4f504e57;

    explicit ControllerInput(QObject *parent = nullptr);
    ~ControllerInput() override;

    [[nodiscard]] int controllerCount() const;
    [[nodiscard]] QVariantList controllers() const;
    [[nodiscard]] bool shellCaptureEnabled() const;
    void setShellCaptureEnabled(bool enabled);

signals:
    void controllerCountChanged(int count);
    void controllersChanged();
    void shellCaptureEnabledChanged();
    void controllerActivity();

private slots:
    void poll();

private:
    struct RepeatingDirection {
        bool active = false;
        qint64 pressedAt = 0;
        qint64 repeatedAt = 0;
        int key = 0;
    };

    void openController(SDL_JoystickID id);
    void closeController(SDL_JoystickID id);
    void handleButton(const SDL_GamepadButtonEvent &event, bool pressed);
    void handleAxis(const SDL_GamepadAxisEvent &event);
    void updateDirection(RepeatingDirection &direction, bool active);
    void dispatchRepeats(qint64 now);
    void postKey(int key, bool pressed, bool autoRepeat = false);
    static int keyForButton(Uint8 button);

    QTimer m_pollTimer;
    QElapsedTimer m_clock;
    QHash<SDL_JoystickID, SDL_Gamepad *> m_gamepads;
    bool m_sdlReady = false;
    bool m_shellCaptureEnabled = true;
    qint64 m_lastControllerMetadataAt = 0;
    RepeatingDirection m_left{false, 0, 0, Qt::Key_Left};
    RepeatingDirection m_right{false, 0, 0, Qt::Key_Right};
    RepeatingDirection m_up{false, 0, 0, Qt::Key_Up};
    RepeatingDirection m_down{false, 0, 0, Qt::Key_Down};
};
