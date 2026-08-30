#include "StreamSurfaceController.h"

#include "AppController.h"
#include "CoreClient.h"

#include <QEvent>
#include <QGuiApplication>
#include <QJsonValue>
#include <QPlatformSurfaceEvent>
#include <QQuickItem>
#include <QQuickWindow>
#include <QScreen>

#include <algorithm>
#include <cmath>

using namespace Qt::StringLiterals;

namespace {
constexpr int UpdateDelayMs = 40;
constexpr int HostDiscoveryDelayMs = 50;
constexpr int MinimumSurfaceSize = 64;

QJsonObject rectObject(int x, int y, int width, int height)
{
    return QJsonObject{{u"x"_s, x}, {u"y"_s, y},
                       {u"width"_s, width}, {u"height"_s, height}};
}
}

StreamSurfaceController::StreamSurfaceController(CoreClient *coreClient,
                                                 AppController *appController,
                                                 QObject *parent)
    : QObject(parent)
    , m_coreClient(coreClient)
    , m_appController(appController)
    , m_capability(capabilityFor(currentPlatformFamily(), QGuiApplication::platformName()))
{
    Q_ASSERT(m_coreClient);
    Q_ASSERT(m_appController);

    m_updateTimer.setSingleShot(true);
    m_updateTimer.setInterval(UpdateDelayMs);
    connect(&m_updateTimer, &QTimer::timeout, this, &StreamSurfaceController::flushUpdate);

    m_hostTimer.setSingleShot(true);
    m_hostTimer.setInterval(HostDiscoveryDelayMs);
    connect(&m_hostTimer, &QTimer::timeout, this, &StreamSurfaceController::discoverHost);

    connect(m_appController, &AppController::routeChanged, this, [this] {
        refreshSurface(true);
        if (m_appController->route() == u"stream"_s) {
            m_hostTimer.start(0);
        }
    });
    connect(m_appController, &AppController::overlayChanged, this,
            [this] { refreshSurface(true); });
    connect(m_coreClient, &CoreClient::stateChanged, this, [this] {
        if (m_coreClient->state() != u"ready"_s) {
            m_requestId.clear();
            m_streamerActive = false;
            return;
        }
        if (m_streamerActive || !m_surface.value(u"visible"_s).toBool()) {
            scheduleUpdate(true);
        }
    });
    connect(m_coreClient, &CoreClient::responseReceived,
            this, &StreamSurfaceController::processResponse);
    connect(m_coreClient, &CoreClient::requestFailed, this,
            [this](const QString &requestId, const QString &, const QString &message) {
                processFailure(requestId, message);
            });
    connect(m_coreClient, &CoreClient::eventReceived,
            this, &StreamSurfaceController::processEvent);

    m_surface = encodeSurface(Metrics{});
}

StreamSurfaceController::~StreamSurfaceController()
{
    if (m_window) m_window->removeEventFilter(this);
    if (m_host) m_host->removeEventFilter(this);
}

QJsonObject StreamSurfaceController::surface() const { return m_surface; }
QString StreamSurfaceController::compositionMode() const { return m_capability.mode; }
QString StreamSurfaceController::compositionDescription() const
{
    return m_capability.description;
}
bool StreamSurfaceController::embedded() const { return m_capability.embedded; }
bool StreamSurfaceController::hostAvailable() const { return !m_host.isNull(); }
QString StreamSurfaceController::lastError() const { return m_lastError; }

void StreamSurfaceController::setWindow(QQuickWindow *window)
{
    if (m_window == window) return;
    if (m_window) {
        m_window->removeEventFilter(this);
        disconnect(m_window, nullptr, this, nullptr);
    }
    setHost(nullptr);
    m_window = window;
    m_tearingDown = false;
    if (!m_window) {
        refreshSurface(true);
        return;
    }

    m_window->installEventFilter(this);
    const auto refresh = [this] { refreshSurface(); };
    connect(m_window, &QQuickWindow::xChanged, this, refresh);
    connect(m_window, &QQuickWindow::yChanged, this, refresh);
    connect(m_window, &QQuickWindow::widthChanged, this, refresh);
    connect(m_window, &QQuickWindow::heightChanged, this, refresh);
    connect(m_window, &QQuickWindow::visibilityChanged, this, refresh);
    connect(m_window, &QQuickWindow::screenChanged, this, refresh);
    connect(m_window, &QObject::destroyed, this, [this] {
        m_window = nullptr;
        setHost(nullptr);
        refreshSurface(true);
    });
    discoverHost();
}

void StreamSurfaceController::teardown()
{
    if (m_tearingDown) return;
    m_tearingDown = true;
    m_streamerActive = false;
    refreshSurface(true);
}

