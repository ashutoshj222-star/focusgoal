import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings

extension ManagedSettingsStore.Name {
    static let focus = Self("focus")
}

extension DeviceActivityName {
    static let focusSession = Self("focusSession")
}

/// Applies and removes the app shields (Apple's Screen Time blocking) for a session.
enum ShieldManager {
    private static let store = ManagedSettingsStore(named: .focus)
    private static let blockKey = "blockSelection"
    private static let allowKey = "allowSelection"

    /// Apps, categories and websites to block during a session (picked with Apple's app picker).
    static var blockSelection: FamilyActivitySelection {
        get { load(blockKey) }
        set { save(newValue, blockKey) }
    }

    /// Apps that stay usable when Deep Focus locks the whole phone.
    static var allowSelection: FamilyActivitySelection {
        get { load(allowKey) }
        set { save(newValue, allowKey) }
    }

    static func apply(_ session: FocusSession) {
        let block = blockSelection
        if session.lockWholePhone {
            store.shield.applications = nil
            store.shield.applicationCategories = .all(except: allowSelection.applicationTokens)
            store.shield.webDomainCategories = .all()
            store.shield.webDomains = nil
        } else {
            store.shield.applications = block.applicationTokens.isEmpty ? nil : block.applicationTokens
            store.shield.applicationCategories = block.categoryTokens.isEmpty ? nil : .specific(block.categoryTokens)
            store.shield.webDomains = block.webDomainTokens.isEmpty ? nil : block.webDomainTokens
            store.shield.webDomainCategories = block.categoryTokens.isEmpty ? nil : .specific(block.categoryTokens)
        }
        // Deep Focus: no deleting apps (including FocusGoal) until the timer ends.
        store.application.denyAppRemoval = session.mode == .deep ? true : nil
    }

    static func clear() {
        store.clearAllSettings()
    }

    /// Asks iOS to wake the monitor extension when the session ends, so shields come off even if
    /// FocusGoal isn't running. Screen Time schedules must be at least 15 minutes long, so short
    /// sessions use a longer interval plus a "warning" that fires at the real end time.
    static func scheduleEnd(for session: FocusSession) throws {
        let center = DeviceActivityCenter()
        center.stopMonitoring([.focusSession])

        let minimum: TimeInterval = 15 * 60 + 60
        let intervalEnd = max(session.end, session.start.addingTimeInterval(minimum))
        let lead = Int(intervalEnd.timeIntervalSince(session.end).rounded())
        let parts: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute, .second]
        let calendar = Calendar.current

        let schedule = DeviceActivitySchedule(
            intervalStart: calendar.dateComponents(parts, from: session.start),
            intervalEnd: calendar.dateComponents(parts, from: intervalEnd),
            repeats: false,
            warningTime: lead > 0 ? DateComponents(second: lead) : nil
        )
        try center.startMonitoring(.focusSession, during: schedule)
    }

    static func stopMonitoring() {
        DeviceActivityCenter().stopMonitoring([.focusSession])
    }

    private static func load(_ key: String) -> FamilyActivitySelection {
        guard let data = SharedStore.defaults.data(forKey: key),
              let selection = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
        else { return FamilyActivitySelection() }
        return selection
    }

    private static func save(_ selection: FamilyActivitySelection, _ key: String) {
        if let data = try? JSONEncoder().encode(selection) {
            SharedStore.defaults.set(data, forKey: key)
        }
    }
}
