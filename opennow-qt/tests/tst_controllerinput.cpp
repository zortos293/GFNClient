#include "ControllerInput.h"

#include <QCoreApplication>
#include <QEvent>
#include <QKeyEvent>
#include <QTest>

class ControllerKeySink final : public QObject
{
public:
    int leftPresses = 0;
    int repeatedLeftPresses = 0;

protected:
    bool eventFilter(QObject *watched, QEvent *event) override
    {
        if (event->type() == QEvent::KeyPress) {
            const auto *key = static_cast<QKeyEvent *>(event);
            if (key->key() == Qt::Key_Left
                    && key->nativeScanCode() == ControllerInput::syntheticControllerScanCode) {
                ++leftPresses;
                if (key->isAutoRepeat()) ++repeatedLeftPresses;
            }
        }
        return QObject::eventFilter(watched, event);
    }
};

class ControllerInputTest final : public QObject
{
    Q_OBJECT

private slots:
    void virtualGamepadHotplugHysteresisAndRepeat()
    {
        ControllerInput input;
        const auto initialCount = input.controllerCount();
        ControllerKeySink sink;
        QCoreApplication::instance()->installEventFilter(&sink);

        SDL_VirtualJoystickDesc descriptor{};
        SDL_INIT_INTERFACE(&descriptor);
        descriptor.type = SDL_JOYSTICK_TYPE_GAMEPAD;
        descriptor.naxes = SDL_GAMEPAD_AXIS_COUNT;
        descriptor.nbuttons = SDL_GAMEPAD_BUTTON_COUNT;
        descriptor.axis_mask = 1u << SDL_GAMEPAD_AXIS_LEFTX;
        descriptor.button_mask = 1u << SDL_GAMEPAD_BUTTON_SOUTH;
        descriptor.name = "OpenNOW virtual validation controller";
        const auto id = SDL_AttachVirtualJoystick(&descriptor);
        QVERIFY2(id != 0, SDL_GetError());
        QTRY_COMPARE_WITH_TIMEOUT(input.controllerCount(), initialCount + 1, 2'000);

        auto *joystick = SDL_GetJoystickFromID(id);
        QVERIFY(joystick);
        QVERIFY(SDL_SetJoystickVirtualAxis(joystick, SDL_GAMEPAD_AXIS_LEFTX, -19'000));
        SDL_UpdateJoysticks();
        QTRY_COMPARE_WITH_TIMEOUT(sink.leftPresses, 1, 1'000);

        // The 12k/18k hysteresis band must not emit a second navigation step.
        QVERIFY(SDL_SetJoystickVirtualAxis(joystick, SDL_GAMEPAD_AXIS_LEFTX, -15'000));
        SDL_UpdateJoysticks();
        QTest::qWait(100);
        QCOMPARE(sink.leftPresses, 1);

        // A held direction starts repeat after the 280ms delay.
        QTRY_VERIFY_WITH_TIMEOUT(sink.repeatedLeftPresses >= 1, 1'000);

        // Releasing below 12k and crossing 18k again creates one fresh step.
        QVERIFY(SDL_SetJoystickVirtualAxis(joystick, SDL_GAMEPAD_AXIS_LEFTX, -11'000));
        SDL_UpdateJoysticks();
        QTest::qWait(30);
        const auto beforeSecondPress = sink.leftPresses;
        QVERIFY(SDL_SetJoystickVirtualAxis(joystick, SDL_GAMEPAD_AXIS_LEFTX, -19'000));
        SDL_UpdateJoysticks();
        QTRY_COMPARE_WITH_TIMEOUT(sink.leftPresses, beforeSecondPress + 1, 1'000);

        QVERIFY(SDL_DetachVirtualJoystick(id));
        QTRY_COMPARE_WITH_TIMEOUT(input.controllerCount(), initialCount, 2'000);
        QCoreApplication::instance()->removeEventFilter(&sink);
    }
};

QTEST_MAIN(ControllerInputTest)

#include "tst_controllerinput.moc"
