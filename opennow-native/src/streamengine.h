#pragma once

#include <QByteArray>
#include <QHash>
#include <QJsonObject>
#include <QObject>
#include <QProcess>
#include <QQueue>

struct NativeStreamerSessionContext final
{
    QJsonObject session;
    QJsonObject settings;
    QJsonObject shortcuts;
    QJsonObject nvstVideo;

    QJsonObject toJson() const;
};

class StreamEngine final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QString phase READ phase NOTIFY phaseChanged)
    Q_PROPERTY(QString statusText READ statusText NOTIFY statusTextChanged)
    Q_PROPERTY(QString codec READ codec NOTIFY statsChanged)
    Q_PROPERTY(QString resolution READ resolution NOTIFY statsChanged)
    Q_PROPERTY(int fps READ fps NOTIFY statsChanged)
    Q_PROPERTY(int bitrateKbps READ bitrateKbps NOTIFY statsChanged)
    Q_PROPERTY(int latencyMs READ latencyMs NOTIFY statsChanged)
    Q_PROPERTY(double packetLoss READ packetLoss NOTIFY statsChanged)
    Q_PROPERTY(bool available READ available NOTIFY availableChanged)

public:
    static constexpr int ProtocolVersion = 4;

    explicit StreamEngine(QObject *parent = nullptr);
    ~StreamEngine() override;

    QString phase() const { return m_phase; }
    QString statusText() const { return m_statusText; }
    QString codec() const { return m_codec; }
    QString resolution() const { return m_resolution; }
    int fps() const { return m_fps; }
    int bitrateKbps() const { return m_bitrateKbps; }
    int latencyMs() const { return m_latencyMs; }
    double packetLoss() const { return m_packetLoss; }
    bool available() const { return m_available; }

    QString startRemoteSession(const NativeStreamerSessionContext &context);
    Q_INVOKABLE QString startRemoteSession(const QJsonObject &context);
    Q_INVOKABLE QString handleOffer(const QString &sdp);
    Q_INVOKABLE QString handleOffer(const QString &sdp, const QJsonObject &context);
    Q_INVOKABLE QString addRemoteIce(const QJsonObject &candidate);
    Q_INVOKABLE QString sendInput(const QByteArray &payload, bool partiallyReliable = false);
    Q_INVOKABLE QString sendInputPacket(const QJsonObject &input);
    Q_INVOKABLE QString setInputPaused(bool paused);
    Q_INVOKABLE QString updateSurface(const QJsonObject &surface);
    Q_INVOKABLE QString setSurface(const QJsonObject &surface);
    Q_INVOKABLE QString updateShortcuts(const QJsonObject &shortcuts);
    Q_INVOKABLE QString stop(const QString &reason = QStringLiteral("stopped"));

    Q_INVOKABLE void startDemo(const QString &quality = QStringLiteral("720p60"));
    Q_INVOKABLE void setQuality(const QString &quality);
    Q_INVOKABLE QString setBitrate(int bitrateKbps);
    Q_INVOKABLE void ping();

signals:
    void phaseChanged();
    void statusTextChanged();
    void statsChanged();
    void availableChanged();
    void runtimeReady(const QJsonObject &capabilities);
    void answerReady(const QString &requestId, const QString &sdp, const QString &nvstSdp);
    void localIceCandidate(const QJsonObject &candidate);
    void streamStatus(const QString &status, const QString &message);
    void streamStats(const QJsonObject &stats);
    void shortcutTriggered(const QString &action);
    void inputReady(int protocolVersion);
    void clipboardPasteRequested();
    void inputCaptureChanged(bool captured);
    void requestSucceeded(const QString &requestId, const QString &commandType);
    void requestFailed(const QString &requestId, const QString &commandType,
                       const QString &code, const QString &message);
    void streamerError(const QString &code, const QString &message);
    void runtimeEvent(const QString &type, const QJsonObject &payload);

private:
    enum class RuntimeMode {
        None,
        Production,
        Demo,
    };

    bool ensureStarted(RuntimeMode mode);
    void startProcess(RuntimeMode mode, const QString &path);
    QString sendProtocolCommand(const QString &type, QJsonObject fields = {},
                                bool expectsResponse = true);
    void sendDemoCommand(const QJsonObject &command);
    void writeCommand(const QJsonObject &command);
    void flushQueuedCommands();
    void failPendingCommands(const QString &code, const QString &message);
    void processLine(const QByteArray &line);
    void processProtocolMessage(const QJsonObject &message);
    void processDemoMessage(const QJsonObject &message);
    void updateStats(const QJsonObject &stats);
    void setPhase(const QString &phase, const QString &status);
    void setAvailable(bool available);
    QString productionRuntimePath() const;
    QString demoRuntimePath() const;

    QProcess m_process;
    QByteArray m_buffer;
    QQueue<QJsonObject> m_queuedCommands;
    QHash<QString, QString> m_pendingCommands;
    QJsonObject m_sessionContext;
    RuntimeMode m_runtimeMode = RuntimeMode::None;
    bool m_protocolReady = false;
    bool m_ignoreNextFinished = false;
    QString m_helloRequestId;
    QString m_phase = QStringLiteral("idle");
    QString m_statusText = QStringLiteral("Native runtime ready");
    QString m_codec = QStringLiteral("VP8");
    QString m_resolution = QStringLiteral("1280 × 720");
    int m_fps = 60;
    int m_bitrateKbps = 0;
    int m_latencyMs = 0;
    double m_packetLoss = 0.0;
    bool m_available = false;
    QString m_pendingQuality = QStringLiteral("720p60");
};