StreamSurfaceController::Capability StreamSurfaceController::capabilityFor(
    PlatformFamily family, const QString &platformName)
{
    switch (family) {
    case PlatformFamily::Windows:
        return {u"paired-auxiliary"_s,
                u"Windows presentation currently uses a distinct native presenter targeted to "
                 "the Qt window bounds; it is not an embedded Qt Quick surface and ordinary QML "
                 "cannot overlap it reliably."_s,
                false, true};
    case PlatformFamily::Linux:
        if (platformName.compare(u"xcb"_s, Qt::CaseInsensitive) == 0) {
            return {u"paired-auxiliary"_s,
                    u"The current X11 launch contract uses a separate native top-level surface "
                     "aligned to the Qt stream region; ordinary QML cannot overlap it reliably."_s,
                    false, true};
        }
        if (platformName.contains(u"wayland"_s, Qt::CaseInsensitive)) {
            return {u"paired-auxiliary"_s,
                    u"Wayland does not permit foreign child-window embedding; presentation uses "
                     "a separate compositor-managed surface."_s,
                    false, true};
        }
        return {u"unsupported"_s,
                u"Native stream-surface attachment is supported on Linux only through X11 or "
                 "the explicit Wayland auxiliary-surface fallback."_s,
                false, false};
    case PlatformFamily::MacOS:
        return {u"paired-auxiliary"_s,
                u"macOS cannot embed an AppKit view across the streamer process boundary; "
                 "presentation uses an ordered auxiliary NSWindow."_s,
                false, true};
    case PlatformFamily::Other:
        return {u"unsupported"_s,
                u"This operating system has no native stream-surface attachment contract."_s,
                false, false};
    }
    return {};
}

QJsonObject StreamSurfaceController::encodeSurface(const Metrics &metrics)
{
    const auto dpr = std::clamp(std::isfinite(metrics.devicePixelRatio)
                                    ? metrics.devicePixelRatio : 1.0,
                                0.5, 8.0);
    const auto localX = qRound(metrics.windowRect.x() * dpr);
    const auto localY = qRound(metrics.windowRect.y() * dpr);
    const auto actualWidth = qRound(metrics.windowRect.width() * dpr);
    const auto actualHeight = qRound(metrics.windowRect.height() * dpr);
    const auto width = std::max(MinimumSurfaceSize, actualWidth);
    const auto height = std::max(MinimumSurfaceSize, actualHeight);
    const auto visible = metrics.visible && actualWidth >= MinimumSurfaceSize
        && actualHeight >= MinimumSurfaceSize && !metrics.windowHandle.isEmpty();
    QJsonObject surface{
        {u"rect"_s, rectObject(localX, localY, width, height)},
        {u"screenRect"_s,
         rectObject(qRound(metrics.screenPosition.x() * dpr),
                    qRound(metrics.screenPosition.y() * dpr), width, height)},
        {u"logicalScreenRect"_s,
         rectObject(qRound(metrics.screenPosition.x()), qRound(metrics.screenPosition.y()),
                    std::max(MinimumSurfaceSize, qRound(metrics.windowRect.width())),
                    std::max(MinimumSurfaceSize, qRound(metrics.windowRect.height())))},
        {u"visible"_s, visible},
        {u"deviceScaleFactor"_s, dpr},
    };
    if (visible) surface.insert(u"windowHandle"_s, metrics.windowHandle);
    return surface;
}

bool StreamSurfaceController::eventFilter(QObject *watched, QEvent *event)
{
    if (watched == m_window && event->type() == QEvent::PlatformSurface) {
        const auto *surfaceEvent = static_cast<QPlatformSurfaceEvent *>(event);
        if (surfaceEvent->surfaceEventType() == QPlatformSurfaceEvent::SurfaceAboutToBeDestroyed) {
            m_tearingDown = true;
            refreshSurface(true);
        } else {
            m_tearingDown = false;
            refreshSurface(true);
        }
    } else if (watched == m_host && event->type() == QEvent::Destroy) {
        setHost(nullptr);
        refreshSurface(true);
        if (m_appController->route() == u"stream"_s) m_hostTimer.start();
    } else if (watched == m_window
               && (event->type() == QEvent::Show || event->type() == QEvent::Hide
                   || event->type() == QEvent::Move || event->type() == QEvent::Resize
                   || event->type() == QEvent::WindowStateChange
                   || event->type() == QEvent::DevicePixelRatioChange)) {
        refreshSurface();
    }
    return QObject::eventFilter(watched, event);
}

StreamSurfaceController::PlatformFamily StreamSurfaceController::currentPlatformFamily()
{
#if defined(Q_OS_WIN)
    return PlatformFamily::Windows;
#elif defined(Q_OS_LINUX)
    return PlatformFamily::Linux;
#elif defined(Q_OS_MACOS)
    return PlatformFamily::MacOS;
#else
    return PlatformFamily::Other;
#endif
}

QString StreamSurfaceController::encodeWindowHandle(WId handle)
{
    return handle == 0 ? QString{} : u"0x%1"_s.arg(static_cast<qulonglong>(handle), 0, 16);
}

void StreamSurfaceController::discoverHost()
{
    if (!m_window || m_tearingDown) return;
    auto *host = m_window->findChild<QQuickItem *>(u"streamSurfaceHost"_s,
                                                   Qt::FindChildrenRecursively);
    setHost(host);
    refreshSurface(true);
    if (!host && m_appController->route() == u"stream"_s) m_hostTimer.start();
}

