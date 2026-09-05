package com.example

import java.util.Locale

data class Step(val action: String, val argument: String = "")
data class Outcome(val accepted: Boolean, val message: String)

object Parser {
    fun parse(input: String): List<Step>? {
        val parts = input.trim().split(
            Regex("\\s+then\\s+", RegexOption.IGNORE_CASE)
        )
        if (parts.isEmpty() || parts.size > 6) return null

        val result = mutableListOf<Step>()

        for (part in parts) {
            val text = part.trim()
            val lower = text.lowercase(Locale.ROOT)

            val step = when {
                lower.startsWith("open ") && text.length > 5 ->
                    Step("open", text.substring(5).trim())

                lower.startsWith("launch ") && text.length > 7 ->
                    Step("open", text.substring(7).trim())

                lower.startsWith("tap ") && text.length > 4 ->
                    Step("tap", text.substring(4).trim())

                lower.startsWith("click ") && text.length > 6 ->
                    Step("tap", text.substring(6).trim())

                lower.startsWith("press ") && text.length > 6 ->
                    Step("tap", text.substring(6).trim())

                lower.startsWith("type ") && text.length > 5 ->
                    Step("type", text.substring(5))

                lower.startsWith("write ") && text.length > 6 ->
                    Step("type", text.substring(6))

                lower.startsWith("enter ") && text.length > 6 ->
                    Step("type", text.substring(6))

                lower in listOf("scroll down", "swipe down") -> Step("down")
                lower in listOf("scroll up", "swipe up") -> Step("up")
                lower in listOf("back", "go back") -> Step("back")
                lower in listOf("home", "go home") -> Step("home")
                lower in listOf("read screen", "read the screen", "read") -> Step("read")
                else -> return null
            }
            result.add(step)
        }

        return result
    }

    fun describe(step: Step): String = when (step.action) {
        "open" -> "Open ${step.argument}"
        "tap" -> "Tap exact label: ${step.argument}"
        "type" -> "REPLACE focused text with: ${step.argument.take(160)}"
        "down" -> "Scroll down"
        "up" -> "Scroll up"
        "back" -> "Go back"
        "home" -> "Go home"
        "read" -> "Read accessible screen text aloud"
        else -> "Unsupported action"
    }
}
