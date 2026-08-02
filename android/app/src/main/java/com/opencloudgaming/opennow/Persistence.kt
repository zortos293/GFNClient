package com.opencloudgaming.opennow

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.encodeToString
import java.security.MessageDigest
import java.util.UUID
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val STORE_NAME = "opennow_native"
private const val CATALOG_CACHE_STORE_NAME = "opennow_catalog_cache"
private const val SECURE_STORE_NAME = "opennow_auth_secure"
private const val KEY_SETTINGS = "settings"
private const val KEY_AUTH = "auth"
private const val KEY_DEVICE_ID = "gfn_device_id"
private const val KEY_CATALOG_CACHE_PREFIX = "catalog_cache_"
private const val KEY_ANDROID_UPDATE_DISMISSED_NOTICE = "android_update_dismissed_notice"
private const val KEY_QUEUED_GAME_KEYS = "queued_game_keys"
private const val CATALOG_CACHE_TTL_MS = 12L * 60L * 60L * 1000L
private const val QUEUED_GAME_LIMIT = 24
// Keys that must never be written to the external (potentially world-readable) file.
private val SENSITIVE_KEYS = setOf(KEY_AUTH, KEY_DEVICE_ID)
private val AUTH_STORE_LOCK = Any()

class ExternalPrefs private constructor(context: Context, val name: String) {
    private val primaryFile: File
    private val fallbackFile: File
    private val data = mutableMapOf<String, String>()
    private val lock = Any()
    // Single-thread dispatcher: apply() writes are serialized in submission order
    // (last-write-wins) without blocking the caller.
    private val writeScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))

    init {
        val extDir = runCatching { context.getExternalFilesDir(null) }.getOrNull()
        primaryFile = File(extDir ?: context.filesDir, "$name.xml")
        fallbackFile = File(context.filesDir, "$name.xml")
        synchronized(lock) {
            migrateFromInternal(context, name)
            load()
        }
    }

    companion object {
        private val instances = mutableMapOf<String, ExternalPrefs>()
        private val globalLock = Any()

        fun get(context: Context, name: String): ExternalPrefs {
            return synchronized(globalLock) {
                instances.getOrPut(name) {
                    ExternalPrefs(context.applicationContext, name)
                }
            }
        }
    }

    private fun migrateFromInternal(context: Context, name: String) {
        if (primaryFile.exists() || fallbackFile.exists()) return
        val internalPrefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        val allInternal = internalPrefs.all
        if (allInternal.isNotEmpty()) {
            // Sensitive keys must not land in the external (world-readable) file.
            // Migrate them directly into the secure internal store instead,
            // skipping any key that is already present there.
            val securePrefs = context.applicationContext
                .getSharedPreferences(SECURE_STORE_NAME, Context.MODE_PRIVATE)
            val secureEdit = securePrefs.edit()
            var hasSensitive = false
            allInternal.forEach { (k, v) ->
                if (v is String && k in SENSITIVE_KEYS && !securePrefs.contains(k)) {
                    secureEdit.putString(k, v)
                    hasSensitive = true
                }
            }
            if (hasSensitive) secureEdit.commit()

            // Migrate non-sensitive keys to the external file
            allInternal.forEach { (k, v) ->
                if (v is String && k !in SENSITIVE_KEYS) data[k] = v
            }
            val primarySuccess = writeToFile(primaryFile, data.toMap())
            val fallbackSuccess = if (!primarySuccess) writeToFile(fallbackFile, data.toMap()) else true
            if (primarySuccess || fallbackSuccess) {
                internalPrefs.edit().clear().commit()
            }
        }
    }

    private fun load() {
        val hasPrimary = primaryFile.exists()
        val hasFallback = fallbackFile.exists()
        val targetFile = when {
            hasPrimary && hasFallback -> {
                if (primaryFile.lastModified() >= fallbackFile.lastModified()) {
                    primaryFile
                } else {
                    fallbackFile
                }
            }
            hasPrimary -> primaryFile
            hasFallback -> fallbackFile
            else -> return
        }
        // Snapshot existing data so we can restore it if parsing fails mid-way
        val existing = data.toMap()
        runCatching {
            val parser = Xml.newPullParser()
            FileInputStream(targetFile).use { fis ->
                parser.setInput(fis, "UTF-8")
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "string") {
                        val name = parser.getAttributeValue(null, "name")
                        val value = parser.nextText()
                        if (name != null) {
                            data[name] = value
                        }
                    }
                    event = parser.next()
                }
            }
        }.onFailure {
            it.printStackTrace()
            // Restore pre-parse snapshot to avoid leaving data in a partial state
            data.clear()
            data.putAll(existing)
            val alternativeFile = if (targetFile == primaryFile) fallbackFile else primaryFile
            if (alternativeFile.exists()) {
                val existingBeforeAlt = data.toMap()
                runCatching {
                    val parser = Xml.newPullParser()
                    FileInputStream(alternativeFile).use { fis ->
                        parser.setInput(fis, "UTF-8")
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            if (event == XmlPullParser.START_TAG && parser.name == "string") {
                                val name = parser.getAttributeValue(null, "name")
                                val value = parser.nextText()
                                if (name != null) {
                                    data[name] = value
                                }
                            }
                            event = parser.next()
                        }
                    }
                }.onFailure { e ->
                    e.printStackTrace()
                    // Restore again if the alternative file also failed mid-parse
                    data.clear()
                    data.putAll(existingBeforeAlt)
                }
            }
        }
    }

    private fun save(mapSnapshot: Map<String, String>) {
        synchronized(lock) {
            val success = writeToFile(primaryFile, mapSnapshot)
            if (!success) {
                writeToFile(fallbackFile, mapSnapshot)
            }
        }
    }

    private fun writeToFile(file: File, mapSnapshot: Map<String, String>): Boolean {
        return runCatching {
            val parent = file.parentFile ?: return false
            parent.mkdirs()
            val tmpFile = File(parent, "${file.name}.tmp")
            tmpFile.bufferedWriter().use { writer ->
                writer.write("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n")
                writer.write("<map>\n")
                for ((k, v) in mapSnapshot) {
                    writer.write("    <string name=\"")
                    writer.write(escapeXmlAttribute(k))
                    writer.write("\">")
                    writer.write(escapeXmlText(v))
                    writer.write("</string>\n")
                }
                writer.write("</map>\n")
            }
            if (tmpFile.exists()) {
                if (tmpFile.renameTo(file)) {
                    true
                } else {
                    tmpFile.copyTo(file, overwrite = true)
                    tmpFile.delete()
                    true
                }
            } else {
                false
            }
        }.getOrElse {
            it.printStackTrace()
            false
        }
    }

    private fun escapeXmlAttribute(str: String): String =
        str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

    private fun escapeXmlText(str: String): String =
        str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun getString(key: String, defValue: String?): String? = synchronized(lock) { data[key] ?: defValue }

    val all: Map<String, Any?> get() = synchronized(lock) { data.toMap() }

    fun edit(): Editor = Editor()

    inner class Editor {
        private val actions = mutableListOf<() -> Unit>()

        fun putString(key: String, value: String?): Editor {
            actions.add {
                if (value == null) data.remove(key) else data[key] = value
            }
            return this
        }

        fun remove(key: String): Editor {
            actions.add { data.remove(key) }
            return this
        }

        // Captures snapshot synchronously under the lock then dispatches the write
        // on a single-thread background scope, so:
        //   - The caller is never blocked by IO
        //   - Writes are still dispatched in submission order (last-write-wins guaranteed)
        fun apply() {
            val snapshot = synchronized(lock) {
                actions.forEach { it() }
                actions.clear()
                data.toMap()
            }
            writeScope.launch { save(snapshot) }
        }

        // Single synchronized block keeps action application, snapshot capture, and file
        // write atomic — eliminating the interleaving window where a concurrent commit()
        // could write a newer snapshot between our two previously-separate lock sections.
        fun commit(): Boolean = synchronized(lock) {
            actions.forEach { it() }
            actions.clear()
            val snapshot = data.toMap()
            val success = writeToFile(primaryFile, snapshot)
            if (!success) writeToFile(fallbackFile, snapshot) else true
        }
    }
}

