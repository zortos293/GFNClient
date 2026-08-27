#include "streamengine.h"

#include <QCoreApplication>
#include <QDir>
#include <QFileInfo>
#include <QJsonDocument>
#include <QJsonValue>
#include <QProcessEnvironment>
#include <QSysInfo>
#include <QUuid>
#include <QtMath>

namespace {

QString executableName(const QString &baseName)
{
#ifdef Q_OS_WIN
    return baseName + QStringLiteral(".exe");
#else
    return baseName;
#endif
}

QString firstExecutable(const QStringList &candidates)
{
    for (const auto &candidate : candidates) {
        const QFileInfo info(candidate);
        if (info.isFile() && info.isExecutable()) {
            return info.absoluteFilePath();
        }
    }
    return {};
}

} // namespace

QJsonObject NativeStreamerSessionContext::toJson() const
{
    QJsonObject context{
        {QStringLiteral("session"), session},
        {QStringLiteral("settings"), settings},
        {QStringLiteral("shortcuts"), shortcuts},
    };
    if (!nvstVideo.isEmpty()) {
        context.insert(QStringLiteral("nvstVideo"), nvstVideo);
    }
    return context;
}

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

    connect(&m_process, &QProcess::readyReadStandardError, this, [this] {
        m_process.readAllStandardError();
    });

    connect(&m_process, &QProcess::started, this, [this] {
        if (m_runtimeMode == RuntimeMode::Production) {
            m_helloRequestId = QUuid::createUuid().toString(QUuid::WithoutBraces);
            m_pendingCommands.insert(m_helloRequestId, QStringLiteral("hello"));
            writeCommand({
                {QStringLiteral("id"), m_helloRequestId},
                {QStringLiteral("type"), QStringLiteral("hello")},
                {QStringLiteral("protocolVersion"), ProtocolVersion},
            });
            return;
        }

        if (m_runtimeMode == RuntimeMode::Demo) {
            setAvailable(true);
            writeCommand({{QStringLiteral("type"), QStringLiteral("hello")}});
            flushQueuedCommands();
        }
    });

    connect(&m_process, &QProcess::errorOccurred, this, [this](QProcess::ProcessError) {
        const auto message = m_runtimeMode == RuntimeMode::Production
            ? QStringLiteral("Native streamer could not start")
            : QStringLiteral("Native WebRTC demo could not start");
        setPhase(QStringLiteral("error"), message);
        setAvailable(false);
        failPendingCommands(QStringLiteral("process-start-failed"), message);
        emit streamerError(QStringLiteral("process-start-failed"), message);
    });

    connect(&m_process, qOverload<int, QProcess::ExitStatus>(&QProcess::finished), this,
            [this](int, QProcess::ExitStatus) {
                if (m_ignoreNextFinished) {
                    m_ignoreNextFinished = false;
                    return;
                }

                m_protocolReady = false;
                m_sessionContext = {};
                setAvailable(false);
                failPendingCommands(QStringLiteral("process-ended"),
                                    QStringLiteral("Native streamer process ended"));
                if (m_phase != QStringLiteral("idle")) {
                    setPhase(QStringLiteral("idle"), QStringLiteral("Native runtime stopped"));
                }
            });
}

QString StreamEngine::productionRuntimePath() const
{
    const auto executableDir = QCoreApplication::applicationDirPath();
    const auto name = executableName(QStringLiteral("opennow-streamer"));
#ifdef Q_OS_WIN
    const auto platform = QStringLiteral("win32");
#elif defined(Q_OS_MACOS)
    const auto platform = QStringLiteral("darwin");
#else
    const auto platform = QStringLiteral("linux");
#endif
    auto architecture = QSysInfo::currentCpuArchitecture();
    if (architecture == QStringLiteral("x86_64")) {
        architecture = QStringLiteral("x64");
    } else if (architecture == QStringLiteral("aarch64")) {
        architecture = QStringLiteral("arm64");
    }
    const auto platformKey = platform + QLatin1Char('-') + architecture;
    const QStringList roots = {
        QDir(executableDir).filePath(QStringLiteral("native/opennow-streamer")),
        QDir(executableDir).filePath(QStringLiteral("../native/opennow-streamer")),
        QDir(executableDir).filePath(QStringLiteral("../../native/opennow-streamer")),
        QDir::current().filePath(QStringLiteral("native/opennow-streamer")),
        QDir::current().filePath(QStringLiteral("../native/opennow-streamer")),
    };

    QStringList candidates{
        QDir(executableDir).filePath(name),
        QDir(executableDir).filePath(QStringLiteral("native/opennow-streamer/") + name),
    };
    for (const auto &root : roots) {
        const QDir directory(root);
        candidates.append(directory.filePath(platformKey + QLatin1Char('/') + name));
        candidates.append(directory.filePath(QStringLiteral("bin/") + platformKey + QLatin1Char('/') + name));
        candidates.append(directory.filePath(QStringLiteral("bin/") + name));
        candidates.append(directory.filePath(QStringLiteral("dist/") + platformKey + QLatin1Char('/') + name));
        candidates.append(directory.filePath(QStringLiteral("dist/") + name));
        candidates.append(directory.filePath(QStringLiteral("target/release/") + name));
        candidates.append(directory.filePath(QStringLiteral("target/debug/") + name));
    }
    return firstExecutable(candidates);
}

