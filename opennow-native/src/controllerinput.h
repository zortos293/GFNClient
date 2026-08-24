#pragma once

#include <QObject>
#include <QTimer>

class ControllerInput final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool connected READ connected NOTIFY connectedChanged)
    Q_PROPERTY(QString controllerName READ controllerName NOTIFY connectedChanged)

public:
    explicit ControllerInput(QObject *parent = nullptr);
    ~ControllerInput() override;

    bool connected() const { return m_connected; }
    QString controllerName() const { return m_controllerName; }

signals:
    void connectedChanged();
    void sectionRequested(int delta);

private:
    void pollEvents();
    void postKey(int key, bool pressed);
    void refreshController();

    QTimer m_pollTimer;
    bool m_initialized = false;
    bool m_connected = false;
    QString m_controllerName = QStringLiteral("Keyboard");
};