private fun Float.finiteIn(minimum: Float, maximum: Float, fallback: Float): Float =
    if (isFinite()) coerceIn(minimum, maximum) else fallback

internal fun AppSettings.normalizedForAndroid(): AppSettings {
    val streamDefaults = StreamSettings()
    val touchDefaults = AndroidTouchSettings()
    val compatibleStream = stream.withCodecColorCompatibility()
    val lowPowerSafe = compatibleStream.copy(
        codec = compatibleStream.codec,
        sessionProxyUrl = stream.sessionProxyUrl.trim(),
        maxBitrateMbps = compatibleStream.maxBitrateMbps.coerceIn(1, 150),
        fps = compatibleStream.fps.coerceIn(30, 360),
        mouseSensitivity = compatibleStream.mouseSensitivity.finiteIn(0.25f, 3f, streamDefaults.mouseSensitivity),
        mouseAcceleration = compatibleStream.mouseAcceleration.coerceIn(1, 150),
        mouseScrollSensitivity = compatibleStream.mouseScrollSensitivity.coerceIn(10, 100),
        streamSharpeningAmount = compatibleStream.streamSharpeningAmount.finiteIn(
            0f,
            1f,
            streamDefaults.streamSharpeningAmount,
        ),
    )
    return copy(
        stream = lowPowerSafe,
        posterSizeScale = posterSizeScale.finiteIn(MIN_GAME_CARD_SCALE, MAX_GAME_CARD_SCALE, 1f),
        androidTouch = androidTouch.copy(
            opacity = androidTouch.opacity.finiteIn(0.15f, 1f, touchDefaults.opacity),
            scale = androidTouch.scale.finiteIn(0.6f, 1.4f, touchDefaults.scale),
            buttonScale = androidTouch.buttonScale.finiteIn(0.65f, 1.5f, touchDefaults.buttonScale),
            stickScale = androidTouch.stickScale.finiteIn(0.65f, 1.5f, touchDefaults.stickScale),
            joystickDeadZone = androidTouch.joystickDeadZone.finiteIn(0f, 0.3f, touchDefaults.joystickDeadZone),
            edgePaddingDp = androidTouch.edgePaddingDp.finiteIn(0f, 72f, touchDefaults.edgePaddingDp),
            bottomPaddingDp = androidTouch.bottomPaddingDp.finiteIn(0f, 120f, touchDefaults.bottomPaddingDp),
            leftOffsetXDp = androidTouch.leftOffsetXDp.finiteIn(-220f, 220f, touchDefaults.leftOffsetXDp),
            leftOffsetYDp = androidTouch.leftOffsetYDp.finiteIn(-160f, 160f, touchDefaults.leftOffsetYDp),
            rightOffsetXDp = androidTouch.rightOffsetXDp.finiteIn(-220f, 220f, touchDefaults.rightOffsetXDp),
            rightOffsetYDp = androidTouch.rightOffsetYDp.finiteIn(-160f, 160f, touchDefaults.rightOffsetYDp),
            nativeTouchScrollScale = androidTouch.nativeTouchScrollScale.finiteIn(
                0.25f,
                2f,
                touchDefaults.nativeTouchScrollScale,
            ),
            nativeTouchJitterThresholdDp = androidTouch.nativeTouchJitterThresholdDp.finiteIn(
                0f,
                24f,
                touchDefaults.nativeTouchJitterThresholdDp,
            ),
            offsets = androidTouch.offsets.mapValues { (_, offset) ->
                TouchOffset(
                    x = offset.x.finiteIn(-320f, 320f, 0f),
                    y = offset.y.finiteIn(-320f, 320f, 0f),
                )
            },
        ),
        streamIntroMusic = streamIntroMusic,
        queueReadyMusic = queueReadyMusic,
        legacyCropStreamToFill = false,
        stretchStreamToFit = stretchStreamToFit,
        streamPresentationProfileVersion = streamPresentationProfileVersion.coerceAtLeast(STREAM_PRESENTATION_PROFILE_VERSION),
        nerdCatalogBackgroundUri = nerdCatalogBackgroundUri?.trim()?.takeIf { it.isNotBlank() },
        tvSafeAreaPaddingDp = tvSafeAreaPaddingDp.finiteIn(0f, 120f, 16f),
        tvLayoutProfileVersion = tvLayoutProfileVersion.coerceAtLeast(0),
        controllerUiSounds = controllerUiSounds,
        autoFullScreen = true,
    )
}

