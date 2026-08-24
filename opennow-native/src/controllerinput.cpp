#include "controllerinput.h"

#include <QCoreApplication>
#include <QGuiApplication>
#include <QKeyEvent>
#include <QMouseEvent>
#include <QWheelEvent>

#include <SDL3/SDL.h>

#include <algorithm>

namespace {
void writeLe16(QByteArray &bytes, int offset, quint16 value)
{
    bytes[offset] = static_cast<char>(value & 0xff);
    bytes[offset + 1] = static_cast<char>((value >> 8) & 0xff);
}

void writeLe32(QByteArray &bytes, int offset, quint32 value)
{
    for (int index = 0; index < 4; ++index) {
        bytes[offset + index] = static_cast<char>((value >> (index * 8)) & 0xff);
    }
}

void writeLe64(QByteArray &bytes, int offset, quint64 value)
{
    for (int index = 0; index < 8; ++index) {
        bytes[offset + index] = static_cast<char>((value >> (index * 8)) & 0xff);
    }
}

void writeBe16(QByteArray &bytes, int offset, quint16 value)
{
    bytes[offset] = static_cast<char>((value >> 8) & 0xff);
    bytes[offset + 1] = static_cast<char>(value & 0xff);
}

void writeBe64(QByteArray &bytes, int offset, quint64 value)
{
    for (int index = 0; index < 8; ++index) {
        bytes[offset + index] = static_cast<char>((value >> ((7 - index) * 8)) & 0xff);
    }
}

quint16 gamepadButtons(SDL_Gamepad *gamepad)
{
    struct Mapping { SDL_GamepadButton button; quint16 mask; };
    static constexpr Mapping mappings[] = {
        {SDL_GAMEPAD_BUTTON_DPAD_UP, 0x0001}, {SDL_GAMEPAD_BUTTON_DPAD_DOWN, 0x0002},
        {SDL_GAMEPAD_BUTTON_DPAD_LEFT, 0x0004}, {SDL_GAMEPAD_BUTTON_DPAD_RIGHT, 0x0008},
        {SDL_GAMEPAD_BUTTON_START, 0x0010}, {SDL_GAMEPAD_BUTTON_BACK, 0x0020},
        {SDL_GAMEPAD_BUTTON_LEFT_STICK, 0x0040}, {SDL_GAMEPAD_BUTTON_RIGHT_STICK, 0x0080},
        {SDL_GAMEPAD_BUTTON_LEFT_SHOULDER, 0x0100}, {SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER, 0x0200},
        {SDL_GAMEPAD_BUTTON_GUIDE, 0x0400}, {SDL_GAMEPAD_BUTTON_SOUTH, 0x1000},
        {SDL_GAMEPAD_BUTTON_EAST, 0x2000}, {SDL_GAMEPAD_BUTTON_WEST, 0x4000},
        {SDL_GAMEPAD_BUTTON_NORTH, 0x8000},
    };
    quint16 result = 0;
    for (const auto &mapping : mappings) {
        if (SDL_GetGamepadButton(gamepad, mapping.button)) {
            result |= mapping.mask;
        }
    }
    return result;
}

qint16 invertedAxis(SDL_Gamepad *gamepad, SDL_GamepadAxis axis)
{
    const int value = SDL_GetGamepadAxis(gamepad, axis);
    return static_cast<qint16>(std::clamp(-value, -32768, 32767));
}
}

ControllerInput::ControllerInput(QObject *parent)
    : QObject(parent)
{
    m_initialized = SDL_Init(SDL_INIT_GAMEPAD | SDL_INIT_EVENTS);
    if (!m_initialized) {
        return;
    }

    refreshController();
    if (qApp) {
        qApp->installEventFilter(this);
    }
    m_pollTimer.setInterval(4);
    m_pollTimer.setTimerType(Qt::PreciseTimer);
    connect(&m_pollTimer, &QTimer::timeout, this, &ControllerInput::pollEvents);
    m_pollTimer.start();
}

