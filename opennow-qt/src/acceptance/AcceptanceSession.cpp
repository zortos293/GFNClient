#include "acceptance/AcceptanceSession.h"
#include "acceptance/AcceptanceProfiler.h"
#include "app/AppController.h"

#include <QElapsedTimer>
#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQmlError>
#include <QQuickWindow>
#include <QTimer>
#include <QVariantMap>

#include <cstdio>
#include <cstdlib>
#include <memory>
#include <utility>

using namespace Qt::StringLiterals;

AcceptanceSession::AcceptanceSession(QGuiApplication &application,
                                     QQmlApplicationEngine &engine,
                                     AppController &controller, CoreClient &coreClient,
                                     const QStringList &arguments)
    : m_application(application)
    , m_engine(engine)
    , m_controller(controller)
    , m_coreClient(coreClient)
    , m_arguments(arguments)
    , m_smokeTest(arguments.contains(u"--smoke-test"_s))
    , m_smokeConsolePersistenceRollback(m_smokeTest
          && arguments.contains(u"--smoke-console-persistence-rollback"_s))
    , m_smokeStreamerEvent(m_smokeTest && arguments.contains(u"--smoke-streamer-event"_s))
    , m_smokeFullscreenStatsShortcut(m_smokeTest
          && arguments.contains(u"--smoke-fullscreen-stats-shortcut"_s))
    , m_performanceReportIndex(arguments.indexOf(u"--performance-report"_s))
    , m_performanceMode(m_performanceReportIndex >= 0
          && m_performanceReportIndex + 1 < arguments.size())
{
    QObject::connect(&m_engine, &QQmlApplicationEngine::warnings, this,
                     [this](const QList<QQmlError> &warnings) {
                         m_qmlWarningOccurred = true;
                         for (const auto &warning : warnings) {
                             const auto message = warning.toString().toUtf8();
                             std::fprintf(stderr, "%s\n", message.constData());
                         }
                     });
}

bool AcceptanceSession::allowsExplicitCore() const
{
    return (!m_smokeTest || m_smokeConsolePersistenceRollback || m_smokeStreamerEvent)
        && !m_performanceMode;
}

bool AcceptanceSession::allowsBundledCore() const
{
    return !m_smokeTest && !m_performanceMode;
}

void AcceptanceSession::configureContext()
{
    m_engine.rootContext()->setContextProperty(u"SmokeTestMode"_s, m_smokeTest);
    m_engine.rootContext()->setContextProperty(
        u"SmokeTestGame"_s,
        m_smokeTest ? QVariantMap{
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
}

int AcceptanceSession::measureStartup(const QElapsedTimer &startupTimer, qint64 qmlReadyMs)
{
    if (m_arguments.contains(u"--measure-startup"_s)) {
        auto *window = m_engine.rootObjects().isEmpty()
            ? nullptr
            : qobject_cast<QQuickWindow *>(m_engine.rootObjects().first());
        if (!window) {
            return EXIT_FAILURE;
        }
        const auto reported = std::make_shared<bool>(false);
        QObject::connect(window, &QQuickWindow::frameSwapped, this,
                         [this, startupTimer, qmlReadyMs, reported] {
                             if (*reported) return;
                             *reported = true;
                             std::fprintf(stdout,
                                          "{\"qmlReadyMs\":%lld,\"firstFrameMs\":%lld}\n",
                                          static_cast<long long>(qmlReadyMs),
                                          static_cast<long long>(startupTimer.elapsed()));
                             std::fflush(stdout);
                             m_application.exit(EXIT_SUCCESS);
                         });
        QTimer::singleShot(5'000, this, [this, reported] {
            if (*reported) return;
            *reported = true;
            std::fprintf(stderr, "startup measurement timed out before the first frame\n");
            std::fflush(stderr);
            m_application.exit(EXIT_FAILURE);
        });
    }
    return EXIT_SUCCESS;
}

int AcceptanceSession::startWorkload()
{
    if (m_performanceMode) {
        if (m_controller.reducedMotion()) {
            qCritical("Performance acceptance requires production motion to be enabled");
            return EXIT_FAILURE;
        }
        const auto integerOption = [this](const QString &name, int fallback) {
            const auto index = m_arguments.indexOf(name);
            if (index < 0 || index + 1 >= m_arguments.size()) return fallback;
            bool valid = false;
            const auto value = m_arguments.at(index + 1).toInt(&valid);
            return valid ? value : fallback;
        };
        const auto realOption = [this](const QString &name, double fallback) {
            const auto index = m_arguments.indexOf(name);
            if (index < 0 || index + 1 >= m_arguments.size()) return fallback;
            bool valid = false;
            const auto value = m_arguments.at(index + 1).toDouble(&valid);
            return valid ? value : fallback;
        };
        auto *window = m_engine.rootObjects().isEmpty()
            ? nullptr
            : qobject_cast<QQuickWindow *>(m_engine.rootObjects().first());
        auto options = AcceptanceProfiler::Options{
            .reportPath = m_arguments.at(m_performanceReportIndex + 1),
            .width = integerOption(u"--performance-width"_s, 1920),
            .height = integerOption(u"--performance-height"_s, 1080),
            .cycles = integerOption(u"--performance-cycles"_s, 3),
            .refreshRateHz = realOption(u"--performance-refresh-hz"_s, 0.0),
            .machineLabel = [this] {
                const auto index = m_arguments.indexOf(u"--performance-label"_s);
                return index >= 0 && index + 1 < m_arguments.size()
                    ? m_arguments.at(index + 1) : QString{};
            }(),
            .requireHardware = m_arguments.contains(u"--performance-require-hardware"_s),
        };
        auto *profiler = new AcceptanceProfiler(
            window, &m_controller, std::move(options),
            [this](bool passed) {
                m_application.exit(passed && !m_qmlWarningOccurred ? EXIT_SUCCESS : EXIT_FAILURE);
            }, this);
        if (!profiler->start()) {
            qCritical("Could not start the Qt performance acceptance workload");
            return EXIT_FAILURE;
        }

        return EXIT_SUCCESS;
    }
    return startSmokeWorkload();
}