class SettingsStore(context: Context) {
    private val prefs = ExternalPrefs.get(context, STORE_NAME)
    private val androidTvProfile = isAndroidTvProfile(context)
    private val _settings = MutableStateFlow(
        load()
            .withCurrentStreamPresentationDefaults(androidTvProfile)
            .normalizedForAndroid(),
    )
    val settings: StateFlow<AppSettings> = _settings

    private fun load(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching { OpenNowJson.decodeFromString<AppSettings>(raw) }.getOrElse { AppSettings() }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
            .withCurrentStreamPresentationDefaults(androidTvProfile)
            .normalizedForAndroid()
        prefs.edit().putString(KEY_SETTINGS, OpenNowJson.encodeToString(next)).apply()
        _settings.value = next
    }

    fun replace(next: AppSettings) {
        val normalized = next
            .withCurrentStreamPresentationDefaults(androidTvProfile)
            .normalizedForAndroid()
        prefs.edit().putString(KEY_SETTINGS, OpenNowJson.encodeToString(normalized)).apply()
        _settings.value = normalized
    }

    fun reset() {
        replace(AppSettings())
    }

}

class AuthStore(context: Context) {
    private val sharedPrefs = context.applicationContext.getSharedPreferences(SECURE_STORE_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadAndMigrate(context))
    val state: StateFlow<PersistedAuthState> = _state

