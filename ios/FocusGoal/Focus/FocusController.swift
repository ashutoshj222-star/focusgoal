import ActivityKit
import Combine
import FamilyControls
import Foundation
import UserNotifications
import WidgetKit

struct AppSettings: Equatable {
    var userName = ""
    var apiKey = ""
    var aiModel = ""
    var lockWholePhoneByDefault = false
    var onboardingDone = false
}

struct FocusStats: Equatable {
    var todayMinutes = 0
    var totalMinutes = 0
    var sessionsCompleted = 0
    var streakDays = 0
}

/// Starts, stops and finishes focus sessions and keeps shields, notifications, the widget and the
/// Live Activity in sync. Everything else in the app reads state from here.
@MainActor
final class FocusController: ObservableObject {
    static let shared = FocusController()

    @Published private(set) var session: FocusSession?
    @Published private(set) var now = Date.now
    @Published private(set) var stats = FocusStats()
    @Published private(set) var authorized = false
    @Published private(set) var blockSelection: FamilyActivitySelection
    @Published private(set) var allowSelection: FamilyActivitySelection
    @Published var settings: AppSettings {
        didSet { if settings != oldValue { saveSettings() } }
    }

    private let defaults = UserDefaults.standard
    private var ticker: AnyCancellable?

    var activeSession: FocusSession? {
        guard let session, session.isRunning(at: now) else { return nil }
        return session
    }

    var blockedCount: Int {
        blockSelection.applicationTokens.count + blockSelection.categoryTokens.count + blockSelection.webDomainTokens.count
    }

    private init() {
        session = SharedStore.session
        blockSelection = ShieldManager.blockSelection
        allowSelection = ShieldManager.allowSelection
        settings = AppSettings(
            userName: UserDefaults.standard.string(forKey: "userName") ?? "",
            apiKey: Keychain.read("aiApiKey") ?? "",
            aiModel: UserDefaults.standard.string(forKey: "aiModel") ?? "",
            lockWholePhoneByDefault: UserDefaults.standard.bool(forKey: "lockAllDefault"),
            onboardingDone: UserDefaults.standard.bool(forKey: "onboardingDone")
        )
        authorized = AuthorizationCenter.shared.authorizationStatus == .approved
        stats = loadStats()
        ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect().sink { [weak self] date in
            self?.tick(date)
        }
    }

    // MARK: - Permissions

    func refresh() {
        authorized = AuthorizationCenter.shared.authorizationStatus == .approved
        stats = loadStats()
        finishIfExpired()
    }

