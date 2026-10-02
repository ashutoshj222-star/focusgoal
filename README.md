# FocusGoal

A focus app for Android that blocks distracting apps while you work — with **Mira**, a cartoon focus buddy you can chat with.

## What it does

- **Focus** – pick a duration and the apps you chose (Instagram, YouTube, …) are blocked until the timer ends. You can stop it if you really need to.
- **Deep Focus** – the strict mode. The timer **can't be stopped**, blocked apps stay locked, Settings and the Play Store are blocked, and FocusGoal **can't be uninstalled** until the session ends. Optional **Lock entire phone**: only calls, messages and the clock keep working.
- **Mira** – an animated cartoon character who cheers you on when a session starts, scolds you (nicely) when you open a blocked app, and celebrates when you finish. Chat with her in the Mira tab. Add an Anthropic API key in Settings and she answers anything using Claude. Without a key she uses built-in replies.
- **Home-screen widget** – shows the live countdown, or starts a focus session with one tap.
- **Setup flow** – each permission has an **Allow** button that opens the right Android screen and turns into a ✓ once it's granted.
- Glassmorphism UI with a single violet accent on a deep night background.

## How the blocking works

| Feature | Android mechanism |
| --- | --- |
| Detect & block apps | Accessibility service (`BlockerAccessibilityService`) reads the package name of the app coming to the foreground. It never reads screen content. |
| Block screen | `BlockedActivity`, launched over the blocked app after sending you home |
| Uninstall protection | Device admin (`FocusDeviceAdminReceiver`). Android won't uninstall an active admin, and during Deep Focus the blocker keeps you out of Settings, so it can't be switched off mid-session |
| Timer | Stored end time + `AlarmManager` + an ongoing countdown notification. Survives app kills and reboots |
| Widget | `FocusWidgetProvider` with a `Chronometer` countdown |

## Get the APK

Every push builds an APK in GitHub Actions: open the **Actions** tab → latest **Build APK** run → download **FocusGoal-debug-apk**. Unzip it and install the `.apk` on your phone. You'll need to allow installs from unknown sources.

Or build it yourself with Android Studio (Ladybug or newer). Open the project and press Run, or from a terminal:

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### First launch

1. Type your name, then tap **Allow** on each card:
   - **App blocker (required)** – find *FocusGoal app blocker* in Accessibility and switch it on.
     On Android 13+ a sideloaded APK may show this switch greyed out. Open **App info → ⋮ → Allow restricted settings**, then try again.
   - **Notifications**, **Display over other apps**, **Uninstall protection**, **Run in background**.
2. Open the **Apps** tab and choose what to block. Common social apps are pre-selected.
3. Pick a time and tap **Focus** or **Deep Focus**.

## Project layout

```
app/src/main/java/com/focusgoal/app/
├── ai/          Mira's brain: Claude client, chat history, built-in lines
├── data/        FocusRepository (session, settings, stats), installed apps
├── focus/       FocusManager (start/stop/finish), block rules, permissions, receivers
├── service/     Accessibility service that does the blocking
├── ui/          Compose screens, glass theme, Mira character drawing
└── widget/      Home-screen widget
```

## Notes

- Built with Kotlin + Jetpack Compose. Min Android 8.0 (API 26), targets Android 15.
- iOS isn't supported: Apple only allows app blocking through the Screen Time API, which needs a separate Swift app and a special entitlement.
- An app that uses an accessibility service for blocking and stops itself being uninstalled has to explain clearly why it needs those permissions, or Google Play may reject it. This build is meant for personal use and sideloading.
