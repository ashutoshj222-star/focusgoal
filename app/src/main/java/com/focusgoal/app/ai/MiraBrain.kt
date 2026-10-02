package com.focusgoal.app.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ChatTurn(val fromUser: Boolean, val text: String)

/** Something that can write Mira's next chat message. Calls block — run them off the main thread. */
interface MiraBrain {
    /**
     * @param history the visible chat so far, oldest first; the last turn is the user's.
     * @param context live app facts (timer, today's minutes…) attached to the newest user message.
     * @return Mira's reply, or null if the model declined to answer.
     */
    fun reply(history: List<ChatTurn>, context: String): String?
    fun close() {}
}

/** Which AI service a pasted key belongs to — worked out from the key's prefix. */
enum class AiProvider(val label: String, val defaultModel: String) {
    CLAUDE("Claude (Anthropic)", ClaudeBrain.MODEL),
    OPENAI("ChatGPT (OpenAI)", "gpt-5-mini"),
    GEMINI("Gemini (Google)", "gemini-flash-latest"),
    ;

    companion object {
        fun detect(apiKey: String): AiProvider? {
            val key = apiKey.trim()
            return when {
                key.isEmpty() -> null
                key.startsWith("sk-ant-") -> CLAUDE
                key.startsWith("AIza") -> GEMINI
                key.startsWith("sk-") -> OPENAI
                else -> null
            }
        }

        fun create(apiKey: String, model: String): MiraBrain? {
            val provider = detect(apiKey) ?: return null
            val m = model.trim().ifEmpty { provider.defaultModel }
            return when (provider) {
                CLAUDE -> ClaudeBrain(apiKey.trim(), m)
                OPENAI -> OpenAiBrain(apiKey.trim(), m)
                GEMINI -> GeminiBrain(apiKey.trim(), m)
            }
        }
    }
}

object MiraPersona {
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

    private const val MAX_TURNS = 24

    /**
     * Last [MAX_TURNS] turns, merged so roles alternate and the first turn is the user's (what every
     * provider expects), with the app context appended to the newest user message.
     */
    fun prepare(history: List<ChatTurn>, context: String): List<ChatTurn> {
        val merged = ArrayList<ChatTurn>()
        for (turn in history.takeLast(MAX_TURNS)) {
            if (turn.text.isBlank()) continue
            if (merged.isEmpty() && !turn.fromUser) continue
            val last = merged.lastOrNull()
            if (last != null && last.fromUser == turn.fromUser) {
                merged[merged.lastIndex] = last.copy(text = last.text + "\n\n" + turn.text)
            } else {
                merged += turn
            }
        }
        require(merged.isNotEmpty() && merged.last().fromUser) { "The last turn must come from the user" }
        merged[merged.lastIndex] = merged.last().let { it.copy(text = "${it.text}\n\n[App context: $context]") }
        return merged
    }
}

/** Small JSON-over-HTTPS helper for the providers without an SDK in this app. */
internal fun postJson(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 20_000
        conn.readTimeout = 90_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(text).getJSONObject("error").optString("message") }.getOrNull()
            throw IOException("HTTP $code: ${message?.takeIf { it.isNotBlank() } ?: text.take(160)}")
        }
        return JSONObject(text)
    } finally {
        conn.disconnect()
    }
}

/** ChatGPT via OpenAI's Chat Completions API. */
class OpenAiBrain(private val apiKey: String, private val model: String) : MiraBrain {
    override fun reply(history: List<ChatTurn>, context: String): String? {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", MiraPersona.SYSTEM_PROMPT))
        MiraPersona.prepare(history, context).forEach { turn ->
            messages.put(JSONObject().put("role", if (turn.fromUser) "user" else "assistant").put("content", turn.text))
        }
        val response = postJson(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer $apiKey"),
            JSONObject().put("model", model).put("messages", messages),
        )
        val message = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        if (!message.isNull("refusal")) return null
        return message.optString("content").trim().ifEmpty { null }
    }
}

/** Gemini via Google's Generative Language API. */
class GeminiBrain(private val apiKey: String, private val model: String) : MiraBrain {
    override fun reply(history: List<ChatTurn>, context: String): String? {
        val contents = JSONArray()
        MiraPersona.prepare(history, context).forEach { turn ->
            contents.put(
                JSONObject()
                    .put("role", if (turn.fromUser) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.text))),
            )
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", MiraPersona.SYSTEM_PROMPT))))
            .put("contents", contents)
        val modelPath = URLEncoder.encode(model, "UTF-8")
        val response = postJson(
            "https://generativelanguage.googleapis.com/v1beta/models/$modelPath:generateContent",
            mapOf("x-goog-api-key" to apiKey),
            body,
        )
        val candidates = response.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return null
        return (0 until parts.length())
            .mapNotNull { parts.getJSONObject(it).optString("text").takeIf { t -> t.isNotBlank() } }
            .joinToString("")
            .trim()
            .ifEmpty { null }
    }
}
