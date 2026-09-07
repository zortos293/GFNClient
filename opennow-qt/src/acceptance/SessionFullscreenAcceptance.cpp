#include "acceptance/AcceptanceSession.h"
#include "app/AppController.h"

#include <QGuiApplication>
#include <QKeyEvent>
#include <QQmlApplicationEngine>
#include <QQuickItem>
#include <QQuickWindow>
#include <QTimer>
#include <QVariantMap>

#include <cstdlib>
#include <memory>

using namespace Qt::StringLiterals;

int AcceptanceSession::startSessionFullscreenWorkload()
{
    auto *window = m_engine.rootObjects().isEmpty() ? nullptr
        : qobject_cast<QQuickWindow *>(m_engine.rootObjects().first());
    auto *store = m_engine.singletonInstance<QObject *>(u"OpenNOW"_s, u"ShellStore"_s);
    if (!window || !store) return EXIT_FAILURE;
    store->setProperty("streamer", QVariantMap{{u"status"_s, u"streaming"_s}});
    store->setProperty("streamState", u"streaming"_s);
    const auto restoredVisibility = m_arguments.contains(u"--smoke-fullscreen-restore-maximized"_s)
        ? QWindow::Maximized : QWindow::Windowed;
    if (restoredVisibility == QWindow::Maximized) window->showMaximized();
    window->requestActivate();
    const auto step = std::make_shared<int>(0);
    auto *timer = new QTimer(this);
    timer->setInterval(150);
    connect(timer, &QTimer::timeout, this, [this, window, store, restoredVisibility, step, timer] {
        const auto require = [this, step, timer](bool ok, const char *message) {
            if (!ok) {
                qCritical("Session fullscreen step %d: %s", *step, message);
                timer->stop();
                m_application.exit(EXIT_FAILURE);
            }
            return ok;
        };
        const auto keyClick = [window](Qt::Key key) {
            QKeyEvent press(QEvent::KeyPress, key, Qt::NoModifier);
            QGuiApplication::sendEvent(window, &press);
            QKeyEvent release(QEvent::KeyRelease, key, Qt::NoModifier);
            QGuiApplication::sendEvent(window, &release);
        };
        if (!require(!m_qmlWarningOccurred, "QML warning")) return;
        switch ((*step)++) {
        case 0:
            if (!require(window->visibility() == restoredVisibility,
                         "initial window state not applied")) return;
            if (!require(QMetaObject::invokeMethod(window, "toggleFullscreen"),
                         "fullscreen toggle unavailable")) return;
            break;
        case 1: {
            auto *shortcut = window->findChild<QObject *>(u"shellFullscreenShortcut"_s);
            if (!require(!shortcut || !shortcut->property("enabled").toBool(),
                         "shell shortcut competed with stream input")) return;
            if (!require(window->visibility() == QWindow::FullScreen,
                         "stream did not enter fullscreen")) return;
            if (!require(QMetaObject::invokeMethod(store, "requestStreamExitConfirmation"),
                         "exit confirmation unavailable")) return;
            break;
        }
        case 2:
            if (!require(m_controller.overlay() == u"desktop-stream-exit-confirm"_s
                    && window->visibility() == QWindow::FullScreen,
                    "exit confirmation changed fullscreen state")) return;
            keyClick(Qt::Key_Return);
            break;
        case 3:
            if (!require(m_controller.route() != u"stream"_s
                    && store->property("streamState").toString() == u"idle"_s
                    && window->visibility() == QWindow::FullScreen,
                    "session exit did not preserve the fullscreen shell")) return;
            keyClick(Qt::Key_F11);
            break;
        case 4:
            if (!require(window->visibility() == restoredVisibility,
                         "F11 did not restore the shell window after session exit")) return;
            keyClick(Qt::Key_F11);
            break;
        case 5:
            if (!require(window->visibility() == QWindow::FullScreen,
                         "F11 did not re-enter fullscreen from the shell")) return;
            window->contentItem()->forceActiveFocus();
            keyClick(Qt::Key_F11);
            break;
        case 6:
            if (!require(window->visibility() == restoredVisibility,
                         "F11 depended on the shell page focus")) return;
            timer->stop();
            m_application.exit(EXIT_SUCCESS);
            break;
        }
    });
    timer->start();
    return EXIT_SUCCESS;
}
