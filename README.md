# Yoke

A minimal, text-only Android home screen launcher. No ads, no tracking, no network calls.

Yoke keeps what makes a minimal launcher useful (a short list of home apps, type-to-launch app drawer, swipe gestures, hidden apps, renaming, double tap to lock, Private Space support) and drops everything else.

## Planned

- **Long-press app details** via a ContentProvider contract that apps can implement to expose a small status/summary view. Conductore is the first provider.
- **Quick-add to the Obsidian Cockpit Board** straight from the launcher.
- **Omarchy themes** for colours and typography.

## Build

Requirements: JDK 17 and the Android SDK (compileSdk 36, minSdk 24).

```sh
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew assembleDebug testDebugUnitTest
```

The debug APK ends up in `app/build/outputs/apk/debug/` (application id `com.outsmartis.yoke.debug`).

## License

[GNU GPLv3](LICENSE).

Yoke is a fork of Olauncher by Tanuj (github.com/tanujnotes/Olauncher), GPLv3.