QString StreamEngine::demoRuntimePath() const
{
    const auto executableDir = QCoreApplication::applicationDirPath();
    const auto name = executableName(QStringLiteral("opennow-webrtc-demo"));
    return firstExecutable({
        QDir(executableDir).filePath(name),
        QDir(executableDir).filePath(QStringLiteral("../streamer/target/debug/") + name),
        QDir(executableDir).filePath(QStringLiteral("../../streamer/target/debug/") + name),
        QDir::current().filePath(QStringLiteral("streamer/target/debug/") + name),
        QDir::current().filePath(QStringLiteral("opennow-native/streamer/target/debug/") + name),
    });
}

StreamEngine::~StreamEngine()
{
    // waitForFinished() runs a nested Qt event loop. Disconnect first so a
    // late QProcess error/finished signal cannot re-enter failPendingCommands
    // while the application and this engine are being torn down.
    QObject::disconnect(&m_process, nullptr, this, nullptr);
    m_pendingCommands.clear();
    m_queuedCommands.clear();
    m_helloRequestId.clear();
    if (m_process.state() == QProcess::NotRunning) {
        return;
    }
    m_process.closeWriteChannel();
    m_process.terminate();
    if (!m_process.waitForFinished(1000)) {
        m_process.kill();
        m_process.waitForFinished(1000);
    }
}

bool StreamEngine::ensureStarted(RuntimeMode mode)
{
    if (m_process.state() != QProcess::NotRunning && m_runtimeMode == mode) {
        return true;
    }

    if (m_process.state() != QProcess::NotRunning) {
        failPendingCommands(QStringLiteral("runtime-switched"),
                            QStringLiteral("Native runtime mode changed"));
        m_ignoreNextFinished = true;
        m_process.kill();
        m_process.waitForFinished(1500);
    }

    m_buffer.clear();
    m_protocolReady = false;
    setAvailable(false);
    m_runtimeMode = mode;
    const auto path = mode == RuntimeMode::Production ? productionRuntimePath() : demoRuntimePath();
    if (path.isEmpty()) {
        const auto message = mode == RuntimeMode::Production
            ? QStringLiteral("native/opennow-streamer was not found")
            : QStringLiteral("The local WebRTC demo helper was not found");
        setPhase(QStringLiteral("error"), message);
        emit streamerError(QStringLiteral("runtime-not-found"), message);
        return false;
    }
    startProcess(mode, path);
    return true;
}

void StreamEngine::startProcess(RuntimeMode mode, const QString &path)
{
    m_runtimeMode = mode;
    auto environment = QProcessEnvironment::systemEnvironment();
    if (mode == RuntimeMode::Production) {
        environment.insert(QStringLiteral("OPENNOW_NATIVE_STREAMER_PROTOCOL"),
                           QString::number(ProtocolVersion));
    }
    m_process.setProcessEnvironment(environment);
    m_process.setProgram(path);
    m_process.setArguments({});
    m_process.start();
}

QString StreamEngine::sendProtocolCommand(const QString &type, QJsonObject fields,
                                          bool expectsResponse)
{
    const auto id = QUuid::createUuid().toString(QUuid::WithoutBraces);
    if (!ensureStarted(RuntimeMode::Production)) {
        emit requestFailed(id, type, QStringLiteral("runtime-not-found"),
                           QStringLiteral("native/opennow-streamer was not found"));
        return id;
    }

    fields.insert(QStringLiteral("id"), id);
    fields.insert(QStringLiteral("type"), type);
    if (expectsResponse) {
        m_pendingCommands.insert(id, type);
    }

    if (m_protocolReady && m_process.state() == QProcess::Running) {
        writeCommand(fields);
    } else {
        m_queuedCommands.enqueue(fields);
    }
    return id;
}

