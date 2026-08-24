#pragma once

#include "authdata.h"

#include <QDir>
#include <QFile>
#include <QJsonDocument>
#include <QSaveFile>
#include <QStandardPaths>

namespace OpenNow::Auth {

struct PersistedState
{
    Provider selectedProvider;
    std::optional<Session> session;
};

class SessionStore final
{
public:
    SessionStore()
    {
        const auto root = QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation);
        m_path = QDir(root).filePath(QStringLiteral("auth/session.json"));
    }

    explicit SessionStore(QString path)
        : m_path(std::move(path))
    {
    }

    QString path() const { return m_path; }

    std::optional<PersistedState> load() const
    {
        QFile file(m_path);
        if (!file.exists()) {
            return std::nullopt;
        }
        if (!QFile::setPermissions(m_path, QFileDevice::ReadOwner | QFileDevice::WriteOwner)) {
            return std::nullopt;
        }
        if (!file.open(QIODevice::ReadOnly) || file.size() > 1024 * 1024) {
            return std::nullopt;
        }
        QJsonParseError error;
        const auto document = QJsonDocument::fromJson(file.readAll(), &error);
        if (error.error != QJsonParseError::NoError || !document.isObject()) {
            return std::nullopt;
        }
        const auto root = document.object();
        if (root.value(QStringLiteral("version")).toInt() != 1) {
            return std::nullopt;
        }
        const auto provider = providerFromJson(root.value(QStringLiteral("selectedProvider")).toObject());
        if (!provider) {
            return std::nullopt;
        }
        PersistedState state{*provider, std::nullopt};
        if (root.value(QStringLiteral("session")).isObject()) {
            state.session = sessionFromJson(root.value(QStringLiteral("session")).toObject());
        }
        return state;
    }

    bool save(const PersistedState &state) const
    {
        const QFileInfo info(m_path);
        QDir directory(info.absolutePath());
        if (!directory.mkpath(QStringLiteral("."))) {
            return false;
        }
        QFile::setPermissions(directory.absolutePath(), QFileDevice::ReadOwner | QFileDevice::WriteOwner
                                                           | QFileDevice::ExeOwner);

        QJsonObject root{{QStringLiteral("version"), 1},
                         {QStringLiteral("selectedProvider"), providerToJson(state.selectedProvider)}};
        root.insert(QStringLiteral("session"),
                    state.session ? QJsonValue(sessionToJson(*state.session)) : QJsonValue(QJsonValue::Null));

        QSaveFile file(m_path);
        file.setDirectWriteFallback(false);
        if (!file.open(QIODevice::WriteOnly)) {
            return false;
        }
        if (!file.setPermissions(QFileDevice::ReadOwner | QFileDevice::WriteOwner)) {
            file.cancelWriting();
            return false;
        }
        const auto bytes = QJsonDocument(root).toJson(QJsonDocument::Compact);
        if (file.write(bytes) != bytes.size() || !file.commit()) {
            return false;
        }
        return QFile::setPermissions(m_path, QFileDevice::ReadOwner | QFileDevice::WriteOwner);
    }

private:
    QString m_path;
};

} // namespace OpenNow::Auth
