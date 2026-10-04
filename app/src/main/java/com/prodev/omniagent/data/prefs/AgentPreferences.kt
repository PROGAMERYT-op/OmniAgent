package com.prodev.omniagent.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.prodev.omniagent.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentPreferences private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("omniagent_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: AgentPreferences? = null

        /**
         * Returns the process-wide singleton [AgentPreferences].
         *
         * The UI (Settings screen) and the execution engine MUST share the same instance so
         * that changes written through one are immediately observed by the other's StateFlows.
         * Creating separate instances previously caused the engine to read stale provider /
         * model / toggle values.
         */
        fun getInstance(context: Context): AgentPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentPreferences(context).also { INSTANCE = it }
            }
        }

        const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
        const val KEY_SELECTED_MODEL = "selected_model"
        const val KEY_SAFETY_GUARDRAILS = "safety_guardrails_enabled"
        const val KEY_PRIVACY_SHIELD = "privacy_shield_enabled"
        const val KEY_VOICE_MODE = "voice_mode_enabled"
        const val KEY_ONBOARDING_DONE = "onboarding_completed"

        // Provider selection
        const val KEY_PROVIDER = "selected_provider"
        const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
        const val KEY_OPENROUTER_MODEL = "openrouter_selected_model"

        const val DEFAULT_MODEL = "gemini-2.0-flash"
        const val DEFAULT_PROVIDER = "gemini"
        const val DEFAULT_OPENROUTER_MODEL = "mistralai/mixtral-8x7b-instruct"

        /**
         * Placeholder shipped in `.env.example`. It is a syntactically valid key shaped string
         * but is NOT a real key, so it must be treated as "not configured" rather than being
         * sent to the API (which would otherwise fail with an opaque 400 error).
         */
        const val PLACEHOLDER_API_KEY = "AIzaSy_DEFAULT_PLACEHOLDER_KEY"
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

    private val _selectedProvider = MutableStateFlow(prefs.getString(KEY_PROVIDER, DEFAULT_PROVIDER) ?: DEFAULT_PROVIDER)
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(prefs.getString(KEY_OPENROUTER_API_KEY, "") ?: "")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _openRouterModel = MutableStateFlow(
        prefs.getString(KEY_OPENROUTER_MODEL, DEFAULT_OPENROUTER_MODEL) ?: DEFAULT_OPENROUTER_MODEL
    )
    val openRouterModel: StateFlow<String> = _openRouterModel.asStateFlow()

    // ── Gemini ───────────────────────────────────────────────────────────────

    fun getEffectiveApiKey(): String {
        val custom = prefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""
        if (custom.isNotBlank()) return custom
        val configured = BuildConfig.GEMINI_API_KEY
        return if (configured.isBlank() || configured == PLACEHOLDER_API_KEY) "" else configured
    }

    fun setCustomApiKey(key: String) {
        prefs.edit().putString(KEY_CUSTOM_API_KEY, key.trim()).apply()
        _customApiKey.value = key.trim()
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
        _selectedModel.value = model
    }

    // ── OpenRouter ───────────────────────────────────────────────────────────

    fun getEffectiveOpenRouterKey(): String {
        return prefs.getString(KEY_OPENROUTER_API_KEY, "") ?: ""
    }

    fun setOpenRouterApiKey(key: String) {
        prefs.edit().putString(KEY_OPENROUTER_API_KEY, key.trim()).apply()
        _openRouterApiKey.value = key.trim()
    }

    fun setOpenRouterModel(model: String) {
        prefs.edit().putString(KEY_OPENROUTER_MODEL, model).apply()
        _openRouterModel.value = model
    }

    // ── Provider ─────────────────────────────────────────────────────────────

    fun setProvider(provider: String) {
        prefs.edit().putString(KEY_PROVIDER, provider).apply()
        _selectedProvider.value = provider
    }

    /** Returns the active model ID regardless of provider. */
    fun getActiveModel(): String = when (_selectedProvider.value) {
        "openrouter" -> _openRouterModel.value
        else -> _selectedModel.value
    }

    // ── Shared settings ──────────────────────────────────────────────────────

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
