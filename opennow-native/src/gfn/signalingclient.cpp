#include "signalingclient.h"

#include <QCryptographicHash>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonParseError>
#include <QJsonValue>
#include <QHash>
#include <QHostAddress>
#include <QRandomGenerator>
#include <QSslConfiguration>
#include <QSslError>
#include <QSslSocket>
#include <QStringDecoder>
#include <QUrlQuery>

#include <limits>
#include <utility>

namespace {
constexpr qsizetype MaxHandshakeBytes = 16 * 1024;
constexpr qsizetype MaxFrameBytes = 1024 * 1024;
constexpr qsizetype MaxMessageBytes = 2 * 1024 * 1024;
constexpr qsizetype MaxPendingBytes = 2 * 1024 * 1024;
constexpr qsizetype MaxSocketWriteBytes = 2 * 1024 * 1024;
constexpr int MaxPendingMessages = 128;
constexpr int ConnectTimeoutMs = 15000;
constexpr int HeartbeatIntervalMs = 5000;
constexpr auto WebSocketGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
constexpr auto PlayOrigin = "https://play.geforcenow.com";
constexpr auto UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                           "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 "
                           "Safari/537.36 NVIDIACEFClient/HEAD/debb5919f6 GFN-PC/2.0.80.173";

void setError(QString *error, const QString &message)
{
    if (error) {
        *error = message;
    }
}

bool containsHttpToken(const QByteArray &value, const QByteArray &token)
{
    const auto parts = value.split(',');
    for (const auto &part : parts) {
        if (part.trimmed().compare(token, Qt::CaseInsensitive) == 0) {
            return true;
        }
    }
    return false;
}

QByteArray randomBytes(qsizetype size)
{
    QByteArray bytes(size, Qt::Uninitialized);
    auto *generator = QRandomGenerator::system();
    for (qsizetype offset = 0; offset < size; offset += 4) {
        const quint32 value = generator->generate();
        const auto count = qMin<qsizetype>(4, size - offset);
        for (qsizetype index = 0; index < count; ++index) {
            bytes[offset + index] = static_cast<char>((value >> (index * 8)) & 0xff);
        }
    }
    return bytes;
}

QString safeCloseReason(const QByteArray &payload)
{
    if (payload.size() <= 2) {
        return QStringLiteral("socket closed");
    }

    QStringDecoder decoder(QStringDecoder::Utf8);
    const QString decoded = decoder.decode(QByteArrayView(payload).sliced(2));
    if (decoder.hasError()) {
        return QStringLiteral("socket closed");
    }
    QString reason;
    reason.reserve(qMin(decoded.size(), 120));
    for (const auto character : decoded) {
        if (character.category() != QChar::Other_Control) {
            reason.append(character);
        }
        if (reason.size() == 120) {
            break;
        }
    }
    return reason.left(120).trimmed().isEmpty() ? QStringLiteral("socket closed")
                                                : reason.left(120).trimmed();
}

bool isValidCloseCode(quint16 code)
{
    const bool protocolCode = code >= 1000 && code <= 1014 && code != 1004 && code != 1005
                              && code != 1006;
    return protocolCode || (code >= 3000 && code < 5000);
}

bool isTcpCandidate(const QString &candidate)
{
    const auto parts = candidate.trimmed().split(' ', Qt::SkipEmptyParts);
    return parts.size() > 2 && parts.at(2).compare(QStringLiteral("tcp"), Qt::CaseInsensitive) == 0;
}

bool isTrustedSessionSignalingUrl(const QUrl &url, const QString &signalingServer, QString *error)
{
    if (SignalingClient::isTrustedSignalingUrl(url, nullptr)) {
        return true;
    }
    if (!url.isValid() || url.scheme().compare(QStringLiteral("wss"), Qt::CaseInsensitive) != 0
        || !url.userName().isEmpty() || !url.password().isEmpty() || url.hasFragment()
        || url.port(443) != 443) {
        setError(error, QStringLiteral("Signaling endpoint must be a credential-free wss URL on port 443"));
        return false;
    }
    auto expected = signalingServer.trimmed();
    if (!expected.contains(QStringLiteral("://"))) {
        expected.prepend(QStringLiteral("wss://"));
    }
    const auto expectedHost = QUrl(expected).host(QUrl::FullyDecoded).toLower();
    const auto actualHost = url.host(QUrl::FullyDecoded).toLower();
    QHostAddress address;
    if (actualHost.isEmpty() || actualHost != expectedHost || !address.setAddress(actualHost)) {
        setError(error, QStringLiteral("Signaling endpoint is not the CloudMatch-provided NVIDIA host"));
        return false;
    }
    return true;
}
}

