#include "AppController.h"
#include "AcceptanceProfiler.h"
#include "ControllerInput.h"
#include "CoreClient.h"
#include "InputModeTracker.h"
#include "Localization.h"
#include "SingleInstance.h"
#include "ThumbnailGenerator.h"

#include <QGuiApplication>
#include <QElapsedTimer>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQuickStyle>
#include <QTimer>
#include <QDir>
#include <QFileInfo>
#include <QQmlError>
#include <QQuickItem>
#include <QQuickWindow>
#include <QFileOpenEvent>

#include <cstdio>
#include <memory>

using namespace Qt::StringLiterals;

namespace {
class FileOpenFilter final : public QObject
{
public:
    explicit FileOpenFilter(AppController *controller)
        : m_controller(controller)
    {
    }

protected:
    bool eventFilter(QObject *watched, QEvent *event) override
    {
        if (event->type() != QEvent::FileOpen) {
            return QObject::eventFilter(watched, event);
        }
        const auto *openEvent = static_cast<QFileOpenEvent *>(event);
        if (!openEvent->url().isValid()) return false;
        const auto handled = m_controller->handleArguments(
            {QCoreApplication::applicationFilePath(), openEvent->url().toString()});
        if (handled) m_controller->activateWindow();
        return handled;
    }

private:
    AppController *m_controller;
};
}

