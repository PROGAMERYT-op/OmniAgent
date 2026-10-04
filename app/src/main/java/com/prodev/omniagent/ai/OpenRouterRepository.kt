package com.prodev.omniagent.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Repository that routes AI requests to OpenRouter (https://openrouter.ai).
 *
 * OpenRouter exposes an OpenAI-compatible Chat Completions API at
 * https://openrouter.ai/api/v1/chat/completions and a public model listing API at
 * https://openrouter.ai/api/v1/models.
 *
 * Tool calling uses the OpenAI `tools` format, and the response is mapped back to
 * the shared [AgentStepResult] / [AgentAction] types so the engine needs no changes.
 */
class OpenRouterRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ── Public API ────────────────────────────────────────────────────────────

    /** Ping a model to verify the key and connection. */
    suspend fun testConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("OpenRouter API key is empty. Please enter it in Settings."))
        }
        try {
            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Ping check. Reply with: OmniAgent AI Connected.")
                    })
                })
                put("max_tokens", 32)
            }
            val request = buildRequest(apiKey, body)
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val msg = try {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                } catch (e: Exception) { "HTTP ${response.code}: $responseBody" }
                return@withContext Result.failure(Exception(msg))
            }

            val reply = try {
                val json = JSONObject(responseBody)
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "Connected successfully")
            } catch (e: Exception) { "Connected successfully" }

            Result.success(reply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches the public model list from OpenRouter.
     * The `/api/v1/models` endpoint is available without authentication.
     * Falls back to [FALLBACK_OPENROUTER_MODELS] on any error.
     */
    suspend fun fetchModels(): Result<List<AvailableModel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .get()
                .header("HTTP-Referer", "https://github.com/omniagent-ai")
                .header("X-Title", "OmniAgent AI")
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.success(FALLBACK_OPENROUTER_MODELS)
            }

            val dataArray = JSONObject(bodyString).optJSONArray("data")
                ?: return@withContext Result.success(FALLBACK_OPENROUTER_MODELS)

            val list = mutableListOf<AvailableModel>()
            for (i in 0 until dataArray.length()) {
                val m = dataArray.optJSONObject(i) ?: continue
                val id = m.optString("id")
                if (id.isBlank()) continue
                val name = m.optString("name", id)
                val desc = buildContextDescription(m)
                list.add(AvailableModel(id = id, displayName = name, description = desc, provider = "openrouter"))
            }

            if (list.isEmpty()) Result.success(FALLBACK_OPENROUTER_MODELS)
            else Result.success(list)
        } catch (e: Exception) {
            Result.success(FALLBACK_OPENROUTER_MODELS)
        }
    }

    /** Sends the agent reasoning request to OpenRouter and parses the tool-call response. */
    suspend fun getNextAgentAction(
        apiKey: String,
        model: String,
        userGoal: String,
        screenHierarchyText: String,
        screenBitmap: Bitmap?,
        actionHistory: List<String>
    ): Result<AgentStepResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Missing OpenRouter API key. Please configure in Settings."))
        }

        try {
            val systemPrompt = """
                You are OmniAgent AI, an expert autonomous smartphone assistant.
                You are operating the user's Android phone to accomplish their goal: "$userGoal".

                You have access to:
                1. The Accessibility Node tree representing interactive UI components with their bounds (x,y coordinates).
                2. Real-time visual screenshot (if provided) when standard DOM elements are not visible.

                CRITICAL RULES:
                - Review the screen state carefully.
                - Call ONE tool function at a time to advance towards the goal.
                - Prefer clickElementByText if the target text is clearly visible in the node tree.
                - Use clickAtCoordinates(x, y) if coordinates are clear or from visual screenshot.
                - If an action involves sensitive financial, payment, account deletion, or high-risk actions, YOU MUST CALL requestUserConfirmation first!
                - When the user goal is completely fulfilled, call agentFinished(summaryMessage).
                - Keep your reasoning concise and state your next thought.
            """.trimIndent()

            val contextText = buildString {
                appendLine("User Goal: $userGoal")
                if (actionHistory.isNotEmpty()) {
                    appendLine("\nActions Executed So Far:")
                    actionHistory.takeLast(6).forEach { appendLine("- $it") }
                }
                appendLine("\nCurrent Screen Hierarchy:")
                appendLine(screenHierarchyText.take(12000))
                appendLine("\nWhat is the next step to execute? Explain your thought and invoke the corresponding tool.")
            }

            // Build messages array
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    if (screenBitmap != null) {
                        // Multimodal content array
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", contextText)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,${screenBitmap.toBase64()}")
                                    put("detail", "low")
                                })
                            })
                        })
                    } else {
                        put("content", contextText)
                    }
                })
            }

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("tools", buildOpenAiToolDeclarations())
                put("tool_choice", "auto")
                put("temperature", 0.2)
                put("max_tokens", 1024)
            }

            val request = buildRequest(apiKey, requestBody)
            val response = client.newCall(request).execute()
            val responseBodyStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorDetails = try {
                    JSONObject(responseBodyStr).getJSONObject("error").getString("message")
                } catch (e: Exception) { "HTTP ${response.code}: $responseBodyStr" }
                return@withContext Result.failure(Exception("OpenRouter API Error: $errorDetails"))
            }

            Result.success(parseOpenRouterResponse(responseBodyStr))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildRequest(apiKey: String, body: JSONObject): Request =
        Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .post(body.toString().toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/omniagent-ai")
            .header("X-Title", "OmniAgent AI")
            .build()

    private fun parseOpenRouterResponse(jsonString: String): AgentStepResult {
        val root = JSONObject(jsonString)
        val choices = root.optJSONArray("choices")
            ?: return AgentStepResult(thought = "No response from OpenRouter.", action = null, isFinished = true)

        val firstChoice = choices.optJSONObject(0)
            ?: return AgentStepResult(thought = "Empty response.", action = null, isFinished = true)

        val message = firstChoice.optJSONObject("message")
            ?: return AgentStepResult(thought = "Malformed response.", action = null, isFinished = true)

        val thoughtText = message.optString("content").takeIf { it.isNotBlank() }
        val toolCalls = message.optJSONArray("tool_calls")

        var action: AgentAction? = null
        if (toolCalls != null && toolCalls.length() > 0) {
            val firstCall = toolCalls.optJSONObject(0)
            val function = firstCall?.optJSONObject("function")
            if (function != null) {
                val name = function.optString("name")
                val argsString = function.optString("arguments", "{}")
                val args = try { JSONObject(argsString) } catch (e: Exception) { JSONObject() }

                action = when (name) {
                    "openApp" -> AgentAction.OpenApp(args.optString("packageName"))
                    "clickElementByText" -> AgentAction.ClickElementByText(args.optString("text"))
                    "clickAtCoordinates" -> AgentAction.ClickAtCoordinates(
                        args.optInt("x", 0), args.optInt("y", 0)
                    )
                    "typeTextIntoField" -> AgentAction.TypeText(
                        args.optString("elementId", args.optString("elementTextOrId", "")),
                        args.optString("text")
                    )
                    "scroll" -> AgentAction.Scroll(args.optString("direction", "DOWN"))
                    "pressBack" -> AgentAction.PressBack
                    "pressHome" -> AgentAction.PressHome
                    "requestUserConfirmation" -> AgentAction.RequestConfirmation(
                        args.optString("actionDescription", "Sensitive action")
                    )
                    "showHUDPointer" -> AgentAction.ShowHUD(
                        args.optInt("x", 0), args.optInt("y", 0)
                    )
                    "saveRoutineMacro" -> AgentAction.SaveRoutineMacro(
                        args.optString("title", "New Macro"),
                        args.optString("stepsJson", "[]")
                    )
                    "agentFinished" -> AgentAction.Finished(
                        args.optString("summaryMessage", "Task completed.")
                    )
                    else -> AgentAction.Unknown(name, argsString)
                }
            }
        }

        val isFinished = action is AgentAction.Finished || (action == null && thoughtText != null)
        return AgentStepResult(
            thought = thoughtText ?: (if (action != null) "Executing: ${action.javaClass.simpleName}" else null),
            action = action,
            isFinished = isFinished,
            rawText = thoughtText
        )
    }

    private fun buildOpenAiToolDeclarations(): JSONArray {
        val tools = JSONArray()

        fun tool(name: String, description: String, params: JSONObject): JSONObject = JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", params)
            })
        }

        fun params(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()): JSONObject =
            JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
                if (required.isNotEmpty()) put("required", JSONArray(required))
            }

        fun strProp(desc: String) = JSONObject().apply { put("type", "string"); put("description", desc) }
        fun intProp(desc: String) = JSONObject().apply { put("type", "integer"); put("description", desc) }

        tools.apply {
            put(tool("openApp", "Opens an application by its Android package name.",
                params("packageName" to strProp("e.g. com.google.android.youtube"), required = listOf("packageName"))))
            put(tool("clickElementByText", "Clicks a UI component matching the provided visible text or description.",
                params("text" to strProp("The visible label or content description of the target."), required = listOf("text"))))
            put(tool("clickAtCoordinates", "Simulates a touch tap gesture at specific screen (X, Y) pixel coordinates.",
                params("x" to intProp("X pixel coordinate"), "y" to intProp("Y pixel coordinate"), required = listOf("x", "y"))))
            put(tool("typeTextIntoField", "Types text into a focused or identified input edit field.",
                params("elementId" to strProp("ID or label of the input field"), "text" to strProp("The text to input"), required = listOf("text"))))
            put(tool("scroll", "Performs a scroll or swipe gesture on the screen.",
                params("direction" to strProp("UP, DOWN, LEFT, or RIGHT"), required = listOf("direction"))))
            put(tool("pressBack", "Triggers the system Back button action.", params()))
            put(tool("pressHome", "Triggers the system Home button action.", params()))
            put(tool("requestUserConfirmation",
                "Pauses execution and prompts the user for confirmation before executing high-risk actions.",
                params("actionDescription" to strProp("Clear description of what action needs approval"), required = listOf("actionDescription"))))
            put(tool("showHUDPointer", "Displays a temporary visual highlight at screen coordinates.",
                params("x" to intProp("X coordinate"), "y" to intProp("Y coordinate"), required = listOf("x", "y"))))
            put(tool("saveRoutineMacro", "Saves the completed multi-step sequence as a reusable macro routine.",
                params("title" to strProp("Title of routine"), "stepsJson" to strProp("JSON serialized list of steps"),
                    required = listOf("title", "stepsJson"))))
            put(tool("agentFinished", "Notifies the user that the requested task has been completed.",
                params("summaryMessage" to strProp("Final summary explanation for user"), required = listOf("summaryMessage"))))
        }

        return tools
    }

    private fun buildContextDescription(modelJson: JSONObject): String {
        val contextLength = modelJson.optJSONObject("context_length")?.optLong("value")
            ?: modelJson.optLong("context_length", 0)
        return if (contextLength > 0) "${contextLength / 1000}K context" else ""
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        val maxDimension = 1024
        val scaled = if (width > maxDimension || height > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(width, height)
            Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
        } else this
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        /** Static fallback list shown when model fetch is not possible. */
        val FALLBACK_OPENROUTER_MODELS = listOf(
            AvailableModel("mistralai/mixtral-8x7b-instruct", "Mixtral 8x7B Instruct", "Fast MoE model", "openrouter"),
            AvailableModel("meta-llama/llama-3-70b-instruct", "Llama 3 70B Instruct", "Meta open-source flagship", "openrouter"),
            AvailableModel("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", "Anthropic — strong reasoning", "openrouter"),
            AvailableModel("google/gemini-flash-1.5", "Gemini 1.5 Flash (via OpenRouter)", "Fast & multimodal", "openrouter"),
            AvailableModel("openai/gpt-4o-mini", "GPT-4o Mini", "Fast OpenAI model", "openrouter"),
        )
    }
}
