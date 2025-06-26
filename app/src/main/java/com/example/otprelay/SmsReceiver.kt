package com.example.otprelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "settings")
private val API_ENDPOINT_KEY = stringPreferencesKey("api_endpoint")

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {
            val bundle: Bundle? = intent.extras
            try {
                if (bundle != null) {
                    val pdus = bundle["pdus"] as? Array<*>
                    val messages = pdus?.mapNotNull {
                        SmsMessage.createFromPdu(it as ByteArray)
                    } ?: emptyList()
                    for (sms in messages) {
                        val messageBody = sms.messageBody
                        val sender = sms.originatingAddress ?: ""
                        val timestamp = sms.timestampMillis
                        val otp = extractOtp(messageBody)
                        if (otp != null) {
                            // Launch coroutine to get API endpoint and enqueue worker
                            CoroutineScope(Dispatchers.IO).launch {
                                val apiEndpoint = context.dataStore.data.first()[API_ENDPOINT_KEY] ?: ""
                                if (apiEndpoint.startsWith("https://")) {
                                    val inputData = Data.Builder()
                                        .putString(KEY_OTP, otp)
                                        .putString(KEY_SENDER, sender)
                                        .putLong(KEY_TIMESTAMP, timestamp)
                                        .putString(KEY_MESSAGE, messageBody)
                                        .putString(KEY_API_ENDPOINT, apiEndpoint)
                                        .build()
                                    val work = OneTimeWorkRequestBuilder<OtpRelayWorker>()
                                        .setInputData(inputData)
                                        .build()
                                    WorkManager.getInstance(context).enqueue(work)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Exception: ${e.message}")
            }
        }
    }

    private fun extractOtp(message: String): String? {
        // Common OTP keywords
        val keywords = listOf("otp", "code", "verification", "verify", "password")
        val lower = message.lowercase()
        val hasKeyword = keywords.any { lower.contains(it) }
        // 4-8 digit code
        val digitRegex = Regex("\\b\\d{4,8}\\b")
        val alphaNumRegex = Regex("\\b[a-zA-Z0-9]{4,10}\\b")
        val digitMatch = digitRegex.find(message)
        if (digitMatch != null && hasKeyword) return digitMatch.value
        // Alphanumeric code with keyword
        val alphaNumMatch = alphaNumRegex.find(message)
        if (alphaNumMatch != null && hasKeyword) return alphaNumMatch.value
        // Fallback: just a 4-8 digit code
        if (digitMatch != null) return digitMatch.value
        return null
    }
} 