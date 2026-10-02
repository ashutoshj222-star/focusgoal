import SwiftUI

private let quickPrompts = ["Motivate me 💪", "Plan my study session", "I keep getting distracted", "Give me a focus tip"]

struct ChatView: View {
    @EnvironmentObject private var focus: FocusController
    @EnvironmentObject private var chat: MiraChat
    @State private var input = ""

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                MiraView(mood: focus.activeSession != nil ? .focused : .happy, talking: chat.typing)
                    .frame(width: 64, height: 64)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Mira").font(.title3.weight(.semibold))
                    Text(status).font(.subheadline).foregroundStyle(Palette.textDim)
                }
                Spacer()
            }
            .padding(.horizontal, 10).padding(.vertical, 4)
            .glass(radius: 26)
            .padding(.horizontal, 16).padding(.top, 8)

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(chat.messages) { message in
                            Bubble(text: message.text, mine: message.fromUser).id(message.id)
                        }
                        if chat.typing { Bubble(text: "…", mine: false).id("typing") }
                    }
                    .padding(16)
                }
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: chat.messages.count) { _, _ in scrollToEnd(proxy) }
                .onChange(of: chat.typing) { _, _ in scrollToEnd(proxy) }
            }

            if input.isEmpty && !chat.typing {
                ScrollView(.horizontal) {
                    HStack(spacing: 8) {
                        ForEach(quickPrompts, id: \.self) { prompt in
                            Button(prompt) { send(prompt) }
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(Palette.textDim)
                                .padding(.horizontal, 14).padding(.vertical, 8)
                                .glassCapsule()
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .scrollIndicators(.hidden)
                .padding(.bottom, 8)
            }

            HStack(spacing: 8) {
                TextField("Message Mira…", text: $input, axis: .vertical)
                    .lineLimit(1...4)
                    .submitLabel(.send)
                    .onSubmit { send(input) }
                Button { send(input) } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(input.isEmpty || chat.typing ? AnyShapeStyle(.white.opacity(0.14)) : AnyShapeStyle(Palette.accent)))
                }
                .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty || chat.typing)
            }
            .padding(.leading, 18).padding(.trailing, 6).padding(.vertical, 6)
            .glassCapsule(tint: .white.opacity(0.1))
            .padding(.horizontal, 16).padding(.bottom, 8)
        }
        .onAppear {
            if chat.messages.isEmpty {
                chat.addMira(MiraLines.greeting(name: focus.settings.userName, hour: Calendar.current.component(.hour, from: .now)))
            }
        }
    }

    private var status: String {
        if chat.typing { return "typing…" }
        if focus.settings.apiKey.isEmpty { return "your focus buddy · offline mode" }
        if let provider = AIProvider.detect(focus.settings.apiKey) {
            return "your focus buddy · \(provider.label.components(separatedBy: " (").first ?? provider.label) on ✨"
        }
        return "your focus buddy · check your API key"
    }

    private func send(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        input = ""
        Task { await chat.send(trimmed) }
    }

    private func scrollToEnd(_ proxy: ScrollViewProxy) {
        withAnimation {
            if chat.typing { proxy.scrollTo("typing", anchor: .bottom) } else if let last = chat.messages.last { proxy.scrollTo(last.id, anchor: .bottom) }
        }
    }
}

private struct Bubble: View {
    let text: String
    let mine: Bool

    var body: some View {
        HStack {
            if mine { Spacer(minLength: 50) }
            Text(text)
                .font(.body)
                .padding(.horizontal, 16).padding(.vertical, 11)
                .background {
                    if mine {
                        UnevenRoundedRectangle(topLeadingRadius: 22, bottomLeadingRadius: 22, bottomTrailingRadius: 6, topTrailingRadius: 22)
                            .fill(Palette.accentGradient)
                    }
                }
                .modifier(Glass(
                    shape: UnevenRoundedRectangle(topLeadingRadius: 22, bottomLeadingRadius: mine ? 22 : 6, bottomTrailingRadius: mine ? 6 : 22, topTrailingRadius: 22),
                    tint: mine ? .clear : .white.opacity(0.1)
                ))
            if !mine { Spacer(minLength: 50) }
        }
    }
}
