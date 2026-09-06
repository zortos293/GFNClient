#include "AppController.h"
#include "AcceptanceProfiler.h"
#include "ControllerInput.h"
#include "CoreClient.h"
#include "InputModeTracker.h"
#include "Localization.h"
#include "LinuxVulkanGraphics.h"
#include "MotionAcceptance.h"
#ifdef OPENNOW_EMBEDDED_STREAMER
#include "NativeStreamRuntime.h"
#endif
#include "SingleInstance.h"
#include "StreamVideoItem.h"
#include "ThumbnailGenerator.h"

#include <QGuiApplication>
#include <QElapsedTimer>
#include <QFont>
#include <QFontDatabase>
#include <QQmlApplicationEngine>
#include <QQmlComponent>
#include <QQmlContext>
#include <QQuickStyle>
#include <QTimer>
#include <QDir>
#include <QFileInfo>
#include <QVariant>
#include <QVariantMap>
#include <QJSValue>
#include <QQmlError>
#include <QQuickItem>
#include <QQuickWindow>
#include <QSGRendererInterface>
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
    qputenv("QT_TLS_BACKEND", "schannel");
    QElapsedTimer startupTimer;
    startupTimer.start();
    QGuiApplication::setApplicationName(u"OpenNOW"_s);
    QGuiApplication::setOrganizationName(u"OpenCloudGaming"_s);
    QGuiApplication::setOrganizationDomain(u"opennow.app"_s);
    QGuiApplication::setApplicationVersion(QString::fromLatin1(OPENNOW_VERSION));
    QQuickWindow::setDefaultAlphaBuffer(true);
    QQuickWindow::setTextRenderType(QQuickWindow::QtTextRendering);
#if defined(Q_OS_WIN)
    QQuickWindow::setGraphicsApi(QSGRendererInterface::Direct3D11);
#elif defined(Q_OS_MACOS)
    QQuickWindow::setGraphicsApi(QSGRendererInterface::Metal);
#elif defined(Q_OS_LINUX)
    QQuickWindow::setGraphicsApi(QSGRendererInterface::Vulkan);
#endif
    QQuickStyle::setStyle(u"Basic"_s);

    QGuiApplication application(argc, argv);
#if defined(Q_OS_LINUX) && QT_CONFIG(vulkan) && __has_include(<vulkan/vulkan.h>)
    LinuxVulkanGraphics::requestDeviceExtensions();
#endif
    registerStreamVideoItemQmlType();
    qSetMessagePattern(u"%{time yyyy-MM-ddTHH:mm:ss.zzz} %{type} %{category}: %{message}"_s);
    const QStringList bundledFonts = {
        u":/qt/qml/OpenNOW/res/fonts/Nunito-Variable.ttf"_s,
        u":/qt/qml/OpenNOW/res/fonts/IBMPlexMono-Regular.ttf"_s,
        u":/qt/qml/OpenNOW/res/fonts/IBMPlexMono-Medium.ttf"_s,
        u":/qt/qml/OpenNOW/res/fonts/IBMPlexMono-Bold.ttf"_s,
    };
    for (const auto &fontPath : bundledFonts) {
        if (QFontDatabase::addApplicationFont(fontPath) == -1)
            qWarning("Could not load bundled font %s", qUtf8Printable(fontPath));
    }
    QFont applicationFont(QStringLiteral("Nunito"));
    applicationFont.setHintingPreference(QFont::PreferNoHinting);
    applicationFont.setStyleStrategy(QFont::PreferAntialias);
    application.setFont(applicationFont);
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
#ifdef OPENNOW_EMBEDDED_STREAMER
    NativeStreamRuntime nativeStreamRuntime;
    if (!nativeStreamRuntime.start())
        qWarning("Could not start the embedded streamer runtime: %s",
                 qUtf8Printable(nativeStreamRuntime.lastError()));
    StreamVideoItem::setNativeStreamRuntime(&nativeStreamRuntime);
    QObject::connect(
        &controllerInput, &ControllerInput::gamepadSnapshot, &nativeStreamRuntime,
        [&nativeStreamRuntime](quint8 controllerId, quint16 bitmap, quint16 buttons,
                               quint8 leftTrigger, quint8 rightTrigger,
                               qint16 leftStickX, qint16 leftStickY,
                               qint16 rightStickX, qint16 rightStickY) {
            nativeStreamRuntime.submitGamepad(
                controllerId, bitmap, buttons, leftTrigger, rightTrigger,
                leftStickX, leftStickY, rightStickX, rightStickY);
        });
    QObject::connect(&controllerInput, &ControllerInput::localActionRequested,
                     &nativeStreamRuntime, [&nativeStreamRuntime](quint32 action) {
                         nativeStreamRuntime.submitLocalAction(action);
                     });
