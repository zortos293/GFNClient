package com.opencloudgaming.opennow

import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Collections
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class DiscoveredLocalTv(
    val name: String,
    /** Pair URI without the short code. The person confirms physical access by entering it. */
    val pairUri: String,
)

data class LocalTvConnectorState(
    val hosting: Boolean = false,
    val pairUri: String? = null,
    val pairingCode: String? = null,
    val pairedDeviceName: String? = null,
    val pairedDeviceTrusted: Boolean = false,
    val trustRequestedByDevice: Boolean = false,
    val connectedTvName: String? = null,
    val discoveredTvs: List<DiscoveredLocalTv> = emptyList(),
    val discovering: Boolean = false,
    val discoveryCompleted: Boolean = false,
    val requestTrustedAccess: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val phoneConnected: Boolean get() = connectedTvName != null
}

internal data class LocalTvLaunchRequest(
    val gameId: String,
    val title: String?,
)

internal data class LocalTvRemoteRequest(
    val action: String,
    val value: String?,
)

/**
 * Ephemeral local-only pairing for handing a launch from an Android phone to an Android TV.
 * The QR pins the TV's ECDH public key. Pairing and launch bodies are encrypted with AES-GCM;
 * no account tokens, GFN credentials, or remote/cloud relay are involved.
 */