int main(int argc, char *argv[])
{
    QElapsedTimer startupTimer;
    startupTimer.start();
    QGuiApplication::setApplicationName(u"OpenNOW"_s);
    QGuiApplication::setOrganizationName(u"OpenCloudGaming"_s);
    QGuiApplication::setOrganizationDomain(u"opennow.app"_s);
    QGuiApplication::setApplicationVersion(QString::fromLatin1(OPENNOW_VERSION));
    QQuickWindow::setDefaultAlphaBuffer(true);
    QQuickStyle::setStyle(u"Basic"_s);

    QGuiApplication application(argc, argv);
    qSetMessagePattern(u"%{time yyyy-MM-ddTHH:mm:ss.zzz} %{type} %{category}: %{message}"_s);
    const auto arguments = application.arguments();
    SingleInstance singleInstance;
    if (!arguments.contains(u"--allow-multiple-instances"_s)
            && !singleInstance.acquire(arguments)) {
        return EXIT_SUCCESS;
    }
    AppController controller;
    if (!controller.ensureDirectLaunchAssociation())
        qWarning("Could not register the opennow:// direct-launch association");
    FileOpenFilter fileOpenFilter(&controller);
    application.installEventFilter(&fileOpenFilter);
    ControllerInput controllerInput;
    ThumbnailGenerator thumbnailGenerator;
    Localization localization;
    application.installTranslator(&localization);
    CoreClient coreClient;
    InputModeTracker inputModeTracker(&controller);
    application.installEventFilter(&inputModeTracker);
    controller.setControllerCount(controllerInput.controllerCount());
    QObject::connect(&controllerInput, &ControllerInput::controllerCountChanged,
                     &controller, &AppController::setControllerCount);
    QObject::connect(&controllerInput, &ControllerInput::controllerActivity,
                     &controller, [&controller] { controller.setInputMode(u"controller"_s); });

    const auto routeIndex = arguments.indexOf(u"--route"_s);
    if (routeIndex >= 0 && routeIndex + 1 < arguments.size()) {
        controller.navigate(arguments.at(routeIndex + 1));
    }
    const auto overlayIndex = arguments.indexOf(u"--overlay"_s);
    if (overlayIndex >= 0 && overlayIndex + 1 < arguments.size()) {
        controller.showOverlay(arguments.at(overlayIndex + 1));
    }
    if (arguments.contains(u"--reduced-motion"_s)) {
        controller.setReducedMotion(true);
    }

    const auto performanceReportIndex = arguments.indexOf(u"--performance-report"_s);
    const auto performanceMode = performanceReportIndex >= 0
        && performanceReportIndex + 1 < arguments.size();

    QQmlApplicationEngine engine;
    bool qmlWarningOccurred = false;
    QObject::connect(&engine, &QQmlApplicationEngine::warnings, &application,
                     [&qmlWarningOccurred](const QList<QQmlError> &warnings) {
                         qmlWarningOccurred = true;
                         for (const auto &warning : warnings) {
                             const auto message = warning.toString().toUtf8();
                             std::fprintf(stderr, "%s\n", message.constData());
                         }
                     });
    engine.rootContext()->setContextProperty(u"AppController"_s, &controller);
    engine.rootContext()->setContextProperty(u"ControllerInput"_s, &controllerInput);
    engine.rootContext()->setContextProperty(u"ThumbnailGenerator"_s, &thumbnailGenerator);
    engine.rootContext()->setContextProperty(u"I18n"_s, &localization);
    engine.rootContext()->setContextProperty(u"CoreClient"_s, &coreClient);
    engine.rootContext()->setContextProperty(
        u"LaunchModeOverride"_s,
        arguments.contains(u"--desktop"_s) ? u"desktop"_s
            : arguments.contains(u"--console"_s) ? u"console"_s : QString{});
    QObject::connect(&localization, &Localization::localeChanged,
                     &engine, &QQmlApplicationEngine::retranslate);
    QObject::connect(&engine, &QQmlApplicationEngine::objectCreationFailed,
                     &application, [] { QCoreApplication::exit(EXIT_FAILURE); },
                     Qt::QueuedConnection);
    engine.loadFromModule(u"OpenNOW"_s, u"Main"_s);
    const auto qmlReadyMs = startupTimer.elapsed();
    controller.handleArguments(arguments);
    QObject::connect(&controller, &AppController::activationRequested,
                     &application, [&engine] {
                         if (engine.rootObjects().isEmpty()) return;
                         auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().first());
                         if (!window) return;
                         window->show();
                         window->raise();
                         window->requestActivate();
                     });
    QObject::connect(&singleInstance, &SingleInstance::activationRequested,
                     &application, [&controller](const QStringList &forwardedArguments) {
                         controller.activationRequested();
                         controller.handleArguments(forwardedArguments);
                     });

    if (arguments.contains(u"--measure-startup"_s)) {
        auto *window = engine.rootObjects().isEmpty()
            ? nullptr
            : qobject_cast<QQuickWindow *>(engine.rootObjects().first());
        if (!window) {
            return EXIT_FAILURE;
        }
        const auto reported = std::make_shared<bool>(false);
        QObject::connect(window, &QQuickWindow::frameSwapped, &application,
                         [&application, &startupTimer, qmlReadyMs, reported] {
                             if (*reported) return;
                             *reported = true;
                             std::fprintf(stdout,
                                          "{\"qmlReadyMs\":%lld,\"firstFrameMs\":%lld}\n",
                                          static_cast<long long>(qmlReadyMs),
                                          static_cast<long long>(startupTimer.elapsed()));
                             std::fflush(stdout);
                             application.exit(EXIT_SUCCESS);
                         });
        QTimer::singleShot(5'000, &application, [&application, reported] {
            if (*reported) return;
            *reported = true;
            std::fprintf(stderr, "startup measurement timed out before the first frame\n");
            std::fflush(stderr);
            application.exit(EXIT_FAILURE);
        });
    }

    const auto smokeTest = arguments.contains(u"--smoke-test"_s);
    const auto coreIndex = arguments.indexOf(u"--core"_s);
    if (!smokeTest && !performanceMode && coreIndex >= 0 && coreIndex + 1 < arguments.size()) {
        coreClient.start(arguments.at(coreIndex + 1));
    } else if (!smokeTest && !performanceMode) {
        const auto bundledCore = QDir(QCoreApplication::applicationDirPath()).filePath(
#ifdef Q_OS_WIN
            u"opennow-core.exe"_s
#else
            u"opennow-core"_s
#endif
        );
        if (QFileInfo::exists(bundledCore)) {
            coreClient.start(bundledCore);
        }
    }

    if (performanceMode) {
        if (controller.reducedMotion()) {
            qCritical("Performance acceptance requires production motion to be enabled");
            return EXIT_FAILURE;
        }
        const auto integerOption = [&arguments](const QString &name, int fallback) {
            const auto index = arguments.indexOf(name);
            if (index < 0 || index + 1 >= arguments.size()) return fallback;
            bool valid = false;
            const auto value = arguments.at(index + 1).toInt(&valid);
            return valid ? value : fallback;
        };
        const auto realOption = [&arguments](const QString &name, double fallback) {
            const auto index = arguments.indexOf(name);
            if (index < 0 || index + 1 >= arguments.size()) return fallback;
            bool valid = false;
            const auto value = arguments.at(index + 1).toDouble(&valid);
            return valid ? value : fallback;
        };
        auto *window = engine.rootObjects().isEmpty()
            ? nullptr
            : qobject_cast<QQuickWindow *>(engine.rootObjects().first());
        auto options = AcceptanceProfiler::Options{
            .reportPath = arguments.at(performanceReportIndex + 1),
            .width = integerOption(u"--performance-width"_s, 1920),
            .height = integerOption(u"--performance-height"_s, 1080),
            .cycles = integerOption(u"--performance-cycles"_s, 3),
            .refreshRateHz = realOption(u"--performance-refresh-hz"_s, 0.0),
            .machineLabel = [&arguments] {
                const auto index = arguments.indexOf(u"--performance-label"_s);
                return index >= 0 && index + 1 < arguments.size()
                    ? arguments.at(index + 1) : QString{};
            }(),
            .requireHardware = arguments.contains(u"--performance-require-hardware"_s),
        };
        auto *profiler = new AcceptanceProfiler(
            window, &controller, std::move(options),
            [&application, &qmlWarningOccurred](bool passed) {
                application.exit(passed && !qmlWarningOccurred ? EXIT_SUCCESS : EXIT_FAILURE);
            }, &application);
        if (!profiler->start()) {
            qCritical("Could not start the Qt performance acceptance workload");
            return EXIT_FAILURE;
        }
    } else {
        const auto screenshotIndex = arguments.indexOf(u"--screenshot"_s);
        if (screenshotIndex >= 0 && screenshotIndex + 1 < arguments.size()) {
        const auto screenshotPath = arguments.at(screenshotIndex + 1);
        QTimer::singleShot(1'000, &application,
                           [&application, &engine, &qmlWarningOccurred, screenshotPath] {
            if (engine.rootObjects().isEmpty() || qmlWarningOccurred) {
                application.exit(EXIT_FAILURE);
                return;
            }
            auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().first());
            const auto saved = window && window->grabWindow().save(screenshotPath);
            application.exit(saved ? EXIT_SUCCESS : EXIT_FAILURE);
        });
        } else if (smokeTest) {
        QTimer::singleShot(500, &application, [&application, &engine, &qmlWarningOccurred] {
            auto *window = engine.rootObjects().isEmpty()
                ? nullptr
                : qobject_cast<QQuickWindow *>(engine.rootObjects().first());
            const auto *focused = window ? window->activeFocusItem() : nullptr;
            const auto failed = !window || qmlWarningOccurred || !focused
                || !focused->isVisible() || !focused->isEnabled();
            if (failed) {
                if (!window) {
                    qCritical("QML smoke test did not create a window");
                } else if (!focused) {
                    qCritical("QML smoke test lost active focus");
                } else {
                    qCritical("QML smoke test focus target is not usable: %s (visible=%d, enabled=%d)",
                              focused->metaObject()->className(), focused->isVisible(),
                              focused->isEnabled());
                }
            }
            application.exit(failed ? EXIT_FAILURE : EXIT_SUCCESS);
        });
        }
    }

    return application.exec();
}
