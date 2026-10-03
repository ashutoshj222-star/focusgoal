import SwiftUI
import UserNotifications

/// The "allow, allow, done" setup: each card asks for one permission and flips to a green tick.
struct SetupView: View {
    @EnvironmentObject private var focus: FocusController
    let firstRun: Bool
    let onDone: () -> Void
    @State private var notificationsOn = false
    @State private var name = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                MiraView(mood: focus.authorized && notificationsOn ? .celebrate : .happy).frame(width: 150, height: 150)
                Text(firstRun ? "Hi, I'm Mira!" : "Permissions").font(.system(size: 26, weight: .semibold))
                Text("Allow a couple of things so I can guard your focus.")
                    .font(.subheadline).foregroundStyle(Palette.textDim).multilineTextAlignment(.center)

                if firstRun {
                    TextField("What should I call you?", text: $name)
                        .padding(.horizontal, 18).padding(.vertical, 14)
                        .glassCapsule()
                }

                PermissionCard(
                    title: "Screen Time access",
                    description: "Lets me block the apps you choose. Apple shows a popup, tap Continue and confirm with Face ID or your passcode.",
                    required: true,
                    granted: focus.authorized
                ) {
                    Task { await focus.requestScreenTimeAccess() }
                }
                PermissionCard(
                    title: "Notifications",
                    description: "Tells you when a session is finished.",
                    granted: notificationsOn
                ) {
                    Task {
                        _ = await focus.requestNotifications()
                        await checkNotifications()
                    }
                }

                PillButton(
                    title: focus.authorized ? (firstRun ? "Let's focus ✨" : "Done") : "Continue without blocking",
                    filled: focus.authorized
                ) {
                    if firstRun { focus.settings.userName = name.trimmingCharacters(in: .whitespaces) }
                    focus.settings.onboardingDone = true
                    onDone()
                }
                .padding(.top, 8)
            }
            .padding(20)
        }
        .task { await checkNotifications() }
    }

    private func checkNotifications() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        notificationsOn = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
    }
}

private struct PermissionCard: View {
    let title: String
    let description: String
    var required = false
    let granted: Bool
    let onAllow: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(title).font(.headline)
                    if required {
                        Text("REQUIRED").font(.system(size: 11, weight: .semibold)).foregroundStyle(Palette.accentLight)
                            .padding(.horizontal, 8).padding(.vertical, 2).glassCapsule()
                    }
                }
                Text(description).font(.subheadline).foregroundStyle(Palette.textDim)
            }
            Spacer()
            if granted {
                Image(systemName: "checkmark").font(.headline).foregroundStyle(.white)
                    .frame(width: 40, height: 40).background(Circle().fill(Palette.success))
            } else {
                Button("Allow", action: onAllow)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 18).padding(.vertical, 10)
                    .background(Capsule().fill(Palette.accentGradient))
            }
        }
        .padding(16)
        .glass(tint: granted ? Palette.accent.opacity(0.14) : .white.opacity(0.06))
    }
}