    private fun loadAndMigrate(context: Context): PersistedAuthState {
        val legacyPrefs = ExternalPrefs.get(context, STORE_NAME)

        // Migrate auth credentials if not yet in secure storage
        val hasSecureAuth = sharedPrefs.contains(KEY_AUTH)
        var migratedState: PersistedAuthState? = null
        if (!hasSecureAuth) {
            val legacyRaw = legacyPrefs.getString(KEY_AUTH, null)
            if (!legacyRaw.isNullOrBlank()) {
                val parsed = runCatching { OpenNowJson.decodeFromString<PersistedAuthState>(legacyRaw) }.getOrNull()
                if (parsed != null) {
                    val secureCommitSuccess = sharedPrefs.edit().putString(KEY_AUTH, legacyRaw).commit()
                    if (secureCommitSuccess) {
                        migratedState = parsed
                        legacyPrefs.edit().remove(KEY_AUTH).commit()
                    }
                }
            }
        }

        // Migrate device ID independently — always run even if auth was already migrated,
        // since hasSecureAuth being true does not guarantee KEY_DEVICE_ID is in secure storage.
        if (!sharedPrefs.contains(KEY_DEVICE_ID)) {
            val legacyDeviceId = legacyPrefs.getString(KEY_DEVICE_ID, null)
            if (!legacyDeviceId.isNullOrBlank()) {
                val secureCommitSuccess = sharedPrefs.edit().putString(KEY_DEVICE_ID, legacyDeviceId).commit()
                if (secureCommitSuccess) {
                    legacyPrefs.edit().remove(KEY_DEVICE_ID).commit()
                }
            }
        }

        if (migratedState != null) {
            return migratedState
        }
        return load()
    }

    private fun load(): PersistedAuthState {
        val raw = sharedPrefs.getString(KEY_AUTH, null) ?: return PersistedAuthState()
        return runCatching { OpenNowJson.decodeFromString<PersistedAuthState>(raw) }.getOrElse { PersistedAuthState() }
    }

    fun reload(): PersistedAuthState = synchronized(AUTH_STORE_LOCK) {
        load().also { latest -> _state.value = latest }
    }

    fun save(next: PersistedAuthState) = synchronized(AUTH_STORE_LOCK) {
        sharedPrefs.edit().putString(KEY_AUTH, OpenNowJson.encodeToString(next)).commit()
        _state.value = next
    }

