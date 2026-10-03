import ActivityKit
import SwiftUI
import WidgetKit

@main
struct FocusWidgetBundle: WidgetBundle {
    var body: some Widget {
        FocusTimerWidget()
        FocusLiveActivity()
    }
}

private let accent = Color(red: 0.545, green: 0.486, blue: 1.0)
private let accentLight = Color(red: 0.725, green: 0.682, blue: 1.0)
private let widgetBackground = LinearGradient(
    colors: [Color(red: 0.23, green: 0.16, blue: 0.5), Color(red: 0.1, green: 0.07, blue: 0.25)],
    startPoint: .topLeading, endPoint: .bottomTrailing
)

// MARK: - Home-screen widget

struct FocusEntry: TimelineEntry {
    let date: Date
    let session: FocusSession?
    let lastMinutes: Int
    let todayMinutes: Int
}

struct FocusProvider: TimelineProvider {
    func placeholder(in context: Context) -> FocusEntry {
        FocusEntry(date: .now, session: nil, lastMinutes: 25, todayMinutes: 0)
    }

    func getSnapshot(in context: Context, completion: @escaping (FocusEntry) -> Void) {
        completion(current())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<FocusEntry>) -> Void) {
        let now = current()
        var entries = [now]
        // Flip back to the "ready" state when the session ends.
        if let session = now.session {
            entries.append(FocusEntry(date: session.end, session: nil, lastMinutes: now.lastMinutes, todayMinutes: now.todayMinutes))
        }
        completion(Timeline(entries: entries, policy: .never))
    }

    private func current() -> FocusEntry {
        FocusEntry(
            date: .now,
            session: SharedStore.activeSession,
            lastMinutes: SharedStore.lastDurationMinutes,
            todayMinutes: SharedStore.todayMinutes
        )
    }
}

struct FocusTimerWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "FocusTimer", provider: FocusProvider()) { entry in
            FocusWidgetView(entry: entry)
                .containerBackground(for: .widget) { widgetBackground }
                .widgetURL(URL(string: "focusgoal://home"))
        }
        .configurationDisplayName("Focus timer")
        .description("See your focus timer at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct FocusWidgetView: View {
    let entry: FocusEntry

    var body: some View {
        VStack(spacing: 6) {
            if let session = entry.session, session.end > entry.date {
                Text(session.mode == .deep ? "DEEP FOCUS 🔒" : "FOCUSING")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(accentLight)
                Text(timerInterval: entry.date...session.end, countsDown: true)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white)
                Text("Mira is guarding your focus")
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.7))
            } else {
                Text("FocusGoal")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.7))
                Text(String(format: "%02d:00", entry.lastMinutes))
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                Text("Tap to start · today \(entry.todayMinutes)m")
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.7))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Live Activity (lock screen + Dynamic Island)

struct FocusLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FocusActivityAttributes.self) { context in
            HStack(spacing: 14) {
                Image(systemName: context.state.deep ? "lock.fill" : "timer")
                    .font(.title2)
                    .foregroundStyle(accentLight)
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.state.deep ? "Deep Focus" : "Focus mode")
                        .font(.headline)
                        .foregroundStyle(.white)
                    Text("Distracting apps are blocked")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.7))
                }
                Spacer()
                Text(timerInterval: Date.now...max(Date.now, context.state.end), countsDown: true)
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .multilineTextAlignment(.trailing)
                    .frame(width: 110)
                    .foregroundStyle(.white)
            }
            .padding()
            .activityBackgroundTint(Color(red: 0.1, green: 0.07, blue: 0.25))
            .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(context.state.deep ? "Deep" : "Focus", systemImage: context.state.deep ? "lock.fill" : "timer")
                        .foregroundStyle(accentLight)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(timerInterval: Date.now...max(Date.now, context.state.end), countsDown: true)
                        .monospacedDigit()
                        .frame(width: 70)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text("Stay with it. Mira believes in you 💜")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.7))
                }
            } compactLeading: {
                Image(systemName: "timer").foregroundStyle(accent)
            } compactTrailing: {
                Text(timerInterval: Date.now...max(Date.now, context.state.end), countsDown: true)
                    .monospacedDigit()
                    .frame(width: 44)
            } minimal: {
                Image(systemName: "timer").foregroundStyle(accent)
            }
        }
    }
}