ControllerInput::~ControllerInput()
{
    for (auto *gamepad : std::as_const(m_gamepads)) {
        SDL_CloseGamepad(gamepad);
    }
    if (m_initialized) {
        SDL_QuitSubSystem(SDL_INIT_GAMEPAD | SDL_INIT_EVENTS);
    }
}

void ControllerInput::refreshController()
{
    for (auto *gamepad : std::as_const(m_gamepads)) {
        SDL_CloseGamepad(gamepad);
    }
    m_gamepads.clear();
    int count = 0;
    SDL_JoystickID *gamepads = SDL_GetGamepads(&count);
    for (int index = 0; index < count; ++index) {
        if (auto *gamepad = SDL_OpenGamepad(gamepads[index])) {
            m_gamepads.insert(gamepads[index], gamepad);
        }
    }
    count = m_gamepads.size();
    const bool connected = count > 0;
    QString name = QStringLiteral("Keyboard");
    if (connected) {
        const char *rawName = SDL_GetGamepadNameForID(gamepads[0]);
        name = rawName ? QString::fromUtf8(rawName) : QStringLiteral("Gamepad");
    }
    SDL_free(gamepads);

    if (connected != m_connected || name != m_controllerName || count != m_controllerCount) {
        m_connected = connected;
        m_controllerCount = count;
        m_controllerName = name;
        emit connectedChanged();
    }
}

void ControllerInput::setStreaming(bool streaming, int protocolVersion)
{
    m_streaming = streaming;
    m_inputProtocolVersion = std::clamp(protocolVersion, 2, 16);
    m_gamepadSequence = 1;
    m_lastGamepadSendMs = 0;
    m_lastHeartbeatMs = 0;
    if (streaming) {
        m_inputClock.restart();
    }
    m_haveMousePosition = false;
}

void ControllerInput::sendWrappedInput(const QByteArray &payload, bool mouseMove,
                                       bool partiallyReliable)
{
    if (m_inputProtocolVersion <= 2) {
        emit inputPacket(payload, partiallyReliable);
        return;
    }
    const quint64 timestampUs = static_cast<quint64>(m_inputClock.nsecsElapsed() / 1000);
    QByteArray wrapped(mouseMove ? 12 + payload.size() : 10 + payload.size(), '\0');
    wrapped[0] = 0x23;
    writeBe64(wrapped, 1, timestampUs);
    wrapped[9] = mouseMove ? 0x21 : 0x22;
    const int bodyOffset = mouseMove ? 12 : 10;
    if (mouseMove) {
        writeBe16(wrapped, 10, static_cast<quint16>(payload.size()));
    }
    std::copy(payload.cbegin(), payload.cend(), wrapped.begin() + bodyOffset);
    emit inputPacket(wrapped, partiallyReliable);
}

