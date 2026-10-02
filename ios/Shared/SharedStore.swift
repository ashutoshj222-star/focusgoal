import Foundation

/// A running focus session. Shared with the extensions through the App Group so the shield screen,
/// the monitor and the widget all see the same timer.
struct FocusSession: Codable, Equatable {
    enum Mode: String, Codable { case focus, deep }

    var mode: Mode
    var start: Date
    var end: Date
    /// Deep Focus only: shield every app except the "always allowed" ones.
    var lockWholePhone: Bool

    var duration: TimeInterval { end.timeIntervalSince(start) }
    func remaining(at now: Date = .now) -> TimeInterval { max(0, end.timeIntervalSince(now)) }
    func isRunning(at now: Date = .now) -> Bool { now < end }
}

enum SharedStore {
    /// Must match the App Group in every target's .entitlements file.
    static let appGroup = "group.com.focusgoal.app"

    static var defaults: UserDefaults { UserDefaults(suiteName: appGroup) ?? .standard }

    private static let sessionKey = "session"
    private static let lastDurationKey = "lastDuration"
    private static let todayMinutesKey = "widgetTodayMinutes"

    static var session: FocusSession? {
        get {
            guard let data = defaults.data(forKey: sessionKey) else { return nil }
            return try? JSONDecoder().decode(FocusSession.self, from: data)
        }
        set {
            if let newValue, let data = try? JSONEncoder().encode(newValue) {
                defaults.set(data, forKey: sessionKey)
            } else {
                defaults.removeObject(forKey: sessionKey)
            }
        }
    }

    /// Session only if it's still running.
    static var activeSession: FocusSession? {
        guard let s = session, s.isRunning() else { return nil }
        return s
    }

    static var lastDurationMinutes: Int {
        get { defaults.object(forKey: lastDurationKey) as? Int ?? 25 }
        set { defaults.set(newValue, forKey: lastDurationKey) }
    }

    /// Mirrored here so the widget can show it.
    static var todayMinutes: Int {
        get { defaults.integer(forKey: todayMinutesKey) }
        set { defaults.set(newValue, forKey: todayMinutesKey) }
    }
}
