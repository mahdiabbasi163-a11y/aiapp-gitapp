package com.example.data.utils

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * دستیار دریافت و استخراج خودکار کد تایید (OTP) از پیامک برای ورود و فراموشی رمز عبور.
 * این ابزار تضمین می‌کند در صورتی که سیم‌کارت روی همان گوشی باشد، کد تایید بدون نیاز به ورود دستی کاربر پر شود.
 */
object SmsOtpHelper {

    private const val TAG = "SmsOtpHelper"

    /**
     * تبدیل ارقام فارسی و عربی به ارقام استاندارد انگلیسی جهت ارسال به سرور
     */
    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                in '۰'..'۹' -> sb.append((ch.code - '۰'.code + '0'.code).toChar())
                in '٠'..'٩' -> sb.append((ch.code - '٠'.code + '0'.code).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * استخراج هوشمند کد تایید ۴ تا ۶ رقمی از متن پیامک ارسالی
     */
    fun extractOtp(smsBody: String): String? {
        if (smsBody.isBlank()) return null
        val normalized = normalizeDigits(smsBody)

        // ۱. جستجو بر اساس کلمات کلیدی پیامک‌های ورود و فراموشی رمز فارسی و انگلیسی
        val keywordRegex = Regex(
            """(?:کد(?:\s*(?:تایید|یکبار\s*مصرف|فعالسازی|ورود|فراموشی(?:\s*رمز)?))?|رمز(?:\s*یکبار\s*مصرف)?|code|otp|verification|pin)\D{0,20}?(\b\d{4,6}\b)""",
            RegexOption.IGNORE_CASE
        )
        val keywordMatch = keywordRegex.find(normalized)
        if (keywordMatch != null) {
            val candidate = keywordMatch.groupValues[1]
            if (candidate.length in 4..6) return candidate
        }

        // ۲. جستجو بر اساس الگوی معکوس (مثلا: "12345 کد تایید شما در کدیار است")
        val reverseKeywordRegex = Regex(
            """(\b\d{4,6}\b)\D{0,20}?(?:کد(?:\s*(?:تایید|یکبار\s*مصرف|فعالسازی|ورود|فراموشی))?|رمز|code|otp)""",
            RegexOption.IGNORE_CASE
        )
        val reverseMatch = reverseKeywordRegex.find(normalized)
        if (reverseMatch != null) {
            val candidate = reverseMatch.groupValues[1]
            if (candidate.length in 4..6) return candidate
        }

        // ۳. استخراج هر عدد مجزای ۴ تا ۶ رقمی که شماره موبایل نباشد
        val standaloneRegex = Regex("""(?<!\d)(\d{4,6})(?!\d)""")
        val matches = standaloneRegex.findAll(normalized).toList()
        for (m in matches) {
            val candidate = m.groupValues[1]
            if (candidate.length in 4..6 && !candidate.startsWith("09")) {
                return candidate
            }
        }

        return null
    }

    /**
     * ثبت BroadcastReceiver پویا برای دریافت آنلاین پیامک به محض رسیدن به گوشی
     */
    fun registerSmsListener(
        context: Context,
        onOtpReceived: (String) -> Unit
    ): BroadcastReceiver? {
        return try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                        try {
                            val messages: Array<SmsMessage?> = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                            val sb = StringBuilder()
                            for (msg in messages) {
                                if (msg != null) {
                                    sb.append(msg.displayMessageBody ?: msg.messageBody ?: "")
                                }
                            }
                            val fullBody = sb.toString()
                            Log.d(TAG, "SMS Received: $fullBody")
                            val otp = extractOtp(fullBody)
                            if (!otp.isNullOrBlank()) {
                                Log.i(TAG, "Auto-filled OTP: $otp")
                                onOtpReceived(otp)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing SMS intent: ${e.message}", e)
                        }
                    }
                }
            }

            val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
                priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            receiver
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register SMS receiver: ${e.message}", e)
            null
        }
    }

    /**
     * لغو ثبت لیسنر پیامک با ایمنی کامل در برابر کرش
     */
    fun unregisterSmsListener(context: Context, receiver: BroadcastReceiver?) {
        if (receiver != null) {
            runCatching {
                context.unregisterReceiver(receiver)
            }.onFailure {
                Log.w(TAG, "Receiver already unregistered or error: ${it.message}")
            }
        }
    }

    /**
     * در صورت وجود دسترسی، صندوق پیامک‌های اخیر (در ۲ دقیقه گذشته) را بررسی می‌کند
     * تا اگر پیامک کمی زودتر رسیده بود نیز خودکار خوانده شود.
     */
    fun readRecentOtpFromInbox(context: Context): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.parse("content://sms/inbox")
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("body", "date"),
                null,
                null,
                "date DESC LIMIT 5"
            )
            cursor?.use {
                val now = System.currentTimeMillis()
                while (it.moveToNext()) {
                    val bodyIndex = it.getColumnIndex("body")
                    val dateIndex = it.getColumnIndex("date")
                    if (bodyIndex >= 0 && dateIndex >= 0) {
                        val body = it.getString(bodyIndex) ?: ""
                        val date = it.getLong(dateIndex)
                        // بررسی پیامک‌های تا سقف ۱۲۰ ثانیه گذشته
                        if (now - date in 0..120_000L) {
                            val otp = extractOtp(body)
                            if (!otp.isNullOrBlank()) {
                                return otp
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS inbox: ${e.message}", e)
            null
        }
    }
}
