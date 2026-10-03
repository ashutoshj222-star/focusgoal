import Foundation

struct ChatMessage: Identifiable, Equatable {
    let id = UUID()
    let fromUser: Bool
    let text: String
    let time = Date.now
}

/// Chat history with Mira (kept while the app runs) and reply generation.
@MainActor
final class MiraChat: ObservableObject {
    static let shared = MiraChat()

    @Published private(set) var messages: [ChatMessage] = []
    @Published private(set) var typing = false

    var lastMiraMessage: ChatMessage? { messages.last { !$0.fromUser } }

    func addMira(_ text: String) {
        messages.append(ChatMessage(fromUser: false, text: text))
    }

    func send(_ text: String) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !typing else { return }
        messages.append(ChatMessage(fromUser: true, text: trimmed))
        typing = true
        defer { typing = false }

        let focus = FocusController.shared
        let settings = focus.settings
        let reply: String
        if settings.apiKey.isEmpty {
            try? await Task.sleep(nanoseconds: 600_000_000) // a tiny pause feels more natural
            reply = MiraLines.offlineReply(trimmed, sessionRunning: focus.activeSession != nil, name: settings.userName)
        } else if AIProvider.detect(settings.apiKey) == nil {
            reply = "I don't recognise that API key 🤔 Use a Claude, ChatGPT, Gemini, Groq or OpenRouter key in Settings."
        } else {
            do {
                reply = try await MiraBrain.reply(
                    apiKey: settings.apiKey,
                    model: settings.aiModel,
                    history: messages.map { ChatTurn(fromUser: $0.fromUser, text: $0.text) },
                    context: describeContext()
                ) ?? "Let's talk about something else — how's your focus going? 🙂"
            } catch {
                reply = "Hmm, I couldn't reach my brain right now (\(error.localizedDescription.prefix(80))). Check your API key in Settings or your internet 🙏"
            }
        }
        messages.append(ChatMessage(fromUser: false, text: reply))
    }

    private func describeContext() -> String {
        let focus = FocusController.shared
        let name = focus.settings.userName.isEmpty ? "unknown" : focus.settings.userName
        let timer: String
        if let s = focus.activeSession {
            let mins = Int(s.remaining() / 60) + 1
            timer = "\(s.mode == .deep ? "Deep Focus (can't be stopped)" : "Focus") session running, about \(mins) min left"
        } else {
            timer = "no session running"
        }
        return "user name: \(name); \(timer); focused today: \(focus.stats.todayMinutes) min; streak: \(focus.stats.streakDays) days"
    }
}