void StreamEngine::sendDemoCommand(const QJsonObject &command)
{
    if (!ensureStarted(RuntimeMode::Demo)) {
        return;
    }
    if (m_process.state() == QProcess::Running) {
        writeCommand(command);
    } else {
        m_queuedCommands.enqueue(command);
    }
}

void StreamEngine::writeCommand(const QJsonObject &command)
{
    if (m_process.state() != QProcess::Running) {
        return;
    }
    m_process.write(QJsonDocument(command).toJson(QJsonDocument::Compact));
    m_process.write("\n");
}

void StreamEngine::flushQueuedCommands()
{
    while (!m_queuedCommands.isEmpty()) {
        writeCommand(m_queuedCommands.dequeue());
    }
}

void StreamEngine::failPendingCommands(const QString &code, const QString &message)
{
    const auto pending = m_pendingCommands;
    m_pendingCommands.clear();
    m_queuedCommands.clear();
    m_helloRequestId.clear();
    for (auto it = pending.cbegin(); it != pending.cend(); ++it) {
        emit requestFailed(it.key(), it.value(), code, message);
    }
}

QString StreamEngine::startRemoteSession(const NativeStreamerSessionContext &context)
{
    return startRemoteSession(context.toJson());
}

QString StreamEngine::startRemoteSession(const QJsonObject &context)
{
    if (!context.value(QStringLiteral("session")).isObject()
        || !context.value(QStringLiteral("settings")).isObject()) {
        const auto id = QUuid::createUuid().toString(QUuid::WithoutBraces);
        const auto message = QStringLiteral("Session context requires session and settings objects");
        emit requestFailed(id, QStringLiteral("start"), QStringLiteral("invalid-context"), message);
        emit streamerError(QStringLiteral("invalid-context"), message);
        return id;
    }

    m_sessionContext = context;
    setPhase(QStringLiteral("connecting"), QStringLiteral("Preparing native remote session"));
    return sendProtocolCommand(QStringLiteral("start"), {
        {QStringLiteral("context"), context},
    });
}

QString StreamEngine::handleOffer(const QString &sdp)
{
    return handleOffer(sdp, m_sessionContext);
}

QString StreamEngine::handleOffer(const QString &sdp, const QJsonObject &context)
{
    if (sdp.isEmpty() || context.isEmpty()) {
        const auto id = QUuid::createUuid().toString(QUuid::WithoutBraces);
        const auto message = QStringLiteral("An SDP offer and session context are required");
        emit requestFailed(id, QStringLiteral("offer"), QStringLiteral("invalid-offer"), message);
        emit streamerError(QStringLiteral("invalid-offer"), message);
        return id;
    }

    m_sessionContext = context;
    setPhase(QStringLiteral("connecting"), QStringLiteral("Negotiating native stream"));
    return sendProtocolCommand(QStringLiteral("offer"), {
        {QStringLiteral("sdp"), sdp},
        {QStringLiteral("context"), context},
    });
}

QString StreamEngine::addRemoteIce(const QJsonObject &candidate)
{
    return sendProtocolCommand(QStringLiteral("remote-ice"), {
        {QStringLiteral("candidate"), candidate},
    });
}

QString StreamEngine::sendInput(const QByteArray &payload, bool partiallyReliable)
{
    return sendInputPacket({
        {QStringLiteral("payloadBase64"), QString::fromLatin1(payload.toBase64())},
        {QStringLiteral("partiallyReliable"), partiallyReliable},
    });
}

QString StreamEngine::sendInputPacket(const QJsonObject &input)
{
    return sendProtocolCommand(QStringLiteral("input"), {
        {QStringLiteral("input"), input},
    }, false);
}

QString StreamEngine::setInputPaused(bool paused)
{
    return sendProtocolCommand(QStringLiteral("input-paused"), {
        {QStringLiteral("paused"), paused},
    });
}

QString StreamEngine::setSurface(const QJsonObject &surface)
{
    return updateSurface(surface);
}

QString StreamEngine::updateSurface(const QJsonObject &surface)
{
    return sendProtocolCommand(QStringLiteral("surface"), {
        {QStringLiteral("surface"), surface},
    });
}

QString StreamEngine::updateShortcuts(const QJsonObject &shortcuts)
{
    return sendProtocolCommand(QStringLiteral("update-shortcuts"), {
        {QStringLiteral("shortcuts"), shortcuts},
    });
}

