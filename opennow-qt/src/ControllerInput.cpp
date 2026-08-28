#include "ControllerInput.h"

#include <QCoreApplication>
#include <QGuiApplication>
#include <QKeyEvent>

#include <algorithm>
#include <QVariantMap>

namespace {
constexpr Sint16 axisPressThreshold = 18000;
constexpr Sint16 axisReleaseThreshold = 12000;
constexpr qint64 repeatDelayMs = 280;
constexpr qint64 repeatIntervalMs = 85;
}

ControllerInput::ControllerInput(QObject *parent)
    : QObject(parent)
{
    SDL_SetHint(SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1");
    m_sdlReady = SDL_InitSubSystem(SDL_INIT_GAMEPAD);
    if (!m_sdlReady) {
        qWarning("SDL gamepad initialization failed: %s", SDL_GetError());
        return;
    }

    int count = 0;
    if (auto *ids = SDL_GetGamepads(&count)) {
        for (int index = 0; index < count; ++index) {
            openController(ids[index]);
        }
        SDL_free(ids);
    }

    m_clock.start();
    m_pollTimer.setTimerType(Qt::PreciseTimer);
    m_pollTimer.setInterval(4);
    connect(&m_pollTimer, &QTimer::timeout, this, &ControllerInput::poll);
    m_pollTimer.start();
}

ControllerInput::~ControllerInput()
{
    m_pollTimer.stop();
    for (auto *gamepad : std::as_const(m_gamepads)) {
        SDL_CloseGamepad(gamepad);
    }
    m_gamepads.clear();
    if (m_sdlReady) {
        SDL_QuitSubSystem(SDL_INIT_GAMEPAD);
    }
}

int ControllerInput::controllerCount() const
{
    return m_gamepads.size();
}

QVariantList ControllerInput::controllers() const
{
    auto ids = m_gamepads.keys();
    std::sort(ids.begin(), ids.end());
    QVariantList result;
    result.reserve(ids.size());
    for (qsizetype index = 0; index < ids.size(); ++index) {
        auto *gamepad = m_gamepads.value(ids.at(index));
        if (!gamepad) {
            continue;
        }
        int batteryPercent = -1;
        const auto power = SDL_GetGamepadPowerInfo(gamepad, &batteryPercent);
        const auto *name = SDL_GetGamepadName(gamepad);
        result.push_back(QVariantMap{
            {QStringLiteral("slot"), index + 1},
            {QStringLiteral("instanceId"), static_cast<qulonglong>(ids.at(index))},
            {QStringLiteral("name"), name ? QString::fromUtf8(name) : QStringLiteral("Game controller")},
            {QStringLiteral("batteryPercent"), batteryPercent},
            {QStringLiteral("charging"), power == SDL_POWERSTATE_CHARGING},
        });
    }
    return result;
}

bool ControllerInput::shellCaptureEnabled() const
{
    return m_shellCaptureEnabled;
}

void ControllerInput::setShellCaptureEnabled(bool enabled)
{
    if (m_shellCaptureEnabled == enabled) {
        return;
    }
    m_shellCaptureEnabled = enabled;
    if (!enabled) {
        for (auto *direction : {&m_left, &m_right, &m_up, &m_down}) {
            direction->active = false;
            direction->pressedAt = 0;
            direction->repeatedAt = 0;
        }
    }
    emit shellCaptureEnabledChanged();
}

void ControllerInput::poll()
{
    SDL_Event event;
    while (SDL_PollEvent(&event)) {
        switch (event.type) {
        case SDL_EVENT_GAMEPAD_ADDED:
            openController(event.gdevice.which);
            break;
        case SDL_EVENT_GAMEPAD_REMOVED:
            closeController(event.gdevice.which);
            break;
        case SDL_EVENT_GAMEPAD_BUTTON_DOWN:
            handleButton(event.gbutton, true);
            break;
        case SDL_EVENT_GAMEPAD_BUTTON_UP:
            handleButton(event.gbutton, false);
            break;
        case SDL_EVENT_GAMEPAD_AXIS_MOTION:
            handleAxis(event.gaxis);
            break;
        default:
            break;
        }
    }

    dispatchRepeats(m_clock.elapsed());
    if (m_clock.elapsed() - m_lastControllerMetadataAt >= 2'000) {
        m_lastControllerMetadataAt = m_clock.elapsed();
        emit controllersChanged();
    }
}

void ControllerInput::openController(SDL_JoystickID id)
{
    if (m_gamepads.contains(id)) {
        return;
    }
    if (auto *gamepad = SDL_OpenGamepad(id)) {
        m_gamepads.insert(id, gamepad);
        emit controllerCountChanged(m_gamepads.size());
        emit controllersChanged();
    } else {
        qWarning("Could not open SDL gamepad %u: %s", id, SDL_GetError());
    }
}

void ControllerInput::closeController(SDL_JoystickID id)
{
    auto *gamepad = m_gamepads.take(id);
    if (!gamepad) {
        return;
    }
    SDL_CloseGamepad(gamepad);
    for (auto *direction : {&m_left, &m_right, &m_up, &m_down}) {
        direction->active = false;
        direction->pressedAt = 0;
        direction->repeatedAt = 0;
    }
    emit controllerCountChanged(m_gamepads.size());
    emit controllersChanged();
}

void ControllerInput::handleButton(const SDL_GamepadButtonEvent &event, bool pressed)
{
    if (!m_shellCaptureEnabled) {
        return;
    }
    const auto key = keyForButton(event.button);
    if (key != 0) {
        postKey(key, pressed);
        if (pressed) {
            emit controllerActivity();
        }
    }
}

void ControllerInput::handleAxis(const SDL_GamepadAxisEvent &event)
{
    if (!m_shellCaptureEnabled) {
        return;
    }

    const auto value = event.value;
    if (event.axis == SDL_GAMEPAD_AXIS_LEFTX) {
        updateDirection(m_left, value < (m_left.active ? -axisReleaseThreshold : -axisPressThreshold));
        updateDirection(m_right, value > (m_right.active ? axisReleaseThreshold : axisPressThreshold));
    } else if (event.axis == SDL_GAMEPAD_AXIS_LEFTY) {
        updateDirection(m_up, value < (m_up.active ? -axisReleaseThreshold : -axisPressThreshold));
        updateDirection(m_down, value > (m_down.active ? axisReleaseThreshold : axisPressThreshold));
    }
}

void ControllerInput::updateDirection(RepeatingDirection &direction, bool active)
{
    if (direction.active == active) {
        return;
    }
    direction.active = active;
    if (active) {
        direction.pressedAt = m_clock.elapsed();
        direction.repeatedAt = direction.pressedAt;
        postKey(direction.key, true);
        postKey(direction.key, false);
        emit controllerActivity();
    }
}

void ControllerInput::dispatchRepeats(qint64 now)
{
    for (auto *direction : {&m_left, &m_right, &m_up, &m_down}) {
        if (!direction->active || now - direction->pressedAt < repeatDelayMs
            || now - direction->repeatedAt < repeatIntervalMs) {
            continue;
        }
        direction->repeatedAt = now;
        postKey(direction->key, true, true);
        postKey(direction->key, false, true);
    }
}

void ControllerInput::postKey(int key, bool pressed, bool autoRepeat)
{
    if (!m_shellCaptureEnabled) {
        return;
    }
    auto *target = QGuiApplication::focusObject();
    if (!target) {
        target = QCoreApplication::instance();
    }
    const auto type = pressed ? QEvent::KeyPress : QEvent::KeyRelease;
    QCoreApplication::postEvent(
        target,
        new QKeyEvent(type, key, Qt::NoModifier, syntheticControllerScanCode, 0, 0, QString(),
                      autoRepeat));
}

int ControllerInput::keyForButton(Uint8 button)
{
    switch (button) {
    case SDL_GAMEPAD_BUTTON_SOUTH:
        return Qt::Key_Return;
    case SDL_GAMEPAD_BUTTON_EAST:
        return Qt::Key_Escape;
    case SDL_GAMEPAD_BUTTON_WEST:
        return Qt::Key_X;
    case SDL_GAMEPAD_BUTTON_NORTH:
        return Qt::Key_Y;
    case SDL_GAMEPAD_BUTTON_DPAD_LEFT:
        return Qt::Key_Left;
    case SDL_GAMEPAD_BUTTON_DPAD_RIGHT:
        return Qt::Key_Right;
    case SDL_GAMEPAD_BUTTON_DPAD_UP:
        return Qt::Key_Up;
    case SDL_GAMEPAD_BUTTON_DPAD_DOWN:
        return Qt::Key_Down;
    case SDL_GAMEPAD_BUTTON_LEFT_SHOULDER:
        return Qt::Key_PageUp;
    case SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER:
        return Qt::Key_PageDown;
    case SDL_GAMEPAD_BUTTON_START:
        return Qt::Key_Menu;
    case SDL_GAMEPAD_BUTTON_BACK:
        return Qt::Key_Back;
    case SDL_GAMEPAD_BUTTON_GUIDE:
        return Qt::Key_F1;
    default:
        return 0;
    }
}
