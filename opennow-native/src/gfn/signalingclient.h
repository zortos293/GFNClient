#pragma once

#include <QByteArray>
#include <QJsonObject>
#include <QList>
#include <QObject>
#include <QString>
#include <QTimer>
#include <QUrl>

class QSslSocket;

class SignalingClient final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(ConnectionState state READ state NOTIFY stateChanged)
    Q_PROPERTY(bool connected READ isConnected NOTIFY connectedChanged)

public:
    enum class ConnectionState {
        Disconnected,
        Connecting,
        Handshaking,
        Connected,
    };
    Q_ENUM(ConnectionState)

    explicit SignalingClient(QObject *parent = nullptr);
    SignalingClient(const QString &signalingServer,
                    const QString &sessionId,
                    const QUrl &signalingUrl = {},
                    QObject *parent = nullptr);
    ~SignalingClient() override;

    ConnectionState state() const { return m_state; }
    bool isConnected() const { return m_state == ConnectionState::Connected; }

    void configure(const QString &signalingServer,
                   const QString &sessionId,
                   const QUrl &signalingUrl = {});

    static bool isTrustedSignalingUrl(const QUrl &url, QString *error = nullptr);
    static QUrl buildSignInUrl(const QString &signalingServer,
                               const QString &sessionId,
                               const QUrl &signalingUrl = {},
                               QString *error = nullptr);
    static QString sessionProtocol(const QString &sessionId, QString *error = nullptr);

public slots:
    void connectToServer();
    void connectToServer(const QString &signalingServer,
                         const QString &sessionId,
                         const QUrl &signalingUrl = {});
    void disconnectFromServer();
    void sendAnswer(const QString &sdp, const QString &nvstSdp = {});
    void sendIceCandidate(const QJsonObject &candidate);
    void sendIceCandidate(const QString &candidate,
                          const QString &sdpMid = {},
                          int sdpMLineIndex = 0,
                          const QString &usernameFragment = {});
    void requestKeyframe(const QString &reason, int backlogFrames, int attempt);

signals:
    void stateChanged(SignalingClient::ConnectionState state);
    void connectedChanged();
    void connected();
    void joined(qint64 localPeerId);
    void disconnected(const QString &reason);
    void offerReceived(const QString &sdp);
    void remoteIceCandidateReceived(const QJsonObject &candidate);
    void sessionEventReceived(const QString &type, const QJsonObject &payload);
    void errorOccurred(const QString &message);
    void diagnosticMessage(const QString &message);

private:
    struct PendingMessage {
        quint8 opcode = 0x1;
        QByteArray payload;
    };

    void setState(ConnectionState state);
    void startSocket(const QUrl &url, const QString &protocol);
    void sendUpgradeRequest();
    void consumeSocketData();
    void consumeHandshake();
    void consumeFrames();
    void handleFrame(quint8 opcode, bool final, const QByteArray &payload);
    void handleTextMessage(const QByteArray &payload);
    void handleSignalingMessage(const QJsonObject &message);
    void handlePeerMessage(const QJsonObject &message);
    void sendPeerInfo();
    void sendPeerPayload(const QJsonObject &payload);
    bool sendJson(const QJsonObject &payload, bool queueIfConnecting = true);
    bool queueMessage(quint8 opcode, const QByteArray &payload);
    bool writeFrame(quint8 opcode, const QByteArray &payload);
    void flushPendingMessages();
    void sendClose(quint16 code, const QString &reason);
    void protocolFailure(quint16 code, const QString &reason);
    void failConnection(const QString &message, bool gracefulClose = false);
    void handleSocketClosed();
    void resetConnection(bool abortSocket, bool clearPending);
    bool isCurrentSocket(const QSslSocket *socket, quint64 generation) const;
    int nextAckId();

    QSslSocket *m_socket = nullptr;
    QTimer m_heartbeatTimer;
    QTimer m_connectTimer;
    QByteArray m_input;
    QByteArray m_handshakeKey;
    QByteArray m_fragment;
    QList<PendingMessage> m_pendingMessages;
    QString m_signalingServer;
    QString m_sessionId;
    QUrl m_signalingUrl;
    QUrl m_activeUrl;
    QString m_protocol;
    QString m_peerName;
    ConnectionState m_state = ConnectionState::Disconnected;
    quint64 m_generation = 0;
    qsizetype m_pendingBytes = 0;
    qint64 m_peerId = 0;
    qint64 m_remotePeerId = 1;
    int m_ackCounter = 0;
    quint8 m_fragmentOpcode = 0;
    bool m_closeSent = false;
    bool m_failureReported = false;
};
