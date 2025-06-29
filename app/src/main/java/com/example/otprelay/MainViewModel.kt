package com.example.otprelay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private val Application.dataStore by preferencesDataStore(name = "settings")

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = application.dataStore
    private val API_ENDPOINT_KEY = stringPreferencesKey("api_endpoint")
    private val UPTIME_START_KEY = stringPreferencesKey("uptime_start")
    private val PROCESSED_COUNT_KEY = stringPreferencesKey("processed_count")
    private val RELAYED_COUNT_KEY = stringPreferencesKey("relayed_count")

    private val _apiEndpoint = MutableStateFlow("")
    val apiEndpoint: StateFlow<String> = _apiEndpoint.asStateFlow()

    private val _relayEnabled = MutableStateFlow(false)
    val relayEnabled: StateFlow<Boolean> = _relayEnabled.asStateFlow()

    private val _status = MutableStateFlow("Inactive")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _lastOtpRelayed = MutableStateFlow("")
    val lastOtpRelayed: StateFlow<String> = _lastOtpRelayed.asStateFlow()

    private val _uptime = MutableStateFlow(0L)
    val uptime: StateFlow<Long> = _uptime.asStateFlow()

    private val _processedCount = MutableStateFlow(0)
    val processedCount: StateFlow<Int> = _processedCount.asStateFlow()

    private val _relayedCount = MutableStateFlow(0)
    val relayedCount: StateFlow<Int> = _relayedCount.asStateFlow()

    init {
        viewModelScope.launch {
            val endpoint = dataStore.data.map { it[API_ENDPOINT_KEY] ?: "" }.first()
            _apiEndpoint.value = endpoint
        }
        // Load stats
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _uptime.value = prefs[UPTIME_START_KEY]?.let { System.currentTimeMillis() - it.toLong() } ?: 0L
                _processedCount.value = prefs[PROCESSED_COUNT_KEY]?.toIntOrNull() ?: 0
                _relayedCount.value = prefs[RELAYED_COUNT_KEY]?.toIntOrNull() ?: 0
            }
        }
        // Start uptime ticker
        viewModelScope.launch {
            while (true) {
                val start = dataStore.data.first()[UPTIME_START_KEY]?.toLongOrNull() ?: 0L
                if (start > 0) {
                    _uptime.value = System.currentTimeMillis() - start
                }
                delay(1000)
            }
        }
    }

    fun saveApiEndpoint(endpoint: String) {
        viewModelScope.launch {
            dataStore.edit { it[API_ENDPOINT_KEY] = endpoint }
            _apiEndpoint.value = endpoint
        }
    }

    fun setRelayEnabled(enabled: Boolean) {
        _relayEnabled.value = enabled
        _status.value = if (enabled) "Active" else "Inactive"
    }

    fun updateLastOtpRelayed(otp: String) {
        _lastOtpRelayed.value = otp
    }

    fun startUptimeIfNeeded() {
        viewModelScope.launch {
            val start = dataStore.data.first()[UPTIME_START_KEY]?.toLongOrNull() ?: 0L
            if (start == 0L) {
                dataStore.edit { it[UPTIME_START_KEY] = System.currentTimeMillis().toString() }
            }
        }
    }

    fun resetUptime() {
        viewModelScope.launch {
            dataStore.edit { it[UPTIME_START_KEY] = System.currentTimeMillis().toString() }
        }
    }

    fun incrementProcessed() {
        viewModelScope.launch {
            val current = dataStore.data.first()[PROCESSED_COUNT_KEY]?.toIntOrNull() ?: 0
            dataStore.edit { it[PROCESSED_COUNT_KEY] = (current + 1).toString() }
        }
    }

    fun incrementRelayed() {
        viewModelScope.launch {
            val current = dataStore.data.first()[RELAYED_COUNT_KEY]?.toIntOrNull() ?: 0
            dataStore.edit { it[RELAYED_COUNT_KEY] = (current + 1).toString() }
        }
    }
} 