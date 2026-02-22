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
    val iconSource: NotificationIconSource = NotificationIconSource.NOTIFICATION
) {
    fun isDefault(): Boolean {
        return compactTextSource == CompactTextSource.TITLE &&
                iconSource == NotificationIconSource.NOTIFICATION
    }
}

internal object AppPresentationOverridesCodec {
    private const val KEY_COMPACT_TEXT = "compact_text"
    private const val KEY_ICON_SOURCE = "icon_source"

    fun parse(raw: String?): Map<String, AppPresentationOverride>? {
        val normalized = raw?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return emptyMap()
        }

        val root = try {
            JSONObject(normalized)
        } catch (_: Throwable) {
            return null
        }

        val values = mutableMapOf<String, AppPresentationOverride>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val packageName = key.trim().lowercase(Locale.ROOT)
            if (packageName.isEmpty()) {
                continue
            }
            val item = root.optJSONObject(key) ?: continue
            val entry = AppPresentationOverride(
                compactTextSource = CompactTextSource.from(item.optString(KEY_COMPACT_TEXT)),
                iconSource = NotificationIconSource.from(item.optString(KEY_ICON_SOURCE))
            )
            if (!entry.isDefault()) {
                values[packageName] = entry
            }
        }

        return values
    }

    fun encode(overrides: Map<String, AppPresentationOverride>): String {
        val root = JSONObject()
        overrides.toSortedMap().forEach { (packageName, entry) ->
            if (entry.isDefault()) {
                return@forEach
            }
            val item = JSONObject().apply {
                put(KEY_COMPACT_TEXT, entry.compactTextSource.id)
                put(KEY_ICON_SOURCE, entry.iconSource.id)
            }
            root.put(packageName, item)
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
}

internal object AppPresentationOverridesLoader {
    @Volatile
    private var cachedRaw: String? = null

    @Volatile
    private var cachedOverrides: Map<String, AppPresentationOverride> = emptyMap()

    fun get(prefs: ConverterPrefs): Map<String, AppPresentationOverride> {
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

            val parsed = AppPresentationOverridesCodec.parse(raw) ?: emptyMap()
            cachedRaw = raw
            cachedOverrides = parsed
            return parsed
        }
    }

    fun invalidate() {
        synchronized(this) {
            cachedRaw = null
            cachedOverrides = emptyMap()
        }
    }
}
