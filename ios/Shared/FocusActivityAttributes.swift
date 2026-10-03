import ActivityKit
import Foundation

/// Live Activity: the countdown on the lock screen and in the Dynamic Island.
struct FocusActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var end: Date
        var deep: Bool
    }

    var title: String
}
