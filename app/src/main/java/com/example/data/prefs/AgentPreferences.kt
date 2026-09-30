package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentPreferences(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("omniagent_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
        const val KEY_SELECTED_MODEL = "selected_model"
        const val KEY_SAFETY_GUARDRAILS = "safety_guardrails_enabled"
        const val KEY_PRIVACY_SHIELD = "privacy_shield_enabled"
        const val KEY_VOICE_MODE = "voice_mode_enabled"
        const val KEY_ONBOARDING_DONE = "onboarding_completed"

        const val DEFAULT_MODEL = "gemini-3.5-flash"
    }

    private val _customApiKey = MutableStateFlow(prefs.getString(KEY_CUSTOM_API_KEY, "") ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _safetyGuardrails = MutableStateFlow(prefs.getBoolean(KEY_SAFETY_GUARDRAILS, true))
    val safetyGuardrails: StateFlow<Boolean> = _safetyGuardrails.asStateFlow()

    private val _privacyShield = MutableStateFlow(prefs.getBoolean(KEY_PRIVACY_SHIELD, true))
    val privacyShield: StateFlow<Boolean> = _privacyShield.asStateFlow()

    private val _voiceMode = MutableStateFlow(prefs.getBoolean(KEY_VOICE_MODE, false))
    val voiceMode: StateFlow<Boolean> = _voiceMode.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    fun getEffectiveApiKey(): String {
        val custom = prefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""
        if (custom.isNotBlank()) return custom
        return BuildConfig.GEMINI_API_KEY.ifBlank { "" }
    }

    fun setCustomApiKey(key: String) {
        prefs.edit().putString(KEY_CUSTOM_API_KEY, key.trim()).apply()
        _customApiKey.value = key.trim()
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
        _selectedModel.value = model
    }

    fun setSafetyGuardrails(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAFETY_GUARDRAILS, enabled).apply()
        _safetyGuardrails.value = enabled
    }

    fun setPrivacyShield(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_SHIELD, enabled).apply()
        _privacyShield.value = enabled
    }

    fun setVoiceMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_MODE, enabled).apply()
        _voiceMode.value = enabled
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
        _onboardingCompleted.value = completed
    }
}
