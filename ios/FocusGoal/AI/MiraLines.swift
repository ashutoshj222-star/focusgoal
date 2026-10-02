import Foundation

/// Mira's built-in lines: used for timer events, and as offline replies when there's no API key.
enum MiraLines {
    private static func hey(_ name: String) -> String { name.isEmpty ? "Hey" : "Hey \(name)" }

    static func greeting(name: String, hour: Int) -> String {
        let part: String
        switch hour {
        case 5...11: part = "Good morning"
        case 12...16: part = "Good afternoon"
        case 17...21: part = "Good evening"
        default: part = "Still up?"
        }
        let who = name.isEmpty ? "" : ", \(name)"
        return [
            "\(part)\(who)! What are we working on today?",
            "\(part)\(who)! Pick a time and let's get in the zone ✨",
            "\(part)\(who)! One focused session beats ten distracted hours.",
        ].randomElement()!
    }

    static func sessionStart(mode: FocusSession.Mode, minutes: Int, name: String) -> String {
        switch mode {
        case .deep:
            return [
                "\(hey(name)), Deep Focus is on for \(minutes) minutes 🔒 No escape hatches. I believe in you!",
                "Locked in for \(minutes) minutes! I'm guarding your phone, you guard your goals 💪",
                "Deep Focus activated. \(minutes) minutes of pure you-time. Let's make it count!",
            ].randomElement()!
        case .focus:
            return [
                "Let's do this! \(minutes) minutes, I'll keep the distractions away 💜",
                "\(hey(name))! Timer's running — \(minutes) minutes. Phone down, mind up.",
                "Focus mode on! Start with the hardest bit first. You've got \(minutes) minutes.",
            ].randomElement()!
        }
    }

    static func sessionComplete(minutes: Int, name: String) -> String {
        [
            "You did it\(name.isEmpty ? "" : ", \(name)")! \(minutes) minutes of real focus 🎉 Take a breather.",
            "Session complete! \(minutes) minutes done. Stretch, drink some water, you earned it ✨",
            "Boom! \(minutes) focused minutes in the bag. I'm so proud of you!",
        ].randomElement()!
    }

    static func sessionStopped(elapsedMinutes: Int) -> String {
        elapsedMinutes < 2
            ? "Stopped already? That's okay — let's try again when you're ready 🙂"
            : "You stopped after \(elapsedMinutes) minutes. Every minute counts! Ready for another round later?"
    }

    private static let duringLines = [
        "You're doing great. Keep going!",
        "Eyes on the task. The feed can wait 😌",
        "Tiny steps still move you forward.",
        "Future you is going to be so thankful.",
        "Breathe in… and back to work 💜",
        "Distractions are knocking. We're not home.",
        "Stay with it — the hard part is starting, and you already did!",
    ]

    static func duringSession(seed: Int) -> String {
        duringLines[((seed % duringLines.count) + duringLines.count) % duringLines.count]
    }

    /// Simple keyword-based reply when Mira has no AI key.
    static func offlineReply(_ message: String, sessionRunning: Bool, name: String) -> String {
        let m = message.lowercased()
        func has(_ words: [String]) -> Bool { words.contains { m.contains($0) } }

        if ["hi", "hello", "hey", "namaste"].contains(where: { m.trimmingCharacters(in: .whitespaces).hasPrefix($0) }) {
            return "\(hey(name))! 💜 Want to start a focus session together?"
        }
        if has(["motivat", "lazy", "tired", "can't", "cant", "bored"]) {
            return [
                "Just do 5 minutes. Seriously, only 5. Momentum will do the rest 💪",
                "You don't need to feel motivated to start — starting is what creates motivation!",
                "Tired is okay. Pick the easiest task and do just that one.",
            ].randomElement()!
        }
        if has(["distract", "instagram", "youtube", "reels", "scroll", "phone"]) {
            return sessionRunning
                ? "I see you 👀 Those apps are blocked for a reason. Back to it — you're almost there!"
                : "Pick those apps in the Apps tab and start Focus. I'll keep them locked for you."
        }
        if has(["plan", "study", "exam", "schedule", "homework"]) {
            return "Try this: write down 3 tasks, start with the hardest one, and do a 45-minute Deep Focus. Then a 10-minute break!"
        }
        if has(["break", "rest"]) {
            return sessionRunning ? "Your break is coming soon — finish strong first!" : "Breaks are important! Stretch, drink water, then let's go again."
        }
        if has(["thank", "love", "cute"]) { return "Aww 🥰 I'm always here for you!" }
        if has(["deep"]) {
            return "Deep Focus locks your chosen apps and can't be stopped until the timer ends. You can't even delete apps. Super strict, super effective!"
        }
        if m.contains("?") {
            return "Good question! Add an AI key (Claude, ChatGPT, Gemini, Groq or OpenRouter) in Settings and I can answer anything. For now: focus first 😉"
        }
        return ["Got it! Want to turn that into a focus session?", "I hear you. One step at a time 💜", "Let's channel that energy into some focused work!"].randomElement()!
    }
}
