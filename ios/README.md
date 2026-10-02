# FocusGoal for iPhone

The iOS version of FocusGoal, written in SwiftUI. It uses Apple's Screen Time APIs (FamilyControls, ManagedSettings, DeviceActivity) to block apps.

## What's included

- **Focus** and **Deep Focus** with the same timer, Mira character, chat and glass design as Android.
- **Blocking** uses Apple's Screen Time. You choose apps, categories (like *Social*) and websites with Apple's app picker.
- **Block screen**: opening a blocked app shows a FocusGoal screen with the logo, when the session ends and a **Stay focused** button.
- **Deep Focus**:
  - The timer can't be stopped.
  - **No app can be deleted** until it ends, including FocusGoal.
  - Optional **Lock entire phone**: every app is blocked except calls and the apps you mark *Always allowed*.
- **Live Activity**: countdown on the lock screen and in the Dynamic Island.
- **Home-screen widget** with the timer.
- **Mira AI chat** with a Claude, ChatGPT, Gemini, Groq or OpenRouter key. The key is stored in the iOS Keychain.

### iPhone limits (Apple's rules, not bugs)

- The block screen can't show a live ticking countdown, so it shows the end time instead. The Live Activity on the lock screen does tick.
- Blocking only **Shorts/Reels inside** YouTube or Instagram isn't possible. Apps can't see inside other apps on iOS.
- Anyone can still turn off Screen Time access in iPhone Settings (it needs their passcode). Apple doesn't let apps prevent that.

## Run it on your iPhone

You need a **Mac with Xcode 16 or newer**, an iPhone on **iOS 17 or newer**, and a **paid Apple Developer account**. Family Controls doesn't work with a free account.

1. Install XcodeGen and generate the Xcode project:
   ```bash
   brew install xcodegen
   cd ios
   xcodegen generate
   open FocusGoal.xcodeproj
   ```
2. Use your own identifiers. Bundle IDs must be unique to your account:
   - In `project.yml`, set `DEVELOPMENT_TEAM` to your Team ID. You can find it at developer.apple.com → Membership.
   - Replace `com.focusgoal.app` with something like `com.yourname.focusgoal` in all 5 bundle IDs.
   - Replace the App Group `group.com.focusgoal.app` in `project.yml` **and** in `Shared/SharedStore.swift`.
   - Run `xcodegen generate` again after editing.
3. In Xcode, open **Signing & Capabilities** for each target. Check that *Family Controls* and *App Groups* are on and your team is selected.
4. Plug in your iPhone, pick it as the run destination, and press **Run** (▶).
5. On the iPhone, open FocusGoal and tap **Allow** on *Screen Time access*. Confirm with Face ID or your passcode.

### Publishing to the App Store / TestFlight

For distribution you must request the **Family Controls (Distribution)** entitlement from Apple. Apply at developer.apple.com/contact/request/family-controls-distribution for the app **and** each extension. Development builds on your own devices work without it.

## Project layout

```
ios/
├── project.yml              XcodeGen spec (targets, bundle IDs, entitlements)
├── FocusGoal/               The app
│   ├── App/                 Entry point + tabs
│   ├── Focus/               FocusController (sessions, stats, Live Activity), Keychain
│   ├── AI/                  Mira's brain (all AI providers), lines, chat
│   └── UI/                  Glass theme, Mira drawing, screens
├── Shared/                  Session store, shield logic, Live Activity model (shared with extensions)
└── Extensions/
    ├── Monitor/             Unlocks apps when the timer ends, even if the app is closed
    ├── Shield/              The block screen shown over blocked apps
    ├── ShieldAction/        What "Stay focused" does
    └── Widget/              Home-screen widget + Live Activity
```

A GitHub Actions workflow (`.github/workflows/ios.yml`) compiles the iOS app on every change to `ios/`, without signing.
