import Foundation

struct ChatTurn {
    let fromUser: Bool
    let text: String
}

/// Which AI service a pasted key belongs to, worked out from its prefix.
enum AIProvider: String, CaseIterable {
    case claude, openai, gemini, groq, openrouter

    var label: String {
        switch self {
        case .claude: "Claude (Anthropic)"
        case .openai: "ChatGPT (OpenAI)"
        case .gemini: "Gemini (Google)"
        case .groq: "Groq"
        case .openrouter: "OpenRouter (any model)"
        }
    }

    var defaultModel: String {
        switch self {
        case .claude: "claude-opus-5-5"
        case .openai: "gpt-5-mini"
        case .gemini: "gemini-flash-latest"
        case .groq: "llama-3.1-8b-instant"
        case .openrouter: "openrouter/auto"
        }
    }

    static func detect(_ key: String) -> AIProvider? {
        let k = key.trimmingCharacters(in: .whitespacesAndNewlines)
        if k.isEmpty { return nil }
        if k.hasPrefix("sk-ant-") { return .claude }
        if k.hasPrefix("sk-or-") { return .openrouter }
        if k.hasPrefix("gsk_") { return .groq }
        if k.hasPrefix("AIza") { return .gemini }
        if k.hasPrefix("sk-") { return .openai }
        return nil
    }
}

enum MiraBrainError: LocalizedError {
    case http(Int, String)
    case badResponse

    var errorDescription: String? {
        switch self {
        case let .http(code, message): "HTTP \(code): \(message)"
        case .badResponse: "Unexpected response"
        }
    }
}

/// Calls the chosen AI service. There's no official Swift SDK for these APIs, so this uses HTTPS + JSON.
enum MiraBrain {
    static let systemPrompt = """
    You are Mira, the friendly cartoon focus buddy inside FocusGoal, an app that blocks distracting \
    apps so people can study and work. You're warm, upbeat and a little playful, like a supportive \
    friend who also keeps them honest.

    How you talk:
    - Keep replies short: usually 1-3 sentences, chat-style, at most one emoji.
    - Encourage focus. If they want to scroll social media during a session, kindly redirect them.
    - Help with planning study or work sessions, breaking tasks down, beating procrastination, and \
    quick questions about whatever they're working on.
    - Each user message ends with an [App context: ...] note from the app (timer state, today's focus \
    minutes). Use it naturally; never quote it or mention that it exists.
    - You can't start or stop timers or change settings yourself. If asked, tell them which button to \
    press: "Focus" (can be stopped) or "Deep Focus" (can't be stopped, blocks deleting apps).
    """

    /// Returns Mira's reply, or nil if the model declined to answer.
    static func reply(apiKey: String, model: String, history: [ChatTurn], context: String) async throws -> String? {
        guard let provider = AIProvider.detect(apiKey) else { return nil }
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let m = model.trimmingCharacters(in: .whitespaces).isEmpty ? provider.defaultModel : model.trimmingCharacters(in: .whitespaces)
        let turns = prepare(history, context: context)

        switch provider {
        case .claude: return try await claude(key: key, model: m, turns: turns)
        case .gemini: return try await gemini(key: key, model: m, turns: turns)
        case .openai: return try await openAICompatible(base: "https://api.openai.com/v1", key: key, model: m, turns: turns)
        case .groq: return try await openAICompatible(base: "https://api.groq.com/openai/v1", key: key, model: m, turns: turns)
        case .openrouter: return try await openAICompatible(base: "https://openrouter.ai/api/v1", key: key, model: m, turns: turns)
        }
    }

    /// Last 24 turns, merged so roles alternate and the first turn is the user's, with the app
    /// context appended to the newest user message.
    static func prepare(_ history: [ChatTurn], context: String) -> [ChatTurn] {
        var merged: [ChatTurn] = []
        for turn in history.suffix(24) where !turn.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            if merged.isEmpty && !turn.fromUser { continue }
            if let last = merged.last, last.fromUser == turn.fromUser {
                merged[merged.count - 1] = ChatTurn(fromUser: last.fromUser, text: last.text + "\n\n" + turn.text)
            } else {
                merged.append(turn)
            }
        }
        if let last = merged.last, last.fromUser {
            merged[merged.count - 1] = ChatTurn(fromUser: true, text: last.text + "\n\n[App context: \(context)]")
        }
        return merged
    }

    // MARK: - Providers

    private static func claude(key: String, model: String, turns: [ChatTurn]) async throws -> String? {
        let body: [String: Any] = [
            "model": model,
            "max_tokens": 4096,
            "system": systemPrompt,
            "messages": turns.map { ["role": $0.fromUser ? "user" : "assistant", "content": $0.text] },
            // Short chat replies don't need deep reasoning.
            "output_config": ["effort": "low"],
            // If a safety classifier declines, let the API retry on a fallback model.
            "fallbacks": "default",
        ]
        let json = try await post(
            "https://api.anthropic.com/v1/messages",
            headers: [
                "x-api-key": key,
                "anthropic-version": "2023-06-01",
                "anthropic-beta": "server-side-fallback-2026-07-01",
            ],
            body: body
        )
        if json["stop_reason"] as? String == "refusal" { return nil }
        let blocks = json["content"] as? [[String: Any]] ?? []
        let text = blocks.filter { $0["type"] as? String == "text" }.compactMap { $0["text"] as? String }.joined()
        return text.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
    }

    private static func openAICompatible(base: String, key: String, model: String, turns: [ChatTurn]) async throws -> String? {
        var messages: [[String: Any]] = [["role": "system", "content": systemPrompt]]
        messages += turns.map { ["role": $0.fromUser ? "user" : "assistant", "content": $0.text] }
        let json = try await post(
            "\(base)/chat/completions",
            headers: ["Authorization": "Bearer \(key)"],
            body: ["model": model, "messages": messages]
        )
        guard let choice = (json["choices"] as? [[String: Any]])?.first,
              let message = choice["message"] as? [String: Any] else { throw MiraBrainError.badResponse }
        if let refusal = message["refusal"] as? String, !refusal.isEmpty { return nil }
        return (message["content"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
    }

    private static func gemini(key: String, model: String, turns: [ChatTurn]) async throws -> String? {
        let body: [String: Any] = [
            "system_instruction": ["parts": [["text": systemPrompt]]],
            "contents": turns.map { ["role": $0.fromUser ? "user" : "model", "parts": [["text": $0.text]]] },
        ]
        let path = model.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? model
        let json = try await post(
            "https://generativelanguage.googleapis.com/v1beta/models/\(path):generateContent",
            headers: ["x-goog-api-key": key],
            body: body
        )
        guard let candidate = (json["candidates"] as? [[String: Any]])?.first,
              let content = candidate["content"] as? [String: Any],
              let parts = content["parts"] as? [[String: Any]] else { return nil }
        return parts.compactMap { $0["text"] as? String }.joined().trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
    }

    private static func post(_ url: String, headers: [String: String], body: [String: Any]) async throws -> [String: Any] {
        guard let endpoint = URL(string: url) else { throw MiraBrainError.badResponse }
        var request = URLRequest(url: endpoint, timeoutInterval: 90)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(code) else {
            let message = (json["error"] as? [String: Any])?["message"] as? String
                ?? String(data: data, encoding: .utf8)?.prefix(160).description ?? ""
            throw MiraBrainError.http(code, message)
        }
        return json
    }
}

extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
