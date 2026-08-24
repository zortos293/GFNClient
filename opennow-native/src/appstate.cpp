#include "appstate.h"

#include <QClipboard>
#include <QDateTime>
#include <QDir>
#include <QGuiApplication>
#include <QSaveFile>
#include <QStandardPaths>

AppState::AppState(QObject *parent)
    : QObject(parent)
{
}

QString AppState::profileName() const
{
    return m_settings.value(QStringLiteral("profile/name"), QStringLiteral("Zortos")).toString();
}

QString AppState::profileInitial() const
{
    const auto name = profileName().trimmed();
    return name.isEmpty() ? QStringLiteral("?") : name.left(1).toUpper();
}

QString AppState::serverName() const
{
    return m_settings.value(QStringLiteral("server/name"), QStringLiteral("EU West - Frankfurt")).toString();
}

QString AppState::serverRegion() const
{
    return m_settings.value(QStringLiteral("server/region"), QStringLiteral("EU-WEST")).toString();
}

int AppState::serverLatency() const
{
    return m_settings.value(QStringLiteral("server/latency"), 9).toInt();
}

QVariant AppState::preference(const QString &key, const QVariant &fallback) const
{
    return m_settings.value(QStringLiteral("preferences/") + key, fallback);
}

void AppState::setPreference(const QString &key, const QVariant &value)
{
    const auto path = QStringLiteral("preferences/") + key;
    if (m_settings.value(path) == value) {
        return;
    }
    m_settings.setValue(path, value);
    m_settings.sync();
    emit preferenceChanged(key, value);
}

void AppState::resetPreferences()
{
    m_settings.beginGroup(QStringLiteral("preferences"));
    m_settings.remove(QString());
    m_settings.endGroup();
    m_settings.sync();
    emit preferencesReset();
}

void AppState::selectProfile(const QString &name)
{
    if (name.trimmed().isEmpty() || profileName() == name) {
        return;
    }
    m_settings.setValue(QStringLiteral("profile/name"), name);
    m_settings.sync();
    emit profileChanged();
}

void AppState::selectServer(const QString &name, const QString &region, int latencyMs)
{
    m_settings.setValue(QStringLiteral("server/name"), name);
    m_settings.setValue(QStringLiteral("server/region"), region);
    m_settings.setValue(QStringLiteral("server/latency"), latencyMs);
    m_settings.sync();
    emit serverChanged();
}

QString AppState::exportSessions()
{
    auto directory = QStandardPaths::writableLocation(QStandardPaths::DownloadLocation);
    if (directory.isEmpty()) {
        directory = QDir::homePath();
    }
    QDir().mkpath(directory);
    const auto filename = QStringLiteral("OpenNOW-session-report-%1.csv")
                              .arg(QDateTime::currentDateTimeUtc().toString(QStringLiteral("yyyyMMdd-HHmmss")));
    m_lastExportPath = QDir(directory).filePath(filename);
    QSaveFile file(m_lastExportPath);
    if (!file.open(QIODevice::WriteOnly | QIODevice::Text)) {
        m_lastExportPath.clear();
        emit exportCompleted(m_lastExportPath);
        return m_lastExportPath;
    }
    file.write("game,started_at,duration_minutes,region,latency_ms,average_fps\n");
    file.write("Cyber Drift 2088,2026-08-24T19:12:00Z,86,EU-WEST,9,120\n");
    file.write("Starfall Frontier,2026-08-23T21:04:00Z,54,EU-WEST,11,117\n");
    file.write("Iron Harvest 2,2026-08-22T17:31:00Z,43,EU-WEST,12,111\n");
    file.write("Nightfall Protocol,2026-08-21T22:45:00Z,72,EU-WEST,10,119\n");
    if (!file.commit()) {
        m_lastExportPath.clear();
    }
    emit exportCompleted(m_lastExportPath);
    return m_lastExportPath;
}

QString AppState::nextScreenshotPath() const
{
    auto directory = QStandardPaths::writableLocation(QStandardPaths::PicturesLocation);
    if (directory.isEmpty()) {
        directory = QDir::homePath();
    }
    directory = QDir(directory).filePath(QStringLiteral("OpenNOW"));
    QDir().mkpath(directory);
    return QDir(directory).filePath(
        QStringLiteral("OpenNOW-%1.png")
            .arg(QDateTime::currentDateTimeUtc().toString(QStringLiteral("yyyyMMdd-HHmmss-zzz"))));
}

void AppState::copyText(const QString &text)
{
    QGuiApplication::clipboard()->setText(text);
}
