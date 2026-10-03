import FamilyControls
import SwiftUI

private let durations = [10, 15, 25, 45, 60, 90, 120]

struct HomeView: View {
    @EnvironmentObject private var focus: FocusController
    let openApps: () -> Void
    let openChat: () -> Void
    let openSetup: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                if let session = focus.activeSession {
                    ActiveSessionView(session: session, openChat: openChat)
                } else {
                    IdleView(openApps: openApps, openChat: openChat, openSetup: openSetup)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .scrollIndicators(.hidden)
        .background(Color.clear)
    }
}

// MARK: - Idle: pick a duration, then Focus or Deep Focus

private struct IdleView: View {
    @EnvironmentObject private var focus: FocusController
    @EnvironmentObject private var chat: MiraChat
    let openApps: () -> Void
    let openChat: () -> Void
    let openSetup: () -> Void

    @State private var minutes = SharedStore.lastDurationMinutes
    @State private var notice: String?
    @State private var showDeepSheet = false
    @State private var greeting = ""

    var body: some View {
        let stats = focus.stats

        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(focus.settings.userName.isEmpty ? "Hi there 👋" : "Hi, \(focus.settings.userName) 👋")
                    .font(.system(size: 26, weight: .semibold))
                Text("Ready to focus?").font(.subheadline).foregroundStyle(Palette.textDim)
            }
            Spacer()
            Text("🔥 \(stats.streakDays) day\(stats.streakDays == 1 ? "" : "s")")
                .font(.system(size: 15, weight: .semibold))
                .padding(.horizontal, 14).padding(.vertical, 8)
                .glassCapsule()
        }

        MiraHero(text: notice ?? recentMiraLine ?? greeting, mood: .happy, onTap: openChat)
            .onAppear {
                if greeting.isEmpty {
                    greeting = MiraLines.greeting(name: focus.settings.userName, hour: Calendar.current.component(.hour, from: .now))
                }
            }

        if !focus.authorized {
            Button(action: openSetup) {
                HStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Palette.danger)
                    VStack(alignment: .leading) {
                        Text("Screen Time access is off").font(.headline)
                        Text("Tap to allow it so I can block apps.").font(.subheadline).foregroundStyle(Palette.textDim)
                    }
                    Spacer()
                }
                .padding(18).glass()
            }
            .buttonStyle(.plain)
        }

        // ---- Timer picker ----
        VStack(spacing: 18) {
            HStack {
                RoundButton(symbol: "minus") { minutes = max(5, minutes - 5) }
                TimerRing(progress: max(0.04, Double(minutes) / 120)) {
                    VStack(spacing: 2) {
                        Text(formatCountdown(TimeInterval(minutes * 60)))
                            .font(.system(size: minutes >= 60 ? 34 : 44, weight: .light, design: .rounded))
                            .monospacedDigit()
                        Text("minutes").font(.caption).foregroundStyle(Palette.textFaint)
                    }
                }
                .frame(width: 180, height: 180)
                RoundButton(symbol: "plus") { minutes = min(240, minutes + 5) }
            }
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(durations, id: \.self) { d in
                        Button { minutes = d } label: {
                            Text(d < 60 ? "\(d) m" : d % 60 == 0 ? "\(d / 60) h" : "\(d / 60)h \(d % 60)m")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(d == minutes ? Color.white : Palette.textDim)
                                .padding(.horizontal, 16).padding(.vertical, 9)
                                .background(Capsule().fill(d == minutes ? Palette.accent.opacity(0.55) : .white.opacity(0.08)))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .scrollIndicators(.hidden)
        }
        .padding(.vertical, 22).padding(.horizontal, 16)
        .frame(maxWidth: .infinity)
        .glass()

        // ---- Start buttons ----
        HStack(spacing: 12) {
            PillButton(title: "Focus", systemImage: "play.fill", filled: false) {
                if let error = focus.start(mode: .focus, minutes: minutes) { notice = error } else { notice = nil }
            }
            PillButton(title: "Deep Focus", systemImage: "lock.fill") {
                if !focus.authorized {
                    notice = "I need Screen Time access first — tap the warning above 🙏"
                } else if focus.blockedCount == 0 && !focus.settings.lockWholePhoneByDefault {
                    notice = "Pick some apps to block first — open the Apps tab 📱"
                } else {
                    notice = nil
                    showDeepSheet = true
                }
            }
        }
        Text("Focus can be stopped any time. Deep Focus can't be stopped, and apps can't be deleted until it ends.")
            .font(.footnote).foregroundStyle(Palette.textFaint).multilineTextAlignment(.center)

        BlockedSummary(openApps: openApps)

        HStack(spacing: 12) {
            StatTile(label: "Today", value: "\(stats.todayMinutes)m")
            StatTile(label: "Streak", value: "\(stats.streakDays)d")
            StatTile(label: "Sessions", value: "\(stats.sessionsCompleted)")
        }
        .sheet(isPresented: $showDeepSheet) {
            DeepFocusSheet(minutes: minutes) { lockAll in
                showDeepSheet = false
                if let error = focus.start(mode: .deep, minutes: minutes, lockWholePhone: lockAll) { notice = error }
            }
            .presentationDetents([.medium, .large])
            .presentationBackground(.ultraThinMaterial)
        }
    }

    private var recentMiraLine: String? {
        guard let last = chat.lastMiraMessage, focus.now.timeIntervalSince(last.time) < 120 else { return nil }
        return last.text
    }
}