void ControllerInput::sendKeyboardEvent(QKeyEvent *event, bool pressed)
{
    if (event->isAutoRepeat()) {
        return;
    }
    int virtualKey = event->key();
    switch (event->key()) {
    case Qt::Key_Escape: virtualKey = 0x1b; break;
    case Qt::Key_Tab: virtualKey = 0x09; break;
    case Qt::Key_Backspace: virtualKey = 0x08; break;
    case Qt::Key_Return:
    case Qt::Key_Enter: virtualKey = 0x0d; break;
    case Qt::Key_Space: virtualKey = 0x20; break;
    case Qt::Key_Left: virtualKey = 0x25; break;
    case Qt::Key_Up: virtualKey = 0x26; break;
    case Qt::Key_Right: virtualKey = 0x27; break;
    case Qt::Key_Down: virtualKey = 0x28; break;
    case Qt::Key_Delete: virtualKey = 0x2e; break;
    case Qt::Key_Insert: virtualKey = 0x2d; break;
    case Qt::Key_Home: virtualKey = 0x24; break;
    case Qt::Key_End: virtualKey = 0x23; break;
    case Qt::Key_PageUp: virtualKey = 0x21; break;
    case Qt::Key_PageDown: virtualKey = 0x22; break;
    default:
        if (event->key() >= Qt::Key_F1 && event->key() <= Qt::Key_F24) {
            virtualKey = 0x70 + event->key() - Qt::Key_F1;
        } else if (virtualKey < 0 || virtualKey > 0xff) {
            return;
        }
        break;
    }
    quint16 modifiers = 0;
    if (event->modifiers().testFlag(Qt::ShiftModifier) && event->key() != Qt::Key_Shift) modifiers |= 0x01;
    if (event->modifiers().testFlag(Qt::ControlModifier) && event->key() != Qt::Key_Control) modifiers |= 0x02;
    if (event->modifiers().testFlag(Qt::AltModifier) && event->key() != Qt::Key_Alt) modifiers |= 0x04;
    if (event->modifiers().testFlag(Qt::MetaModifier) && event->key() != Qt::Key_Meta) modifiers |= 0x08;
    QByteArray payload(18, '\0');
    writeLe32(payload, 0, pressed ? 3 : 4);
    writeBe16(payload, 4, static_cast<quint16>(virtualKey));
    writeBe16(payload, 6, modifiers);
    writeBe64(payload, 10, static_cast<quint64>(m_inputClock.nsecsElapsed() / 1000));
    sendWrappedInput(payload, false, false);
}

void ControllerInput::sendMouseButtonEvent(QMouseEvent *event, bool pressed)
{
    int button = 0;
    switch (event->button()) {
    case Qt::LeftButton: button = 1; break;
    case Qt::MiddleButton: button = 2; break;
    case Qt::RightButton: button = 3; break;
    case Qt::BackButton: button = 4; break;
    case Qt::ForwardButton: button = 5; break;
    default: return;
    }
    QByteArray payload(18, '\0');
    writeLe32(payload, 0, pressed ? 8 : 9);
    payload[4] = static_cast<char>(button);
    writeBe64(payload, 10, static_cast<quint64>(m_inputClock.nsecsElapsed() / 1000));
    sendWrappedInput(payload, false, false);
}

void ControllerInput::sendMouseMoveEvent(QMouseEvent *event)
{
    const auto position = event->globalPosition();
    if (!m_haveMousePosition) {
        m_lastMousePosition = position;
        m_haveMousePosition = true;
        return;
    }
    const auto delta = position - m_lastMousePosition;
    m_lastMousePosition = position;
    const int dx = std::clamp(qRound(delta.x()), -32768, 32767);
    const int dy = std::clamp(qRound(delta.y()), -32768, 32767);
    if (dx == 0 && dy == 0) {
        return;
    }
    QByteArray payload(22, '\0');
    writeLe32(payload, 0, 7);
    writeBe16(payload, 4, static_cast<quint16>(static_cast<qint16>(dx)));
    writeBe16(payload, 6, static_cast<quint16>(static_cast<qint16>(dy)));
    writeBe64(payload, 14, static_cast<quint64>(m_inputClock.nsecsElapsed() / 1000));
    sendWrappedInput(payload, true, true);
}

void ControllerInput::sendMouseWheelEvent(QWheelEvent *event)
{
    const int delta = std::clamp(event->angleDelta().y(), -32768, 32767);
    QByteArray payload(22, '\0');
    writeLe32(payload, 0, 10);
    writeBe16(payload, 6, static_cast<quint16>(static_cast<qint16>(delta)));
    writeBe64(payload, 14, static_cast<quint64>(m_inputClock.nsecsElapsed() / 1000));
    sendWrappedInput(payload, false, false);
}