QString StreamEngine::stop(const QString &reason)
{
    if (m_runtimeMode == RuntimeMode::Demo) {
        sendDemoCommand({{QStringLiteral("type"), QStringLiteral("stop")}});
        return {};
    }
    if (m_process.state() == QProcess::NotRunning && m_pendingCommands.isEmpty()) {
        setPhase(QStringLiteral("idle"), QStringLiteral("Native runtime stopped"));
        return {};
    }
    return sendProtocolCommand(QStringLiteral("stop"), {
        {QStringLiteral("reason"), reason},
    });
}

void StreamEngine::startDemo(const QString &quality)
{
    m_pendingQuality = quality;
    setPhase(QStringLiteral("connecting"), QStringLiteral("Negotiating local WebRTC demo"));
    sendDemoCommand({
        {QStringLiteral("type"), QStringLiteral("start-demo")},
        {QStringLiteral("quality"), quality},
    });
}

void StreamEngine::setQuality(const QString &quality)
{
    m_pendingQuality = quality;
    if (m_runtimeMode != RuntimeMode::Demo) {
        return;
    }
    sendDemoCommand({
        {QStringLiteral("type"), QStringLiteral("set-quality")},
        {QStringLiteral("quality"), quality},
    });
}

QString StreamEngine::setBitrate(int bitrateKbps)
{
    const auto normalized = qBound(5000, bitrateKbps, 150000);
    if (m_runtimeMode == RuntimeMode::Demo) {
        sendDemoCommand({
            {QStringLiteral("type"), QStringLiteral("set-bitrate")},
            {QStringLiteral("bitrateKbps"), normalized},
        });
        return {};
    }
    return sendProtocolCommand(QStringLiteral("bitrate"), {
        {QStringLiteral("maxBitrateKbps"), normalized},
    });
}

