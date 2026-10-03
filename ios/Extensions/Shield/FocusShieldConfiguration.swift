import ManagedSettings
import ManagedSettingsUI
import UIKit

/// The screen iOS shows over a blocked app: FocusGoal's logo, when the session ends, and "Stay focused".
/// (Apple doesn't allow a live ticking timer here, so it shows the end time.)
final class FocusShieldConfiguration: ShieldConfigurationDataSource {
    override func configuration(shielding application: Application) -> ShieldConfiguration {
        make(name: application.localizedDisplayName)
    }

    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration {
        make(name: application.localizedDisplayName)
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        make(name: webDomain.domain)
    }

    override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration {
        make(name: webDomain.domain)
    }

    private func make(name: String?) -> ShieldConfiguration {
        let accent = UIColor(red: 0.545, green: 0.486, blue: 1.0, alpha: 1)
        let session = SharedStore.session
        let what = name ?? "This app"

        var subtitle = "\(what) is blocked while you focus."
        if let session {
            let time = DateFormatter.localizedString(from: session.end, dateStyle: .none, timeStyle: .short)
            let mode = session.mode == .deep ? "Deep Focus" : "Focus"
            subtitle = "\(what) is blocked until \(time).\n\(mode) is on. You've got this 💪"
        }

        let icon = UIImage(systemName: "timer", withConfiguration: UIImage.SymbolConfiguration(pointSize: 48, weight: .semibold))?
            .withTintColor(accent, renderingMode: .alwaysOriginal)

        return ShieldConfiguration(
            backgroundBlurStyle: .systemUltraThinMaterialDark,
            backgroundColor: UIColor(red: 0.055, green: 0.043, blue: 0.122, alpha: 0.92),
            icon: icon,
            title: ShieldConfiguration.Label(text: "FocusGoal", color: .white),
            subtitle: ShieldConfiguration.Label(text: subtitle, color: UIColor.white.withAlphaComponent(0.75)),
            primaryButtonLabel: ShieldConfiguration.Label(text: "Stay focused", color: .white),
            primaryButtonBackgroundColor: accent,
            secondaryButtonLabel: nil
        )
    }
}
