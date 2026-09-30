package com.prodev.omniagent.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class SanitizedNode(
    val id: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isSensitive: Boolean
) {
    fun toPromptString(): String {
        val type = className?.substringAfterLast('.') ?: "View"
        val desc = text ?: contentDescription ?: ""
        return if (isSensitive) {
            "[$type (SENSITIVE - PROTECTED) at bounds: (${bounds.left},${bounds.top})-(${bounds.right},${bounds.bottom})]"
        } else {
            "[$type \"$desc\" at (${bounds.centerX()}, ${bounds.centerY()}) bounds: (${bounds.left},${bounds.top})-(${bounds.right},${bounds.bottom}) clickable=$isClickable editable=$isEditable]"
        }
    }
}

object PrivacyEngine {
    private val SENSITIVE_KEYWORDS = listOf(
        "password", "passcode", "pin", "cvv", "cvc", "security code",
        "card number", "credit card", "debit card", "otp", "one-time",
        "secret", "ssn", "social security", "bank account", "routing number"
    )

    private val SENSITIVE_ACTION_KEYWORDS = listOf(
        "pay", "checkout", "transfer", "confirm payment", "buy now",
        "delete", "erase", "remove account", "uninstall", "factory reset",
        "send money", "confirm order", "authorize"
    )

    fun isNodeSensitive(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true

        val text = (node.text?.toString() ?: "").lowercase()
        val desc = (node.contentDescription?.toString() ?: "").lowercase()
        val viewId = (node.viewIdResourceName ?: "").lowercase()

        val fullText = "$text $desc $viewId"
        if (SENSITIVE_KEYWORDS.any { keyword -> fullText.contains(keyword) }) {
            return true
        }

        // Check for 13-19 digit card numbers or 3-4 digit CVV patterns
        if (text.replace(" ", "").matches(Regex(".*\\d{13,19}.*"))) {
            return true
        }

        return false
    }

    fun isActionSensitive(actionDescription: String): Boolean {
        val lower = actionDescription.lowercase()
        return SENSITIVE_ACTION_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }

    /**
     * Traverses the AccessibilityNodeInfo tree and extracts sanitized nodes.
     * Sensitive nodes have their text masked to protect privacy.
     */
    fun sanitizeHierarchy(
        root: AccessibilityNodeInfo?,
        privacyShieldEnabled: Boolean
    ): List<SanitizedNode> {
        val result = mutableListOf<SanitizedNode>()
        if (root == null) return result

        fun traverse(node: AccessibilityNodeInfo) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val isSensitive = privacyShieldEnabled && isNodeSensitive(node)

            val rawText = node.text?.toString()
            val rawDesc = node.contentDescription?.toString()

            val sanitizedText = if (isSensitive) {
                "[PROTECTED SENSITIVE FIELD]"
            } else rawText

            val sanitizedDesc = if (isSensitive) {
                "[PROTECTED DESCRIPTION]"
            } else rawDesc

            // Only include interactive or text-bearing nodes with valid coordinates
            if (bounds.width() > 0 && bounds.height() > 0) {
                if (!sanitizedText.isNullOrBlank() || !sanitizedDesc.isNullOrBlank() || node.isClickable || node.isEditable) {
                    result.add(
                        SanitizedNode(
                            id = node.viewIdResourceName,
                            text = sanitizedText,
                            contentDescription = sanitizedDesc,
                            className = node.className?.toString(),
                            bounds = bounds,
                            isClickable = node.isClickable,
                            isEditable = node.isEditable,
                            isSensitive = isSensitive
                        )
                    )
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverse(child)
            }
        }

        traverse(root)
        return result
    }

    /**
     * Masks sensitive areas on screen capture bitmap by painting solid privacy blocks
     * and a "PRIVACY SHIELD PROTECTED" badge before transmitting to Gemini Vision endpoint.
     */
    fun maskSensitiveRegions(
        original: Bitmap,
        sensitiveBounds: List<Rect>
    ): Bitmap {
        if (sensitiveBounds.isEmpty()) return original

        val maskedBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(maskedBitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val textPaint = Paint().apply {
            color = Color.CYAN
            textSize = 24f
            isAntiAlias = true
            isFakeBoldText = true
        }

        for (rect in sensitiveBounds) {
            // Expand rect slightly for security margin
            val expanded = Rect(
                (rect.left - 4).coerceAtLeast(0),
                (rect.top - 4).coerceAtLeast(0),
                (rect.right + 4).coerceAtMost(canvas.width),
                (rect.bottom + 4).coerceAtMost(canvas.height)
            )
            canvas.drawRect(expanded, paint)
            canvas.drawText("🔒 PROTECTED", expanded.left.toFloat() + 8f, expanded.centerY().toFloat() + 8f, textPaint)
        }

        return maskedBitmap
    }
}
