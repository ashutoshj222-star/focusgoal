package com.focusgoal.app.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams

data class ChatTurn(val fromUser: Boolean, val text: String)

/**
 * Talks to Claude for Mira's replies. Kept free of Android classes so it stays easy to test.
 * All calls block, so run them on a background dispatcher.
 */
class ClaudeBrain(apiKey: String) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    /**
     * @param history the visible chat so far, oldest first; the last turn must be the user's.
     * @param context live facts (timer, blocked apps…) attached to the newest user message so the
     *   system prompt stays identical across requests.
     * @return Mira's reply, or null if Claude declined to answer.
     */
    fun reply(history: List<ChatTurn>, context: String): String? {
        val turns = mergeTurns(history.takeLast(MAX_TURNS))
        require(turns.isNotEmpty() && turns.last().fromUser) { "The last turn must come from the user" }

        val builder = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(4096L)
            .system(SYSTEM_PROMPT)
            // Short, friendly chat replies don't need deep reasoning.
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
            // If a request is declined by a safety classifier, let the API retry it on a fallback model.
            .addBeta("server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))

        turns.forEachIndexed { i, turn ->
            if (turn.fromUser) {
                val text = if (i == turns.lastIndex) "${turn.text}\n\n[App context: $context]" else turn.text
                builder.addUserMessage(text)
            } else {
                builder.addAssistantMessage(turn.text)
            }
        }

        val response = client.beta().messages().create(builder.build())
        if (response.stopReason().orElse(null) == BetaStopReason.REFUSAL) return null

        return response.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("")
            .trim()
            .ifEmpty { null }
    }

    fun close() = client.close()

    companion object {
        const val MODEL = "claude-opus-5-5"
        private const val MAX_TURNS = 24

        val SYSTEM_PROMPT = """
            You are Mira, the friendly cartoon focus buddy inside FocusGoal, an Android app that blocks
            distracting apps so people can study and work. You're warm, upbeat and a little playful, like a
            supportive friend who also keeps them honest.

            How you talk:
            - Keep replies short: usually 1-3 sentences, chat-style, at most one emoji.
            - Encourage focus. If they want to scroll social media during a session, kindly redirect them.
            - Help with planning study or work sessions, breaking tasks down, beating procrastination,
              and quick questions about whatever they're working on.
            - Each user message ends with an [App context: ...] note from the app (timer state, today's
              focus minutes). Use it naturally; never quote it or mention that it exists.
            - You can't start or stop timers or change settings yourself. If asked, tell them which button
              to press: "Focus" (can be stopped) or "Deep Focus" (can't be stopped, blocks uninstalling).
        """.trimIndent()

        /** The API wants alternating turns that start with the user, so merge and trim accordingly. */
        internal fun mergeTurns(history: List<ChatTurn>): List<ChatTurn> {
            val merged = ArrayList<ChatTurn>()
            for (turn in history) {
                if (turn.text.isBlank()) continue
                if (merged.isEmpty() && !turn.fromUser) continue
                val last = merged.lastOrNull()
                if (last != null && last.fromUser == turn.fromUser) {
                    merged[merged.lastIndex] = last.copy(text = last.text + "\n\n" + turn.text)
                } else {
                    merged += turn
                }
            }
            return merged
        }
    }
}
