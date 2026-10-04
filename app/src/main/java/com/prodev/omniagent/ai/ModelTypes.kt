package com.prodev.omniagent.ai

/**
 * Represents a single AI model available from a provider.
 * Used by both [GeminiAgentRepository] and [OpenRouterRepository].
 */
data class AvailableModel(
    /** The raw model identifier used in API calls (e.g. "gemini-1.5-flash", "mistralai/mixtral-8x7b-instruct"). */
    val id: String,
    /** Human-readable display name shown in the UI. */
    val displayName: String,
    /** Optional description / context window info. */
    val description: String = "",
    /** Provider tag: "gemini" or "openrouter". */
    val provider: String = "gemini"
)