void StreamSurfaceController::setHost(QQuickItem *host)
{
    if (m_host == host) return;
    const auto wasAvailable = !m_host.isNull();
    if (m_host) {
        m_host->removeEventFilter(this);
        disconnect(m_host, nullptr, this, nullptr);
    }
    m_host = host;
    if (m_host) {
        m_host->installEventFilter(this);
        const auto refresh = [this] { refreshSurface(); };
        connect(m_host, &QQuickItem::xChanged, this, refresh);
        connect(m_host, &QQuickItem::yChanged, this, refresh);
        connect(m_host, &QQuickItem::widthChanged, this, refresh);
        connect(m_host, &QQuickItem::heightChanged, this, refresh);
        connect(m_host, &QQuickItem::visibleChanged, this, refresh);
        connect(m_host, &QQuickItem::windowChanged, this, [this](QQuickWindow *) {
            refreshSurface(true);
        });
    }
    if (wasAvailable != !m_host.isNull()) emit hostAvailableChanged();
}

void StreamSurfaceController::refreshSurface(bool immediate)
{
    Metrics metrics;
    if (m_window && m_host) {
        const auto sceneRect = m_host->mapRectToScene(m_host->boundingRect());
        metrics.windowRect = sceneRect;
        metrics.screenPosition = m_window->mapToGlobal(sceneRect.topLeft());
        metrics.devicePixelRatio = m_window->effectiveDevicePixelRatio();
        const auto windowUsable = m_window->isVisible()
            && m_window->visibility() != QWindow::Hidden
            && m_window->visibility() != QWindow::Minimized;
        metrics.visible = !m_tearingDown && m_capability.presentationSupported
            && m_appController->route() == u"stream"_s && m_appController->overlay().isEmpty()
            && windowUsable && m_host->isVisible();
        if (metrics.visible) metrics.windowHandle = encodeWindowHandle(m_window->winId());
    }

    const auto next = encodeSurface(metrics);
    if (next == m_surface) return;
    m_surface = next;
    ++m_generation;
    m_dirty = true;
    m_failureCount = 0;
    setLastError({});
    emit surfaceChanged();
    if (m_streamerActive || !m_surface.value(u"visible"_s).toBool()) {
        scheduleUpdate(immediate);
    }
}

void StreamSurfaceController::scheduleUpdate(bool immediate)
{
    m_dirty = true;
    if (immediate) {
        m_updateTimer.stop();
        flushUpdate();
    } else if (!m_updateTimer.isActive()) {
        m_updateTimer.start();
    }
}

void StreamSurfaceController::flushUpdate()
{
    if (!m_dirty || !m_requestId.isEmpty() || m_coreClient->state() != u"ready"_s) return;
    m_requestGeneration = m_generation;
    m_requestId = m_coreClient->request(
        u"streamer.surface.update"_s, QJsonObject{{u"surface"_s, m_surface}}, 5'000);
    if (m_requestId.isEmpty()) {
        setLastError(u"Core transport is not ready for the stream-surface update."_s);
        return;
    }
    m_dirty = false;
}

void StreamSurfaceController::setLastError(const QString &error)
{
    if (m_lastError == error) return;
    m_lastError = error;
    emit lastErrorChanged();
}

void StreamSurfaceController::processResponse(const QString &requestId,
                                              const QJsonObject &result)
{
    if (requestId != m_requestId) return;
    m_requestId.clear();
    const auto currentChanged = m_requestGeneration != m_generation;
    const auto applied = result.value(u"applied"_s).toBool();
    if (applied) {
        m_failureCount = 0;
        setLastError({});
    } else if (m_streamerActive) {
        ++m_failureCount;
        setLastError(u"Native streamer did not apply the current stream surface."_s);
    }
    if (currentChanged || m_dirty || (!applied && m_streamerActive && m_failureCount < 3)) {
        scheduleUpdate();
    }
}

void StreamSurfaceController::processFailure(const QString &requestId, const QString &message)
{
    if (requestId != m_requestId) return;
    m_requestId.clear();
    ++m_failureCount;
    m_dirty = true;
    setLastError(message);
    if (m_streamerActive && m_failureCount < 3) scheduleUpdate();
}

void StreamSurfaceController::processEvent(const QString &name, const QJsonObject &payload)
{
    if (name != u"streamer.changed"_s) return;
    const auto nested = payload.value(u"streamer"_s).toObject();
    const auto status = nested.isEmpty()
        ? payload.value(u"status"_s).toString()
        : nested.value(u"status"_s).toString();
    const auto active = !status.isEmpty() && status != u"stopped"_s && status != u"error"_s;
    if (active && !m_streamerActive) {
        m_streamerActive = true;
        m_failureCount = 0;
        scheduleUpdate(true);
    } else if (!active) {
        m_streamerActive = false;
        m_requestId.clear();
    }
}
