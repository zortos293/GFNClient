#include "MotionAcceptance.h"
#include "AppController.h"

#include <QCoreApplication>
#include <QKeyEvent>
#include <QPointer>
#include <QQuickItem>
#include <QQuickWindow>
#include <QTimer>
#include <QVariant>
#include <cmath>
#include <memory>

namespace {
QQuickItem *findItem(QQuickItem *item, const QString &name)
{
    if (!item) return nullptr;
    if (item->objectName() == name) return item;
    for (auto *child : item->childItems())
        if (auto *match = findItem(child, name)) return match;
    return nullptr;
}
struct Run {
    int tick = 0;
    bool sawIntermediate = false;
    double previous = 0;
    QPointer<QQuickItem> modal;
    QPointer<QQuickItem> motion;
    QPointer<QObject> page;
    QPointer<QQuickItem> surface;
};
}

void startStoreNavigationAcceptance(QQuickWindow *window, const bool *qmlWarningOccurred)
{
    auto *page = findItem(window->contentItem(), QStringLiteral("desktopStoreContent"));
    auto *viewport = findItem(window->contentItem(), QStringLiteral("storeViewport"));
    auto *upper = findItem(window->contentItem(), QStringLiteral("storeShelf-new"));
    auto *motion = page ? page->findChild<QObject *>(QStringLiteral("storeScrollAnimation")) : nullptr;
    if (!page || !viewport || !upper || !motion) {
        qCritical("Store navigation fixture is incomplete");
        QCoreApplication::exit(1);
        return;
    }
    struct State { int tick = 0; double previous = 0; double hoverY = 0; };
    const auto state = std::make_shared<State>();
    auto *timer = new QTimer(window);
    timer->setInterval(40);
    QObject::connect(timer, &QTimer::timeout, window,
        [window, page, viewport, upper, motion, qmlWarningOccurred, timer, state] {
        const auto require = [timer, state](bool ok, const char *message) {
            if (!ok) {
                qCritical("Store navigation tick %d: %s", state->tick, message);
                timer->stop();
                QCoreApplication::exit(1);
            }
            return ok;
        };
        const auto select = [page](const char *zone) {
            return QMetaObject::invokeMethod(page, "selectZone",
                Q_ARG(QVariant, QVariant(QString::fromLatin1(zone))), Q_ARG(QVariant, QVariant(0)));
        };
        const auto key = [window](int code) {
            QKeyEvent press(QEvent::KeyPress, code, Qt::NoModifier);
            QCoreApplication::sendEvent(window, &press);
            QKeyEvent release(QEvent::KeyRelease, code, Qt::NoModifier);
            QCoreApplication::sendEvent(window, &release);
        };
        const auto y = [viewport] { return viewport->property("contentY").toDouble(); };
        const auto zone = [page] { return page->property("focusZone").toString(); };
        const int tick = ++state->tick;
        if (!require(!*qmlWarningOccurred, "QML warning")) return;
        if (tick == 1 && !require(select("popular"), "row navigation unavailable")) return;
        if (tick >= 2 && tick <= 8) {
            // Reproduce enter events caused by rows moving under a stationary
            // pointer, using the actual shelf signal wired by the production UI.
            if (motion->property("running").toBool())
                QMetaObject::invokeMethod(upper, "gamePointed", Q_ARG(int, 0));
            if (!require(zone() == QStringLiteral("popular"), "hover stole animated navigation")
                || !require(y() + 0.5 >= state->previous, "downward scroll reversed")) return;
            state->previous = y();
        }
        if (tick == 10) {
            if (!require(y() > 0 && !motion->property("running").toBool(), "row never settled")) return;
            state->hoverY = y();
            QMetaObject::invokeMethod(upper, "gamePointed", Q_ARG(int, 0));
            if (!require(zone() == QStringLiteral("new"), "hover selection no longer works")) return;
        }
        if (tick >= 11 && tick <= 18)
            if (!require(std::abs(y() - state->hoverY) < 0.5, "hover started scrolling")) return;
        // Real key delivery: reverse direction before the prior move finishes.
        if (tick == 20) key(Qt::Key_Up);
        if (tick == 21 || tick == 22) key(Qt::Key_Down);
        if (tick == 32) {
            if (!require(zone() == QStringLiteral("popular") && !motion->property("running").toBool(),
                         "rapid up/down did not settle on the requested row")) return;
            auto *row = findItem(window->contentItem(), QStringLiteral("storeShelf-popular"));
            const auto top = row ? row->mapToItem(viewport, QPointF{}).y() : -9999;
            if (!require(row && top >= -0.5 && top + row->height() <= viewport->height() + 0.5,
                         "selected row is outside the viewport")) return;
        }
        if (tick == 33) { key(Qt::Key_Up); key(Qt::Key_Up); key(Qt::Key_Up); }
        if (tick == 43 && !require(zone() == QStringLiteral("hero") && std::abs(y()) < 0.5,
                                  "up navigation did not return to the hero")) return;
        if (tick == 44) select("popular");
        if (tick == 45) {
            // Direct scrolling must take ownership from the focus animation.
            QMetaObject::invokeMethod(viewport, "movementStarted");
            viewport->setProperty("contentY", 87.0);
        }
        if (tick == 55) {
            if (!require(std::abs(y() - 87) < 0.5 && !motion->property("running").toBool(),
                         "animation fought manual scrolling")) return;
            timer->stop();
            QCoreApplication::exit(0);
        }
    });
    timer->start();
}

