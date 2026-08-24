#include "controllerinput.h"

#include <QCoreApplication>
#include <QGuiApplication>
#include <QKeyEvent>

#include <SDL3/SDL.h>

ControllerInput::ControllerInput(QObject *parent)
    : QObject(parent)
{
    m_initialized = SDL_Init(SDL_INIT_GAMEPAD | SDL_INIT_EVENTS);
    if (!m_initialized) {
        return;
    }

    refreshController();
    m_pollTimer.setInterval(4);
    m_pollTimer.setTimerType(Qt::PreciseTimer);
    connect(&m_pollTimer, &QTimer::timeout, this, &ControllerInput::pollEvents);
    m_pollTimer.start();
}

ControllerInput::~ControllerInput()
{
    if (m_initialized) {
        SDL_QuitSubSystem(SDL_INIT_GAMEPAD | SDL_INIT_EVENTS);
    }
}

void ControllerInput::refreshController()
{
    int count = 0;
    SDL_JoystickID *gamepads = SDL_GetGamepads(&count);
    const bool connected = count > 0;
    QString name = QStringLiteral("Keyboard");
    if (connected) {
        const char *rawName = SDL_GetGamepadNameForID(gamepads[0]);
        name = rawName ? QString::fromUtf8(rawName) : QStringLiteral("Gamepad");
    }
    SDL_free(gamepads);

    if (connected != m_connected || name != m_controllerName) {
        m_connected = connected;
        m_controllerName = name;
        emit connectedChanged();
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
}
