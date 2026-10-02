package com.focusgoal.app.ai

import android.content.Context
import com.focusgoal.app.data.FocusMode
import com.focusgoal.app.data.FocusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val time: Long = System.currentTimeMillis(),
)

/** Chat history with Mira (kept for the life of the app process) and reply generation. */
class MiraChat private constructor(private val appContext: Context) {

    private var nextId = 1L
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _typing = MutableStateFlow(false)
    val typing: StateFlow<Boolean> = _typing.asStateFlow()

    /** Mira's latest line, shown in her speech bubble on the home screen. */
    private val _bubble = MutableStateFlow<String?>(null)
    val bubble: StateFlow<String?> = _bubble.asStateFlow()

    private var brain: Pair<String, ClaudeBrain>? = null

    @Synchronized
    private fun add(fromUser: Boolean, text: String) {
        val msg = ChatMessage(nextId++, fromUser, text)
        _messages.update { it + msg }
        if (!fromUser) _bubble.value = text
    }

    fun addMiraMessage(text: String) = add(fromUser = false, text = text)

    fun setBubble(text: String) {
        _bubble.value = text
    }

    /** Sends the user's message and appends Mira's answer. Call from a coroutine. */
    suspend fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _typing.value) return
        add(fromUser = true, text = trimmed)
        _typing.value = true
        try {
            val repo = FocusRepository.get(appContext)
            val settings = repo.settings.value
            val session = repo.activeSession()
            val reply = if (settings.apiKey.isBlank()) {
                kotlinx.coroutines.delay(600) // a tiny pause feels more natural
                MiraLines.offlineReply(trimmed, session != null, settings.userName)
            } else {
                withContext(Dispatchers.IO) {
                    runCatching {
                        brainFor(settings.apiKey).reply(
                            history = _messages.value.map { ChatTurn(it.fromUser, it.text) },
                            context = describeContext(),
                        )
                    }.getOrElse { e ->
                        "Hmm, I couldn't reach my brain right now (${e.message?.take(80) ?: "network error"}). " +
                            "Check your API key in Settings or your internet 🙏"
                    }
                } ?: "Let's talk about something else — how's your focus going? 🙂"
            }
            add(fromUser = false, text = reply)
        } finally {
            _typing.value = false
        }
    }

    private fun brainFor(apiKey: String): ClaudeBrain {
        brain?.let { (key, b) -> if (key == apiKey) return b else b.close() }
        return ClaudeBrain(apiKey).also { brain = apiKey to it }
    }

    private fun describeContext(): String {
        val repo = FocusRepository.get(appContext)
        val session = repo.activeSession()
        val stats = repo.stats.value
        val name = repo.settings.value.userName.ifBlank { "unknown" }
        val timer = if (session == null) {
            "no session running"
        } else {
            val mins = (session.remainingMs() / 60_000) + 1
            val mode = if (session.mode == FocusMode.DEEP) "Deep Focus (can't be stopped)" else "Focus"
            "$mode session running, about $mins min left, ${session.blocked.size} apps blocked"
        }
        return "user name: $name; $timer; focused today: ${stats.todayMinutes} min; streak: ${stats.streakDays} days"
    }

    companion object {
        @Volatile
        private var instance: MiraChat? = null

        fun get(context: Context): MiraChat =
            instance ?: synchronized(this) {
                instance ?: MiraChat(context.applicationContext).also { instance = it }
            }
    }
}