void startMotionAcceptance(QQuickWindow *window, AppController *controller,
                           const bool *qmlWarningOccurred, bool fullscreen)
{
    if (!window) { QCoreApplication::exit(1); return; }
    if (fullscreen) window->showFullScreen();
    const auto run = std::make_shared<Run>();
    auto *timer = new QTimer(window);
    timer->setInterval(30);
    QObject::connect(timer, &QTimer::timeout, window,
        [window, controller, qmlWarningOccurred, fullscreen, run, timer] {
        const auto find = [window](const char *name) {
            return findItem(window->contentItem(), QString::fromLatin1(name));
        };
        const auto require = [timer, run](bool condition, const char *message) {
            if (!condition) {
                qCritical("Motion acceptance tick %d: %s", run->tick, message);
                timer->stop();
                QCoreApplication::exit(1);
            }
            return condition;
        };
        ++run->tick;
        if (!require(!*qmlWarningOccurred, "QML warning during motion")) return;
        auto *outer = find("mainRouteLoader");
        if (!require(outer && outer->opacity() == 1, "navigation faded the entire shell/video")) return;
        if (!require(!fullscreen || window->visibility() == QWindow::FullScreen,
                     "overlay changed fullscreen state")) return;

        const int tick = run->tick;
        if (tick == 1) controller->navigate(QStringLiteral("home"));
        if (tick == 4) {
            run->modal = find("desktopGameModal");
            run->motion = find("gameDetailsMotion");
            auto *pageLoader = find("desktopPageLoader");
            if (!require(run->modal && run->motion && pageLoader, "missing retained modal/page")) return;
            run->page = pageLoader->property("item").value<QObject *>();
            controller->navigate(QStringLiteral("game-detail"));
        }
        if (tick >= 5 && tick <= 36) {
            auto *pageLoader = find("desktopPageLoader");
            if (!require(run->modal && run->motion && run->page && pageLoader
                    && pageLoader->property("item").value<QObject *>() == run->page,
                    "opening/closing details recreated the underlying page")) return;
            const double progress = run->motion->property("progress").toDouble();
            if (!require(std::isfinite(progress) && progress >= 0 && progress <= 1,
                         "reveal overshot its bounds")) return;
            run->sawIntermediate |= progress > 0 && progress < 1;
            if (!require((progress == 0 || run->modal->isVisible()), "popup hidden mid-transition")) return;
            if ((tick >= 6 && tick <= 7) || (tick >= 11 && tick <= 18))
                if (!require(progress + 0.001 >= run->previous, "opening jumped backwards")) return;
            if (tick == 9 || (tick >= 21 && tick <= 27))
                if (!require(progress <= run->previous + 0.001, "closing jumped forwards")) return;
            if (tick == 18 || tick == 36)
                if (!require(progress == 1, "reopened popup never settled")) return;
            if (tick == 27)
                if (!require(progress == 0 && !run->modal->isVisible(), "closed popup retained visibility")) return;
            run->previous = progress;
        }
        if (tick == 8 || tick == 19 || tick == 37) controller->goBack();
        if (tick == 10 || tick == 28) controller->navigate(QStringLiteral("game-detail"));
        if (tick == 38) {
            controller->navigate(QStringLiteral("stream"));
            run->surface = find("streamSurfaceHost");
        }
        if (tick >= 39) {
            auto *fallback = find("fallbackOverlayHost");
            if (!require(fallback && fallback->opacity() == 0,
                         "native overlay left a second dimming layer behind")) return;
            if (!require(run->surface && find("streamSurfaceHost") == run->surface,
                         "stream presenter was recreated by an overlay")) return;
            if (tick == 39 || tick == 43) controller->showOverlay(QStringLiteral("desktop-stream-menu"));
            if (tick == 41) controller->showOverlay(QString{});
            auto *motion = find("streamMenuMotion");
            if (!require(motion != nullptr, "missing stream menu motion")) return;
            if (tick == 53) {
                if (!require(motion->property("progress").toDouble() == 1, "stream menu failed to reopen")) return;
                // Invoke the real resume action, including its close completion.
                if (!require(QMetaObject::invokeMethod(motion->parent(), "runAction", Q_ARG(QVariant, QVariant(0))),
                             "resume action unavailable")) return;
            }
            if (tick == 62) {
                if (!require(controller->overlay().isEmpty() && motion->property("progress").toDouble() == 0,
                             "resume did not finish exactly after close")) return;
                controller->showOverlay(QStringLiteral("desktop-stream-exit-confirm"));
            }
            if (tick == 64) controller->showOverlay(QString{});
        }
        if (tick == 72) {
            if (!require(controller->reducedMotion() || run->sawIntermediate,
                         "normal mode snapped instead of animating")) return;
            if (!require(!controller->reducedMotion() || !run->sawIntermediate,
                         "reduced motion still animated")) return;
            timer->stop();
            QCoreApplication::exit(0);
        }
    });
    timer->start();
}
