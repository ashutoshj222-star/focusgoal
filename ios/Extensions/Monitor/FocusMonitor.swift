import DeviceActivity
import Foundation

/// iOS wakes this extension at the end of the scheduled interval (or at the warning time for short
/// sessions) — it removes the shields so apps unlock right on time, even if FocusGoal is closed.
/// The app records the finished session the next time it opens.
final class FocusMonitor: DeviceActivityMonitor {
    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        endIfDue()
    }

    override func intervalWillEndWarning(for activity: DeviceActivityName) {
        super.intervalWillEndWarning(for: activity)
        endIfDue()
    }

    private func endIfDue() {
        guard let session = SharedStore.session else {
            ShieldManager.clear()
            return
        }
        // Small tolerance: the system may wake us a few seconds early.
        if Date.now >= session.end.addingTimeInterval(-10) {
            ShieldManager.clear()
        }
    }
}
