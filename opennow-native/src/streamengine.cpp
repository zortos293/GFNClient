#include "streamengine.h"

#include <QCoreApplication>
#include <QDir>
#include <QFileInfo>
#include <QJsonDocument>
#include <QJsonValue>

StreamEngine::StreamEngine(QObject *parent)
    : QObject(parent)
{
    m_process.setProcessChannelMode(QProcess::SeparateChannels);

    connect(&m_process, &QProcess::readyReadStandardOutput, this, [this] {
        m_buffer.append(m_process.readAllStandardOutput());
        while (true) {
            const auto newline = m_buffer.indexOf('\n');
            if (newline < 0) {
                break;
            }
            processLine(m_buffer.left(newline).trimmed());
            m_buffer.remove(0, newline + 1);
        }
    });

    connect(&m_process, &QProcess::started, this, [this] {
        if (!m_available) {
            m_available = true;
            emit availableChanged();
        }
        sendCommand({{QStringLiteral("type"), QStringLiteral("hello")}});
    });

    connect(&m_process, &QProcess::errorOccurred, this, [this](QProcess::ProcessError) {
        setPhase(QStringLiteral("error"), QStringLiteral("Native WebRTC runtime could not start"));
        if (m_available) {
            m_available = false;
            emit availableChanged();
        }
    });

    connect(&m_process, qOverload<int, QProcess::ExitStatus>(&QProcess::finished), this,
            [this](int, QProcess::ExitStatus) {
                if (m_phase != QStringLiteral("idle")) {
                    setPhase(QStringLiteral("idle"), QStringLiteral("Native runtime stopped"));
                }
                if (m_available) {
                    m_available = false;
                    emit availableChanged();
                }
            });

    QTimer::singleShot(0, this, &StreamEngine::ensureStarted);
}

QString StreamEngine::runtimePath() const
{
    const auto executableDir = QCoreApplication::applicationDirPath();
    const QStringList candidates = {
        QDir(executableDir).filePath(QStringLiteral("opennow-webrtc-demo")),
        QDir(executableDir).filePath(QStringLiteral("../streamer/target/debug/opennow-webrtc-demo")),
        QDir(executableDir).filePath(QStringLiteral("../../streamer/target/debug/opennow-webrtc-demo")),
        QDir::current().filePath(QStringLiteral("streamer/target/debug/opennow-webrtc-demo")),
        QDir::current().filePath(QStringLiteral("opennow-native/streamer/target/debug/opennow-webrtc-demo")),
    };

    for (const auto &candidate : candidates) {
        if (QFileInfo(candidate).isExecutable()) {
            return QFileInfo(candidate).absoluteFilePath();
        }
    }
    return candidates.first();
}

void StreamEngine::ensureStarted()
{
    if (m_process.state() != QProcess::NotRunning) {
        return;
    }
    m_process.start(runtimePath());
}

void StreamEngine::sendCommand(const QJsonObject &command)
{
    ensureStarted();
    if (!m_process.waitForStarted(1500)) {
        return;
    }
    m_process.write(QJsonDocument(command).toJson(QJsonDocument::Compact));
    m_process.write("\n");
}

void StreamEngine::startDemo(const QString &quality)
{
    m_pendingQuality = quality;
    setPhase(QStringLiteral("connecting"), QStringLiteral("Negotiating local WebRTC session"));
    sendCommand({
        {QStringLiteral("type"), QStringLiteral("start-demo")},
        {QStringLiteral("quality"), quality},
    });
}

void StreamEngine::stop()
{
    sendCommand({{QStringLiteral("type"), QStringLiteral("stop")}});
}

void StreamEngine::setQuality(const QString &quality)
{
    m_pendingQuality = quality;
    sendCommand({
        {QStringLiteral("type"), QStringLiteral("set-quality")},
        {QStringLiteral("quality"), quality},
    });
}

void StreamEngine::setBitrate(int bitrateKbps)
{
    sendCommand({
        {QStringLiteral("type"), QStringLiteral("set-bitrate")},
        {QStringLiteral("bitrateKbps"), qBound(1000, bitrateKbps, 100000)},
    });
}

void StreamEngine::ping()
{
    sendCommand({{QStringLiteral("type"), QStringLiteral("ping")}});
}

void StreamEngine::setPhase(const QString &phase, const QString &status)
{
    if (m_phase != phase) {
        m_phase = phase;
        emit phaseChanged();
    }
    if (m_statusText != status) {
        m_statusText = status;
        emit statusTextChanged();
    }
}

void StreamEngine::processLine(const QByteArray &line)
{
    QJsonParseError error;
    const auto document = QJsonDocument::fromJson(line, &error);
    if (error.error != QJsonParseError::NoError || !document.isObject()) {
        return;
    }

    const auto event = document.object();
    const auto type = event.value(QStringLiteral("type")).toString();
    if (type == QStringLiteral("hello")) {
        setPhase(QStringLiteral("idle"), QStringLiteral("GStreamer WebRTC runtime ready"));
    } else if (type == QStringLiteral("state")) {
        setPhase(event.value(QStringLiteral("phase")).toString(),
                 event.value(QStringLiteral("message")).toString());
    } else if (type == QStringLiteral("stats")) {
        m_codec = event.value(QStringLiteral("codec")).toString(m_codec);
        m_resolution = event.value(QStringLiteral("resolution")).toString(m_resolution);
        m_fps = event.value(QStringLiteral("fps")).toInt(m_fps);
        m_bitrateKbps = event.value(QStringLiteral("bitrateKbps")).toInt(m_bitrateKbps);
        m_latencyMs = event.value(QStringLiteral("latencyMs")).toInt(m_latencyMs);
        m_packetLoss = event.value(QStringLiteral("packetLoss")).toDouble(m_packetLoss);
        emit statsChanged();
    } else if (type == QStringLiteral("error")) {
        setPhase(QStringLiteral("error"), event.value(QStringLiteral("message")).toString());
    }

    emit runtimeEvent(type, event);
}
