#pragma once

#include <QJsonObject>
#include <QObject>
#include <QProcess>
#include <QTimer>

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
    explicit StreamEngine(QObject *parent = nullptr);

    QString phase() const { return m_phase; }
    QString statusText() const { return m_statusText; }
    QString codec() const { return m_codec; }
    QString resolution() const { return m_resolution; }
    int fps() const { return m_fps; }
    int bitrateKbps() const { return m_bitrateKbps; }
    int latencyMs() const { return m_latencyMs; }
    double packetLoss() const { return m_packetLoss; }
    bool available() const { return m_available; }

    Q_INVOKABLE void startDemo(const QString &quality = QStringLiteral("720p60"));
    Q_INVOKABLE void stop();
    Q_INVOKABLE void setQuality(const QString &quality);
    Q_INVOKABLE void setBitrate(int bitrateKbps);
    Q_INVOKABLE void ping();

signals:
    void phaseChanged();
    void statusTextChanged();
    void statsChanged();
    void availableChanged();
    void runtimeEvent(const QString &type, const QJsonObject &payload);

private:
    void ensureStarted();
    void sendCommand(const QJsonObject &command);
    void processLine(const QByteArray &line);
    void setPhase(const QString &phase, const QString &status);
    QString runtimePath() const;

    QProcess m_process;
    QByteArray m_buffer;
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
