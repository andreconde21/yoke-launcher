# Yoke

A minimal, text-only Android home screen launcher. No ads, no tracking. The only network calls are the optional weather line and Omarchy wallpaper downloads.

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

## Themes and font

Yoke ships every [Omarchy](https://github.com/basecamp/omarchy) theme (MIT,
Copyright (c) David Heinemeier Hansson), regenerated with
`tools/generate_omarchy_themes.py <omarchy-checkout>` at the same pinned commit
Conductore uses. "Follow my Omarchy PC" reads the theme Conductore last synced from the
followed PC (`/pc_theme` on its launcher-details provider , permission `com.outsmartis.permission.READ_LAUNCHER_DETAILS`) and updates live.

The optional JetBrains Mono font is bundled under the SIL Open Font License
1.1 (`app/src/main/res/raw/jetbrains_mono_ofl.txt`).

## Weather

Optional one-line weather under the date (off by default) from [Open-Meteo](https://open-meteo.com/), no account or key. Besides Omarchy wallpaper downloads it is the only network access in Yoke; a manually chosen city sends only its coordinates (2 decimals), and "Current location" asks for coarse location only when you pick it.

## License

[GNU GPLv3](LICENSE).

Yoke is a fork of Olauncher by Tanuj (github.com/tanujnotes/Olauncher), GPLv3.

## Smart grayscale

The whole phone is grayscale except while an exception app is in front. It uses Android's colour-correction
grayscale, so it needs a one-time permission (WRITE_SECURE_SETTINGS). Android lets only developer tools grant it;
the grant survives Yoke updates and is used only to switch colour correction on and off. Two ways:

**On the phone, with Shizuku:** install Shizuku (Google Play or shizuku.rikka.app), start it with Wireless
debugging (no computer needed), then open Yoke, Settings, Smart grayscale and tap Grant with Shizuku. Allow the
Shizuku prompt. Yoke runs only `pm grant` for itself.

**From a computer, with adb:**

```
adb shell pm grant com.outsmartis.yoke android.permission.WRITE_SECURE_SETTINGS
```

(debug build: `com.outsmartis.yoke.debug`).

Then turn on Yoke's accessibility service. Android 13+, sideloaded: App info, three dots, Allow restricted
settings first, then enable the accessibility service. Switch on Settings, Smart grayscale. The
service reads only the name of the app in front. Yoke itself is an exception by default; pick the rest under Exceptions.
Also available as gesture actions, palette commands and a Quick Settings tile.
