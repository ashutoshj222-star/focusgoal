package com.focusgoal.app.ai

import com.focusgoal.app.data.FocusMode
import kotlin.random.Random

/** Mira's built-in lines: used for timer events, and as offline replies when there's no API key. */
object MiraLines {

    private fun hey(name: String) = if (name.isBlank()) "Hey" else "Hey $name"

    fun greeting(name: String, hour: Int): String {
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Still up?"
        }
        val who = if (name.isBlank()) "" else ", $name"
        return listOf(
            "$part$who! What are we working on today?",
            "$part$who! Pick a time and let's get in the zone ✨",
            "$part$who! One focused session beats ten distracted hours.",
        ).random()
    }

    fun sessionStart(mode: FocusMode, minutes: Int, name: String): String = when (mode) {
        FocusMode.DEEP -> listOf(
            "${hey(name)}, Deep Focus is on for $minutes minutes 🔒 No escape hatches. I believe in you!",
            "Locked in for $minutes minutes! I'm guarding your phone, you guard your goals 💪",
            "Deep Focus activated. $minutes minutes of pure you-time. Let's make it count!",
        ).random()
        FocusMode.FOCUS -> listOf(
            "Let's do this! $minutes minutes, I'll keep the distractions away 💜",
            "${hey(name)}! Timer's running — $minutes minutes. Phone down, mind up.",
            "Focus mode on! Start with the hardest bit first. You've got $minutes minutes.",
        ).random()
    }

    fun sessionComplete(minutes: Int, name: String): String = listOf(
        "You did it${if (name.isBlank()) "" else ", $name"}! $minutes minutes of real focus 🎉 Take a breather.",
        "Session complete! $minutes minutes done. Stretch, drink some water, you earned it ✨",
        "Boom! $minutes focused minutes in the bag. I'm so proud of you!",
    ).random()

    fun sessionStopped(elapsedMin: Int): String =
        if (elapsedMin < 2) "Stopped already? That's okay — let's try again when you're ready 🙂"
        else "You stopped after $elapsedMin minutes. Every minute counts! Ready for another round later?"

    private val duringLines = listOf(
        "You're doing great. Keep going!",
        "Eyes on the task. The feed can wait 😌",
        "Tiny steps still move you forward.",
        "Future you is going to be so thankful.",
        "Breathe in… and back to work 💜",
        "Distractions are knocking. We're not home.",
        "Stay with it — the hard part is starting, and you already did!",
    )

    fun duringSession(seed: Int): String = duringLines[Math.floorMod(seed, duringLines.size)]

    fun blocked(appLabel: String, deep: Boolean): String = listOf(
        "Nope! $appLabel is on a break right now 🙅‍♀️",
        "Hey, we said no $appLabel during focus time!",
        "Caught you 👀 $appLabel will still be there later.",
        if (deep) "Deep Focus means deep focus! $appLabel stays locked." else "Not now — let's get back to your task.",
    ).random()

    val idleTips = listOf(
        "Tip: 25 minutes of focus + 5 minutes of rest is the classic Pomodoro.",
        "Deep Focus can't be stopped — perfect for exam prep.",
        "Put your phone face down and start with the smallest task.",
        "Tap me any time to chat!",
    )

    /** A simple keyword-based reply when Mira has no AI brain connected. */
    fun offlineReply(message: String, sessionRunning: Boolean, name: String): String {
        val m = message.lowercase()
        return when {
            listOf("hi", "hello", "hey", "namaste").any { m.trim().startsWith(it) } ->
                "${hey(name)}! 💜 Want to start a focus session together?"
            listOf("motivat", "lazy", "tired", "can't", "cant", "bored").any { it in m } -> listOf(
                "Just do 5 minutes. Seriously, only 5. Momentum will do the rest 💪",
                "You don't need to feel motivated to start — starting is what creates motivation!",
                "Tired is okay. Pick the easiest task and do just that one.",
            ).random()
            listOf("distract", "instagram", "youtube", "reels", "scroll", "phone").any { it in m } ->
                if (sessionRunning) "I see you 👀 Those apps are blocked for a reason. Back to it — you're almost there!"
                else "Pick those apps in the Apps tab and start Focus. I'll keep them locked for you."
            listOf("plan", "study", "exam", "schedule", "homework").any { it in m } ->
                "Try this: write down 3 tasks, start with the hardest one, and do a 45-minute Deep Focus. Then a 10-minute break!"
            listOf("break", "rest").any { it in m } ->
                if (sessionRunning) "Your break is coming soon — finish strong first!" else "Breaks are important! Stretch, drink water, then let's go again."
            listOf("thank", "love", "cute").any { it in m } -> "Aww 🥰 I'm always here for you!"
            listOf("deep focus", "deep").any { it in m } ->
                "Deep Focus locks your chosen apps and can't be stopped or uninstalled until the timer ends. Super strict, super effective!"
            "?" in m -> "Good question! Add your Claude API key in Settings and I can answer anything. For now: focus first, then we figure it out together 😉"
            else -> listOf(
                "Got it! Want to turn that into a focus session?",
                "I hear you. One step at a time 💜",
                "Let's channel that energy into some focused work!",
            ).random(Random(m.hashCode()))
        }
    }
}