    fun activeSession(): AuthSession? = synchronized(AUTH_STORE_LOCK) {
        val state = _state.value
        state.sessions.firstOrNull { it.user.userId == state.activeUserId } ?: state.sessions.firstOrNull()
    }

    fun setActiveSession(userId: String) = synchronized(AUTH_STORE_LOCK) {
        val current = _state.value
        val session = current.sessions.firstOrNull { it.user.userId == userId } ?: return@synchronized
        save(current.copy(activeUserId = session.user.userId, selectedProvider = session.provider))
    }

    fun upsertSession(session: AuthSession) = synchronized(AUTH_STORE_LOCK) {
        val current = _state.value
        val sessions = buildList {
            add(session)
            addAll(current.sessions.filterNot { it.user.userId == session.user.userId })
        }
        save(
            current.copy(
                sessions = sessions,
                activeUserId = session.user.userId,
                selectedProvider = session.provider,
            ),
        )
    }

    fun updateSessionIfUnchanged(expected: AuthSession, updated: AuthSession): Boolean = synchronized(AUTH_STORE_LOCK) {
        val current = _state.value
        val existing = current.sessions.firstOrNull { it.user.userId == expected.user.userId }
        if (existing != expected) return@synchronized false
        val sessions = current.sessions.map { session ->
            if (session.user.userId == expected.user.userId) updated else session
        }
        save(current.copy(sessions = sessions))
        true
    }

    fun removeSession(userId: String) = synchronized(AUTH_STORE_LOCK) {
        val current = _state.value
        val sessions = current.sessions.filterNot { it.user.userId == userId }
        save(current.copy(sessions = sessions, activeUserId = sessions.firstOrNull()?.user?.userId))
    }

    fun clear() = synchronized(AUTH_STORE_LOCK) {
        save(PersistedAuthState())
    }

    fun stableDeviceId(): String {
        val existing = sharedPrefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val next = UUID.randomUUID().toString()
        sharedPrefs.edit().putString(KEY_DEVICE_ID, next).commit()
        return next
    }
}

class CatalogCacheStore(context: Context) {
    private val appContext = context.applicationContext

    init {
        // Catalog payloads reached multiple megabytes and shared a file with ordinary
        // settings. Every small settings commit therefore rewrote the entire catalog on
        // the UI thread. Drop the old cache (it is disposable) and keep it isolated.
        val legacyPrefs = ExternalPrefs.get(appContext, STORE_NAME)
        val legacyKeys = legacyPrefs.all.keys.filter { it.startsWith(KEY_CATALOG_CACHE_PREFIX) }
        if (legacyKeys.isNotEmpty()) {
            legacyPrefs.edit().apply {
                legacyKeys.forEach(::remove)
            }.apply()
        }
    }

