#pragma once

#include <QObject>
#include <QSettings>
#include <QVariant>

class AppState final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QString profileName READ profileName NOTIFY profileChanged)
    Q_PROPERTY(QString profileInitial READ profileInitial NOTIFY profileChanged)
    Q_PROPERTY(QString serverName READ serverName NOTIFY serverChanged)
    Q_PROPERTY(QString serverRegion READ serverRegion NOTIFY serverChanged)
    Q_PROPERTY(int serverLatency READ serverLatency NOTIFY serverChanged)
    Q_PROPERTY(QString lastExportPath READ lastExportPath NOTIFY exportCompleted)

public:
    explicit AppState(QObject *parent = nullptr);

    QString profileName() const;
    QString profileInitial() const;
    QString serverName() const;
    QString serverRegion() const;
    int serverLatency() const;
    QString lastExportPath() const { return m_lastExportPath; }

    Q_INVOKABLE QVariant preference(const QString &key, const QVariant &fallback = {}) const;
    Q_INVOKABLE void setPreference(const QString &key, const QVariant &value);
    Q_INVOKABLE void resetPreferences();
    Q_INVOKABLE void selectProfile(const QString &name);
    Q_INVOKABLE void selectServer(const QString &name, const QString &region, int latencyMs);
    Q_INVOKABLE QString exportSessions();
    Q_INVOKABLE QString nextScreenshotPath() const;
    Q_INVOKABLE void copyText(const QString &text);

signals:
    void preferenceChanged(const QString &key, const QVariant &value);
    void preferencesReset();
    void profileChanged();
    void serverChanged();
    void exportCompleted(const QString &path);

private:
    QSettings m_settings;
    QString m_lastExportPath;
};