// MARK: - Running session

private struct ActiveSessionView: View {
    @EnvironmentObject private var focus: FocusController
    @EnvironmentObject private var chat: MiraChat
    let session: FocusSession
    let openChat: () -> Void
    @State private var confirmStop = false

    var body: some View {
        let now = focus.now
        let remaining = session.remaining(at: now)
        let deep = session.mode == .deep

        HStack(spacing: 6) {
            if deep { Image(systemName: "lock.fill").font(.caption) }
            Text(deep ? "DEEP FOCUS · LOCKED" : "FOCUS MODE").font(.system(size: 12, weight: .semibold)).kerning(0.6)
        }
        .padding(.horizontal, 16).padding(.vertical, 8)
        .glassCapsule(tint: deep ? Palette.accent.opacity(0.3) : .white.opacity(0.06))

        TimerRing(progress: remaining / max(1, session.duration), lineWidth: 16) {
            VStack(spacing: 4) {
                Text(formatCountdown(remaining))
                    .font(.system(size: remaining >= 3600 ? 44 : 56, weight: .light, design: .rounded))
                    .monospacedDigit()
                Text("ends at \(formatClock(session.end))").font(.subheadline).foregroundStyle(Palette.textDim)
            }
        }
        .frame(width: 270, height: 270)

        MiraHero(text: bubble(now: now), mood: .focused, onTap: openChat)

        GlassCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(session.lockWholePhone ? "Whole phone locked" : "\(focus.blockedCount) apps & sites blocked").font(.headline)
                    Text(session.lockWholePhone ? "Only your always-allowed apps work" : "They unlock when the timer ends")
                        .font(.subheadline).foregroundStyle(Palette.textDim)
                }
                Spacer()
                if !session.lockWholePhone { AppIconRow(selection: focus.blockSelection) }
            }
        }

        if deep {
            GlassCard {
                HStack(spacing: 12) {
                    Image(systemName: "lock.fill").foregroundStyle(Palette.accentLight)
                    Text("Deep Focus can't be stopped, and apps can't be deleted until it ends. You've got this!")
                        .font(.subheadline).foregroundStyle(Palette.textDim)
                }
            }
        } else {
            PillButton(title: "Stop session", filled: false) { confirmStop = true }
                .confirmationDialog("Stop focusing?", isPresented: $confirmStop, titleVisibility: .visible) {
                    Button("Stop", role: .destructive) { focus.stop() }
                    Button("Keep going", role: .cancel) {}
                } message: {
                    Text("You still have \(formatCountdown(remaining)) left. Mira will be a little sad 🥺")
                }
        }
    }

    private func bubble(now: Date) -> String {
        if let last = chat.lastMiraMessage, now.timeIntervalSince(last.time) < 30 { return last.text }
        return MiraLines.duringSession(seed: Int(now.timeIntervalSince1970 / 30))
    }
}