bool ControllerInput::eventFilter(QObject *watched, QEvent *event)
{
    if (m_streaming && m_inputClock.isValid()) {
        switch (event->type()) {
        case QEvent::KeyPress: sendKeyboardEvent(static_cast<QKeyEvent *>(event), true); break;
        case QEvent::KeyRelease: sendKeyboardEvent(static_cast<QKeyEvent *>(event), false); break;
        case QEvent::MouseButtonPress: sendMouseButtonEvent(static_cast<QMouseEvent *>(event), true); break;
        case QEvent::MouseButtonRelease: sendMouseButtonEvent(static_cast<QMouseEvent *>(event), false); break;
        case QEvent::MouseMove: sendMouseMoveEvent(static_cast<QMouseEvent *>(event)); break;
        case QEvent::Wheel: sendMouseWheelEvent(static_cast<QWheelEvent *>(event)); break;
        default: break;
        }
    }
    return QObject::eventFilter(watched, event);
}

void ControllerInput::sendGamepadStates()
{
    if (!m_streaming || !m_inputClock.isValid()) {
        return;
    }
    const auto elapsedMs = m_inputClock.elapsed();
    if (elapsedMs - m_lastHeartbeatMs >= 2000) {
        QByteArray heartbeat(4, Qt::Uninitialized);
        writeLe32(heartbeat, 0, 2);
        emit inputPacket(heartbeat, false);
        m_lastHeartbeatMs = elapsedMs;
    }
    if (elapsedMs - m_lastGamepadSendMs < 16) {
        return;
    }
    m_lastGamepadSendMs = elapsedMs;
    const quint64 timestampUs = static_cast<quint64>(m_inputClock.nsecsElapsed() / 1000);
    const int count = std::min(4, static_cast<int>(m_gamepads.size()));
    const quint16 bitmap = count > 0 ? static_cast<quint16>(((1u << count) - 1u) | (((1u << count) - 1u) << 8)) : 0;
    int controllerIndex = 0;
    for (auto *gamepad : std::as_const(m_gamepads)) {
        if (controllerIndex >= count) {
            break;
        }
        QByteArray raw(38, '\0');
        writeLe32(raw, 0, 12);
        writeLe16(raw, 4, 26);
        writeLe16(raw, 6, static_cast<quint16>(controllerIndex));
        writeLe16(raw, 8, bitmap);
        writeLe16(raw, 10, 20);
        writeLe16(raw, 12, gamepadButtons(gamepad));
        const int leftTrigger = std::max(0, int(SDL_GetGamepadAxis(gamepad, SDL_GAMEPAD_AXIS_LEFT_TRIGGER)));
        const int rightTrigger = std::max(0, int(SDL_GetGamepadAxis(gamepad, SDL_GAMEPAD_AXIS_RIGHT_TRIGGER)));
        const quint16 triggers = static_cast<quint16>((leftTrigger * 255 / 32767)
                                                       | ((rightTrigger * 255 / 32767) << 8));
        writeLe16(raw, 14, triggers);
        writeLe16(raw, 16, static_cast<quint16>(SDL_GetGamepadAxis(gamepad, SDL_GAMEPAD_AXIS_LEFTX)));
        writeLe16(raw, 18, static_cast<quint16>(invertedAxis(gamepad, SDL_GAMEPAD_AXIS_LEFTY)));
        writeLe16(raw, 20, static_cast<quint16>(SDL_GetGamepadAxis(gamepad, SDL_GAMEPAD_AXIS_RIGHTX)));
        writeLe16(raw, 22, static_cast<quint16>(invertedAxis(gamepad, SDL_GAMEPAD_AXIS_RIGHTY)));
        writeLe16(raw, 26, 85);
        writeLe64(raw, 30, timestampUs);

        if (m_inputProtocolVersion <= 2) {
            emit inputPacket(raw, true);
        } else {
            QByteArray wrapped(54, '\0');
            wrapped[0] = 0x23;
            writeBe64(wrapped, 1, timestampUs);
            wrapped[9] = 0x26;
            wrapped[10] = static_cast<char>(controllerIndex);
            writeBe16(wrapped, 11, m_gamepadSequence++);
            wrapped[13] = 0x21;
            writeBe16(wrapped, 14, static_cast<quint16>(raw.size()));
            std::copy(raw.cbegin(), raw.cend(), wrapped.begin() + 16);
            emit inputPacket(wrapped, true);
        }
        ++controllerIndex;
    }
}

