import SwiftUI

@main
struct FocusGoalApp: App {
    @StateObject private var focus = FocusController.shared
    @StateObject private var chat = MiraChat.shared
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(focus)
                .environmentObject(chat)
                .preferredColorScheme(.dark)
                .tint(Palette.accent)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { focus.refresh() }
                }
        }
    }
}

enum Tab: Hashable { case home, apps, mira, settings }

struct RootView: View {
    @EnvironmentObject private var focus: FocusController
    @State private var tab: Tab = .home
    @State private var showSetup = false

    var body: some View {
        ZStack {
            GlowBackground()
            if !focus.settings.onboardingDone {
                SetupView(firstRun: true) {}
            } else {
                TabView(selection: $tab) {
                    HomeView(openApps: { tab = .apps }, openChat: { tab = .mira }, openSetup: { showSetup = true })
                        .tabItem { Label("Focus", systemImage: "timer") }.tag(Tab.home)
                    AppsView()
                        .tabItem { Label("Apps", systemImage: "lock.app.dashed") }.tag(Tab.apps)
                    ChatView()
                        .tabItem { Label("Mira", systemImage: "face.smiling") }.tag(Tab.mira)
                    SettingsView(openSetup: { showSetup = true })
                        .tabItem { Label("Settings", systemImage: "gearshape") }.tag(Tab.settings)
                }
                .toolbarBackground(.ultraThinMaterial, for: .tabBar)
                .toolbarBackground(.visible, for: .tabBar)
            }
        }
        .sheet(isPresented: $showSetup) {
            ZStack {
                GlowBackground()
                SetupView(firstRun: false) { showSetup = false }
            }
        }
        .onOpenURL { _ in tab = .home }
    }
}
