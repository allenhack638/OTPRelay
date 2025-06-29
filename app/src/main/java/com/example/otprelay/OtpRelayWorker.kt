package com.example.otprelay

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit

// Data keys
const val KEY_OTP = "otp"
const val KEY_SENDER = "sender"
const val KEY_TIMESTAMP = "timestamp"
const val KEY_MESSAGE = "message"
const val KEY_API_ENDPOINT = "api_endpoint"

// Data class for JSON payload
data class OtpPayload(
    val otp: String,
    val sender: String,
    val timestamp: Long,
    val message: String?
)

interface OtpRelayApi {
    @POST("")
    suspend fun relayOtp(@Body payload: OtpPayload): retrofit2.Response<Unit>
}

class OtpRelayWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    private val Context.dataStore by preferencesDataStore(name = "settings")
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val otp = inputData.getString(KEY_OTP) ?: return@withContext Result.failure()
        val sender = inputData.getString(KEY_SENDER) ?: ""
        val timestamp = inputData.getLong(KEY_TIMESTAMP, 0L)
        val message = inputData.getString(KEY_MESSAGE)
        val apiEndpoint = inputData.getString(KEY_API_ENDPOINT) ?: return@withContext Result.failure()
        if (!apiEndpoint.startsWith("https://")) return@withContext Result.failure()
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl(apiEndpoint)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val api = retrofit.create(OtpRelayApi::class.java)
            val payload = OtpPayload(otp, sender, timestamp, message)
            val response = api.relayOtp(payload)
            if (response.isSuccessful) {
                incrementRelayedCounter(applicationContext)
                return@withContext Result.success()
            } else {
                return@withContext Result.retry()
            }
        } catch (e: IOException) {
            return@withContext Result.retry()
        } catch (e: Exception) {
            return@withContext Result.failure()
        }
    }

    private fun incrementRelayedCounter(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.dataStore.data.first()
            val key = stringPreferencesKey("relayed_count")
            val current = prefs[key]?.toIntOrNull() ?: 0
            context.dataStore.edit { prefsEdit: MutablePreferences -> prefsEdit[key] = (current + 1).toString() }
        }
    }
} 