SignalingClient::SignalingClient(QObject *parent)
    : QObject(parent)
{
    m_heartbeatTimer.setInterval(HeartbeatIntervalMs);
    connect(&m_heartbeatTimer, &QTimer::timeout, this, [this] {
        if (m_state == ConnectionState::Connected) {
            sendJson({{QStringLiteral("hb"), 1}}, false);
        }
    });

    m_connectTimer.setSingleShot(true);
    connect(&m_connectTimer, &QTimer::timeout, this, [this] {
        if (m_state == ConnectionState::Connecting || m_state == ConnectionState::Handshaking) {
            failConnection(QStringLiteral("Signaling connection timed out"));
        }
    });

    const quint64 peerSuffix = (static_cast<quint64>(QRandomGenerator::system()->generate()) << 32)
                               | QRandomGenerator::system()->generate();
    m_peerName = QStringLiteral("peer-%1").arg(peerSuffix % 10000000000ULL);
}

SignalingClient::SignalingClient(const QString &signalingServer,
                                 const QString &sessionId,
                                 const QUrl &signalingUrl,
                                 QObject *parent)
    : SignalingClient(parent)
{
    configure(signalingServer, sessionId, signalingUrl);
}

SignalingClient::~SignalingClient()
{
    ++m_generation;
    resetConnection(true, true);
}

void SignalingClient::configure(const QString &signalingServer,
                                const QString &sessionId,
                                const QUrl &signalingUrl)
{
    if (m_state != ConnectionState::Disconnected) {
        disconnectFromServer();
    }
    m_signalingServer = signalingServer.trimmed();
    m_sessionId = sessionId;
    m_signalingUrl = signalingUrl;
}

bool SignalingClient::isTrustedSignalingUrl(const QUrl &url, QString *error)
{
    if (!url.isValid() || url.scheme().compare(QStringLiteral("wss"), Qt::CaseInsensitive) != 0) {
        setError(error, QStringLiteral("Signaling endpoint must be a valid wss URL"));
        return false;
    }
    if (!url.userName().isEmpty() || !url.password().isEmpty() || url.hasFragment()) {
        setError(error, QStringLiteral("Signaling endpoint must not contain credentials or a fragment"));
        return false;
    }

    const auto host = url.host(QUrl::FullyDecoded).toLower();
    if (host.isEmpty() || (host != QStringLiteral("nvidiagrid.net")
                           && !host.endsWith(QStringLiteral(".nvidiagrid.net")))) {
        setError(error, QStringLiteral("Signaling endpoint is not a trusted NVIDIA Grid host"));
        return false;
    }
    if (url.port(443) != 443) {
        setError(error, QStringLiteral("Signaling endpoint must use the standard TLS port"));
        return false;
    }
    return true;
}

