package com.appsfolder.livebridge.liveupdate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log
import com.appsfolder.livebridge.R

class SmsOtpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val granted = context.checkSelfPermission(
                android.Manifest.permission.RECEIVE_SMS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        if (intent.action != SMS_RECEIVED_ACTION) return

        val prefs = ConverterPrefs(context)
        if (!prefs.getOtpDetectionEnabled()) return

        val messages = extractSmsMessages(intent) ?: return
        val fullText = messages.joinToString(" ") { it.messageBody.orEmpty() }
        if (fullText.isBlank()) return

        val otpCode = extractOtpFromText(fullText) ?: return

        // Auto copy if enabled
        if (prefs.getOtpAutoCopyEnabled()) {
            copyToClipboard(context, otpCode)
        }

        // Fire the pill
        LiveUpdateNotifier.ensureChannel(context)
        LiveUpdateNotifier.triggerSyntheticSystemEvent(
            context = context,
            title = otpCode,
            text = "OTP • Tap to copy",
            iconResId = R.drawable.ic_stat_liveupdate,
            customDurationMs = prefs.getOtpDurationMs()
        )

        Log.d(TAG, "OTP detected and pill triggered: $otpCode")
    }

    private fun extractSmsMessages(intent: Intent): Array<SmsMessage>? {
        val pdus = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                val bundle = intent.extras ?: return null
                val format = bundle.getString("format")
                @Suppress("UNCHECKED_CAST")
                val rawPdus = bundle.get("pdus") as? Array<Any> ?: return null
                rawPdus.map { pdu ->
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                }.toTypedArray()
            } else {
                @Suppress("UNCHECKED_CAST", "DEPRECATION")
                val rawPdus = intent.extras?.get("pdus") as? Array<Any> ?: return null
                rawPdus.map { pdu ->
                    @Suppress("DEPRECATION")
                    SmsMessage.createFromPdu(pdu as ByteArray)
                }.toTypedArray()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to extract SMS PDUs", e)
            return null
        }
        return pdus.filterNotNull().toTypedArray().ifEmpty { null }
    }

    private fun extractOtpFromText(text: String): String? {
        val lower = text.lowercase()

        // Must have an OTP trigger word
        val hasOtpTrigger = OTP_TRIGGERS.any { lower.contains(it) }
        if (!hasOtpTrigger) return null

        // Find digit sequences of 4-8 digits
        for (pattern in OTP_PATTERNS) {
            for (match in pattern.findAll(text)) {
                val digits = match.groupValues.getOrNull(1)
                    ?.filter(Char::isDigit)
                    ?: match.value.filter(Char::isDigit)
                if (digits.length in 4..8) {
                    // Skip if it looks like a phone number or year
                    if (digits.length == 10 || digits.length == 11) continue
                    return digits
                }
            }
        }
        return null
    }

    private fun copyToClipboard(context: Context, code: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager ?: return
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("OTP", code)
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to copy OTP to clipboard", e)
        }
    }

    companion object {
        private const val TAG = "SmsOtpReceiver"
        private const val SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED"

        private val OTP_TRIGGERS = setOf(
            "otp", "one-time", "one time", "verification code", "verify",
            "confirmation code", "security code", "login code", "passcode",
            "2fa", "auth code", "sms code", "pin", "do not share",
            "don't share", "is your code", "is your otp", "use otp",
            "код подтверждения", "проверочный код", "код входа", "одноразовый"
        )

        private val OTP_PATTERNS = listOf(
            Regex("""(?<![.\d])(\d{4,8})(?![.\d])"""),
            Regex("""(?:code|otp|pin|код)[^\d]*(\d{4,8})""", RegexOption.IGNORE_CASE)
        )
    }
}