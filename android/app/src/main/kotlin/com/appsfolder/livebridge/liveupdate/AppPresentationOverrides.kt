package com.appsfolder.livebridge.liveupdate

import org.json.JSONObject
import java.util.Locale

internal enum class CompactTextSource(val id: String) {
    TITLE("title"),
    TEXT("text");

    companion object {
        fun from(raw: String?): CompactTextSource {
            val normalized = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return entries.firstOrNull { it.id == normalized } ?: TITLE
        }
    }
}

internal enum class NotificationIconSource(val id: String) {
    NOTIFICATION("notification"),
    APP("app");

    companion object {
        fun from(raw: String?): NotificationIconSource {
            val normalized = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return entries.firstOrNull { it.id == normalized } ?: NOTIFICATION
        }
    }
}

internal data class AppPresentationOverride(
    val compactTextSource: CompactTextSource = CompactTextSource.TITLE,
    val iconSource: NotificationIconSource = NotificationIconSource.NOTIFICATION,
    val liveDurationTimeoutMs: Long = 0L
) {
    fun isDefault(): Boolean {
        return compactTextSource == CompactTextSource.TITLE &&
                iconSource == NotificationIconSource.NOTIFICATION &&
                liveDurationTimeoutMs == -1L // Use -1 internally for inherit
    }
}

internal data class AppPresentationOverridesState(
    val defaultOverride: AppPresentationOverride = AppPresentationOverride(liveDurationTimeoutMs = -1L),
    val packageOverrides: Map<String, AppPresentationOverride> = emptyMap()
) {
    fun resolve(packageNameLower: String): AppPresentationOverride {
        val specific = packageOverrides[packageNameLower] ?: return defaultOverride
        
        val mergedCompactText = if (specific.compactTextSource != CompactTextSource.TITLE) specific.compactTextSource else defaultOverride.compactTextSource
        val mergedIconSource = if (specific.iconSource != NotificationIconSource.NOTIFICATION) specific.iconSource else defaultOverride.iconSource
        val mergedLiveDuration = if (specific.liveDurationTimeoutMs != -1L) specific.liveDurationTimeoutMs else defaultOverride.liveDurationTimeoutMs

        return AppPresentationOverride(
            compactTextSource = mergedCompactText,
            iconSource = mergedIconSource,
            liveDurationTimeoutMs = if (mergedLiveDuration == -1L) 0L else mergedLiveDuration
        )
    }

    fun isEmpty(): Boolean {
        return defaultOverride.isDefault() && packageOverrides.isEmpty()
    }
}

internal object AppPresentationOverridesCodec {
    private const val KEY_COMPACT_TEXT = "compact_text"
    private const val KEY_ICON_SOURCE = "icon_source"
    private const val KEY_LIVE_DURATION_TIMEOUT_MS = "live_duration_timeout_ms"
    private const val KEY_DEFAULT_OVERRIDE = "__default__"

    fun parse(raw: String?): AppPresentationOverridesState? {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return AppPresentationOverridesState()
        }

        val root = try {
            JSONObject(normalized)
        } catch (_: Throwable) {
            return null
        }

        val defaultOverride = parseEntry(root.optJSONObject(KEY_DEFAULT_OVERRIDE))
            ?: AppPresentationOverride()
        val values = mutableMapOf<String, AppPresentationOverride>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == KEY_DEFAULT_OVERRIDE) {
                continue
            }
            val packageName = key.trim().lowercase(Locale.ROOT)
            if (packageName.isEmpty()) {
                continue
            }
            val entry = parseEntry(root.optJSONObject(key)) ?: continue
            if (entry != defaultOverride) {
                values[packageName] = entry
            }
        }

        return AppPresentationOverridesState(
            defaultOverride = defaultOverride,
            packageOverrides = values
        )
    }

    fun encode(state: AppPresentationOverridesState): String {
        val root = JSONObject()
        if (!state.defaultOverride.isDefault()) {
            root.put(KEY_DEFAULT_OVERRIDE, encodeEntry(state.defaultOverride))
        }
        state.packageOverrides.toSortedMap().forEach { (packageName, entry) ->
            if (entry == state.defaultOverride) {
                return@forEach
            }
            root.put(packageName, encodeEntry(entry))
        }
        return root.toString()
    }

    fun normalizeForStorage(raw: String?): String? {
        val parsed = parse(raw) ?: return null
        return if (parsed.isEmpty()) "" else encode(parsed)
    }

    fun normalizeForDownload(raw: String?): String? {
        val parsed = parse(raw) ?: return null
        return encode(parsed)
    }

    private fun parseEntry(item: JSONObject?): AppPresentationOverride? {
        item ?: return null
        return AppPresentationOverride(
            compactTextSource = CompactTextSource.from(item.optString(KEY_COMPACT_TEXT)),
            iconSource = NotificationIconSource.from(item.optString(KEY_ICON_SOURCE)),
            liveDurationTimeoutMs = item.optLong(KEY_LIVE_DURATION_TIMEOUT_MS, -1L)
        )
    }

    private fun encodeEntry(entry: AppPresentationOverride): JSONObject {
        return JSONObject().apply {
            put(KEY_COMPACT_TEXT, entry.compactTextSource.id)
            put(KEY_ICON_SOURCE, entry.iconSource.id)
            if (entry.liveDurationTimeoutMs != -1L) {
                put(KEY_LIVE_DURATION_TIMEOUT_MS, entry.liveDurationTimeoutMs)
            }
        }
    }
}

internal object AppPresentationOverridesLoader {
    @Volatile
    private var cachedRaw: String? = null

    @Volatile
    private var cachedOverrides: AppPresentationOverridesState = AppPresentationOverridesState()

    fun get(prefs: ConverterPrefs): AppPresentationOverridesState {
        val raw = prefs.getAppPresentationOverridesRaw()
        cachedOverrides.let { existing ->
            if (cachedRaw == raw) {
                return existing
            }
        }

        synchronized(this) {
            cachedOverrides.let { existing ->
                if (cachedRaw == raw) {
                    return existing
                }
            }

            val parsed = AppPresentationOverridesCodec.parse(raw) ?: AppPresentationOverridesState()
            cachedRaw = raw
            cachedOverrides = parsed
            return parsed
        }
    }

    fun invalidate() {
        synchronized(this) {
            cachedRaw = null
            cachedOverrides = AppPresentationOverridesState()
        }
    }
}
