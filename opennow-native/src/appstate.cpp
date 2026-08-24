#include "appstate.h"

#include <QClipboard>
#include <QDateTime>
#include <QDir>
#include <QGuiApplication>
#include <QJsonDocument>
#include <QSaveFile>
#include <QStandardPaths>
#include <utility>

namespace {
QByteArray csvCell(const QVariant &value)
{
    auto text = value.toString();
    text.replace('"', QStringLiteral("\"\""));
    return QByteArrayLiteral("\"") + text.toUtf8() + QByteArrayLiteral("\"");
}
}

AppState::AppState(QObject *parent)
    : QObject(parent)
{
    if (m_settings.value(QStringLiteral("profile/name")).toString() == QStringLiteral("Zortos")) {
        m_settings.remove(QStringLiteral("profile/name"));
    }
    if (m_settings.value(QStringLiteral("server/name")).toString() == QStringLiteral("EU West - Frankfurt")) {
        m_settings.remove(QStringLiteral("server/name"));
        m_settings.remove(QStringLiteral("server/region"));
        m_settings.remove(QStringLiteral("server/latency"));
    }
    const auto parsed = QJsonDocument::fromJson(
        m_settings.value(QStringLiteral("sessions/history")).toByteArray());
    if (parsed.isArray()) {
        m_sessions = parsed.toVariant().toList();
    }
}

QString AppState::profileName() const
{
    return m_settings.value(QStringLiteral("profile/name"), QStringLiteral("Player")).toString();
}

QString AppState::profileInitial() const
{
    const auto name = profileName().trimmed();
    return name.isEmpty() ? QStringLiteral("?") : name.left(1).toUpper();
}

QString AppState::serverName() const
{
    return m_settings.value(QStringLiteral("server/name"), QStringLiteral("Automatic")).toString();
}

QString AppState::serverRegion() const
{
    return m_settings.value(QStringLiteral("server/region"), QStringLiteral("Automatic")).toString();
}

int AppState::serverLatency() const
{
    return m_settings.value(QStringLiteral("server/latency"), 0).toInt();
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
    file.write("game,started_at,duration_minutes,region,latency_ms,average_fps,packet_loss_percent,disconnects\n");
    for (const auto &entry : std::as_const(m_sessions)) {
        const auto session = entry.toMap();
        const QList<QByteArray> cells = {
            csvCell(session.value(QStringLiteral("title"))),
            csvCell(session.value(QStringLiteral("startedAt"))),
            QByteArray::number(session.value(QStringLiteral("durationMinutes")).toInt()),
            csvCell(session.value(QStringLiteral("region"))),
            QByteArray::number(session.value(QStringLiteral("latencyMs")).toInt()),
            QByteArray::number(session.value(QStringLiteral("averageFps")).toInt()),
            QByteArray::number(session.value(QStringLiteral("packetLoss")).toDouble(), 'f', 2),
            QByteArray::number(session.value(QStringLiteral("disconnects")).toInt()),
        };
        file.write(cells.join(',') + '\n');
    }
    if (!file.commit()) {
        m_lastExportPath.clear();
    }
    emit exportCompleted(m_lastExportPath);
    return m_lastExportPath;
}

void AppState::recordSession(const QVariantMap &session)
{
    const auto title = session.value(QStringLiteral("title")).toString().trimmed();
    if (title.isEmpty()) {
        return;
    }

    auto normalized = session;
    normalized.insert(QStringLiteral("title"), title);
    if (normalized.value(QStringLiteral("startedAt")).toString().isEmpty()) {
        normalized.insert(QStringLiteral("startedAt"),
                          QDateTime::currentDateTimeUtc().toString(Qt::ISODate));
    }
    normalized.insert(QStringLiteral("durationMinutes"),
                      qMax(0, normalized.value(QStringLiteral("durationMinutes")).toInt()));
    normalized.insert(QStringLiteral("latencyMs"),
                      qMax(0, normalized.value(QStringLiteral("latencyMs")).toInt()));
    normalized.insert(QStringLiteral("averageFps"),
                      qMax(0, normalized.value(QStringLiteral("averageFps")).toInt()));
    normalized.insert(QStringLiteral("packetLoss"),
                      qMax(0.0, normalized.value(QStringLiteral("packetLoss")).toDouble()));
    normalized.insert(QStringLiteral("disconnects"),
                      qMax(0, normalized.value(QStringLiteral("disconnects")).toInt()));

    m_sessions.prepend(normalized);
    constexpr auto maximumStoredSessions = 500;
    while (m_sessions.size() > maximumStoredSessions) {
        m_sessions.removeLast();
    }
    m_settings.setValue(QStringLiteral("sessions/history"),
                        QJsonDocument::fromVariant(m_sessions).toJson(QJsonDocument::Compact));
    m_settings.sync();
    emit sessionsChanged();
}

void AppState::clearSessions()
{
    if (m_sessions.isEmpty()) {
        return;
    }
    m_sessions.clear();
    m_settings.remove(QStringLiteral("sessions/history"));
    m_settings.sync();
    emit sessionsChanged();
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