internal class LocalTvConnector(
    private val discoveryPort: Int = DISCOVERY_PORT,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val random = SecureRandom()
    private val _state = MutableStateFlow(LocalTvConnectorState())
    val state: StateFlow<LocalTvConnectorState> = _state.asStateFlow()
    private val _launchRequests = MutableSharedFlow<LocalTvLaunchRequest>(extraBufferCapacity = 4)
    val launchRequests: SharedFlow<LocalTvLaunchRequest> = _launchRequests.asSharedFlow()
    private val _signInRequests = MutableSharedFlow<AuthSession>(extraBufferCapacity = 2)
    val signInRequests: SharedFlow<AuthSession> = _signInRequests.asSharedFlow()
    private val _remoteRequests = MutableSharedFlow<LocalTvRemoteRequest>(extraBufferCapacity = 8)
    val remoteRequests: SharedFlow<LocalTvRemoteRequest> = _remoteRequests.asSharedFlow()

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var discoveryResponderSocket: DatagramSocket? = null
    @Volatile private var phoneDiscoverySocket: DatagramSocket? = null
    @Volatile private var phoneDiscoveryGeneration: Long = 0L
    @Volatile private var hostKeyPair: KeyPair? = null
    @Volatile private var pairingCode: String? = null
    @Volatile private var pairingExpiresAtMs: Long = 0L
    @Volatile private var pairingAttempts: Int = 0
    @Volatile private var pairedClientPublicKey: ByteArray? = null
    @Volatile private var pairedSharedKey: ByteArray? = null
    private val recentRequestIds = Collections.synchronizedSet(LinkedHashSet<String>())
    @Volatile private var phoneTarget: PhoneTarget? = null

    fun startHosting() {
        if (serverSocket != null) return
        _state.value = _state.value.copy(busy = true, error = null, connectedTvName = null)
        scope.launch {
            runCatching {
                val address = privateLanAddress() ?: error("Connect the TV to a private Wi-Fi or Ethernet network first")
                val keyPair = generateEcKeyPair()
                val code = (random.nextInt(9_000) + 1_000).toString()
                val server = ServerSocket(0, 8, address).apply { reuseAddress = true }
                hostKeyPair = keyPair
                pairingCode = code
                pairingExpiresAtMs = System.currentTimeMillis() + PAIRING_LIFETIME_MS
                pairingAttempts = 0
                pairedClientPublicKey = null
                pairedSharedKey = null
                serverSocket = server
                val pairUri = Uri.Builder()
                    .scheme("opennow")
                    .authority("pair")
                    .appendQueryParameter("h", address.hostAddress)
                    .appendQueryParameter("p", server.localPort.toString())
                    .appendQueryParameter("c", code)
                    .appendQueryParameter("k", base64Url(keyPair.public.encoded))
                    .build()
                    .toString()
                _state.value = LocalTvConnectorState(
                    hosting = true,
                    pairUri = pairUri,
                    pairingCode = code,
                    requestTrustedAccess = _state.value.requestTrustedAccess,
                )
                scope.launch {
                    try {
                        respondToDiscovery(
                            address = address,
                            port = server.localPort,
                            publicKey = keyPair.public.encoded,
                        )
                    } catch (_: IOException) {
                        // Discovery is optional: direct QR pairing still works. A port bind,
                        // network transition, or concurrent shutdown must not crash the process.
                        if (serverSocket === server && !server.isClosed) {
                            _state.value = _state.value.copy(
                                message = "Automatic TV discovery is unavailable; scan the pairing QR instead",
                            )
                        }
                    }
                }
                acceptLoop(server, address)
            }.onFailure { error ->
                closeHost()
                _state.value = LocalTvConnectorState(
                    error = error.message ?: "Could not start TV connector",
                    requestTrustedAccess = _state.value.requestTrustedAccess,
                )
            }
        }
    }

    fun stopHosting() {
        closeHost()
        _state.value = LocalTvConnectorState(
            connectedTvName = _state.value.connectedTvName,
            requestTrustedAccess = _state.value.requestTrustedAccess,
        )
    }

    fun refreshPairingCode() {
        closeHost()
        _state.value = LocalTvConnectorState(requestTrustedAccess = _state.value.requestTrustedAccess)
        startHosting()
    }

    fun setPhoneTrustRequest(enabled: Boolean) {
        _state.value = _state.value.copy(requestTrustedAccess = enabled, error = null, message = null)
    }

    fun setPairedDeviceTrusted(trusted: Boolean) {
        if (_state.value.pairedDeviceName == null) return
        _state.value = _state.value.copy(
            pairedDeviceTrusted = trusted,
            message = if (trusted) "Trusted remote access enabled" else "Sensitive remote controls disabled",
        )
    }

    fun forgetPhoneTarget() {
        phoneTarget = null
        _state.value = _state.value.copy(connectedTvName = null, error = null)
    }

    /** Finds OpenNOW TVs on the local network. The TV never broadcasts its pairing code. */
    fun discoverTvs() {
        val generation = phoneDiscoveryGeneration + 1L
        phoneDiscoveryGeneration = generation
        runCatching { phoneDiscoverySocket?.close() }
        _state.value = _state.value.copy(
            discovering = true,
            discoveryCompleted = false,
            discoveredTvs = emptyList(),
            error = null,
            message = null,
        )
        scope.launch {
            val discovered = linkedMapOf<String, DiscoveredLocalTv>()
            runCatching {
                val localAddress = privateLanAddress()
                    ?: error("Connect this phone to the same private Wi-Fi as the TV")
                DatagramSocket().use { socket ->
                    phoneDiscoverySocket = socket
                    socket.broadcast = true
                    socket.soTimeout = DISCOVERY_POLL_TIMEOUT_MS
                    val request = DISCOVERY_REQUEST.toByteArray(Charsets.UTF_8)
                    discoveryBroadcastAddresses(localAddress).forEach { target ->
                        socket.send(DatagramPacket(request, request.size, target, discoveryPort))
                    }
                    val deadline = System.currentTimeMillis() + DISCOVERY_WINDOW_MS
                    val responseBuffer = ByteArray(MAX_DISCOVERY_PACKET_BYTES)
                    while (System.currentTimeMillis() < deadline) {
                        val packet = DatagramPacket(responseBuffer, responseBuffer.size)
                        try {
                            socket.receive(packet)
                        } catch (_: SocketTimeoutException) {
                            continue
                        }
                        if (!isSamePrivateLan(packet.address, localAddress)) continue
                        parseDiscoveryResponse(packet.data.copyOf(packet.length))?.let { tv ->
                            discovered[tv.pairUri] = tv
                            if (phoneDiscoveryGeneration == generation) {
                                _state.value = _state.value.copy(discoveredTvs = discovered.values.toList())
                            }
                        }
                    }
                }
            }.onFailure { error ->
                if (phoneDiscoveryGeneration == generation &&
                    (error !is java.net.SocketException || phoneDiscoverySocket?.isClosed != true)
                ) {
                    _state.value = _state.value.copy(error = error.message ?: "Could not search for TVs")
                }
            }
            if (phoneDiscoveryGeneration == generation) {
                phoneDiscoverySocket = null
                _state.value = _state.value.copy(
                    discovering = false,
                    discoveryCompleted = true,
                )
            }
        }
    }

    fun pairDiscoveredTv(tv: DiscoveredLocalTv, code: String) {
        val normalizedCode = normalizeLocalTvPairingCode(code)
        if (normalizedCode == null) {
            _state.value = _state.value.copy(error = "Enter the 4-digit code shown on the TV")
            return
        }
        val uri = Uri.parse(tv.pairUri).buildUpon()
            .appendQueryParameter("c", normalizedCode)
            .build()
        pairPhone(uri)
    }

    fun reportPairingError(message: String) {
        _state.value = _state.value.copy(error = message, busy = false)
    }

    fun isPairUri(uri: Uri?): Boolean = isLocalTvPairUri(uri)

    fun pairPhone(uri: Uri) {
        if (!isPairUri(uri)) return
        phoneDiscoveryGeneration += 1L
        runCatching { phoneDiscoverySocket?.close() }
        phoneDiscoverySocket = null
        _state.value = _state.value.copy(busy = true, error = null)
        scope.launch {
            runCatching {
                val host = uri.getQueryParameter("h")?.takeIf(::isPrivateIpv4Text)
                    ?: error("Pairing QR has no valid private TV address")
                val port = uri.getQueryParameter("p")?.toIntOrNull()?.takeIf { it in 1..65535 }
                    ?: error("Pairing QR has no valid TV port")
                val code = uri.getQueryParameter("c")?.takeIf { it.length == 4 && it.all(Char::isDigit) }
                    ?: error("Pairing QR has no valid code")
                val hostPublic = decodePublicKey(uri.getQueryParameter("k") ?: error("Pairing QR has no security key"))
                val phoneKeys = generateEcKeyPair()
                val shared = deriveAesKey(phoneKeys, hostPublic.encoded)
                val requestTrustedAccess = _state.value.requestTrustedAccess
                val encrypted = encrypt(shared, "$code\n${safeDeviceName()}\n${if (requestTrustedAccess) 1 else 0}")
                val response = sendFrame(
                    host = host,
                    port = port,
                    command = COMMAND_PAIR,
                    publicKey = phoneKeys.public.encoded,
                    iv = encrypted.iv,
                    ciphertext = encrypted.ciphertext,
                )
                if (response.first != STATUS_OK) error(response.second.ifBlank { "TV rejected pairing" })
                phoneTarget = PhoneTarget(host, port, phoneKeys, shared)
                _state.value = LocalTvConnectorState(
                    connectedTvName = response.second.ifBlank { "OpenNOW TV" },
                    requestTrustedAccess = requestTrustedAccess,
                )
            }.onFailure { error ->
                phoneTarget = null
                _state.value = LocalTvConnectorState(
                    error = error.message ?: "Could not pair with TV",
                    requestTrustedAccess = _state.value.requestTrustedAccess,
                )
            }
        }
    }

    fun sendLaunch(gameId: String, title: String?) {
        val target = phoneTarget
        if (target == null) {
            _state.value = _state.value.copy(error = "Pair with a TV first")
            return
        }
        _state.value = _state.value.copy(busy = true, error = null)
        scope.launch {
            runCatching {
                val requestId = UUID.randomUUID().toString()
                val safeTitle = title.orEmpty().replace('\n', ' ').take(160)
                val plaintext = "${System.currentTimeMillis()}\n$requestId\n${gameId.replace('\n', ' ').take(200)}\n$safeTitle"
                val encrypted = encrypt(target.sharedKey, plaintext)
                val response = sendFrame(
                    host = target.host,
                    port = target.port,
                    command = COMMAND_LAUNCH,
                    publicKey = target.phoneKeys.public.encoded,
                    iv = encrypted.iv,
                    ciphertext = encrypted.ciphertext,
                )
                if (response.first != STATUS_OK) error(response.second.ifBlank { "TV rejected launch" })
                _state.value = _state.value.copy(busy = false, error = null)
            }.onFailure { error ->
                _state.value = _state.value.copy(busy = false, error = error.message ?: "Could not send launch to TV")
            }
        }
    }

    fun sendSignIn(session: AuthSession) {
        val target = phoneTarget
        if (target == null) {
            _state.value = _state.value.copy(error = "Pair with a TV first")
            return
        }
        _state.value = _state.value.copy(busy = true, error = null, message = null)
        scope.launch {
            runCatching {
                val requestId = UUID.randomUUID().toString()
                val sessionJson = OpenNowJson.encodeToString(session)
                val plaintext = "${System.currentTimeMillis()}\n$requestId\n$sessionJson"
                val encrypted = encrypt(target.sharedKey, plaintext)
                val response = sendFrame(
                    host = target.host,
                    port = target.port,
                    command = COMMAND_SIGN_IN,
                    publicKey = target.phoneKeys.public.encoded,
                    iv = encrypted.iv,
                    ciphertext = encrypted.ciphertext,
                )
                if (response.first != STATUS_OK) error(response.second.ifBlank { "TV rejected sign-in" })
                _state.value = _state.value.copy(busy = false, error = null, message = "Sign-in sent securely to TV")
            }.onFailure { error ->
                _state.value = _state.value.copy(busy = false, error = error.message ?: "Could not sign in TV")
            }
        }
    }

    fun sendRemoteAction(action: String, value: String? = null) {
        val target = phoneTarget
        if (target == null) {
            _state.value = _state.value.copy(error = "Pair with a TV first")
            return
        }
        val safeAction = action.trim().takeIf { it.matches(Regex("[a-z0-9_]{1,48}")) } ?: run {
            _state.value = _state.value.copy(error = "Remote action is invalid")
            return
        }
        val safeValue = value.orEmpty().replace('\n', ' ').take(240)
        _state.value = _state.value.copy(busy = true, error = null, message = null)
        scope.launch {
            runCatching {
                val plaintext = "${System.currentTimeMillis()}\n${UUID.randomUUID()}\n$safeAction\n$safeValue"
                val encrypted = encrypt(target.sharedKey, plaintext)
                val response = sendFrame(
                    host = target.host,
                    port = target.port,
                    command = COMMAND_REMOTE,
                    publicKey = target.phoneKeys.public.encoded,
                    iv = encrypted.iv,
                    ciphertext = encrypted.ciphertext,
                )
                if (response.first != STATUS_OK) error(response.second.ifBlank { "TV rejected remote command" })
                _state.value = _state.value.copy(busy = false, error = null, message = response.second)
            }.onFailure { error ->
                _state.value = _state.value.copy(busy = false, error = error.message ?: "Could not control TV")
            }
        }
    }

    private fun acceptLoop(server: ServerSocket, hostAddress: InetAddress) {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() }.getOrElse { error ->
                if (server.isClosed) return
                throw error
            }
            scope.launch {
                socket.use { client ->
                    client.soTimeout = SOCKET_TIMEOUT_MS
                    if (!isSamePrivateLan(client.inetAddress, hostAddress)) return@use
                    handleClient(client)
                }
            }
        }
    }

    private fun respondToDiscovery(address: Inet4Address, port: Int, publicKey: ByteArray) {
        val socket = DatagramSocket(null)
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(discoveryPort))
            socket.soTimeout = DISCOVERY_POLL_TIMEOUT_MS
            discoveryResponderSocket = socket
            val pairUri = Uri.Builder()
                .scheme("opennow")
                .authority("pair")
                .appendQueryParameter("h", address.hostAddress)
                .appendQueryParameter("p", port.toString())
                .appendQueryParameter("k", base64Url(publicKey))
                .build()
                .toString()
            val response = listOf(DISCOVERY_RESPONSE, safeDeviceName(), pairUri)
                .joinToString("\n")
                .toByteArray(Charsets.UTF_8)
            val requestBuffer = ByteArray(128)
            while (!socket.isClosed && serverSocket != null) {
                val packet = DatagramPacket(requestBuffer, requestBuffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (error: SocketException) {
                    // closeHost/close deliberately interrupts this blocking receive. Android
                    // reports that wake-up as EBADF; it is a normal shutdown, not a process error.
                    if (socket.isClosed || discoveryResponderSocket !== socket) break
                    throw error
                }
                if (!isSamePrivateLan(packet.address, address)) continue
                val request = packet.data.copyOf(packet.length).toString(Charsets.UTF_8)
                if (request != DISCOVERY_REQUEST) continue
                socket.send(DatagramPacket(response, response.size, packet.address, packet.port))
            }
        } finally {
            socket.close()
            if (discoveryResponderSocket === socket) discoveryResponderSocket = null
        }
    }

    private fun parseDiscoveryResponse(bytes: ByteArray): DiscoveredLocalTv? {
        val lines = bytes.toString(Charsets.UTF_8).lines()
        if (lines.getOrNull(0) != DISCOVERY_RESPONSE) return null
        val name = lines.getOrNull(1)?.trim()?.take(80)?.ifBlank { "OpenNOW TV" } ?: return null
        val pairUri = lines.getOrNull(2)?.trim()?.takeIf { raw ->
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return@takeIf false
            isPairUri(uri) && uri.getQueryParameter("c") == null &&
                uri.getQueryParameter("h")?.let(::isPrivateIpv4Text) == true &&
                uri.getQueryParameter("p")?.toIntOrNull() in 1..65535 &&
                !uri.getQueryParameter("k").isNullOrBlank()
        } ?: return null
        return DiscoveredLocalTv(name = name, pairUri = pairUri)
    }

    private fun discoveryBroadcastAddresses(localAddress: Inet4Address): List<InetAddress> {
        val bytes = localAddress.address
        return listOfNotNull(
            runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull(),
            runCatching {
                InetAddress.getByAddress(byteArrayOf(bytes[0], bytes[1], 0xff.toByte(), 0xff.toByte()))
            }.getOrNull(),
        ).distinctBy(InetAddress::getHostAddress)
    }

    private fun handleClient(socket: Socket) {
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        val response = runCatching {
            if (input.readInt() != PROTOCOL_MAGIC) error("Invalid connector request")
            if (input.readUnsignedByte() != PROTOCOL_VERSION) error("Unsupported connector version")
            val command = input.readUnsignedByte()
            val publicKey = input.readSizedBytes(MAX_PUBLIC_KEY_BYTES)
            val iv = input.readSizedBytes(MAX_IV_BYTES)
            val ciphertext = input.readSizedBytes(MAX_CIPHERTEXT_BYTES)
            when (command) {
                COMMAND_PAIR -> handlePair(publicKey, iv, ciphertext)
                COMMAND_LAUNCH -> handleLaunch(publicKey, iv, ciphertext)
                COMMAND_SIGN_IN -> handleSignIn(publicKey, iv, ciphertext)
                COMMAND_REMOTE -> handleRemote(publicKey, iv, ciphertext)
                else -> STATUS_BAD_REQUEST to "Unknown connector command"
            }
        }.getOrElse { error -> STATUS_BAD_REQUEST to (error.message ?: "Invalid connector request") }
        output.writeInt(response.first)
        output.writeUTF(response.second.take(240))
        output.flush()
    }

    @Synchronized
    private fun handlePair(clientPublicKey: ByteArray, iv: ByteArray, ciphertext: ByteArray): Pair<Int, String> {
        val keyPair = hostKeyPair ?: return STATUS_UNAVAILABLE to "TV pairing is no longer active"
        val expectedCode = pairingCode ?: return STATUS_UNAVAILABLE to "TV pairing is no longer active"
        if (System.currentTimeMillis() > pairingExpiresAtMs) return STATUS_FORBIDDEN to "Pairing QR expired; create a new one"
        if (pairingAttempts >= MAX_PAIRING_ATTEMPTS) return STATUS_FORBIDDEN to "Too many pairing attempts; create a new QR"
        pairingAttempts += 1
        val shared = deriveAesKey(keyPair, clientPublicKey)
        val lines = decrypt(shared, iv, ciphertext).lines()
        if (!MessageDigest.isEqual(lines.firstOrNull().orEmpty().toByteArray(), expectedCode.toByteArray())) {
            return STATUS_FORBIDDEN to "Pairing code did not match"
        }
        pairedClientPublicKey = clientPublicKey.copyOf()
        pairedSharedKey = shared
        pairingCode = null
        val deviceName = lines.getOrNull(1)?.take(80)?.ifBlank { "Android phone" } ?: "Android phone"
        val trustRequested = lines.getOrNull(2) == "1"
        _state.value = _state.value.copy(
            pairedDeviceName = deviceName,
            pairedDeviceTrusted = false,
            trustRequestedByDevice = trustRequested,
            busy = false,
            error = null,
            message = if (trustRequested) "$deviceName requested trusted remote access" else "$deviceName paired for game launching",
        )
        return STATUS_OK to safeDeviceName()
    }

    private fun handleLaunch(clientPublicKey: ByteArray, iv: ByteArray, ciphertext: ByteArray): Pair<Int, String> {
        val expectedClient = pairedClientPublicKey ?: return STATUS_FORBIDDEN to "Pair the phone again"
        val shared = pairedSharedKey ?: return STATUS_FORBIDDEN to "Pair the phone again"
        if (!MessageDigest.isEqual(clientPublicKey, expectedClient)) return STATUS_FORBIDDEN to "Phone is not paired"
        val lines = decrypt(shared, iv, ciphertext).lines()
        val timestamp = lines.getOrNull(0)?.toLongOrNull() ?: return STATUS_BAD_REQUEST to "Launch has no timestamp"
        if (kotlin.math.abs(System.currentTimeMillis() - timestamp) > LAUNCH_MAX_AGE_MS) {
            return STATUS_FORBIDDEN to "Launch request expired"
        }
        val requestId = lines.getOrNull(1).orEmpty()
        if (requestId.isBlank() || !recentRequestIds.add(requestId)) return STATUS_FORBIDDEN to "Launch request was already used"
        synchronized(recentRequestIds) {
            while (recentRequestIds.size > MAX_RECENT_REQUEST_IDS) {
                recentRequestIds.iterator().run { next(); remove() }
            }
        }
        val gameId = lines.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return STATUS_BAD_REQUEST to "Launch has no game"
        _launchRequests.tryEmit(LocalTvLaunchRequest(gameId = gameId, title = lines.getOrNull(3)?.takeIf { it.isNotBlank() }))
        return STATUS_OK to "Launch sent"
    }

    private fun handleSignIn(clientPublicKey: ByteArray, iv: ByteArray, ciphertext: ByteArray): Pair<Int, String> {
        val expectedClient = pairedClientPublicKey ?: return STATUS_FORBIDDEN to "Pair the phone again"
        val shared = pairedSharedKey ?: return STATUS_FORBIDDEN to "Pair the phone again"
        if (!MessageDigest.isEqual(clientPublicKey, expectedClient)) return STATUS_FORBIDDEN to "Phone is not paired"
        if (!_state.value.pairedDeviceTrusted) return STATUS_FORBIDDEN to "Trust this phone on the TV before switching accounts"
        val plaintext = decrypt(shared, iv, ciphertext)
        val firstBreak = plaintext.indexOf('\n')
        val secondBreak = plaintext.indexOf('\n', firstBreak + 1)
        if (firstBreak <= 0 || secondBreak <= firstBreak) return STATUS_BAD_REQUEST to "Sign-in request is malformed"
        val timestamp = plaintext.substring(0, firstBreak).toLongOrNull() ?: return STATUS_BAD_REQUEST to "Sign-in has no timestamp"
        if (kotlin.math.abs(System.currentTimeMillis() - timestamp) > SIGN_IN_MAX_AGE_MS) {
            return STATUS_FORBIDDEN to "Sign-in request expired"
        }
        val requestId = plaintext.substring(firstBreak + 1, secondBreak)
        if (requestId.isBlank() || !recentRequestIds.add(requestId)) return STATUS_FORBIDDEN to "Sign-in request was already used"
        val session = runCatching { OpenNowJson.decodeFromString<AuthSession>(plaintext.substring(secondBreak + 1)) }
            .getOrElse { return STATUS_BAD_REQUEST to "Sign-in data could not be read" }
        if (session.tokens.accessToken.isBlank() || session.user.userId.isBlank() || session.provider.code.isBlank()) {
            return STATUS_BAD_REQUEST to "Sign-in data is incomplete"
        }
        _signInRequests.tryEmit(session)
        return STATUS_OK to "Sign-in received"
    }

    private fun handleRemote(clientPublicKey: ByteArray, iv: ByteArray, ciphertext: ByteArray): Pair<Int, String> {
        val expectedClient = pairedClientPublicKey ?: return STATUS_FORBIDDEN to "Pair the phone again"
        val shared = pairedSharedKey ?: return STATUS_FORBIDDEN to "Pair the phone again"
        if (!MessageDigest.isEqual(clientPublicKey, expectedClient)) return STATUS_FORBIDDEN to "Phone is not paired"
        if (!_state.value.pairedDeviceTrusted) return STATUS_FORBIDDEN to "Trust this phone on the TV to use remote controls"
        val lines = decrypt(shared, iv, ciphertext).lines()
        val timestamp = lines.getOrNull(0)?.toLongOrNull() ?: return STATUS_BAD_REQUEST to "Remote command has no timestamp"
        if (kotlin.math.abs(System.currentTimeMillis() - timestamp) > REMOTE_MAX_AGE_MS) {
            return STATUS_FORBIDDEN to "Remote command expired"
        }
        val requestId = lines.getOrNull(1).orEmpty()
        if (!rememberRequestId(requestId)) return STATUS_FORBIDDEN to "Remote command was already used"
        val action = lines.getOrNull(2)?.takeIf { it.matches(Regex("[a-z0-9_]{1,48}")) }
            ?: return STATUS_BAD_REQUEST to "Remote command is invalid"
        _remoteRequests.tryEmit(LocalTvRemoteRequest(action, lines.getOrNull(3)?.takeIf(String::isNotBlank)))
        return STATUS_OK to "TV command sent"
    }

    private fun rememberRequestId(requestId: String): Boolean {
        if (requestId.isBlank() || !recentRequestIds.add(requestId)) return false
        synchronized(recentRequestIds) {
            while (recentRequestIds.size > MAX_RECENT_REQUEST_IDS) {
                recentRequestIds.iterator().run { next(); remove() }
            }
        }
        return true
    }

    private fun sendFrame(
        host: String,
        port: Int,
        command: Int,
        publicKey: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
    ): Pair<Int, String> = Socket().use { socket ->
        socket.connect(java.net.InetSocketAddress(host, port), SOCKET_TIMEOUT_MS)
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        output.writeInt(PROTOCOL_MAGIC)
        output.writeByte(PROTOCOL_VERSION)
        output.writeByte(command)
        output.writeSizedBytes(publicKey)
        output.writeSizedBytes(iv)
        output.writeSizedBytes(ciphertext)
        output.flush()
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        input.readInt() to input.readUTF()
    }

    private fun closeHost() {
        runCatching { serverSocket?.close() }
        runCatching { discoveryResponderSocket?.close() }
        serverSocket = null
        discoveryResponderSocket = null
        hostKeyPair = null
        pairingCode = null
        pairingExpiresAtMs = 0L
        pairingAttempts = 0
        pairedClientPublicKey = null
        pairedSharedKey = null
        recentRequestIds.clear()
    }

    fun close() {
        closeHost()
        phoneDiscoveryGeneration += 1L
        runCatching { phoneDiscoverySocket?.close() }
        phoneDiscoverySocket = null
        scope.cancel()
    }

    private data class PhoneTarget(
        val host: String,
        val port: Int,
        val phoneKeys: KeyPair,
        val sharedKey: ByteArray,
    )

    private data class EncryptedPayload(val iv: ByteArray, val ciphertext: ByteArray)

    private fun encrypt(key: ByteArray, plaintext: String): EncryptedPayload {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return EncryptedPayload(iv, cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    private fun decrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): String {
        require(iv.size == 12) { "Invalid encrypted request" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun generateEcKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"), random)
        generateKeyPair()
    }

    private fun decodePublicKey(encoded: String) =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(base64UrlDecode(encoded)))

    private fun deriveAesKey(ownKeys: KeyPair, peerPublicBytes: ByteArray): ByteArray {
        val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPublicBytes))
        val sharedSecret = KeyAgreement.getInstance("ECDH").run {
            init(ownKeys.private)
            doPhase(peer, true)
            generateSecret()
        }
        val salt = "OpenNOW-local-tv-v1".toByteArray()
        val prk = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(salt, "HmacSHA256"))
            doFinal(sharedSecret)
        }
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(prk, "HmacSHA256"))
            doFinal("launch-key\u0001".toByteArray()).copyOf(32)
        }
    }

    private fun privateLanAddress(): Inet4Address? =
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }

    private fun isSamePrivateLan(remote: InetAddress, local: InetAddress): Boolean {
        if (remote.isLoopbackAddress && local.isLoopbackAddress) return true
        val remote4 = remote as? Inet4Address ?: return false
        val local4 = local as? Inet4Address ?: return false
        if (!remote4.isSiteLocalAddress || !local4.isSiteLocalAddress) return false
        val remoteBytes = remote4.address
        val localBytes = local4.address
        return remoteBytes[0] == localBytes[0] && remoteBytes[1] == localBytes[1]
    }

    private fun isPrivateIpv4Text(value: String): Boolean =
        runCatching { InetAddress.getByName(value) as? Inet4Address }
            .getOrNull()
            ?.isSiteLocalAddress == true

    private fun safeDeviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" ")
            .take(80)
            .ifBlank { "OpenNOW Android" }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun base64UrlDecode(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun DataOutputStream.writeSizedBytes(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readSizedBytes(maxBytes: Int): ByteArray {
        val size = readInt()
        require(size in 1..maxBytes) { "Connector request is too large" }
        return ByteArray(size).also(::readFully)
    }

    private fun <T> java.util.Enumeration<T>.toList(): List<T> = Collections.list(this)

    private companion object {
        const val PROTOCOL_MAGIC = 0x4f4e5456
        const val PROTOCOL_VERSION = 1
        const val COMMAND_PAIR = 1
        const val COMMAND_LAUNCH = 2
        const val COMMAND_SIGN_IN = 3
        const val COMMAND_REMOTE = 4
        const val STATUS_OK = 200
        const val STATUS_BAD_REQUEST = 400
        const val STATUS_FORBIDDEN = 403
        const val STATUS_UNAVAILABLE = 503
        const val SOCKET_TIMEOUT_MS = 5_000
        const val LAUNCH_MAX_AGE_MS = 60_000L
        const val SIGN_IN_MAX_AGE_MS = 60_000L
        const val REMOTE_MAX_AGE_MS = 60_000L
        const val PAIRING_LIFETIME_MS = 5L * 60L * 1000L
        const val MAX_PAIRING_ATTEMPTS = 5
        const val MAX_RECENT_REQUEST_IDS = 64
        const val MAX_PUBLIC_KEY_BYTES = 512
        const val MAX_IV_BYTES = 32
        const val MAX_CIPHERTEXT_BYTES = 65_536
        const val DISCOVERY_PORT = 39_047
        const val DISCOVERY_REQUEST = "OPENNOW_TV_DISCOVERY_V1"
        const val DISCOVERY_RESPONSE = "OPENNOW_TV_RESPONSE_V1"
        const val DISCOVERY_WINDOW_MS = 1_800L
        const val DISCOVERY_POLL_TIMEOUT_MS = 180
        const val MAX_DISCOVERY_PACKET_BYTES = 2_048
    }
}

internal fun isLocalTvPairUri(uri: Uri?): Boolean =
    uri?.scheme.equals("opennow", ignoreCase = true) && uri?.host.equals("pair", ignoreCase = true)

internal fun normalizeLocalTvPairingCode(value: String): String? =
    value.trim().takeIf { it.matches(Regex("[0-9]{4}")) }