QString SignalingClient::sessionProtocol(const QString &sessionId, QString *error)
{
    if (sessionId.isEmpty() || sessionId.size() > 256) {
        setError(error, QStringLiteral("Invalid signaling session identifier"));
        return {};
    }

    for (const auto character : sessionId) {
        const ushort value = character.unicode();
        const bool alphaNumeric = (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                                  || (value >= '0' && value <= '9');
        const bool tokenPunctuation = QByteArray("!#$%&'*+-.^_`|~").contains(static_cast<char>(value));
        if (value > 0x7f || (!alphaNumeric && !tokenPunctuation)) {
            setError(error, QStringLiteral("Invalid signaling session identifier"));
            return {};
        }
    }
    return QStringLiteral("x-nv-sessionid.%1").arg(sessionId);
}

QUrl SignalingClient::buildSignInUrl(const QString &signalingServer,
                                     const QString &sessionId,
                                     const QUrl &signalingUrl,
                                     QString *error)
{
    if (sessionProtocol(sessionId, error).isEmpty()) {
        return {};
    }

    QUrl url = signalingUrl;
    if (url.isEmpty()) {
        auto server = signalingServer.trimmed();
        if (server.isEmpty()) {
            setError(error, QStringLiteral("Missing signaling endpoint"));
            return {};
        }
        if (!server.contains(QStringLiteral("://"))) {
            server.prepend(QStringLiteral("wss://"));
        }
        url = QUrl(server);
        if (url.path().isEmpty()) {
            url.setPath(QStringLiteral("/nvst/"));
        }
    }
    if (!isTrustedSessionSignalingUrl(url, signalingServer, error)) {
        return {};
    }

    url.setFragment({});
    url.setPort(url.port(443) == 443 ? -1 : url.port());
    auto path = url.path();
    if (path.isEmpty()) {
        path = QStringLiteral("/");
    }
    if (!path.endsWith('/')) {
        path.append('/');
    }
    url.setPath(path + QStringLiteral("sign_in"));

    QUrlQuery query;
    query.addQueryItem(QStringLiteral("peer_id"), QStringLiteral("peer-0"));
    query.addQueryItem(QStringLiteral("version"), QStringLiteral("2"));
    query.addQueryItem(QStringLiteral("peer_role"), QStringLiteral("1"));
    query.addQueryItem(QStringLiteral("pairing_id"), sessionId);
    url.setQuery(query);

    if (!isTrustedSessionSignalingUrl(url, signalingServer, error)) {
        return {};
    }
    return url;
}

void SignalingClient::connectToServer(const QString &signalingServer,
                                      const QString &sessionId,
                                      const QUrl &signalingUrl)
{
    configure(signalingServer, sessionId, signalingUrl);
    connectToServer();
}

void SignalingClient::connectToServer()
{
    if (m_state != ConnectionState::Disconnected) {
        return;
    }

    QString error;
    auto url = buildSignInUrl(m_signalingServer, m_sessionId, m_signalingUrl, &error);
    const auto protocol = sessionProtocol(m_sessionId, &error);
    if (!url.isValid() || protocol.isEmpty()) {
        emit errorOccurred(error.isEmpty() ? QStringLiteral("Invalid signaling configuration") : error);
        return;
    }

    QUrlQuery query(url);
    query.removeAllQueryItems(QStringLiteral("peer_id"));
    query.addQueryItem(QStringLiteral("peer_id"), m_peerName);
    url.setQuery(query);
    startSocket(url, protocol);
}

void SignalingClient::startSocket(const QUrl &url, const QString &protocol)
{
    ++m_generation;
    resetConnection(true, false);

    const auto generation = m_generation;
    auto *socket = new QSslSocket(this);
    m_socket = socket;
    m_activeUrl = url;
    m_protocol = protocol;
    m_failureReported = false;
    m_closeSent = false;
    m_peerId = 0;
    m_remotePeerId = 1;
    m_ackCounter = 0;
    m_input.clear();
    m_fragment.clear();
    m_fragmentOpcode = 0;

    auto configuration = socket->sslConfiguration();
    configuration.setProtocol(QSsl::TlsV1_2OrLater);
    socket->setSslConfiguration(configuration);
    socket->setPeerVerifyMode(QSslSocket::VerifyPeer);
    socket->setPeerVerifyName(url.host());
    socket->setReadBufferSize(MaxMessageBytes + MaxHandshakeBytes);

    connect(socket, &QSslSocket::encrypted, this, [this, socket, generation] {
        if (!isCurrentSocket(socket, generation)) {
            return;
        }
        setState(ConnectionState::Handshaking);
        sendUpgradeRequest();
    });
    connect(socket, &QSslSocket::readyRead, this, [this, socket, generation] {
        if (isCurrentSocket(socket, generation)) {
            consumeSocketData();
        }
    });
    connect(socket, &QSslSocket::bytesWritten, this, [this, socket, generation](qint64) {
        if (isCurrentSocket(socket, generation)) {
            flushPendingMessages();
        }
    });
    connect(socket, &QSslSocket::disconnected, this, [this, socket, generation] {
        if (isCurrentSocket(socket, generation)) {
            handleSocketClosed();
        }
    });
    connect(socket, &QSslSocket::errorOccurred, this,
            [this, socket, generation](QAbstractSocket::SocketError) {
                if (isCurrentSocket(socket, generation) && m_state != ConnectionState::Disconnected) {
                    failConnection(QStringLiteral("Signaling transport failed: %1").arg(socket->errorString()));
                }
            });
    connect(socket, &QSslSocket::sslErrors, this,
            [this, socket, generation](const QList<QSslError> &) {
                if (isCurrentSocket(socket, generation)) {
                    failConnection(QStringLiteral("Signaling TLS certificate validation failed"));
                }
            });

    setState(ConnectionState::Connecting);
    m_connectTimer.start(ConnectTimeoutMs);
    socket->connectToHostEncrypted(url.host(), static_cast<quint16>(url.port(443)));
}

void SignalingClient::sendUpgradeRequest()
{
    if (!m_socket || m_state != ConnectionState::Handshaking) {
        return;
    }

    m_handshakeKey = randomBytes(16).toBase64();
    QByteArray target = m_activeUrl.path(QUrl::FullyEncoded).toUtf8();
    if (target.isEmpty()) {
        target = "/";
    }
    const auto query = m_activeUrl.query(QUrl::FullyEncoded).toUtf8();
    if (!query.isEmpty()) {
        target += '?' + query;
    }

    QByteArray host = m_activeUrl.host(QUrl::FullyEncoded).toUtf8();
    if (m_activeUrl.port(443) != 443) {
        host += ':' + QByteArray::number(m_activeUrl.port());
    }

    QByteArray request;
    request.reserve(1024 + target.size() + m_protocol.size());
    request += "GET " + target + " HTTP/1.1\r\n";
    request += "Host: " + host + "\r\n";
    request += "Upgrade: websocket\r\n";
    request += "Connection: Upgrade\r\n";
    request += "Sec-WebSocket-Key: " + m_handshakeKey + "\r\n";
    request += "Sec-WebSocket-Version: 13\r\n";
    request += "Sec-WebSocket-Protocol: " + m_protocol.toLatin1() + "\r\n";
    request += "Origin: " + QByteArray(PlayOrigin) + "\r\n";
    request += "User-Agent: " + QByteArray(UserAgent) + "\r\n\r\n";
    m_socket->write(request);
}

void SignalingClient::consumeSocketData()
{
    if (!m_socket) {
        return;
    }
    m_input += m_socket->readAll();
    if (m_input.size() > MaxMessageBytes + MaxHandshakeBytes) {
        protocolFailure(1009, QStringLiteral("Signaling packet exceeded the receive limit"));
        return;
    }

    if (m_state == ConnectionState::Handshaking) {
        consumeHandshake();
    }
    if (m_state == ConnectionState::Connected) {
        consumeFrames();
    }
}

void SignalingClient::consumeHandshake()
{
    const auto end = m_input.indexOf("\r\n\r\n");
    if (end < 0) {
        if (m_input.size() > MaxHandshakeBytes) {
            failConnection(QStringLiteral("Signaling handshake headers were too large"));
        }
        return;
    }
    if (end + 4 > MaxHandshakeBytes) {
        failConnection(QStringLiteral("Signaling handshake headers were too large"));
        return;
    }

    const auto headerBlock = m_input.left(end);
    m_input.remove(0, end + 4);
    const auto lines = headerBlock.split('\n');
    const auto status = lines.isEmpty() ? QList<QByteArray>{}
                                        : lines.first().trimmed().split(' ');
    if (status.size() < 2 || status.at(0) != QByteArray("HTTP/1.1")
        || status.at(1) != QByteArray("101")) {
        failConnection(QStringLiteral("Signaling server rejected the WebSocket upgrade"));
        return;
    }

    QHash<QByteArray, QByteArray> headers;
    for (qsizetype index = 1; index < lines.size(); ++index) {
        const auto line = lines.at(index).trimmed();
        const auto colon = line.indexOf(':');
        if (colon <= 0) {
            failConnection(QStringLiteral("Signaling server returned malformed upgrade headers"));
            return;
        }
        const auto name = line.left(colon).trimmed().toLower();
        const auto value = line.mid(colon + 1).trimmed();
        if (headers.contains(name)) {
            headers[name] += ',' + value;
        } else {
            headers.insert(name, value);
        }
    }

    const auto expectedAccept = QCryptographicHash::hash(m_handshakeKey + WebSocketGuid,
                                                          QCryptographicHash::Sha1)
                                    .toBase64();
    const bool valid = headers.value("upgrade").compare("websocket", Qt::CaseInsensitive) == 0
                       && containsHttpToken(headers.value("connection"), "upgrade")
                       && headers.value("sec-websocket-accept") == expectedAccept
                       && headers.value("sec-websocket-protocol") == m_protocol.toLatin1()
                       && !headers.contains("sec-websocket-extensions");
    if (!valid) {
        failConnection(QStringLiteral("Signaling server returned an invalid WebSocket upgrade"));
        return;
    }

    m_connectTimer.stop();
    setState(ConnectionState::Connected);
    sendPeerInfo();
    flushPendingMessages();
    m_heartbeatTimer.start();
    emit connected();
}

void SignalingClient::consumeFrames()
{
    while (m_state == ConnectionState::Connected) {
        if (m_input.size() < 2) {
            return;
        }

        const auto first = static_cast<quint8>(m_input.at(0));
        const auto second = static_cast<quint8>(m_input.at(1));
        const bool final = (first & 0x80) != 0;
        const quint8 opcode = first & 0x0f;
        if ((first & 0x70) != 0 || (second & 0x80) != 0) {
            protocolFailure(1002, QStringLiteral("Invalid signaling WebSocket frame"));
            return;
        }

        quint64 payloadSize = second & 0x7f;
        qsizetype headerSize = 2;
        if (payloadSize == 126) {
            if (m_input.size() < 4) {
                return;
            }
            payloadSize = (static_cast<quint8>(m_input.at(2)) << 8)
                          | static_cast<quint8>(m_input.at(3));
            headerSize = 4;
            if (payloadSize < 126) {
                protocolFailure(1002, QStringLiteral("Non-canonical signaling WebSocket frame"));
                return;
            }
        } else if (payloadSize == 127) {
            if (m_input.size() < 10) {
                return;
            }
            payloadSize = 0;
            for (int index = 2; index < 10; ++index) {
                payloadSize = (payloadSize << 8) | static_cast<quint8>(m_input.at(index));
            }
            headerSize = 10;
            if (payloadSize < 65536 || (static_cast<quint8>(m_input.at(2)) & 0x80) != 0) {
                protocolFailure(1002, QStringLiteral("Invalid signaling WebSocket frame length"));
                return;
            }
        }

        const bool controlFrame = (opcode & 0x08) != 0;
        if ((controlFrame && (!final || payloadSize > 125)) || payloadSize > MaxFrameBytes
            || payloadSize > static_cast<quint64>(std::numeric_limits<qsizetype>::max())) {
            protocolFailure(controlFrame ? 1002 : 1009,
                            QStringLiteral("Signaling WebSocket frame exceeded its limit"));
            return;
        }
        if (m_input.size() < headerSize + static_cast<qsizetype>(payloadSize)) {
            return;
        }

        const auto payload = m_input.mid(headerSize, static_cast<qsizetype>(payloadSize));
        m_input.remove(0, headerSize + static_cast<qsizetype>(payloadSize));
        handleFrame(opcode, final, payload);
    }
}

void SignalingClient::handleFrame(quint8 opcode, bool final, const QByteArray &payload)
{
    if (opcode == 0x8) {
        if (payload.size() == 1) {
            protocolFailure(1002, QStringLiteral("Invalid signaling close frame"));
            return;
        }
        if (payload.size() >= 2) {
            const auto code = static_cast<quint16>((static_cast<quint8>(payload.at(0)) << 8)
                                                   | static_cast<quint8>(payload.at(1)));
            if (!isValidCloseCode(code)) {
                protocolFailure(1002, QStringLiteral("Invalid signaling close status"));
                return;
            }
            QStringDecoder decoder(QStringDecoder::Utf8);
            decoder.decode(QByteArrayView(payload).sliced(2));
            if (decoder.hasError()) {
                protocolFailure(1007, QStringLiteral("Invalid signaling close reason"));
                return;
            }
        }
        const auto reason = safeCloseReason(payload);
        if (!m_closeSent) {
            writeFrame(0x8, payload);
            m_closeSent = true;
        }
        if (m_socket) {
            m_socket->disconnectFromHost();
        }
        if (m_state != ConnectionState::Disconnected) {
            m_heartbeatTimer.stop();
            setState(ConnectionState::Disconnected);
            emit disconnected(reason);
        }
        return;
    }
    if (opcode == 0x9) {
        writeFrame(0xA, payload);
        return;
    }
    if (opcode == 0xA) {
        return;
    }
    if (opcode == 0x2 || (opcode != 0x0 && opcode != 0x1)) {
        protocolFailure(opcode == 0x2 ? 1003 : 1002,
                        QStringLiteral("Unsupported signaling WebSocket frame"));
        return;
    }

    if (opcode == 0x1) {
        if (m_fragmentOpcode != 0) {
            protocolFailure(1002, QStringLiteral("Interleaved signaling WebSocket messages"));
            return;
        }
        if (final) {
            handleTextMessage(payload);
            return;
        }
        m_fragmentOpcode = opcode;
        m_fragment = payload;
    } else {
        if (m_fragmentOpcode == 0) {
            protocolFailure(1002, QStringLiteral("Unexpected signaling continuation frame"));
            return;
        }
        if (m_fragment.size() + payload.size() > MaxMessageBytes) {
            protocolFailure(1009, QStringLiteral("Signaling message exceeded the receive limit"));
            return;
        }
        m_fragment += payload;
        if (final) {
            const auto message = std::move(m_fragment);
            m_fragment.clear();
            m_fragmentOpcode = 0;
            handleTextMessage(message);
        }
    }
}

void SignalingClient::handleTextMessage(const QByteArray &payload)
{
    if (payload.size() > MaxMessageBytes) {
        protocolFailure(1009, QStringLiteral("Signaling message exceeded the receive limit"));
        return;
    }
    QStringDecoder decoder(QStringDecoder::Utf8);
    decoder.decode(payload);
    if (decoder.hasError()) {
        protocolFailure(1007, QStringLiteral("Signaling message was not valid UTF-8"));
        return;
    }

    QJsonParseError error;
    const auto document = QJsonDocument::fromJson(payload, &error);
    if (error.error != QJsonParseError::NoError || !document.isObject()) {
        emit diagnosticMessage(QStringLiteral("Ignoring a non-JSON signaling packet"));
        return;
    }
    handleSignalingMessage(document.object());
}

void SignalingClient::handleSignalingMessage(const QJsonObject &message)
{
    const auto peerInfo = message.value(QStringLiteral("peer_info")).toObject();
    if (!peerInfo.isEmpty() && peerInfo.value(QStringLiteral("name")).toString() == m_peerName
        && peerInfo.value(QStringLiteral("id")).isDouble()) {
        const auto assignedId = peerInfo.value(QStringLiteral("id")).toInteger();
        if (assignedId >= 0 && assignedId != m_peerId) {
            m_peerId = assignedId;
            emit joined(m_peerId);
        }
    }

    if (message.value(QStringLiteral("ackid")).isDouble()) {
        const auto peerInfoId = peerInfo.value(QStringLiteral("id"));
        if (!peerInfoId.isDouble() || peerInfoId.toInteger() != m_peerId) {
            sendJson({{QStringLiteral("ack"), message.value(QStringLiteral("ackid"))}}, false);
        }
    }

    const auto heartbeat = message.value(QStringLiteral("hb"));
    if ((heartbeat.isDouble() && heartbeat.toInt() != 0) || (heartbeat.isBool() && heartbeat.toBool())) {
        sendJson({{QStringLiteral("hb"), 1}}, false);
        return;
    }
    if (message.value(QStringLiteral("error")).toString() == QStringLiteral("peerRemoved")) {
        emit disconnected(QStringLiteral("peerRemoved"));
        return;
    }

    const auto peerMessage = message.value(QStringLiteral("peer_msg")).toObject();
    if (peerMessage.isEmpty() || !peerMessage.value(QStringLiteral("msg")).isString()) {
        return;
    }
    if (peerMessage.value(QStringLiteral("from")).isDouble()) {
        m_remotePeerId = peerMessage.value(QStringLiteral("from")).toInteger();
    }

    const auto text = peerMessage.value(QStringLiteral("msg")).toString().trimmed();
    if (text == QStringLiteral("BYE")) {
        emit disconnected(QStringLiteral("BYE"));
        return;
    }

    QJsonParseError error;
    const auto document = QJsonDocument::fromJson(text.toUtf8(), &error);
    if (error.error != QJsonParseError::NoError || !document.isObject()) {
        emit diagnosticMessage(QStringLiteral("Ignoring a non-JSON signaling peer payload"));
        return;
    }
    handlePeerMessage(document.object());
}

void SignalingClient::handlePeerMessage(const QJsonObject &message)
{
    const auto type = message.value(QStringLiteral("type")).toString();
    emit sessionEventReceived(type, message);

    if (type == QStringLiteral("offer") && message.value(QStringLiteral("sdp")).isString()) {
        emit offerReceived(message.value(QStringLiteral("sdp")).toString());
        return;
    }
    if (message.value(QStringLiteral("candidate")).isString()) {
        QJsonObject candidate{{QStringLiteral("candidate"), message.value(QStringLiteral("candidate"))}};
        const auto sdpMid = message.value(QStringLiteral("sdpMid"));
        if (sdpMid.isString() || sdpMid.isNull()) {
            candidate.insert(QStringLiteral("sdpMid"), sdpMid);
        }
        const auto lineIndex = message.value(QStringLiteral("sdpMLineIndex"));
        candidate.insert(QStringLiteral("sdpMLineIndex"),
                         lineIndex.isDouble() || lineIndex.isNull() ? lineIndex : QJsonValue(0));
        const auto username = message.value(QStringLiteral("usernameFragment"));
        if (username.isString() || username.isNull()) {
            candidate.insert(QStringLiteral("usernameFragment"), username);
        }
        emit remoteIceCandidateReceived(candidate);
    }
}

void SignalingClient::sendPeerInfo()
{
    sendJson({
        {QStringLiteral("ackid"), nextAckId()},
        {QStringLiteral("peer_info"),
         QJsonObject{
             {QStringLiteral("browser"), QStringLiteral("Chrome")},
             {QStringLiteral("browserVersion"), QStringLiteral("131")},
             {QStringLiteral("connected"), true},
             {QStringLiteral("id"), m_peerId},
             {QStringLiteral("name"), m_peerName},
             {QStringLiteral("peerRole"), 0},
             {QStringLiteral("resolution"), QStringLiteral("1920x1080")},
             {QStringLiteral("version"), 2},
         }},
    }, false);
}

void SignalingClient::sendAnswer(const QString &sdp, const QString &nvstSdp)
{
    QJsonObject answer{
        {QStringLiteral("type"), QStringLiteral("answer")},
        {QStringLiteral("sdp"), sdp},
    };
    if (!nvstSdp.isEmpty()) {
        answer.insert(QStringLiteral("nvstSdp"), nvstSdp);
    }
    sendPeerPayload(answer);
}

void SignalingClient::sendIceCandidate(const QJsonObject &candidate)
{
    const auto candidateText = candidate.value(QStringLiteral("candidate")).toString();
    if (candidateText.isEmpty() || isTcpCandidate(candidateText)) {
        return;
    }

    QJsonObject payload{{QStringLiteral("candidate"), candidateText}};
    for (const auto &key : {QStringLiteral("sdpMid"), QStringLiteral("sdpMLineIndex"),
                            QStringLiteral("usernameFragment")}) {
        if (candidate.contains(key)) {
            payload.insert(key, candidate.value(key));
        }
    }
    sendPeerPayload(payload);
}

void SignalingClient::sendIceCandidate(const QString &candidate,
                                       const QString &sdpMid,
                                       int sdpMLineIndex,
                                       const QString &usernameFragment)
{
    QJsonObject payload{
        {QStringLiteral("candidate"), candidate},
        {QStringLiteral("sdpMLineIndex"), sdpMLineIndex},
    };
    if (!sdpMid.isNull()) {
        payload.insert(QStringLiteral("sdpMid"), sdpMid);
    }
    if (!usernameFragment.isNull()) {
        payload.insert(QStringLiteral("usernameFragment"), usernameFragment);
    }
    sendIceCandidate(payload);
}

void SignalingClient::requestKeyframe(const QString &reason, int backlogFrames, int attempt)
{
    sendPeerPayload({
        {QStringLiteral("type"), QStringLiteral("request_keyframe")},
        {QStringLiteral("reason"), reason},
        {QStringLiteral("backlogFrames"), backlogFrames},
        {QStringLiteral("attempt"), attempt},
    });
}

void SignalingClient::sendPeerPayload(const QJsonObject &payload)
{
    sendJson({
        {QStringLiteral("peer_msg"),
         QJsonObject{
             {QStringLiteral("from"), m_peerId},
             {QStringLiteral("to"), m_remotePeerId},
             {QStringLiteral("msg"), QString::fromUtf8(QJsonDocument(payload).toJson(QJsonDocument::Compact))},
         }},
        {QStringLiteral("ackid"), nextAckId()},
    });
}

bool SignalingClient::sendJson(const QJsonObject &payload, bool queueIfConnecting)
{
    const auto bytes = QJsonDocument(payload).toJson(QJsonDocument::Compact);
    if (m_state == ConnectionState::Connected && m_socket) {
        if (m_socket->bytesToWrite() + bytes.size() + 14 <= MaxSocketWriteBytes) {
            return writeFrame(0x1, bytes);
        }
        return queueIfConnecting && queueMessage(0x1, bytes);
    }
    if (queueIfConnecting
        && (m_state == ConnectionState::Connecting || m_state == ConnectionState::Handshaking)) {
        return queueMessage(0x1, bytes);
    }
    return false;
}

bool SignalingClient::queueMessage(quint8 opcode, const QByteArray &payload)
{
    if (payload.size() > MaxFrameBytes || m_pendingMessages.size() >= MaxPendingMessages
        || m_pendingBytes + payload.size() > MaxPendingBytes) {
        emit errorOccurred(QStringLiteral("Signaling send queue limit reached"));
        return false;
    }
    m_pendingMessages.append({opcode, payload});
    m_pendingBytes += payload.size();
    return true;
}

bool SignalingClient::writeFrame(quint8 opcode, const QByteArray &payload)
{
    if (!m_socket || m_socket->state() != QAbstractSocket::ConnectedState || payload.size() > MaxFrameBytes) {
        return false;
    }

    QByteArray frame;
    frame.reserve(payload.size() + 14);
    frame.append(static_cast<char>(0x80 | opcode));
    const auto size = static_cast<quint64>(payload.size());
    if (size < 126) {
        frame.append(static_cast<char>(0x80 | size));
    } else if (size <= 0xffff) {
        frame.append(static_cast<char>(0x80 | 126));
        frame.append(static_cast<char>((size >> 8) & 0xff));
        frame.append(static_cast<char>(size & 0xff));
    } else {
        frame.append(static_cast<char>(0x80 | 127));
        for (int shift = 56; shift >= 0; shift -= 8) {
            frame.append(static_cast<char>((size >> shift) & 0xff));
        }
    }

    const auto mask = randomBytes(4);
    frame += mask;
    const auto payloadOffset = frame.size();
    frame += payload;
    for (qsizetype index = 0; index < payload.size(); ++index) {
        frame[payloadOffset + index] = payload.at(index) ^ mask.at(index % 4);
    }
    return m_socket->write(frame) == frame.size();
}

void SignalingClient::flushPendingMessages()
{
    while (m_state == ConnectionState::Connected && m_socket && !m_pendingMessages.isEmpty()
           && m_socket->bytesToWrite() + m_pendingMessages.first().payload.size() + 14
                  <= MaxSocketWriteBytes) {
        const auto message = m_pendingMessages.takeFirst();
        m_pendingBytes -= message.payload.size();
        if (!writeFrame(message.opcode, message.payload)) {
            failConnection(QStringLiteral("Could not write signaling data"));
            return;
        }
    }
}

void SignalingClient::sendClose(quint16 code, const QString &reason)
{
    if (m_closeSent || !m_socket || m_state != ConnectionState::Connected) {
        return;
    }
    auto reasonBytes = reason.toUtf8().left(123);
    QByteArray payload;
    payload.reserve(reasonBytes.size() + 2);
    payload.append(static_cast<char>((code >> 8) & 0xff));
    payload.append(static_cast<char>(code & 0xff));
    payload += reasonBytes;
    m_closeSent = writeFrame(0x8, payload);
}

void SignalingClient::protocolFailure(quint16 code, const QString &reason)
{
    sendClose(code, QStringLiteral("protocol error"));
    failConnection(reason, true);
}

void SignalingClient::failConnection(const QString &message, bool gracefulClose)
{
    if (m_failureReported) {
        return;
    }
    m_failureReported = true;
    const bool wasConnected = m_state == ConnectionState::Connected;
    m_connectTimer.stop();
    m_heartbeatTimer.stop();
    emit errorOccurred(message);
    ++m_generation;
    resetConnection(!gracefulClose, true);
    if (wasConnected) {
        emit disconnected(QStringLiteral("transport error"));
    }
}

void SignalingClient::handleSocketClosed()
{
    const bool shouldNotify = m_state != ConnectionState::Disconnected;
    m_connectTimer.stop();
    m_heartbeatTimer.stop();
    m_socket->deleteLater();
    m_socket = nullptr;
    m_pendingMessages.clear();
    m_pendingBytes = 0;
    setState(ConnectionState::Disconnected);
    if (shouldNotify) {
        emit disconnected(QStringLiteral("socket closed"));
    }
}

void SignalingClient::disconnectFromServer()
{
    ++m_generation;
    const bool canCloseCleanly = m_state == ConnectionState::Connected;
    if (m_state == ConnectionState::Connected) {
        sendClose(1000, QStringLiteral("client disconnect"));
    }
    resetConnection(!canCloseCleanly, true);
}

void SignalingClient::resetConnection(bool abortSocket, bool clearPending)
{
    m_connectTimer.stop();
    m_heartbeatTimer.stop();
    if (m_socket) {
        disconnect(m_socket, nullptr, this, nullptr);
        if (abortSocket) {
            m_socket->abort();
            m_socket->deleteLater();
        } else {
            auto *retiredSocket = m_socket;
            connect(retiredSocket, &QSslSocket::disconnected,
                    retiredSocket, &QObject::deleteLater);
            retiredSocket->disconnectFromHost();
            QTimer::singleShot(500, retiredSocket, [retiredSocket] {
                if (retiredSocket->state() != QAbstractSocket::UnconnectedState) {
                    retiredSocket->abort();
                }
                retiredSocket->deleteLater();
            });
        }
        m_socket = nullptr;
    }
    m_input.clear();
    m_fragment.clear();
    m_fragmentOpcode = 0;
    m_handshakeKey.clear();
    m_activeUrl.clear();
    m_protocol.clear();
    if (clearPending) {
        m_pendingMessages.clear();
        m_pendingBytes = 0;
    }
    setState(ConnectionState::Disconnected);
}

bool SignalingClient::isCurrentSocket(const QSslSocket *socket, quint64 generation) const
{
    return socket == m_socket && generation == m_generation;
}

void SignalingClient::setState(ConnectionState state)
{
    if (m_state == state) {
        return;
    }
    const bool connectedBefore = isConnected();
    m_state = state;
    emit stateChanged(m_state);
    if (connectedBefore != isConnected()) {
        emit connectedChanged();
    }
}

int SignalingClient::nextAckId()
{
    if (m_ackCounter == std::numeric_limits<int>::max()) {
        m_ackCounter = 0;
    }
    return ++m_ackCounter;
}
