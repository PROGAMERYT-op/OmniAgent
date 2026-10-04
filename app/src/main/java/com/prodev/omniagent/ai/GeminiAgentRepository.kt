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

class GeminiAgentRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gemini API Key is empty. Please enter your API key in Settings."))
        }
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "Ping check. Reply with: OmniAgent AI Connected."))
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(bodyString).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "HTTP ${response.code}: $bodyString"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val replyText = try {
                val jsonObj = JSONObject(bodyString)
                val candidates = jsonObj.getJSONArray("candidates")
                val parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                parts.getJSONObject(0).optString("text", "Connected successfully")
            } catch (e: Exception) {
                "Connected successfully"
            }

            Result.success(replyText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches the live list of Gemini models available for the given API key.
     * Only returns models that support the [generateContent] method.
     *
     * Falls back to a small static list if the key is empty or the request fails.
     */
    suspend fun fetchGeminiModels(apiKey: String): Result<List<AvailableModel>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.success(FALLBACK_GEMINI_MODELS)
        }
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey&pageSize=100"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.success(FALLBACK_GEMINI_MODELS)
            }

            val modelsArray = JSONObject(bodyString).optJSONArray("models")
                ?: return@withContext Result.success(FALLBACK_GEMINI_MODELS)

            val list = mutableListOf<AvailableModel>()
            for (i in 0 until modelsArray.length()) {
                val m = modelsArray.optJSONObject(i) ?: continue
                val rawName = m.optString("name") // "models/gemini-1.5-flash"
                val modelId = rawName.removePrefix("models/")
                if (modelId.isBlank()) continue

                // Only include models that actually support generateContent
                val methodsArray = m.optJSONArray("supportedGenerationMethods") ?: continue
                val supportsGenerate = (0 until methodsArray.length()).any {
                    methodsArray.optString(it) == "generateContent"
                }
                if (!supportsGenerate) continue

                list.add(
                    AvailableModel(
                        id = modelId,
                        displayName = m.optString("displayName", modelId),
                        description = m.optString("description", ""),
                        provider = "gemini"
                    )
                )
            }

            if (list.isEmpty()) Result.success(FALLBACK_GEMINI_MODELS)
            else Result.success(list)
        } catch (e: Exception) {
            Result.success(FALLBACK_GEMINI_MODELS) // Never leave the user with a broken picker
        }
    }

    companion object {
        /** Static fallback shown when no API key is entered or the network call fails. */
        val FALLBACK_GEMINI_MODELS = listOf(
            AvailableModel("gemini-2.0-flash", "Gemini 2.0 Flash", "Fast, multimodal — recommended", "gemini"),
            AvailableModel("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite", "Ultra-low latency", "gemini"),
            AvailableModel("gemini-1.5-pro", "Gemini 1.5 Pro", "Deep reasoning, large context", "gemini"),
            AvailableModel("gemini-1.5-flash", "Gemini 1.5 Flash", "Balanced speed & quality", "gemini"),
        )
    }

    suspend fun getNextAgentAction(
        apiKey: String,
        model: String,
        userGoal: String,
        screenHierarchyText: String,
        screenBitmap: Bitmap?,
        actionHistory: List<String>
    ): Result<AgentStepResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Missing Gemini API Key. Please configure in Settings."))
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

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

            // Build request JSON
            val requestJson = JSONObject()

            // System instruction
            requestJson.put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemPrompt))
                })
            })

            // Contents
            val contentsArray = JSONArray()
            val userContent = JSONObject().apply {
                put("role", "user")
                val partsArray = JSONArray()

                // Text part with goal, screen hierarchy and action history
                val contextPrompt = buildString {
                    appendLine("User Goal: $userGoal")
                    if (actionHistory.isNotEmpty()) {
                        appendLine("\nActions Executed So Far:")
                        actionHistory.takeLast(6).forEach { appendLine("- $it") }
                    }
                    appendLine("\nCurrent Screen Hierarchy:")
                    appendLine(screenHierarchyText.take(15000))
                    appendLine("\nWhat is the next step to execute? Explain your thought and invoke the corresponding tool.")
                }
                partsArray.put(JSONObject().put("text", contextPrompt))

                // Multimodal vision part if screenshot provided
                if (screenBitmap != null) {
                    val base64Image = screenBitmap.toBase64()
                    partsArray.put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Image)
                        })
                    })
                }

                put("parts", partsArray)
            }
            contentsArray.put(userContent)
            requestJson.put("contents", contentsArray)

            // Tools declaration
            requestJson.put("tools", JSONArray().apply {
                put(JSONObject().apply {
                    put("functionDeclarations", buildFunctionDeclarations())
                })
            })

            // Generation config
            requestJson.put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("topP", 0.9)
            })

            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorDetails = try {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseBody"
                }
                return@withContext Result.failure(Exception("Gemini API Error: $errorDetails"))
            }

            val stepResult = parseGeminiResponse(responseBody)
            Result.success(stepResult)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGeminiResponse(jsonString: String): AgentStepResult {
        val root = JSONObject(jsonString)
        val candidates = root.optJSONArray("candidates") ?: return AgentStepResult(
            thought = "No candidate response from Gemini.",
            action = null,
            isFinished = true
        )

        val firstCandidate = candidates.optJSONObject(0) ?: return AgentStepResult(
            thought = "Empty response candidate.",
            action = null,
            isFinished = true
        )

        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts") ?: JSONArray()

        var thoughtText: String? = null
        var action: AgentAction? = null

        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue

            if (part.has("text")) {
                val t = part.optString("text")
                if (t.isNotBlank()) {
                    thoughtText = if (thoughtText == null) t else "$thoughtText\n$t"
                }
            }

            if (part.has("functionCall")) {
                val fnCall = part.getJSONObject("functionCall")
                val name = fnCall.getString("name")
                val args = fnCall.optJSONObject("args") ?: JSONObject()

                action = when (name) {
                    "openApp" -> AgentAction.OpenApp(args.optString("packageName"))
                    "clickElementByText" -> AgentAction.ClickElementByText(args.optString("text"))
                    "clickAtCoordinates" -> AgentAction.ClickAtCoordinates(
                        args.optInt("x", 0),
                        args.optInt("y", 0)
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
                        args.optInt("x", 0),
                        args.optInt("y", 0)
                    )
                    "saveRoutineMacro" -> AgentAction.SaveRoutineMacro(
                        args.optString("title", "New Macro"),
                        args.optString("stepsJson", "[]")
                    )
                    "agentFinished" -> AgentAction.Finished(
                        args.optString("summaryMessage", "Task completed.")
                    )
                    else -> AgentAction.Unknown(name, args.toString())
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

    private fun buildFunctionDeclarations(): JSONArray {
        val array = JSONArray()

        array.put(JSONObject().apply {
            put("name", "openApp")
            put("description", "Opens an application by its Android package name.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("packageName", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "e.g. com.google.android.youtube or com.google.android.deskclock")
                    })
                })
                put("required", JSONArray().apply { put("packageName") })
            })
        })

        array.put(JSONObject().apply {
            put("name", "clickElementByText")
            put("description", "Clicks a UI component matching the provided visible text or description.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("text", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The visible label or content description of the target button or item.")
                    })
                })
                put("required", JSONArray().apply { put("text") })
            })
        })

        array.put(JSONObject().apply {
            put("name", "clickAtCoordinates")
            put("description", "Simulates a touch tap gesture at specific screen (X, Y) pixel coordinates.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("x", JSONObject().put("type", "INTEGER").put("description", "X pixel coordinate"))
                    put("y", JSONObject().put("type", "INTEGER").put("description", "Y pixel coordinate"))
                })
                put("required", JSONArray().apply { put("x"); put("y") })
            })
        })

        array.put(JSONObject().apply {
            put("name", "typeTextIntoField")
            put("description", "Types text into a focused or identified input edit field.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("elementId", JSONObject().put("type", "STRING").put("description", "ID or label of the input field"))
                    put("text", JSONObject().put("type", "STRING").put("description", "The text to input"))
                })
                put("required", JSONArray().apply { put("text") })
            })
        })

        array.put(JSONObject().apply {
            put("name", "scroll")
            put("description", "Performs a scroll or swipe gesture on the screen.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("direction", JSONObject().put("type", "STRING").put("description", "UP, DOWN, LEFT, or RIGHT"))
                })
                put("required", JSONArray().apply { put("direction") })
            })
        })

        array.put(JSONObject().apply {
            put("name", "pressBack")
            put("description", "Triggers the system Back button action.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        array.put(JSONObject().apply {
            put("name", "pressHome")
            put("description", "Triggers the system Home button action to return to launcher.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        array.put(JSONObject().apply {
            put("name", "requestUserConfirmation")
            put("description", "Pauses execution and prompts the user for confirmation before executing high-risk or sensitive actions (payments, delete, sending messages).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("actionDescription", JSONObject().put("type", "STRING").put("description", "Clear human-readable description of what action needs approval"))
                })
                put("required", JSONArray().apply { put("actionDescription") })
            })
        })

        array.put(JSONObject().apply {
            put("name", "showHUDPointer")
            put("description", "Displays a temporary visual highlight / touch ripple indicator at screen coordinates.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("x", JSONObject().put("type", "INTEGER").put("description", "X coordinate"))
                    put("y", JSONObject().put("type", "INTEGER").put("description", "Y coordinate"))
                })
                put("required", JSONArray().apply { put("x"); put("y") })
            })
        })

        array.put(JSONObject().apply {
            put("name", "saveRoutineMacro")
            put("description", "Saves the completed multi-step sequence as a reusable macro routine.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("title", JSONObject().put("type", "STRING").put("description", "Title of routine"))
                    put("stepsJson", JSONObject().put("type", "STRING").put("description", "JSON serialized list of steps"))
                })
                put("required", JSONArray().apply { put("title"); put("stepsJson") })
            })
        })

        array.put(JSONObject().apply {
            put("name", "agentFinished")
            put("description", "Notifies the user that the requested task has been completed.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("summaryMessage", JSONObject().put("type", "STRING").put("description", "Final summary explanation for user"))
                })
                put("required", JSONArray().apply { put("summaryMessage") })
            })
        })

        return array
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        // Resize if too large to conserve bandwidth and speed up vision reasoning
        val maxDimension = 1024
        val scaled = if (width > maxDimension || height > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(width, height)
            Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
        } else this

        scaled.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
