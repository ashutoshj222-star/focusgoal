package com.focusgoal.app.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams

/** Claude via the official Anthropic SDK. */
class ClaudeBrain(apiKey: String, private val model: String = MODEL) : MiraBrain {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    override fun reply(history: List<ChatTurn>, context: String): String? {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4096L)
            .system(MiraPersona.SYSTEM_PROMPT)
            // Short, friendly chat replies don't need deep reasoning.
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
            // If a request is declined by a safety classifier, let the API retry it on a fallback model.
            .addBeta("server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))

        MiraPersona.prepare(history, context).forEach { turn ->
            if (turn.fromUser) builder.addUserMessage(turn.text) else builder.addAssistantMessage(turn.text)
        }

        val response = client.beta().messages().create(builder.build())
        if (response.stopReason().orElse(null) == BetaStopReason.REFUSAL) return null

        return response.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("")
            .trim()
            .ifEmpty { null }
    }

    override fun close() = client.close()

    companion object {
        const val MODEL = "claude-opus-5-5"
    }
}
