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
private const val KEY_SETTINGS = "settings"
private const val KEY_AUTH = "auth"
private const val KEY_DEVICE_ID = "gfn_device_id"
private const val KEY_CATALOG_CACHE_PREFIX = "catalog_cache_"
private const val KEY_ANDROID_UPDATE_DISMISSED_NOTICE = "android_update_dismissed_notice"
private const val KEY_QUEUED_GAME_KEYS = "queued_game_keys"
private const val CATALOG_CACHE_TTL_MS = 12L * 60L * 60L * 1000L
private const val QUEUED_GAME_LIMIT = 24

class ExternalPrefs(context: Context, name: String) {
    private val file = File(context.getExternalFilesDir(null), "$name.xml")
    private val data = mutableMapOf<String, String>()
    private val lock = Any()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    init {
        synchronized(lock) {
            migrateFromInternal(context, name)
            load()
        }
    }

    private fun migrateFromInternal(context: Context, name: String) {
        if (file.exists()) return
        val internalPrefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        val allInternal = internalPrefs.all
        if (allInternal.isNotEmpty()) {
            allInternal.forEach { (k, v) ->
                if (v is String) data[k] = v
            }
            save(data.toMap())
            internalPrefs.edit().clear().apply()
        }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val parser = Xml.newPullParser()
            FileInputStream(file).use { fis ->
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
        }.onFailure { it.printStackTrace() }
    }

    private fun save(mapSnapshot: Map<String, String>) {
        synchronized(lock) {
            runCatching {
                file.parentFile?.mkdirs()
                file.bufferedWriter().use { writer ->
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
            }.onFailure { it.printStackTrace() }
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

        fun apply() {
            val snapshot = synchronized(lock) {
                actions.forEach { it() }
                data.toMap()
            }
            ioScope.launch {
                save(snapshot)
            }
        }
    }
}

class SettingsStore(context: Context) {
    private val prefs = ExternalPrefs(context, STORE_NAME)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings

    private fun load(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching { OpenNowJson.decodeFromString<AppSettings>(raw) }.getOrElse { AppSettings() }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value).normalizedForAndroid()
        prefs.edit().putString(KEY_SETTINGS, OpenNowJson.encodeToString(next)).apply()
        _settings.value = next
    }

    fun replace(next: AppSettings) {
        val normalized = next.normalizedForAndroid()
        prefs.edit().putString(KEY_SETTINGS, OpenNowJson.encodeToString(normalized)).apply()
        _settings.value = normalized
    }

    fun reset() {
        replace(AppSettings())
    }

    private fun AppSettings.normalizedForAndroid(): AppSettings {
        val compatibleStream = stream.withCodecColorCompatibility()
        val lowPowerSafe = compatibleStream.copy(
            codec = compatibleStream.codec,
            sessionProxyUrl = stream.sessionProxyUrl.trim(),
            maxBitrateMbps = compatibleStream.maxBitrateMbps.coerceIn(1, 150),
            fps = compatibleStream.fps.coerceIn(30, 360),
            streamSharpeningAmount = compatibleStream.streamSharpeningAmount.coerceIn(0f, 1f),
        )
        return copy(
            stream = lowPowerSafe,
            posterSizeScale = posterSizeScale.coerceIn(0.82f, 1.08f),
            androidTouch = androidTouch.copy(
                opacity = androidTouch.opacity.coerceIn(0.15f, 1f),
                scale = androidTouch.scale.coerceIn(0.6f, 1.4f),
                buttonScale = androidTouch.buttonScale.coerceIn(0.65f, 1.5f),
                stickScale = androidTouch.stickScale.coerceIn(0.65f, 1.5f),
                edgePaddingDp = androidTouch.edgePaddingDp.coerceIn(0f, 72f),
                bottomPaddingDp = androidTouch.bottomPaddingDp.coerceIn(0f, 120f),
                leftOffsetXDp = androidTouch.leftOffsetXDp.coerceIn(-220f, 220f),
                leftOffsetYDp = androidTouch.leftOffsetYDp.coerceIn(-160f, 160f),
                rightOffsetXDp = androidTouch.rightOffsetXDp.coerceIn(-220f, 220f),
                rightOffsetYDp = androidTouch.rightOffsetYDp.coerceIn(-160f, 160f),
            ),
            streamIntroMusic = streamIntroMusic,
            queueReadyMusic = queueReadyMusic,
            stretchStreamToFill = stretchStreamToFill,
            nerdCatalogBackgroundUri = nerdCatalogBackgroundUri?.trim()?.takeIf { it.isNotBlank() },
            tvSafeAreaPaddingDp = tvSafeAreaPaddingDp.coerceIn(0f, 72f),
            controllerUiSounds = controllerUiSounds,
            autoFullScreen = true,
        )
    }
}

class AuthStore(context: Context) {
    private val prefs = ExternalPrefs(context, STORE_NAME)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<PersistedAuthState> = _state

    private fun load(): PersistedAuthState {
        val raw = prefs.getString(KEY_AUTH, null) ?: return PersistedAuthState()
        return runCatching { OpenNowJson.decodeFromString<PersistedAuthState>(raw) }.getOrElse { PersistedAuthState() }
    }

    fun save(next: PersistedAuthState) {
        prefs.edit().putString(KEY_AUTH, OpenNowJson.encodeToString(next)).apply()
        _state.value = next
    }

    fun activeSession(): AuthSession? {
        val state = _state.value
        return state.sessions.firstOrNull { it.user.userId == state.activeUserId } ?: state.sessions.firstOrNull()
    }

    fun setActiveSession(userId: String) {
        val current = _state.value
        val session = current.sessions.firstOrNull { it.user.userId == userId } ?: return
        save(current.copy(activeUserId = session.user.userId, selectedProvider = session.provider))
    }

    fun upsertSession(session: AuthSession) {
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

    fun removeSession(userId: String) {
        val current = _state.value
        val sessions = current.sessions.filterNot { it.user.userId == userId }
        save(current.copy(sessions = sessions, activeUserId = sessions.firstOrNull()?.user?.userId))
    }

    fun clear() {
        save(PersistedAuthState())
    }

    fun stableDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val next = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, next).apply()
        return next
    }
}

class CatalogCacheStore(context: Context) {
    private val prefs = ExternalPrefs(context, STORE_NAME)

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
    private val prefs = ExternalPrefs(context, STORE_NAME)

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
    private val prefs = ExternalPrefs(context, STORE_NAME)

    fun dismissedKey(): String? =
        prefs.getString(KEY_ANDROID_UPDATE_DISMISSED_NOTICE, null)?.takeIf { it.isNotBlank() }

    fun dismiss(key: String) {
        prefs.edit().putString(KEY_ANDROID_UPDATE_DISMISSED_NOTICE, key).apply()
    }
}
