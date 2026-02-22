package com.appsfolder.livebridge.liveupdate

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class LiveParserDictionary(
    val smartRules: List<SmartRuleEntry>,
    val otpStrongTriggers: Set<String>,
    val otpLooseTriggerPattern: Regex,
    val moneyContextPattern: Regex,
    val otpCodePatterns: List<Regex>,
    val orderContextHints: Set<String>,
    val entityTokenPatterns: List<Regex>,
    val statusLabels: Map<String, StageLabelsByLocale>
) {
    fun resolveStatusText(ruleId: String, stageValue: Int, isRussianLocale: Boolean): String? {
        val labels = statusLabels[ruleId.lowercase(Locale.ROOT)] ?: return null
        return labels.resolve(stageValue, isRussianLocale)
    }

    companion object {
        fun default(): LiveParserDictionary {
            val regexOptions = setOf(RegexOption.IGNORE_CASE)
            return LiveParserDictionary(
                smartRules = listOf(
                    SmartRuleEntry(
                        id = "food",
                        maxStage = 4,
                        packageHints = setOf(
                            "kfc", "ubereats", "doordash", "grubhub", "delivery", "еда", "food"
                        ),
                        textTriggers = setOf(
                            "заказ", "order", "доставка", "delivery", "курьер", "courier", "еда", "food"
                        ),
                        signals = listOf(
                            SmartSignalEntry(
                                stage = 4,
                                pattern = Regex(
                                    "(доставлен|получен|delivered|completed|приятного аппетита|enjoy)",
                                    regexOptions
                                )
                            ),
                            SmartSignalEntry(
                                stage = 3,
                                pattern = Regex(
                                    "(курьер[^\\n]*в пути|out for delivery|on the way|едет к вам)",
                                    regexOptions
                                )
                            ),
                            SmartSignalEntry(
                                stage = 2,
                                pattern = Regex(
                                    "(собираем|упаковываем|почти готов|packed|almost ready|ready for pickup)",
                                    regexOptions
                                )
                            ),
                            SmartSignalEntry(
                                stage = 1,
                                pattern = Regex(
                                    "(готовится|готовим|preparing|in kitchen|is being prepared|is being cooked|being cooked|cooking|cooked for you)",
                                    regexOptions
                                )
                            ),
                            SmartSignalEntry(
                                stage = 0,
                                pattern = Regex(
                                    "(заказ[^\\n]*(принят|создан)|order[^\\n]*(received|accepted|confirmed))",
                                    regexOptions
                                )
                            )
                        )
                    ),
                    SmartRuleEntry(
                        id = "taxi",
                        maxStage = 4,
                        packageHints = setOf("taxi", "uber", "lyft", "bolt", "yango", "yandex"),
                        textTriggers = setOf("такси", "taxi", "ride", "поездка", "водитель", "driver"),
                        signals = listOf(
                            SmartSignalEntry(
                                stage = 4,
                                pattern = Regex(
                                    "(поездка завершена|trip completed|arrived at destination|you'?ve arrived)",
                                    regexOptions
                                )
                            ),
                            SmartSignalEntry(
                                stage = 3,
                                pattern = Regex(
                                    "(поездка началась|trip started|ride started|enjoy your ride)",
                                    regexOptions
                                )
                            ),
                            SmartSignalEntry(
                                stage = 2,
                                pattern = Regex(
                                    "(едет к вам|подъезжает|driver[^\\n]*arriving|arriving in|on the way)",
                                    regexOptions
                                )
                            ),
                            SmartSignalEntry(
                                stage = 1,
                                pattern = Regex(
                                    "(водитель найден|driver found|driver assigned|matched with driver)",
                                    regexOptions
                                )
                            ),
                            SmartSignalEntry(
                                stage = 0,
                                pattern = Regex(
                                    "(поиск[^\\n]*водител|ищем[^\\n]*водител|searching for (a )?driver|finding driver)",
                                    regexOptions
                                )
                            )
                        )
                    )
                ),
                otpStrongTriggers = setOf(
                    "otp",
                    "one-time password",
                    "one time password",
                    "verification code",
                    "security code",
                    "login code",
                    "passcode",
                    "2fa",
                    "auth code",
                    "sms code",
                    "код подтверждения",
                    "код входа",
                    "код для входа",
                    "код аккаунта",
                    "одноразовый",
                    "код из смс"
                ),
                otpLooseTriggerPattern = Regex(
                    "(?:(?:\\bcode\\b|\\bкод\\b)\\s*[:#-]?\\s*\\d{4,8})",
                    regexOptions
                ),
                moneyContextPattern = Regex(
                    "(₽|руб\\.?|rub|usd|eur|kzt|тенге|р\\.|\\$|€|price|total|amount|sum|сумм|цена|стоимост|итого)",
                    regexOptions
                ),
                otpCodePatterns = listOf(
                    Regex("(?<!\\d)(\\d{4,8})(?!\\d)"),
                    Regex("(?<!\\d)(\\d(?:[\\s-]?\\d){3,7})(?!\\d)")
                ),
                orderContextHints = setOf("заказ", "order", "delivery", "достав"),
                entityTokenPatterns = listOf(
                    Regex("(?:заказ|order|trip|ride|поездк[аы])\\s*(?:#|№)?\\s*([a-z0-9-]{2,16})"),
                    Regex("(?:#|№)\\s*([a-z0-9-]{2,16})"),
                    Regex("\\b(\\d{4,10})\\b")
                ),
                statusLabels = mapOf(
                    "food" to StageLabelsByLocale(
                        ru = mapOf(0 to "Оплачен", 1 to "Готовится", 2 to "Готов"),
                        en = mapOf(0 to "Paid", 1 to "Cooking", 2 to "Ready")
                    ),
                    "taxi" to StageLabelsByLocale(
                        ru = mapOf(0 to "Поиск", 1 to "Водитель", 2 to "Подъезжает", 3 to "В пути", 4 to "Завершено"),
                        en = mapOf(0 to "Searching", 1 to "Driver", 2 to "Arriving", 3 to "On trip", 4 to "Done")
                    )
                )
            )
        }

        fun fromJson(raw: String): LiveParserDictionary? {
            val defaults = default()
            val root = try {
                JSONObject(raw)
            } catch (_: Throwable) {
                return null
            }

            val smartRules = parseSmartRules(root.optJSONArray("smart_rules")) ?: defaults.smartRules
            val otpStrongTriggers =
                parseStringSet(root.optJSONArray("otp_strong_triggers")).ifEmpty { defaults.otpStrongTriggers }
            val otpLooseTriggerPattern = parseRegex(
                root.optString("otp_loose_trigger_pattern"),
                ignoreCase = true
            ) ?: defaults.otpLooseTriggerPattern
            val moneyContextPattern = parseRegex(
                root.optString("money_context_pattern"),
                ignoreCase = true
            ) ?: defaults.moneyContextPattern

            val otpCodePatterns =
                parseRegexList(root.optJSONArray("otp_code_patterns"), ignoreCase = false).ifEmpty {
                    defaults.otpCodePatterns
                }
            val orderContextHints =
                parseStringSet(root.optJSONArray("order_context_hints")).ifEmpty { defaults.orderContextHints }
            val entityTokenPatterns =
                parseRegexList(root.optJSONArray("entity_token_patterns"), ignoreCase = false).ifEmpty {
                    defaults.entityTokenPatterns
                }
            val statusLabels = parseStatusLabels(root.optJSONObject("status_labels")).ifEmpty { defaults.statusLabels }

            return LiveParserDictionary(
                smartRules = smartRules,
                otpStrongTriggers = otpStrongTriggers,
                otpLooseTriggerPattern = otpLooseTriggerPattern,
                moneyContextPattern = moneyContextPattern,
                otpCodePatterns = otpCodePatterns,
                orderContextHints = orderContextHints,
                entityTokenPatterns = entityTokenPatterns,
                statusLabels = statusLabels
            )
        }

        private fun parseSmartRules(raw: JSONArray?): List<SmartRuleEntry>? {
            raw ?: return null
            val parsed = mutableListOf<SmartRuleEntry>()

            for (index in 0 until raw.length()) {
                val item = raw.optJSONObject(index) ?: continue
                val id = item.optString("id").trim().lowercase(Locale.ROOT)
                if (id.isEmpty()) {
                    continue
                }

                val maxStage = item.optInt("max_stage", 1).coerceAtLeast(1)
                val packageHints = parseStringSet(item.optJSONArray("package_hints"))
                val textTriggers = parseStringSet(item.optJSONArray("text_triggers"))
                val signals = parseSignals(item.optJSONArray("signals"))

                if (signals.isEmpty()) {
                    continue
                }

                parsed += SmartRuleEntry(
                    id = id,
                    maxStage = maxStage,
                    packageHints = packageHints,
                    textTriggers = textTriggers,
                    signals = signals
                )
            }

            return parsed.ifEmpty { null }
        }

        private fun parseSignals(raw: JSONArray?): List<SmartSignalEntry> {
            raw ?: return emptyList()
            val signals = mutableListOf<SmartSignalEntry>()
            for (index in 0 until raw.length()) {
                val item = raw.optJSONObject(index) ?: continue
                val stage = item.optInt("stage", Int.MIN_VALUE)
                if (stage == Int.MIN_VALUE) {
                    continue
                }
                val pattern = parseRegex(item.optString("pattern"), ignoreCase = true) ?: continue
                signals += SmartSignalEntry(stage = stage, pattern = pattern)
            }
            return signals
        }

        private fun parseStringSet(raw: JSONArray?): Set<String> {
            raw ?: return emptySet()
            val values = mutableSetOf<String>()
            for (index in 0 until raw.length()) {
                val value = raw.optString(index).trim().lowercase(Locale.ROOT)
                if (value.isNotEmpty()) {
                    values += value
                }
            }
            return values
        }

        private fun parseRegexList(raw: JSONArray?, ignoreCase: Boolean): List<Regex> {
            raw ?: return emptyList()
            val values = mutableListOf<Regex>()
            for (index in 0 until raw.length()) {
                parseRegex(raw.optString(index), ignoreCase)?.let(values::add)
            }
            return values
        }

        private fun parseStatusLabels(raw: JSONObject?): Map<String, StageLabelsByLocale> {
            raw ?: return emptyMap()
            val values = mutableMapOf<String, StageLabelsByLocale>()
            val keys = raw.keys()
            while (keys.hasNext()) {
                val ruleId = keys.next().trim().lowercase(Locale.ROOT)
                if (ruleId.isEmpty()) {
                    continue
                }
                val entry = raw.optJSONObject(ruleId) ?: continue
                val ru = parseStageLabelMap(entry.optJSONObject("ru"))
                val en = parseStageLabelMap(entry.optJSONObject("en"))
                if (ru.isEmpty() && en.isEmpty()) {
                    continue
                }
                values[ruleId] = StageLabelsByLocale(ru = ru, en = en)
            }
            return values
        }

        private fun parseStageLabelMap(raw: JSONObject?): Map<Int, String> {
            raw ?: return emptyMap()
            val values = mutableMapOf<Int, String>()
            val keys = raw.keys()
            while (keys.hasNext()) {
                val stageKey = keys.next()
                val stage = stageKey.toIntOrNull() ?: continue
                val value = raw.optString(stageKey).trim()
                if (value.isNotEmpty()) {
                    values[stage] = value
                }
            }
            return values.toSortedMap()
        }

        private fun parseRegex(value: String?, ignoreCase: Boolean): Regex? {
            val normalized = value?.trim().orEmpty()
            if (normalized.isEmpty()) {
                return null
            }
            return try {
                if (ignoreCase) {
                    Regex(normalized, setOf(RegexOption.IGNORE_CASE))
                } else {
                    Regex(normalized)
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
}

internal data class SmartRuleEntry(
    val id: String,
    val maxStage: Int,
    val packageHints: Set<String>,
    val textTriggers: Set<String>,
    val signals: List<SmartSignalEntry>
) {
    fun isRelevant(packageNameLower: String, textLower: String): Boolean {
        return packageHints.any(packageNameLower::contains) || textTriggers.any(textLower::contains)
    }
}

internal data class SmartSignalEntry(
    val stage: Int,
    val pattern: Regex
)

internal data class StageLabelsByLocale(
    val ru: Map<Int, String>,
    val en: Map<Int, String>
) {
    fun resolve(stageValue: Int, isRussianLocale: Boolean): String? {
        val source = if (isRussianLocale) {
            if (ru.isNotEmpty()) ru else en
        } else {
            if (en.isNotEmpty()) en else ru
        }
        if (source.isEmpty()) {
            return null
        }

        source[stageValue]?.let { return it }
        val fallbackStage = source.keys.filter { it <= stageValue }.maxOrNull() ?: source.keys.minOrNull()
        return fallbackStage?.let(source::get)
    }
}

internal object LiveParserDictionaryLoader {
    private const val TAG = "LiveParserDictionary"
    private const val ASSET_FILE_NAME = "liveupdate_dictionary.json"
    private const val ASSET_CACHE_KEY = "__asset__"

    @Volatile
    private var cachedSourceKey: String? = null

    @Volatile
    private var cachedDictionary: LiveParserDictionary? = null

    fun get(context: Context, prefs: ConverterPrefs): LiveParserDictionary {
        val customRaw = prefs.getCustomParserDictionaryRaw()
        val sourceKey = customRaw ?: ASSET_CACHE_KEY
        cachedDictionary?.let { existing ->
            if (cachedSourceKey == sourceKey) {
                return existing
            }
        }
        synchronized(this) {
            cachedDictionary?.let { existing ->
                if (cachedSourceKey == sourceKey) {
                    return existing
                }
            }

            val loaded = if (!customRaw.isNullOrBlank()) {
                LiveParserDictionary.fromJson(customRaw)
                    ?: loadFromAssets(context)
                    ?: LiveParserDictionary.default()
            } else {
                loadFromAssets(context) ?: LiveParserDictionary.default()
            }

            cachedSourceKey = sourceKey
            cachedDictionary = loaded
            return loaded
        }
    }

    fun invalidate() {
        synchronized(this) {
            cachedSourceKey = null
            cachedDictionary = null
        }
    }

    private fun loadFromAssets(context: Context): LiveParserDictionary? {
        return try {
            val raw = context.assets.open(ASSET_FILE_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
            LiveParserDictionary.fromJson(raw)
        } catch (error: Throwable) {
            Log.w(TAG, "Failed to load parser dictionary from assets: ${error.message}")
            null
        }
    }
}