    // Cache construction can parse a sizeable file, so defer it until a caller already
    // running on Dispatchers.IO asks for cache data.
    private val prefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ExternalPrefs.get(appContext, CATALOG_CACHE_STORE_NAME)
    }

    fun loadMainGames(userId: String, providerStreamingBaseUrl: String): List<GameInfo>? =
        loadGameList(key("main", userId, providerStreamingBaseUrl))

    fun saveMainGames(userId: String, providerStreamingBaseUrl: String, games: List<GameInfo>) {
        saveGameList(key("main", userId, providerStreamingBaseUrl), games)
    }

    fun loadLibraryGames(userId: String, providerStreamingBaseUrl: String): List<GameInfo>? =
        loadGameList(key("library", userId, providerStreamingBaseUrl))

    fun saveLibraryGames(userId: String, providerStreamingBaseUrl: String, games: List<GameInfo>) {
        saveGameList(key("library", userId, providerStreamingBaseUrl), games)
    }

    fun loadCatalog(
        userId: String,
        providerStreamingBaseUrl: String,
        searchQuery: String,
        sortId: String,
        filterIds: List<String>,
    ): CatalogBrowseResult? =
        load(key("catalog", userId, providerStreamingBaseUrl, searchQuery, sortId, filterIds.sorted().joinToString(",")))

    fun saveCatalog(
        userId: String,
        providerStreamingBaseUrl: String,
        searchQuery: String,
        sortId: String,
        filterIds: List<String>,
        result: CatalogBrowseResult,
    ) {
        save(key("catalog", userId, providerStreamingBaseUrl, searchQuery, sortId, filterIds.sorted().joinToString(",")), result)
    }

    fun clear(): Int {
        val keys = prefs.all.keys.filter { it.startsWith(KEY_CATALOG_CACHE_PREFIX) }
        if (keys.isEmpty()) return 0
        prefs.edit().apply {
            keys.forEach(::remove)
        }.apply()
        return keys.size
    }

    private fun loadGameList(key: String): List<GameInfo>? =
        load(key, ListSerializer(GameInfo.serializer()))

    private fun saveGameList(key: String, games: List<GameInfo>) {
        save(key, games, ListSerializer(GameInfo.serializer()))
    }

    private inline fun <reified T> load(key: String): T? =
        runCatching {
            val raw = prefs.getString(storageKey(key), null) ?: return null
            val obj = OpenNowJson.parseToJsonElement(raw).jsonObject
            val expiresAt = obj["expiresAt"]?.jsonPrimitive?.longOrNull ?: return null
            if (System.currentTimeMillis() > expiresAt) return null
            val data = obj["data"] ?: return null
            OpenNowJson.decodeFromJsonElement<T>(data)
        }.getOrNull()

    private fun <T> load(key: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        runCatching {
            val raw = prefs.getString(storageKey(key), null) ?: return null
            val obj = OpenNowJson.parseToJsonElement(raw).jsonObject
            val expiresAt = obj["expiresAt"]?.jsonPrimitive?.longOrNull ?: return null
            if (System.currentTimeMillis() > expiresAt) return null
            val data = obj["data"] ?: return null
            OpenNowJson.decodeFromJsonElement(serializer, data)
        }.getOrNull()

    private inline fun <reified T> save(key: String, data: T) {
        val now = System.currentTimeMillis()
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("expiresAt", kotlinx.serialization.json.JsonPrimitive(now + CATALOG_CACHE_TTL_MS))
            put("data", OpenNowJson.encodeToJsonElement(data))
        }
        prefs.edit().putString(storageKey(key), payload.toString()).apply()
    }

    private fun <T> save(key: String, data: T, serializer: kotlinx.serialization.KSerializer<T>) {
        val now = System.currentTimeMillis()
        val payload = kotlinx.serialization.json.buildJsonObject {
            put("expiresAt", kotlinx.serialization.json.JsonPrimitive(now + CATALOG_CACHE_TTL_MS))
            put("data", OpenNowJson.encodeToJsonElement(serializer, data))
        }
        prefs.edit().putString(storageKey(key), payload.toString()).apply()
    }

    private fun key(vararg parts: String): String =
        parts.joinToString("|") { it.trim() }

    private fun storageKey(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return KEY_CATALOG_CACHE_PREFIX + digest.joinToString("") { "%02x".format(it) }
    }
}

class QueuedGameStore(context: Context) {
    private val prefs = ExternalPrefs.get(context, STORE_NAME)

    fun load(): List<String> {
        val raw = prefs.getString(KEY_QUEUED_GAME_KEYS, null) ?: return emptyList()
        return runCatching { OpenNowJson.decodeFromString<List<String>>(raw) }
            .getOrElse { emptyList() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(QUEUED_GAME_LIMIT)
    }

    fun record(gameKey: String): List<String> {
        val normalized = gameKey.trim()
        if (normalized.isBlank()) return load()
        val next = (listOf(normalized) + load().filterNot { it == normalized })
            .take(QUEUED_GAME_LIMIT)
        prefs.edit().putString(KEY_QUEUED_GAME_KEYS, OpenNowJson.encodeToString(next)).apply()
        return next
    }
}

class AndroidUpdateNoticeStore(context: Context) {
    private val prefs = ExternalPrefs.get(context, STORE_NAME)

    fun dismissedKey(): String? =
        prefs.getString(KEY_ANDROID_UPDATE_DISMISSED_NOTICE, null)?.takeIf { it.isNotBlank() }

    fun dismiss(key: String) {
        prefs.edit().putString(KEY_ANDROID_UPDATE_DISMISSED_NOTICE, key).apply()
    }
}
