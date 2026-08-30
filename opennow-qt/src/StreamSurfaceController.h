#pragma once

#include <QJsonObject>
#include <QObject>
#include <QPointer>
#include <QRectF>
#include <QTimer>
#include <QtGui/qwindowdefs.h>

class AppController;
class CoreClient;
class QQuickItem;
class QQuickWindow;

class StreamSurfaceController final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QJsonObject surface READ surface NOTIFY surfaceChanged)
    Q_PROPERTY(QString compositionMode READ compositionMode CONSTANT)
    Q_PROPERTY(QString compositionDescription READ compositionDescription CONSTANT)
    Q_PROPERTY(bool embedded READ embedded CONSTANT)
    Q_PROPERTY(bool hostAvailable READ hostAvailable NOTIFY hostAvailableChanged)
    Q_PROPERTY(QString lastError READ lastError NOTIFY lastErrorChanged)

public:
    enum class PlatformFamily {
        Windows,
        Linux,
        MacOS,
        Other,
    };
    Q_ENUM(PlatformFamily)

    struct Capability {
        QString mode;
        QString description;
        bool embedded = false;
        bool presentationSupported = false;

        friend bool operator==(const Capability &, const Capability &) = default;
    };

    struct Metrics {
        QRectF windowRect;
        QPointF screenPosition;
        qreal devicePixelRatio = 1.0;
        QString windowHandle;
        bool visible = false;
    };

    explicit StreamSurfaceController(CoreClient *coreClient,
                                     AppController *appController,
                                     QObject *parent = nullptr);
    ~StreamSurfaceController() override;

    [[nodiscard]] QJsonObject surface() const;
    [[nodiscard]] QString compositionMode() const;
    [[nodiscard]] QString compositionDescription() const;
    [[nodiscard]] bool embedded() const;
    [[nodiscard]] bool hostAvailable() const;
    [[nodiscard]] QString lastError() const;

    void setWindow(QQuickWindow *window);
    Q_INVOKABLE void teardown();

    [[nodiscard]] static Capability capabilityFor(PlatformFamily family,
                                                  const QString &platformName);
    [[nodiscard]] static QJsonObject encodeSurface(const Metrics &metrics);

signals:
    void surfaceChanged();
    void hostAvailableChanged();
    void lastErrorChanged();

protected:
    bool eventFilter(QObject *watched, QEvent *event) override;

private:
    static PlatformFamily currentPlatformFamily();
    static QString encodeWindowHandle(WId handle);
    void discoverHost();
    void setHost(QQuickItem *host);
    void refreshSurface(bool immediate = false);
    void scheduleUpdate(bool immediate = false);
    void flushUpdate();
    void setLastError(const QString &error);
    void processResponse(const QString &requestId, const QJsonObject &result);
    void processFailure(const QString &requestId, const QString &message);
    void processEvent(const QString &name, const QJsonObject &payload);

    CoreClient *m_coreClient;
    AppController *m_appController;
    QPointer<QQuickWindow> m_window;
    QPointer<QQuickItem> m_host;
    Capability m_capability;
    QJsonObject m_surface;
    QTimer m_updateTimer;
    QTimer m_hostTimer;
    QString m_requestId;
    QString m_lastError;
    quint64 m_generation = 0;
    quint64 m_requestGeneration = 0;
    int m_failureCount = 0;
    bool m_dirty = false;
    bool m_streamerActive = false;
    bool m_tearingDown = false;
};