#endif
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
    const auto smokeTest = arguments.contains(u"--smoke-test"_s);
    const auto smokeConsolePersistenceRollback = smokeTest
        && arguments.contains(u"--smoke-console-persistence-rollback"_s);
    const auto smokeStreamerEvent = smokeTest
        && arguments.contains(u"--smoke-streamer-event"_s);
    const auto smokeFullscreenStatsShortcut = smokeTest
        && arguments.contains(u"--smoke-fullscreen-stats-shortcut"_s);

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
#ifdef OPENNOW_EMBEDDED_STREAMER
    engine.rootContext()->setContextProperty(u"NativeStreamRuntime"_s,
                                             &nativeStreamRuntime);
#endif
    engine.rootContext()->setContextProperty(
        u"LaunchModeOverride"_s,
        arguments.contains(u"--desktop"_s) ? u"desktop"_s
            : arguments.contains(u"--console"_s) ? u"console"_s : QString{});
    engine.rootContext()->setContextProperty(u"SmokeTestMode"_s, smokeTest);
    engine.rootContext()->setContextProperty(
        u"SmokeTestGame"_s,
        smokeTest ? QVariantMap{
            {u"title"_s, u"Smoke Test Game"_s},
            {u"isAvailable"_s, true},
            {u"isInLibrary"_s, true},
            {u"publisherName"_s, u"OpenNOW Test Fixture"_s},
            {u"genres"_s, QStringList{u"Fixture"_s}},
            {u"selectedVariantIndex"_s, 0},
            {u"variants"_s, QVariantList{
                QVariantMap{{u"id"_s, u"1001"_s}, {u"store"_s, u"Steam"_s}, {u"inLibrary"_s, true}},
                QVariantMap{{u"id"_s, u"1002"_s}, {u"store"_s, u"Epic Games Store"_s}, {u"inLibrary"_s, false}},
                QVariantMap{{u"id"_s, u"1003"_s}, {u"store"_s, u"Xbox"_s}, {u"inLibrary"_s, true}},
            }},
            {u"availableStores"_s, QStringList{u"Steam"_s, u"Epic Games Store"_s, u"Xbox"_s}},
        } : QVariantMap{});
    QObject::connect(&localization, &Localization::localeChanged,
                     &engine, &QQmlApplicationEngine::retranslate);
    QObject::connect(&engine, &QQmlApplicationEngine::objectCreationFailed,
                     &application, [] { QCoreApplication::exit(EXIT_FAILURE); },
                     Qt::QueuedConnection);
    engine.loadFromModule(u"OpenNOW"_s, u"Main"_s);
    // Isolated visual acceptance options never start or alter a real account.
    if (smokeTest && !engine.rootObjects().isEmpty()) {
        auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().first());
        const auto dimension = [&arguments](const QString &option, int fallback) {
            const auto index = arguments.indexOf(option);
            if (index < 0 || index + 1 >= arguments.size()) return fallback;
            bool ok = false;
            const int value = arguments.at(index + 1).toInt(&ok);
            return ok ? qBound(540, value, 3840) : fallback;
        };
        if (window) window->resize(dimension(u"--smoke-width"_s, 1600),
                                   dimension(u"--smoke-height"_s, 900));
        if (arguments.contains(u"--smoke-paper-design"_s)) {
            auto *store = engine.singletonInstance<QObject *>(u"OpenNOW"_s, u"ShellStore"_s);
            if (store) {
                store->setProperty("settings", QVariantMap{
                    {u"themePack"_s, u"aurora"_s}, {u"appTheme"_s, u"dark"_s},
                    {u"desktopSidebarHover"_s, false}, {u"desktopRailCollapsed"_s, true},
                    {u"resolution"_s, u"2560x1440"_s}, {u"fps"_s, 120},
                    {u"codec"_s, u"av1"_s}, {u"colorQuality"_s, u"10bit_420"_s},
                    {u"maxBitrateMbps"_s, 75}, {u"enableCloudGsync"_s, true}});
                // Visual fixtures stay in the smoke-only QML store. The core is
                // not started, so no account state or preferences are persisted.
                QVariantList accounts;
                const QStringList providers{u"Steam"_s, u"Epic Games"_s, u"Xbox"_s,
                    u"Ubisoft"_s, u"Battle.net"_s, u"GOG"_s, u"Gaijin"_s};
                for (qsizetype i = 0; i < providers.size(); ++i) {
                    accounts.append(QVariantMap{
                        {u"provider"_s, providers.at(i)}, {u"label"_s, providers.at(i)},
                        {u"isConnected"_s, i < 3}, {u"supportsSync"_s, i < 3},
                        {u"syncedGames"_s, 42},
                        {u"status"_s, i < 3 ? u"connected"_s : i == 3 ? u"expired"_s : u"disconnected"_s}});
                }
                store->setProperty("gameAccounts", accounts);
                store->setProperty("gameAccountsState", u"ready"_s);
                store->setProperty("regions", QVariantList{
                    QVariantMap{{u"name"_s,u"EU West"_s},{u"url"_s,u"https://west.example.invalid"_s}},
                    QVariantMap{{u"name"_s,u"EU Central"_s},{u"url"_s,u"https://central.example.invalid"_s}},
                    QVariantMap{{u"name"_s,u"US East"_s},{u"url"_s,u"https://east.example.invalid"_s}}});
                store->setProperty("regionPingResults", QVariantMap{
                    {u"https://west.example.invalid"_s,9},{u"https://central.example.invalid"_s,21},
                    {u"https://east.example.invalid"_s,94}});
            }
        }
        if (arguments.contains(u"--smoke-resolution-open"_s)) {
            auto *picker = window ? window->findChild<QObject *>(u"renewResolutionPicker"_s) : nullptr;
            if (!picker) return EXIT_FAILURE;
            picker->setProperty("expanded", true);
        }
        if (arguments.contains(u"--smoke-light-theme"_s)) {
            auto *store = engine.singletonInstance<QObject *>(u"OpenNOW"_s, u"ShellStore"_s);
            if (store) store->setProperty("settings", QVariantMap{{u"appTheme"_s, u"light"_s}});
        }
        if (arguments.contains(u"--smoke-settings-advanced"_s)) {
            auto *settings = window ? window->findChild<QObject *>(u"desktopSettingsScreen"_s) : nullptr;
            if (settings) settings->setProperty("advancedOpen", true);
        }
        const auto panelIndex = arguments.indexOf(u"--smoke-settings-panel"_s);
        if (panelIndex >= 0 && panelIndex + 1 < arguments.size()) {
            const auto panel = arguments.at(panelIndex + 1);
            const QStringList panels{u"stats"_s,u"audio"_s,u"interface"_s,u"console"_s,
                u"shortcuts"_s,u"controllers"_s,u"subscription"_s};
            auto *settings = window ? window->findChild<QObject *>(u"desktopSettingsScreen"_s) : nullptr;
            if (!settings || !panels.contains(panel)) return EXIT_FAILURE;
            settings->setProperty("acceptancePanel", panel);
        }
        if (arguments.contains(u"--smoke-choice-open"_s)) {
            auto *picker = window ? window->findChild<QObject *>(u"renewNetworkRegion"_s) : nullptr;
            if (!picker && window) picker = window->findChild<QObject *>(u"renewLanguageChoice"_s);
            if (!picker && window) picker = window->findChild<QObject *>(u"streamBackendChoice"_s);
            if (!picker) return EXIT_FAILURE;
            picker->setProperty("expanded", true);
        }
        if (arguments.contains(u"--smoke-renew-settings-actions"_s)) {
            // Repeater delegates may be incubated after the settings Loader.
            // Exercise controls once the first layout has had time to complete.
            QTimer::singleShot(150, &application, [&application, &engine, window] {
            const auto checkActions = [&engine, window]() -> int {
            auto *store = engine.singletonInstance<QObject *>(u"OpenNOW"_s, u"ShellStore"_s);
            if (!window || !store) return EXIT_FAILURE;
            const auto findVisual = [](auto &&self, QQuickItem *item, const QString &name) -> QObject * {
                if (!item) return nullptr;
                if (item->objectName() == name) return item;
                for (auto *child : item->childItems())
                    if (auto *match = self(self, child, name)) return match;
                return nullptr;
            };
            const auto findControl = [window, &findVisual](const QString &name) -> QObject * {
                if (auto *object = window->findChild<QObject *>(name)) return object;
                return findVisual(findVisual, window->contentItem(), name);
            };
            const auto setting = [store](const QString &key) {
                const auto settings = store->property("settings");
                return settings.canConvert<QJSValue>()
                    ? settings.value<QJSValue>().property(key).toVariant()
                    : settings.toMap().value(key);
            };
            bool exercised = false;
            if (auto *theme = findControl(u"renewThemeChoice"_s)) {
                exercised = true;
                const auto items = theme->property("items").value<QJSValue>();
                if (items.property(u"length"_s).toInt() != 8) return EXIT_FAILURE;
                theme->setProperty("expanded", true);
                if (!QMetaObject::invokeMethod(theme,"selected",Q_ARG(QVariant,QVariant(u"bone"_s)))
                    || setting(u"themePack"_s).toString() != u"bone"_s) return EXIT_FAILURE;
            }
            if (auto *shortcuts = findControl(u"renewShortcutsDisclosure"_s)) {
                exercised = true;
                auto *settings = findControl(u"desktopSettingsScreen"_s);
                if (!QMetaObject::invokeMethod(shortcuts,"expansionRequested")) return EXIT_FAILURE;
                auto *inlinePanel = findControl(u"renewInlineShortcuts"_s);
                if (!settings || settings->property("advancedOpen").toBool()
                    || !inlinePanel || !inlinePanel->property("expanded").toBool()) return EXIT_FAILURE;
            }
            if (auto *region = window->findChild<QObject *>(u"renewNetworkRegion"_s)) {
                exercised = true;
                if (!QMetaObject::invokeMethod(region, "selected", Q_ARG(QVariant, QVariant(u"https://central.example.invalid"_s)))
                    || setting(u"region"_s).toString() != u"https://central.example.invalid"_s) return EXIT_FAILURE;
                auto *field = window->findChild<QObject *>(u"renewProxyAddress"_s);
                auto *toggle = window->findChild<QObject *>(u"renewProxyEnabled"_s);
                if (!field || !toggle) return EXIT_FAILURE;
                field->setProperty("text", u"http://proxy.example.invalid:8080"_s);
                if (!QMetaObject::invokeMethod(field,"editingFinished")
                    || !QMetaObject::invokeMethod(toggle,"valueChangedByUser",Q_ARG(bool,true))
                    || setting(u"sessionProxyUrl"_s).toString() != u"http://proxy.example.invalid:8080"_s
                    || !setting(u"sessionProxyEnabled"_s).toBool()) return EXIT_FAILURE;
            }
            if (auto *channel = window->findChild<QObject *>(u"renewUpdateChannel"_s)) {
                exercised = true;
                if (!QMetaObject::invokeMethod(channel,"selected",Q_ARG(int,1),
                        Q_ARG(QVariant,QVariant(QVariantMap{{u"label"_s,u"Nightly"_s},{u"value"_s,u"nightly"_s}})))
                    || setting(u"updateChannel"_s).toString() != u"nightly"_s) return EXIT_FAILURE;
            }
            if (auto *fps = findControl(u"renew-statsShowFps"_s)) {
                exercised = true;
                auto *region = findControl(u"renew-statsShowRegion"_s);
                if (!region || !QMetaObject::invokeMethod(fps,"valueChangedByUser",Q_ARG(bool,false))
                    || !QMetaObject::invokeMethod(region,"valueChangedByUser",Q_ARG(bool,false))
                    || !setting(u"statsShowFps"_s).isValid() || !setting(u"statsShowRegion"_s).isValid()
                    || setting(u"statsShowFps"_s).toBool() || setting(u"statsShowRegion"_s).toBool()) return EXIT_FAILURE;
            }
            if (!exercised) return EXIT_FAILURE;
            return EXIT_SUCCESS;
            };
            if (checkActions() != EXIT_SUCCESS) {
                std::fprintf(stderr, "Desktop Renew settings action acceptance failed\n");
                application.exit(EXIT_FAILURE);
            }
            });
        }
    }
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

    const auto coreIndex = arguments.indexOf(u"--core"_s);
    if ((!smokeTest || smokeConsolePersistenceRollback || smokeStreamerEvent) && !performanceMode
            && coreIndex >= 0 && coreIndex + 1 < arguments.size()) {
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
        if (smokeTest && arguments.contains(u"--smoke-input-capture-error"_s)) {
            auto *store = engine.singletonInstance<QObject *>(u"OpenNOW"_s, u"ShellStore"_s);
            if (!store) return EXIT_FAILURE;
            store->setProperty("streamer", QVariantMap{{u"status"_s, u"streaming"_s}});
            store->setProperty("streamState", u"streaming"_s);
            QTimer::singleShot(150, &application, [&] {
                auto *window = engine.rootObjects().isEmpty() ? nullptr
                    : qobject_cast<QQuickWindow *>(engine.rootObjects().first());
                auto *notice = window ? window->findChild<QQuickItem *>(u"streamInputCaptureNotice"_s) : nullptr;
                auto *surface = window ? window->findChild<QQuickItem *>(u"streamSurfaceHost"_s) : nullptr;
                if (!notice || !surface || !surface->isVisible()) {
                    qCritical("Input capture fixture could not find the visible stream surface and notice");
                    application.exit(EXIT_FAILURE);
                    return;
                }
                notice->setProperty("message", u"Relative mouse input requires Wayland relative-pointer-v1 and pointer-constraints-v1; the compositor does not provide both protocols."_s);
                QTimer::singleShot(250, &application, [&, window, notice, surface] {
                    bool passed = !qmlWarningOccurred && notice->isVisible() && surface->isVisible()
                        && window->findChild<QQuickItem *>(u"streamSurfaceHost"_s) == surface;
                    const auto shot = arguments.indexOf(u"--screenshot"_s);
                    if (shot >= 0 && shot + 1 < arguments.size())
                        passed = passed && QFileInfo(arguments.at(shot + 1)).isAbsolute()
                            && window->grabWindow().save(arguments.at(shot + 1));
                    if (!passed) qCritical("Input capture fixture did not preserve the visible stream surface");
                    application.exit(passed ? EXIT_SUCCESS : EXIT_FAILURE);
                });
            });
        } else if (smokeTest && (arguments.contains(u"--smoke-backend-availability"_s)
                         || arguments.contains(u"--smoke-idle-mode"_s)
                         || arguments.contains(u"--smoke-stream-recovery"_s))) {
            QQmlComponent component(&engine, QUrl(arguments.contains(u"--smoke-idle-mode"_s)
                ? u"qrc:/acceptance/IdleModeAcceptance.qml"_s
                : arguments.contains(u"--smoke-stream-recovery"_s)
                ? u"qrc:/acceptance/StreamRecoveryAcceptance.qml"_s
                : u"qrc:/acceptance/BackendAvailabilityAcceptance.qml"_s));
            auto *fixture = component.create();
            if (!fixture) { qCritical() << component.errors(); return EXIT_FAILURE; }
            fixture->setParent(&engine);
            if (arguments.contains(u"--smoke-stream-recovery"_s)) {
                auto *client = fixture->property("client").value<QObject *>();
                if (!client) return EXIT_FAILURE;
                engine.rootContext()->setContextProperty(u"CoreClient"_s, client);
            }
            QTimer::singleShot(150, &application, [&, fixture] {
                auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().first());
                QVariant passed;
                const bool ok = window && QMetaObject::invokeMethod(fixture, "run", Q_RETURN_ARG(QVariant, passed),
                    Q_ARG(QVariant, QVariant::fromValue(window->contentItem()))) && passed.toBool() && !qmlWarningOccurred;
                application.exit(ok ? EXIT_SUCCESS : EXIT_FAILURE);
            });
        } else if (smokeTest && (arguments.contains(u"--smoke-region-ping"_s) || arguments.contains(u"--smoke-store-paging"_s))) {
            const bool storePaging = arguments.contains(u"--smoke-store-paging"_s);
            QQmlComponent component(&engine, QUrl(storePaging ? u"qrc:/acceptance/StorePagingAcceptance.qml"_s
                : u"qrc:/acceptance/RegionPingAcceptance.qml"_s));
            auto *fixture = component.create();
            if (!fixture) { qCritical() << component.errors(); return EXIT_FAILURE; }
            fixture->setParent(&engine);
            auto *client = fixture->property("client").value<QObject *>();
            if (!client) return EXIT_FAILURE;
            engine.rootContext()->setContextProperty(u"CoreClient"_s, client);
            QTimer::singleShot(150, &application, [&, fixture, storePaging] {
                auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().first());
                auto *screen = window->findChild<QObject *>(storePaging ? u"desktopStoreContent"_s : u"desktopSettingsScreen"_s);
                auto *row = window->findChild<QObject *>(storePaging ? u"storePageStatusText"_s : u"renewRegionLatency"_s);
                auto *button = window->findChild<QObject *>(storePaging ? u"storePageRetry"_s : u"renewRegionPingButton"_s);
                auto *picker = window->findChild<QObject *>(storePaging ? u"storeEmptyStatusText"_s : u"renewNetworkRegion"_s);
                QVariant passed;
                if (!screen || !row || !button || !picker
                    || !QMetaObject::invokeMethod(fixture, "run", Q_RETURN_ARG(QVariant, passed),
                        Q_ARG(QVariant, QVariant::fromValue(screen)), Q_ARG(QVariant, QVariant::fromValue(row)),
                        Q_ARG(QVariant, QVariant::fromValue(button)), Q_ARG(QVariant, QVariant::fromValue(picker)))
                    || !passed.toBool() || qmlWarningOccurred) {
                    qCritical("Store/region acceptance failed");
                    application.exit(EXIT_FAILURE);
                    return;
                }
                if (auto *results = window->findChild<QObject *>(u"renewNetworkRegion"_s))
                    results->setProperty("expanded", true);
                if (storePaging && arguments.contains(u"--smoke-store-navigation"_s)) {
                    startStoreNavigationAcceptance(window, &qmlWarningOccurred);
                    return;
                }
                if (storePaging && arguments.contains(u"--smoke-store-appearance"_s)) {
                    QVariant prepared;
                    if (!QMetaObject::invokeMethod(fixture, "prepareAppearance", Q_RETURN_ARG(QVariant, prepared),
                            Q_ARG(QVariant, QVariant::fromValue(screen)),
                            Q_ARG(QVariant, QVariant(arguments.contains(u"--smoke-store-genres"_s)))) || !prepared.toBool()) {
                        application.exit(EXIT_FAILURE);
                        return;
                    }
                }
                QTimer::singleShot(250, &application, [&, window] {
                    bool passed = !qmlWarningOccurred;
                    const auto shot = arguments.indexOf(u"--screenshot"_s);
                    if (shot >= 0 && shot + 1 < arguments.size()) {
                        const auto path = arguments.at(shot + 1);
                        passed = passed && QFileInfo(path).isAbsolute() && window->grabWindow().save(path);
                    }
                    application.exit(passed ? EXIT_SUCCESS : EXIT_FAILURE);
                });
            });
        } else if (smokeTest && arguments.contains(u"--smoke-settings-motion"_s)) {
            auto *window = engine.rootObjects().isEmpty()
                ? nullptr : qobject_cast<QQuickWindow *>(engine.rootObjects().first());
            startSettingsMotionAcceptance(window, &controller, &qmlWarningOccurred,
                                          arguments.contains(u"--smoke-motion-fullscreen"_s));
        } else if (smokeTest && arguments.contains(u"--smoke-sidebar"_s)) {
            auto *window = engine.rootObjects().isEmpty()
                ? nullptr : qobject_cast<QQuickWindow *>(engine.rootObjects().first());
            startSidebarAcceptance(window, &controller, &qmlWarningOccurred,
                                   arguments.contains(u"--smoke-motion-fullscreen"_s));
        } else if (smokeTest && arguments.contains(u"--smoke-motion"_s)) {
            auto *window = engine.rootObjects().isEmpty()
                ? nullptr : qobject_cast<QQuickWindow *>(engine.rootObjects().first());
            startMotionAcceptance(window, &controller, &qmlWarningOccurred,
                                  arguments.contains(u"--smoke-motion-fullscreen"_s));
        } else if (smokeFullscreenStatsShortcut) {
            auto *window = engine.rootObjects().isEmpty()
                ? nullptr : qobject_cast<QQuickWindow *>(engine.rootObjects().first());
            if (!window) return EXIT_FAILURE;
            window->showFullScreen();
            window->requestActivate();
            auto *statsShortcut = window->findChild<QObject *>(
                arguments.contains(u"--smoke-configured-stats-shortcut"_s)
                    ? u"configuredStreamStatsShortcut"_s : u"streamStatsShortcut"_s);
            auto *copyShortcut = window->findChild<QObject *>(u"streamStatsCopyShortcut"_s);
            auto *streamSurface = window->findChild<QObject *>(u"streamSurfaceHost"_s);
            if (!statsShortcut || !copyShortcut || !streamSurface) return EXIT_FAILURE;
            const auto activateStatsShortcut = [statsShortcut] {
                return QMetaObject::invokeMethod(statsShortcut, "activated", Qt::DirectConnection);
            };
            const auto activateCopyShortcut = [copyShortcut] {
                return QMetaObject::invokeMethod(copyShortcut, "activated", Qt::DirectConnection);
            };
            const auto activateGuideShortcut = [streamSurface] {
                return QMetaObject::invokeMethod(
                    streamSurface, "localShortcutRequested", Qt::DirectConnection,
                    Q_ARG(QString, QStringLiteral("guide")));
            };
            const auto windowStayedFullscreen = [window] {
                return window->visibility() == QWindow::FullScreen
                    && window->property("desktopSurfaceActive").toBool();
            };
            const auto streamInputStayedStable = [window, windowStayedFullscreen] {
                return windowStayedFullscreen()
                    && !window->property("shellCaptureEnabledForSmokeTest").toBool();
            };
            QTimer::singleShot(150, &application,
                               [&application, &controller, statsShortcut, copyShortcut,
                                 activateStatsShortcut, activateCopyShortcut,
                                 activateGuideShortcut, windowStayedFullscreen,
                                 streamInputStayedStable,
                                 &qmlWarningOccurred] {
                if (!streamInputStayedStable() || qmlWarningOccurred
                    || statsShortcut->property("context").toInt() != Qt::ApplicationShortcut
                    || !statsShortcut->property("enabled").toBool()) {
                    application.exit(EXIT_FAILURE);
                    return;
                }
                if (!activateStatsShortcut()) {
                    application.exit(EXIT_FAILURE);
                    return;
                }
                if (controller.overlay() != u"desktop-stream-stats"_s
                        || !streamInputStayedStable()) {
                    application.exit(EXIT_FAILURE);
                    return;
                }
                activateStatsShortcut();
                if (controller.overlay() != u"desktop-stream-stats-expanded"_s
                        || !streamInputStayedStable()) {
                    application.exit(EXIT_FAILURE);
                    return;
                }
                if (!copyShortcut->property("enabled").toBool()
                        || !activateCopyShortcut()
                        || controller.overlay() != u"desktop-stream-stats-expanded"_s
                        || !controller.readClipboardText().startsWith(u"Stream stats:"_s)
                        || !streamInputStayedStable()) {
                    application.exit(EXIT_FAILURE);
                    return;
                }
                activateStatsShortcut();
                if (!controller.overlay().isEmpty() || !streamInputStayedStable()
                        || !activateGuideShortcut()
                        || controller.overlay() != u"desktop-stream-menu"_s
                        || !windowStayedFullscreen()) {
                    application.exit(EXIT_FAILURE);
                    return;
                }
                controller.showOverlay({});
                application.exit(streamInputStayedStable() ? EXIT_SUCCESS : EXIT_FAILURE);
            });
        } else if (screenshotIndex >= 0 && screenshotIndex + 1 < arguments.size()) {
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
        } else if (smokeStreamerEvent) {
            auto *window = engine.rootObjects().isEmpty()
                ? nullptr : qobject_cast<QQuickWindow *>(engine.rootObjects().first());
            if (!window) return EXIT_FAILURE;
            const auto requested = std::make_shared<bool>(false);
            auto *timer = new QTimer(&application);
            timer->setInterval(20);
            QObject::connect(timer, &QTimer::timeout, &application,
                             [&application, &coreClient, window, timer, requested,
                              &qmlWarningOccurred] {
                if (qmlWarningOccurred) {
                    application.exit(EXIT_FAILURE);
                    return;
                }
                if (!*requested) {
                    if (coreClient.state() != u"ready"_s) return;
                    if (coreClient.request(u"test.streamer-event"_s).isEmpty()) {
                        qCritical("Could not request the streamer event fixture");
                        application.exit(EXIT_FAILURE);
                        return;
                    }
                    *requested = true;
                    return;
                }
                const auto snapshot = window->property("streamerSnapshotForSmokeTest").toMap();
                if (snapshot.value(u"status"_s).toString() == u"streaming"_s
                        && snapshot.value(u"firstFrameLatencyMs"_s).toInt() == 37
                        && snapshot.value(u"mediaBackend"_s).toString() == u"ffmpeg"_s
                        && snapshot.value(u"deviceRecoveryCount"_s).toInt() == 2
                        && snapshot.value(u"queueDropCount"_s).toInt() == 4) {
                    timer->stop();
                    application.exit(EXIT_SUCCESS);
                }
            });
            timer->start();
            QTimer::singleShot(3'000, &application, [&application] {
                qCritical("Flat streamer event smoke test timed out");
                application.exit(EXIT_FAILURE);
            });
        } else if (arguments.contains(u"--smoke-release-notes"_s)) {
            auto *store = engine.singletonInstance<QObject *>(u"OpenNOW"_s, u"ShellStore"_s);
            store->setProperty("updaterState", QVariantMap{
                {u"status"_s, u"available"_s}, {u"currentVersion"_s, QGuiApplication::applicationVersion()},
                {u"message"_s, u"A new release is available."_s}, {u"canDownload"_s, true}});
            store->setProperty("releaseHighlights", QVariantMap{{u"bodyMarkdown"_s,
                u"# Release notes\n\n**Bold fix** and *emphasis*.\n\n- First item\n- Second item\n\n"
                 "[Project](https://github.com/OpenCloudGaming/OpenNOW)\n\n"
                 "| Feature | Status |\n| --- | --- |\n| Updates | Ready |\n\n```text\ncode block\n```"_s}});
            QTimer::singleShot(500, &application, [&application, &engine, &controller, &qmlWarningOccurred] {
                auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().first());
                auto *notes = window->findChild<QObject *>(u"releaseNotesDocument"_s);
                const bool desktop = window->property("desktopSurfaceActive").toBool();
                const bool correctRoute = controller.route() != u"updates"_s
                    || window->findChild<QObject *>(u"desktopUpdateScreen"_s);
                QString plain;
                const bool parsed = notes && QMetaObject::invokeMethod(notes, "getText",
                    Q_RETURN_ARG(QString, plain), Q_ARG(int, 0),
                    Q_ARG(int, notes->property("length").toInt()));
                const auto text = plain;
                const bool passed = desktop && correctRoute && parsed && !qmlWarningOccurred
                    && text.contains(u"Bold fix"_s) && text.contains(u"Second item"_s)
                    && text.contains(u"code block"_s) && !text.contains(u"**Bold fix**"_s)
                    && !text.contains(u"# Release notes"_s)
                    && notes->property("contentHeight").toReal() > 100
                    && notes->property("height").toReal() >= notes->property("contentHeight").toReal()
                        + notes->property("topPadding").toReal() + notes->property("bottomPadding").toReal();
                if (!passed) qCritical("Release notes document or desktop update route failed");
                application.exit(passed ? EXIT_SUCCESS : EXIT_FAILURE);
            });
        } else if (smokeConsolePersistenceRollback) {
            auto *window = engine.rootObjects().isEmpty()
                ? nullptr : qobject_cast<QQuickWindow *>(engine.rootObjects().first());
            if (!window) return EXIT_FAILURE;
            const auto phase = std::make_shared<int>(0);
            auto *timer = new QTimer(&application);
            timer->setInterval(20);
            QObject::connect(timer, &QTimer::timeout, &application,
                             [&application, window, timer, phase, &qmlWarningOccurred] {
                if (qmlWarningOccurred) {
                    application.exit(EXIT_FAILURE);
                    return;
                }
                if (*phase == 0) {
                    if (!window->property("settingsLoadedForSmokeTest").toBool()) return;
                    const auto invoked = QMetaObject::invokeMethod(
                        window, "requestConsoleSurface", Q_ARG(QVariant, QVariant(true)));
                    if (!invoked || window->property("desktopSurfaceActive").toBool()) {
                        qCritical("Console mode did not switch the rendered surface immediately");
                        application.exit(EXIT_FAILURE);
                        return;
                    }
                    *phase = 1;
                    return;
                }
                if (*phase == 1) {
                    if (!window->property("consoleModePersistedForSmokeTest").toBool()
                            || window->property("modePersistenceBusyForSmokeTest").toBool()) return;
                    const auto invoked = QMetaObject::invokeMethod(
                        window, "requestConsoleSurface", Q_ARG(QVariant, QVariant(false)));
                    if (!invoked || !window->property("desktopSurfaceActive").toBool()) {
                        qCritical("Desktop mode did not switch the rendered surface immediately");
                        application.exit(EXIT_FAILURE);
                        return;
                    }
                    *phase = 2;
                    return;
                }
                if (*phase == 2) {
                    if (window->property("consoleModePersistedForSmokeTest").toBool()
                            || window->property("modePersistenceBusyForSmokeTest").toBool()) return;
                    const auto consoleInvoked = QMetaObject::invokeMethod(
                        window, "requestConsoleSurface", Q_ARG(QVariant, QVariant(true)));
                    const auto desktopInvoked = QMetaObject::invokeMethod(
                        window, "requestConsoleSurface", Q_ARG(QVariant, QVariant(false)));
                    if (!consoleInvoked || !desktopInvoked
                            || !window->property("desktopSurfaceActive").toBool()) {
                        qCritical("Rapid mode requests did not preserve the latest rendered surface");
                        application.exit(EXIT_FAILURE);
                        return;
                    }
                    *phase = 3;
                    return;
                }
                if (*phase == 3) {
                    if (window->property("consoleModePersistedForSmokeTest").toBool()
                            || window->property("modePersistenceBusyForSmokeTest").toBool()) return;
                    const auto invoked = QMetaObject::invokeMethod(
                        window, "requestConsoleSurface", Q_ARG(QVariant, QVariant(true)));
                    if (!invoked || window->property("desktopSurfaceActive").toBool()) {
                        qCritical("Console mode did not switch after rapid request coalescing");
                        application.exit(EXIT_FAILURE);
                        return;
                    }
                    *phase = 4;
                    return;
                }
                if (*phase == 4) {
                    if (!window->property("consoleModePersistedForSmokeTest").toBool()
                            || window->property("modePersistenceBusyForSmokeTest").toBool()) return;
                    const auto invoked = QMetaObject::invokeMethod(
                        window, "requestConsoleSurface", Q_ARG(QVariant, QVariant(false)));
                    if (!invoked || !window->property("desktopSurfaceActive").toBool()) {
                        qCritical("Desktop mode did not switch before persistence rollback");
                        application.exit(EXIT_FAILURE);
                        return;
                    }
                    *phase = 5;
                    return;
                }
                const auto error = window->property("modePersistenceErrorForSmokeTest").toString();
                if (!window->property("desktopSurfaceActive").toBool()
                        && window->property("consoleModePersistedForSmokeTest").toBool()
                        && !window->property("modePersistenceBusyForSmokeTest").toBool()
                        && error.contains(u"previous mode was restored"_s, Qt::CaseInsensitive)) {
                        timer->stop();
                        application.exit(EXIT_SUCCESS);
                }
            });
            timer->start();
            QTimer::singleShot(3'000, &application, [&application] {
                qCritical("Console mode persistence rollback smoke test timed out");
                application.exit(EXIT_FAILURE);
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

    const auto exitCode = application.exec();
#ifdef OPENNOW_EMBEDDED_STREAMER
    const auto roots = engine.rootObjects();
    for (auto *root : roots) delete root;
    StreamVideoItem::setNativeStreamRuntime(nullptr);
    if (!nativeStreamRuntime.shutdown()) {
        qWarning("Could not complete embedded streamer shutdown: %s",
                 qUtf8Printable(nativeStreamRuntime.lastError()));
    }
#endif
    return exitCode;
}
