#include "SingleInstance.h"

#include <QCoreApplication>
#include <QCryptographicHash>
#include <QJsonArray>
#include <QJsonDocument>
#include <QLocalSocket>
#include <QStandardPaths>

using namespace Qt::StringLiterals;

SingleInstance::SingleInstance(QObject *parent)
    : QObject(parent)
{
    connect(&m_server, &QLocalServer::newConnection, this, &SingleInstance::acceptConnection);
}

bool SingleInstance::acquire(const QStringList &arguments)
{
    const auto name = serverName();
    QLocalSocket peer;
    peer.connectToServer(name, QIODevice::WriteOnly);
    if (peer.waitForConnected(250)) {
        QJsonArray encodedArguments;
        for (const auto &argument : arguments) encodedArguments.append(argument);
        auto payload = QJsonDocument(encodedArguments).toJson(QJsonDocument::Compact);
        payload.append('\n');
        peer.write(payload);
        peer.waitForBytesWritten(500);
        peer.disconnectFromServer();
        return false;
    }

    // Only remove a stale endpoint after proving that no peer accepts it.
    QLocalServer::removeServer(name);
    if (!m_server.listen(name)) {
        qWarning("Could not create the single-instance endpoint: %s",
                 qUtf8Printable(m_server.errorString()));
        return true;
    }
    return true;
}

void SingleInstance::acceptConnection()
{
    while (auto *socket = m_server.nextPendingConnection()) {
        socket->setParent(this);
        connect(socket, &QLocalSocket::readyRead, socket, [this, socket] {
            auto buffer = socket->property("opennowBuffer").toByteArray();
            buffer += socket->readAll();
            qsizetype newline = -1;
            while ((newline = buffer.indexOf('\n')) >= 0) {
                const auto document = QJsonDocument::fromJson(buffer.first(newline).trimmed());
                buffer.remove(0, newline + 1);
                if (!document.isArray()) continue;
                QStringList arguments;
                for (const auto &value : document.array()) {
                    if (value.isString()) arguments.push_back(value.toString());
                }
                emit activationRequested(arguments);
            }
            socket->setProperty("opennowBuffer", buffer);
        });
        connect(socket, &QLocalSocket::disconnected, socket, &QObject::deleteLater);
    }
}

QString SingleInstance::serverName()
{
    const auto scope = QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation).toUtf8();
    const auto digest = QCryptographicHash::hash(scope, QCryptographicHash::Sha256).toHex().first(16);
    return u"opennow-%1"_s.arg(QString::fromLatin1(digest));
}
