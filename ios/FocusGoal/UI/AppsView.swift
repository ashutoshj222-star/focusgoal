import FamilyControls
import SwiftUI

/// What to block. iOS only lets apps choose through Apple's own picker, so selections show as icons.
struct AppsView: View {
    @EnvironmentObject private var focus: FocusController
    @State private var showBlockPicker = false
    @State private var showAllowPicker = false
    @State private var blockDraft = FamilyActivitySelection()
    @State private var allowDraft = FamilyActivitySelection()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("What to block").font(.system(size: 26, weight: .semibold))
                Text("Blocked apps show the FocusGoal screen while a session runs.")
                    .font(.subheadline).foregroundStyle(Palette.textDim)

                if focus.activeSession?.mode == .deep {
                    Label("Deep Focus is on: you can add apps, but can't unblock any until it ends.", systemImage: "lock.fill")
                        .font(.subheadline).foregroundStyle(Palette.textDim)
                        .padding(12).glass(radius: 18, tint: Palette.accent.opacity(0.18))
                }

                SelectionCard(
                    title: "Blocked apps & websites",
                    subtitle: "Instagram, YouTube, games… or whole categories like Social.",
                    selection: focus.blockSelection,
                    buttonTitle: focus.blockedCount == 0 ? "Choose apps" : "Edit"
                ) {
                    blockDraft = focus.blockSelection
                    showBlockPicker = true
                }

                SelectionCard(
                    title: "Always allowed",
                    subtitle: "Used by Deep Focus → Lock entire phone. Calls always work.",
                    selection: focus.allowSelection,
                    buttonTitle: "Choose"
                ) {
                    allowDraft = focus.allowSelection
                    showAllowPicker = true
                }

                GlassCard {
                    Label("Blocking only Shorts or Reels inside an app isn't possible on iPhone. Apple doesn't let apps see inside other apps. You can block the whole app, or the website version in Safari.", systemImage: "info.circle")
                        .font(.footnote).foregroundStyle(Palette.textDim)
                }
            }
            .padding(20)
        }
        .familyActivityPicker(isPresented: $showBlockPicker, selection: $blockDraft)
        .familyActivityPicker(isPresented: $showAllowPicker, selection: $allowDraft)
        .onChange(of: blockDraft) { _, new in
            if showBlockPicker || new != focus.blockSelection { focus.updateBlockSelection(new) }
        }
        .onChange(of: allowDraft) { _, new in
            if new != focus.allowSelection { focus.updateAllowSelection(new) }
        }
    }
}

private struct SelectionCard: View {
    let title: String
    let subtitle: String
    let selection: FamilyActivitySelection
    let buttonTitle: String
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.headline)
                    Text(subtitle).font(.subheadline).foregroundStyle(Palette.textDim)
                }
                Spacer()
                Button(buttonTitle, action: action)
                    .font(.system(size: 15, weight: .semibold))
                    .padding(.horizontal, 16).padding(.vertical, 8)
                    .background(Capsule().fill(Palette.accentGradient))
                    .foregroundStyle(.white)
            }
            let counts = [
                selection.applicationTokens.count > 0 ? "\(selection.applicationTokens.count) apps" : nil,
                selection.categoryTokens.count > 0 ? "\(selection.categoryTokens.count) categories" : nil,
                selection.webDomainTokens.count > 0 ? "\(selection.webDomainTokens.count) websites" : nil,
            ].compactMap { $0 }
            if !counts.isEmpty {
                Text(counts.joined(separator: " · ")).font(.subheadline.weight(.semibold)).foregroundStyle(Palette.accentLight)
                ForEach(Array(selection.applicationTokens), id: \.self) { token in
                    Label(token).font(.subheadline)
                }
                ForEach(Array(selection.categoryTokens), id: \.self) { token in
                    Label(token).font(.subheadline)
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glass()
    }
}
