package com.focusgoal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusgoal.app.ai.ChatMessage
import com.focusgoal.app.ai.MiraChat
import com.focusgoal.app.ai.MiraLines
import com.focusgoal.app.data.FocusRepository
import com.focusgoal.app.ui.character.Mira
import com.focusgoal.app.ui.character.MiraMood
import com.focusgoal.app.ui.theme.Palette
import com.focusgoal.app.ui.theme.glass
import kotlinx.coroutines.launch
import java.util.Calendar

private val QUICK_PROMPTS = listOf(
    "Motivate me 💪",
    "Plan my study session",
    "I keep getting distracted",
    "Give me a focus tip",
)

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val chat = remember { MiraChat.get(context) }
    val repo = remember { FocusRepository.get(context) }
    val settings by repo.settings.collectAsStateWithLifecycle()
    val session by repo.session.collectAsStateWithLifecycle()
    val messages by chat.messages.collectAsStateWithLifecycle()
    val typing by chat.typing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (chat.messages.value.isEmpty()) {
            chat.addMiraMessage(MiraLines.greeting(settings.userName, Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
        }
    }
    LaunchedEffect(messages.size, typing) {
        val count = messages.size + if (typing) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun send(text: String) {
        if (text.isBlank()) return
        input = ""
        scope.launch { chat.send(text) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // ---- Header ----
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(26.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mira(
                Modifier.size(64.dp),
                mood = if (session?.isRunning() == true) MiraMood.FOCUSED else MiraMood.HAPPY,
                talking = typing,
            )
            Column(Modifier.padding(start = 6.dp)) {
                Text("Mira", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                Text(
                    when {
                        typing -> "typing…"
                        settings.apiKey.isBlank() -> "your focus buddy · offline mode"
                        else -> "your focus buddy · AI on ✨"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim,
                )
            }
        }

        // ---- Messages ----
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages, key = { it.id }) { MessageBubble(it) }
            if (typing) {
                item(key = "typing") {
                    MessageBubble(ChatMessage(id = -1, fromUser = false, text = "…"))
                }
            }
        }

        // ---- Quick prompts ----
        if (input.isEmpty() && !typing) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QUICK_PROMPTS.forEach { prompt ->
                    Text(
                        prompt,
                        modifier = Modifier
                            .glass(RoundedCornerShape(50))
                            .clickable { send(prompt) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.TextDim,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ---- Input ----
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(50), fill = Palette.GlassStrong)
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) Text("Message Mira…", color = Palette.TextFaint, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                    cursorBrush = SolidColor(Palette.AccentLight),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(input) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (input.isBlank() || typing) Palette.GlassStrong else Palette.Accent)
                    .clickable(enabled = input.isNotBlank() && !typing) { send(input) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val mine = message.fromUser
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        val shape = if (mine) {
            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
        } else {
            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp)
        }
        Text(
            message.text,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .then(
                    if (mine) Modifier.clip(shape).background(Palette.AccentGradient)
                    else Modifier.glass(shape, fill = Palette.GlassStrong),
                )
                .padding(horizontal = 16.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.Text,
        )
    }
}