void ControllerInput::postKey(int key, bool pressed)
{
    QObject *target = QGuiApplication::focusObject();
    if (!target) {
        return;
    }
    QCoreApplication::postEvent(
        target,
        new QKeyEvent(pressed ? QEvent::KeyPress : QEvent::KeyRelease, key, Qt::NoModifier));
}

void ControllerInput::pollEvents()
{
    SDL_Event event;
    while (SDL_PollEvent(&event)) {
        if (event.type == SDL_EVENT_GAMEPAD_ADDED || event.type == SDL_EVENT_GAMEPAD_REMOVED
            || event.type == SDL_EVENT_GAMEPAD_REMAPPED) {
            refreshController();
            continue;
        }
        if (m_streaming) {
            continue;
        }
        if (event.type == SDL_EVENT_GAMEPAD_AXIS_MOTION) {
            constexpr Sint16 Deadzone = 18000;
            if (event.gaxis.axis == SDL_GAMEPAD_AXIS_LEFTX) {
                const int direction = event.gaxis.value > Deadzone ? 1 : (event.gaxis.value < -Deadzone ? -1 : 0);
                if (direction != 0 && direction != m_axisXDirection) {
                    postKey(direction > 0 ? Qt::Key_Right : Qt::Key_Left, true);
                    postKey(direction > 0 ? Qt::Key_Right : Qt::Key_Left, false);
                }
                m_axisXDirection = direction;
            } else if (event.gaxis.axis == SDL_GAMEPAD_AXIS_LEFTY) {
                const int direction = event.gaxis.value > Deadzone ? 1 : (event.gaxis.value < -Deadzone ? -1 : 0);
                if (direction != 0 && direction != m_axisYDirection) {
                    postKey(direction > 0 ? Qt::Key_Down : Qt::Key_Up, true);
                    postKey(direction > 0 ? Qt::Key_Down : Qt::Key_Up, false);
                }
                m_axisYDirection = direction;
            }
            continue;
        }
        if (event.type != SDL_EVENT_GAMEPAD_BUTTON_DOWN
            && event.type != SDL_EVENT_GAMEPAD_BUTTON_UP) {
            continue;
        }

        const bool pressed = event.type == SDL_EVENT_GAMEPAD_BUTTON_DOWN;
        switch (event.gbutton.button) {
        case SDL_GAMEPAD_BUTTON_DPAD_UP:
            postKey(Qt::Key_Up, pressed);
            break;
        case SDL_GAMEPAD_BUTTON_DPAD_DOWN:
            postKey(Qt::Key_Down, pressed);
            break;
        case SDL_GAMEPAD_BUTTON_DPAD_LEFT:
            postKey(Qt::Key_Left, pressed);
            break;
        case SDL_GAMEPAD_BUTTON_DPAD_RIGHT:
            postKey(Qt::Key_Right, pressed);
            break;
        case SDL_GAMEPAD_BUTTON_SOUTH:
            postKey(Qt::Key_Return, pressed);
            break;
        case SDL_GAMEPAD_BUTTON_EAST:
            postKey(Qt::Key_Escape, pressed);
            break;
        case SDL_GAMEPAD_BUTTON_WEST:
            postKey(Qt::Key_X, pressed);
            break;
        case SDL_GAMEPAD_BUTTON_NORTH:
            postKey(Qt::Key_Y, pressed);
            break;
        case SDL_GAMEPAD_BUTTON_START:
            postKey(Qt::Key_F6, pressed);
            break;
        case SDL_GAMEPAD_BUTTON_LEFT_SHOULDER:
            if (pressed) {
                emit sectionRequested(-1);
            }
            break;
        case SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER:
            if (pressed) {
                emit sectionRequested(1);
            }
            break;
        default:
            break;
        }
    }
    sendGamepadStates();
}