    func requestScreenTimeAccess() async {
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
        } catch {
            print("Screen Time authorization failed: \(error)")
        }
        authorized = AuthorizationCenter.shared.authorizationStatus == .approved
    }

    func requestNotifications() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    // MARK: - What to block

    /// During Deep Focus apps can be added but not removed.
    func updateBlockSelection(_ selection: FamilyActivitySelection) {
        var updated = selection
        if let s = activeSession, s.mode == .deep {
            updated.applicationTokens.formUnion(blockSelection.applicationTokens)
            updated.categoryTokens.formUnion(blockSelection.categoryTokens)
            updated.webDomainTokens.formUnion(blockSelection.webDomainTokens)
        }
        blockSelection = updated
        ShieldManager.blockSelection = updated
        if let s = activeSession { ShieldManager.apply(s) }
    }

    func updateAllowSelection(_ selection: FamilyActivitySelection) {
        allowSelection = selection
        ShieldManager.allowSelection = selection
        if let s = activeSession { ShieldManager.apply(s) }
    }

    // MARK: - Sessions

    /// Returns an error message to show, or nil if the session started.
    @discardableResult
    func start(mode: FocusSession.Mode, minutes: Int, lockWholePhone: Bool = false) -> String? {
        guard activeSession == nil else { return nil }
        guard authorized else { return "I need Screen Time access first — open Settings → Permissions 🙏" }
        let lockAll = mode == .deep && lockWholePhone
        guard lockAll || blockedCount > 0 else { return "Pick some apps to block first — open the Apps tab 📱" }

        let start = Date.now
        let session = FocusSession(mode: mode, start: start, end: start.addingTimeInterval(TimeInterval(minutes * 60)), lockWholePhone: lockAll)
        SharedStore.session = session
        SharedStore.lastDurationMinutes = minutes
        self.session = session

        ShieldManager.apply(session)
        do {
            try ShieldManager.scheduleEnd(for: session)
        } catch {
            print("Couldn't schedule the session end: \(error)") // the app still ends it when opened
        }
        scheduleEndNotification(session)
        startLiveActivity(session)
        WidgetCenter.shared.reloadAllTimelines()

        MiraChat.shared.addMira(MiraLines.sessionStart(mode: mode, minutes: minutes, name: settings.userName))
        return nil
    }

    /// Only regular Focus can be stopped.
    @discardableResult
    func stop() -> Bool {
        guard let s = activeSession else { endCleanup(); return true }
        guard s.mode == .focus else { return false }
        let elapsed = Int((Date.now.timeIntervalSince(s.start) / 60).rounded())
        if elapsed > 0 { recordFocus(minutes: elapsed, completed: false) }
        endCleanup()
        MiraChat.shared.addMira(MiraLines.sessionStopped(elapsedMinutes: elapsed))
        return true
    }

    private func tick(_ date: Date) {
        now = date
        finishIfExpired()
    }

    private func finishIfExpired() {
        guard let s = session, !s.isRunning(at: .now) else { return }
        let minutes = Int((s.duration / 60).rounded())
        recordFocus(minutes: minutes, completed: true)
        endCleanup()
        MiraChat.shared.addMira(MiraLines.sessionComplete(minutes: minutes, name: settings.userName))
    }

    private func endCleanup() {
        session = nil
        SharedStore.session = nil
        ShieldManager.clear()
        ShieldManager.stopMonitoring()
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["focus-end"])
        endLiveActivities()
        WidgetCenter.shared.reloadAllTimelines()
    }

    // MARK: - Notifications & Live Activity

    private func scheduleEndNotification(_ session: FocusSession) {
        let content = UNMutableNotificationContent()
        content.title = "Session complete 🎉"
        content.body = MiraLines.sessionComplete(minutes: Int((session.duration / 60).rounded()), name: settings.userName)
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, session.end.timeIntervalSinceNow), repeats: false)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "focus-end", content: content, trigger: trigger))
    }

    private func startLiveActivity(_ session: FocusSession) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        let state = FocusActivityAttributes.ContentState(end: session.end, deep: session.mode == .deep)
        _ = try? Activity.request(
            attributes: FocusActivityAttributes(title: "FocusGoal"),
            content: .init(state: state, staleDate: session.end),
            pushType: nil
        )
    }

    private func endLiveActivities() {
        let activities = Activity<FocusActivityAttributes>.activities
        Task {
            for activity in activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
    }

    // MARK: - Persistence

    private func saveSettings() {
        defaults.set(settings.userName, forKey: "userName")
        defaults.set(settings.aiModel, forKey: "aiModel")
        defaults.set(settings.lockWholePhoneByDefault, forKey: "lockAllDefault")
        defaults.set(settings.onboardingDone, forKey: "onboardingDone")
        if settings.apiKey.isEmpty { Keychain.delete("aiApiKey") } else { Keychain.write("aiApiKey", settings.apiKey) }
    }

    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private func dayKey(_ date: Date) -> String { Self.dayFormatter.string(from: date) }

    private func recordFocus(minutes: Int, completed: Bool) {
        let today = dayKey(.now)
        let yesterday = dayKey(Calendar.current.date(byAdding: .day, value: -1, to: .now) ?? .now)
        let todayMinutes = (defaults.string(forKey: "statsDay") == today ? defaults.integer(forKey: "statsToday") : 0) + minutes

        var streak = defaults.integer(forKey: "statsStreak")
        var sessions = defaults.integer(forKey: "statsSessions")
        var lastCompleted = defaults.string(forKey: "statsLastCompleted")
        if completed {
            sessions += 1
            switch lastCompleted {
            case today: streak = max(streak, 1)
            case yesterday: streak += 1
            default: streak = 1
            }
            lastCompleted = today
        }
        defaults.set(today, forKey: "statsDay")
        defaults.set(todayMinutes, forKey: "statsToday")
        defaults.set(defaults.integer(forKey: "statsTotal") + minutes, forKey: "statsTotal")
        defaults.set(sessions, forKey: "statsSessions")
        defaults.set(streak, forKey: "statsStreak")
        defaults.set(lastCompleted, forKey: "statsLastCompleted")
        stats = loadStats()
    }

    private func loadStats() -> FocusStats {
        let today = dayKey(.now)
        let yesterday = dayKey(Calendar.current.date(byAdding: .day, value: -1, to: .now) ?? .now)
        let last = defaults.string(forKey: "statsLastCompleted")
        let todayMinutes = defaults.string(forKey: "statsDay") == today ? defaults.integer(forKey: "statsToday") : 0
        SharedStore.todayMinutes = todayMinutes
        return FocusStats(
            todayMinutes: todayMinutes,
            totalMinutes: defaults.integer(forKey: "statsTotal"),
            sessionsCompleted: defaults.integer(forKey: "statsSessions"),
            streakDays: (last == today || last == yesterday) ? defaults.integer(forKey: "statsStreak") : 0
        )
    }
}
