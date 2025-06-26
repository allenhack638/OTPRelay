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

private val Application.dataStore by preferencesDataStore(name = "settings")

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = application.dataStore
    private val API_ENDPOINT_KEY = stringPreferencesKey("api_endpoint")

    private val _apiEndpoint = MutableStateFlow("")
    val apiEndpoint: StateFlow<String> = _apiEndpoint.asStateFlow()

    private val _relayEnabled = MutableStateFlow(false)
    val relayEnabled: StateFlow<Boolean> = _relayEnabled.asStateFlow()

    private val _status = MutableStateFlow("Inactive")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _lastOtpRelayed = MutableStateFlow("")
    val lastOtpRelayed: StateFlow<String> = _lastOtpRelayed.asStateFlow()

    init {
        viewModelScope.launch {
            val endpoint = dataStore.data.map { it[API_ENDPOINT_KEY] ?: "" }.first()
            _apiEndpoint.value = endpoint
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
} 