// MARK: - Pieces

struct MiraHero: View {
    let text: String
    let mood: MiraMood
    let onTap: () -> Void
    @State private var talking = false

    var body: some View {
        HStack(spacing: 4) {
            MiraView(mood: mood, talking: talking).frame(width: 128, height: 128)
            SpeechBubble(text: text)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .task(id: text) {
            talking = true
            try? await Task.sleep(nanoseconds: 1_600_000_000)
            talking = false
        }
    }
}

private struct BlockedSummary: View {
    @EnvironmentObject private var focus: FocusController
    let openApps: () -> Void

    var body: some View {
        Button(action: openApps) {
            VStack(alignment: .leading, spacing: 10) {
                SectionLabel(text: "Blocked during focus")
                HStack {
                    if focus.blockedCount == 0 {
                        Image(systemName: "plus.circle.fill").foregroundStyle(Palette.accentLight)
                        Text("Choose apps to block").font(.headline)
                        Spacer()
                    } else {
                        AppIconRow(selection: focus.blockSelection)
                        Text("\(focus.blockedCount) selected").font(.headline)
                        Spacer()
                        Text("Edit").font(.system(size: 15, weight: .semibold)).foregroundStyle(Palette.accentLight)
                    }
                }
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glass()
        }
        .buttonStyle(.plain)
    }
}

/// Up to five app icons from a selection (Apple renders them; we can't read app names ourselves).
struct AppIconRow: View {
    let selection: FamilyActivitySelection

    var body: some View {
        HStack(spacing: -8) {
            ForEach(Array(selection.applicationTokens.prefix(5)), id: \.self) { token in
                Label(token)
                    .labelStyle(.iconOnly)
                    .frame(width: 30, height: 30)
                    .background(Circle().fill(Palette.bgBottom))
                    .clipShape(Circle())
            }
        }
    }
}

private struct StatTile: View {
    let label: String
    let value: String
    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.system(size: 20, weight: .semibold))
            Text(label).font(.caption).foregroundStyle(Palette.textFaint)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .glass(radius: 20)
    }
}

private struct RoundButton: View {
    let symbol: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 18, weight: .semibold)).frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .glass(radius: 22, tint: .white.opacity(0.12))
    }
}

/// Deep Focus confirmation.
private struct DeepFocusSheet: View {
    @EnvironmentObject private var focus: FocusController
    let minutes: Int
    let onConfirm: (Bool) -> Void
    @State private var lockAll = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 14) {
            MiraView(mood: .focused).frame(width: 100, height: 100)
            Text("Start Deep Focus?").font(.title2.weight(.semibold))
            VStack(alignment: .leading, spacing: 6) {
                bullet("The timer can't be stopped for \(minutes) minutes")
                bullet(lockAll ? "Every app is locked except your always-allowed ones" : "Your blocked apps stay locked")
                bullet("Apps can't be deleted until it ends")
            }
            Toggle(isOn: $lockAll) {
                VStack(alignment: .leading) {
                    Text("Lock entire phone").font(.headline)
                    Text("Calls still work. Pick always-allowed apps in the Apps tab.").font(.caption).foregroundStyle(Palette.textDim)
                }
            }
            .padding(14).glass(radius: 20)
            HStack(spacing: 10) {
                PillButton(title: "Not now", filled: false) { dismiss() }
                PillButton(title: "Lock in 🔒") { onConfirm(lockAll) }
            }
        }
        .padding(22)
        .onAppear { lockAll = focus.settings.lockWholePhoneByDefault }
    }

    private func bullet(_ text: String) -> some View {
        HStack(alignment: .top) {
            Text("•").foregroundStyle(Palette.accentLight)
            Text(text).foregroundStyle(Palette.textDim)
        }
        .font(.subheadline)
    }
}
