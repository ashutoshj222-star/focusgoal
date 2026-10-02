import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var focus: FocusController
    let openSetup: () -> Void
    @State private var showKey = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Settings").font(.system(size: 26, weight: .semibold))

                GlassCard {
                    SectionLabel(text: "You")
                    TextField("Your name", text: $focus.settings.userName)
                        .padding(14).glass(radius: 18, tint: .white.opacity(0.1)).padding(.top, 10)
                    Text("Total focus: \(focus.stats.totalMinutes / 60)h \(focus.stats.totalMinutes % 60)m · \(focus.stats.sessionsCompleted) sessions")
                        .font(.subheadline).foregroundStyle(Palette.textDim).padding(.top, 12)
                }

                GlassCard {
                    SectionLabel(text: "Mira's AI brain")
                    Text("Paste an API key from Claude, ChatGPT (OpenAI), Gemini, Groq or OpenRouter (one key for almost any AI model) and Mira can chat about anything. Without one she uses built-in replies. The key stays on this phone (in the Keychain); usage is billed by that company.")
                        .font(.subheadline).foregroundStyle(Palette.textDim).padding(.top, 6)
                    Group {
                        if showKey {
                            TextField("sk-ant-…  /  sk-…  /  AIza…", text: $focus.settings.apiKey)
                        } else {
                            SecureField("sk-ant-…  /  sk-…  /  AIza…", text: $focus.settings.apiKey)
                        }
                    }
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(14).glass(radius: 18, tint: .white.opacity(0.1)).padding(.top, 10)

                    let key = focus.settings.apiKey
                    if !key.isEmpty {
                        if let provider = AIProvider.detect(key) {
                            Text("✓ \(provider.label) key detected").font(.subheadline).foregroundStyle(Palette.success).padding(.top, 8)
                            TextField("Model (default: \(provider.defaultModel))", text: $focus.settings.aiModel)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .padding(14).glass(radius: 18, tint: .white.opacity(0.1)).padding(.top, 8)
                        } else {
                            Text("Unknown key. Supported: sk-ant- (Claude), sk- (OpenAI), AIza (Gemini), gsk_ (Groq), sk-or- (OpenRouter)")
                                .font(.subheadline).foregroundStyle(Palette.danger).padding(.top, 8)
                        }
                    }
                    Button(showKey ? "Hide key" : "Show key") { showKey.toggle() }
                        .font(.system(size: 15, weight: .semibold)).foregroundStyle(Palette.accentLight).padding(.top, 8)
                }

                GlassCard {
                    SectionLabel(text: "Deep Focus")
                    Toggle(isOn: $focus.settings.lockWholePhoneByDefault) {
                        VStack(alignment: .leading) {
                            Text("Lock entire phone by default").font(.headline)
                            Text("Only calls and your always-allowed apps work").font(.subheadline).foregroundStyle(Palette.textDim)
                        }
                    }
                    .padding(.top, 8)
                }

                Button(action: openSetup) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            SectionLabel(text: "Protection")
                            Text("Permissions & setup").font(.headline)
                            Text("Screen Time access: \(focus.authorized ? "on ✓" : "off")").font(.subheadline).foregroundStyle(Palette.textDim)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(Palette.textDim)
                    }
                    .padding(18).glass()
                }
                .buttonStyle(.plain)

                Text("FocusGoal 1.0 · Mira only sees what you type to her. Blocking uses Apple's Screen Time, so FocusGoal never sees which apps you use.")
                    .font(.footnote).foregroundStyle(Palette.textFaint).padding(.horizontal, 4)
            }
            .padding(20)
        }
    }
}