void StreamEngine::ping()
{
    if (m_runtimeMode == RuntimeMode::Demo) {
        sendDemoCommand({{QStringLiteral("type"), QStringLiteral("ping")}});
    }
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

void StreamEngine::setAvailable(bool available)
{
    if (m_available == available) {
        return;
    }
    m_available = available;
    emit availableChanged();
}

void StreamEngine::processLine(const QByteArray &line)
{
    QJsonParseError error;
    const auto document = QJsonDocument::fromJson(line, &error);
    if (error.error != QJsonParseError::NoError || !document.isObject()) {
        return;
    }

    const auto message = document.object();
    if (m_runtimeMode == RuntimeMode::Production) {
        processProtocolMessage(message);
    } else {
        processDemoMessage(message);
    }
}

void StreamEngine::processProtocolMessage(const QJsonObject &message)
{
    const auto type = message.value(QStringLiteral("type")).toString();
    const auto id = message.value(QStringLiteral("id")).toString();
    const auto commandType = id.isEmpty() ? QString() : m_pendingCommands.take(id);
    const auto isResponse = type == QStringLiteral("ready") || type == QStringLiteral("ok")
        || type == QStringLiteral("answer")
        || (type == QStringLiteral("error") && !id.isEmpty());
    if (isResponse && commandType.isEmpty()) {
        emit runtimeEvent(type, message);
        return;
    }

    if (type == QStringLiteral("ready")) {
        const auto capabilities = message.value(QStringLiteral("capabilities")).toObject();
        const auto version = capabilities.value(QStringLiteral("protocolVersion")).toInt();
        if (version != ProtocolVersion) {
            const auto detail = QStringLiteral("Native streamer protocol version mismatch");
            emit requestFailed(id, QStringLiteral("hello"),
                               QStringLiteral("protocol-version-mismatch"), detail);
            emit streamerError(QStringLiteral("protocol-version-mismatch"), detail);
            setPhase(QStringLiteral("error"), detail);
            m_process.kill();
            return;
        }
        m_helloRequestId.clear();
        m_protocolReady = true;
        setAvailable(true);
        setPhase(QStringLiteral("idle"), QStringLiteral("Native streamer protocol v4 ready"));
        emit requestSucceeded(id, QStringLiteral("hello"));
        emit runtimeReady(capabilities);
        flushQueuedCommands();
    } else if (type == QStringLiteral("ok")) {
        emit requestSucceeded(id, commandType);
        if (commandType == QStringLiteral("start")) {
            setPhase(QStringLiteral("ready"), QStringLiteral("Native remote session ready"));
        } else if (commandType == QStringLiteral("stop")) {
            setPhase(QStringLiteral("idle"), QStringLiteral("Native stream stopped"));
            m_sessionContext = {};
        }
    } else if (type == QStringLiteral("answer")) {
        const auto answer = message.value(QStringLiteral("answer")).toObject();
        emit requestSucceeded(id, commandType);
        emit answerReady(id, answer.value(QStringLiteral("sdp")).toString(),
                         answer.value(QStringLiteral("nvstSdp")).toString());
    } else if (type == QStringLiteral("local-ice")) {
        emit localIceCandidate(message.value(QStringLiteral("candidate")).toObject());
    } else if (type == QStringLiteral("status")) {
        const auto status = message.value(QStringLiteral("status")).toString();
        const auto detail = message.value(QStringLiteral("message")).toString();
        const auto displayMessage = detail.isEmpty() ? status : detail;
        if (status == QStringLiteral("starting")) {
            setPhase(QStringLiteral("connecting"), displayMessage);
        } else if (status == QStringLiteral("ready")) {
            setPhase(QStringLiteral("ready"), displayMessage);
        } else if (status == QStringLiteral("streaming")) {
            setPhase(QStringLiteral("streaming"), displayMessage);
        } else if (status == QStringLiteral("stopped")) {
            setPhase(QStringLiteral("idle"), displayMessage);
        }
        emit streamStatus(status, detail);
    } else if (type == QStringLiteral("stats")) {
        const auto stats = message.value(QStringLiteral("stats")).toObject();
        updateStats(stats);
        emit streamStats(stats);
    } else if (type == QStringLiteral("shortcut")) {
        emit shortcutTriggered(message.value(QStringLiteral("action")).toString());
    } else if (type == QStringLiteral("input-ready")) {
        emit inputReady(message.value(QStringLiteral("protocolVersion")).toInt());
    } else if (type == QStringLiteral("clipboard-paste")) {
        emit clipboardPasteRequested();
    } else if (type == QStringLiteral("input-capture-changed")) {
        emit inputCaptureChanged(message.value(QStringLiteral("captured")).toBool());
    } else if (type == QStringLiteral("error")) {
        const auto code = message.value(QStringLiteral("code")).toString();
        const auto detail = message.value(QStringLiteral("message")).toString();
        if (!id.isEmpty()) {
            emit requestFailed(id, commandType, code, detail);
        }
        emit streamerError(code, detail);
        if (commandType == QStringLiteral("hello") || commandType == QStringLiteral("start")
            || commandType == QStringLiteral("offer") || id.isEmpty()) {
            setPhase(QStringLiteral("error"), detail);
        }
    }

    emit runtimeEvent(type, message);
}

void StreamEngine::processDemoMessage(const QJsonObject &message)
{
    const auto type = message.value(QStringLiteral("type")).toString();
    if (type == QStringLiteral("hello")) {
        setPhase(QStringLiteral("idle"), QStringLiteral("GStreamer WebRTC demo ready"));
    } else if (type == QStringLiteral("state")) {
        setPhase(message.value(QStringLiteral("phase")).toString(),
                 message.value(QStringLiteral("message")).toString());
    } else if (type == QStringLiteral("stats")) {
        updateStats(message);
        emit streamStats(message);
    } else if (type == QStringLiteral("error")) {
        const auto code = message.value(QStringLiteral("code")).toString();
        const auto detail = message.value(QStringLiteral("message")).toString();
        setPhase(QStringLiteral("error"), detail);
        emit streamerError(code, detail);
    }
    emit runtimeEvent(type, message);
}

void StreamEngine::updateStats(const QJsonObject &stats)
{
    m_codec = stats.value(QStringLiteral("codec")).toString(m_codec);
    m_resolution = stats.value(QStringLiteral("resolution")).toString(m_resolution);
    if (stats.contains(QStringLiteral("renderFps"))) {
        m_fps = qRound(stats.value(QStringLiteral("renderFps")).toDouble(m_fps));
    } else if (stats.contains(QStringLiteral("decodedFps"))) {
        m_fps = qRound(stats.value(QStringLiteral("decodedFps")).toDouble(m_fps));
    } else {
        m_fps = stats.value(QStringLiteral("fps")).toInt(m_fps);
    }
    m_bitrateKbps = stats.value(QStringLiteral("bitrateKbps")).toInt(m_bitrateKbps);
    m_latencyMs = stats.value(QStringLiteral("latencyMs")).toInt(m_latencyMs);
    m_packetLoss = stats.value(QStringLiteral("packetLoss")).toDouble(m_packetLoss);
    emit statsChanged();
}
