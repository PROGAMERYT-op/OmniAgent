package com.prodev.omniagent.ai

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val tools: List<GeminiToolWrapper>? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null
)

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiFunctionCall(
    val name: String,
    val args: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiFunctionResponse(
    val name: String,
    val response: Map<String, Any?>
)

@JsonClass(generateAdapter = true)
data class GeminiToolWrapper(
    val functionDeclarations: List<GeminiFunctionDecl>
)

@JsonClass(generateAdapter = true)
data class GeminiFunctionDecl(
    val name: String,
    val description: String,
    val parameters: GeminiParameters? = null
)

@JsonClass(generateAdapter = true)
data class GeminiParameters(
    val type: String = "OBJECT",
    val properties: Map<String, GeminiProperty>,
    val required: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiProperty(
    val type: String,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Float? = 0.2f,
    val topP: Float? = 0.9f
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

// Internal Agent Tool Call result for execution engine
sealed class AgentAction {
    data class OpenApp(val packageName: String) : AgentAction()
    data class ClickElementByText(val text: String) : AgentAction()
    data class ClickAtCoordinates(val x: Int, val y: Int) : AgentAction()
    data class TypeText(val elementTextOrId: String, val text: String) : AgentAction()
    data class Scroll(val direction: String) : AgentAction() // UP, DOWN, LEFT, RIGHT
    object PressBack : AgentAction()
    object PressHome : AgentAction()
    data class RequestConfirmation(val actionDescription: String) : AgentAction()
    data class ShowHUD(val x: Int, val y: Int) : AgentAction()
    data class SaveRoutineMacro(val title: String, val stepsJson: String) : AgentAction()
    data class Finished(val summaryMessage: String) : AgentAction()
    data class Unknown(val rawName: String, val rawArgs: String) : AgentAction()
}

data class AgentStepResult(
    val thought: String?,
    val action: AgentAction?,
    val isFinished: Boolean = false,
    val rawText: String? = null
)
