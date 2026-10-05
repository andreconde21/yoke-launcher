# Yoke backlog

Specced, not built. Testing comes first (week of 2026-10-05); pick from here afterwards.

## Small
- **"Today ↗" built-in entry**: next to "Cockpit board ↗" and "Cockpit calendar ↗", it opens the Today sheet. It can be pinned to a home slot.
- **Built-in entries in the icon row**: the icon-row picker also lists the built-in entries (Cockpit board, calendar, Today), drawn with Obsidian's icon.
- **Overdue time**: optionally show a card's time on the "Overdue" agenda line (currently dropped).
- **Portuguese translation** and a **first-run setup wizard**: vault, Shizuku/grayscale, notification access, default launcher.

## Medium
- **Focus modes**: e.g. "Work", "Evening", "Driving". Each one sets grayscale, Do Not Disturb, hidden apps, wallpaper, theme and which notifications show.
  - Triggers: manual, scheduled, a Wi-Fi network, or a Bluetooth device (e.g. the Tesla).
  - Needs Do Not Disturb access.
- **Interrupt**: a pause screen before apps you mark ("Open Instagram? 5…"), with daily limits and a weekly summary under screen time.
  - Detection reuses the accessibility service's foreground signal.
- **Conductore at a glance**: an optional home line such as `2 agents · 1 waiting`. Tap opens the details sheet, where you can reply.
- **Server pulse**: an optional line from the Uptime Kuma status page or Coolify, such as `✓ all up` or `✗ agrella-api down`. Tap opens Kuma.
- **Driving mode**: when the car's Bluetooth connects, home switches to big text with Maps, Music, Phone and Conductore. Maybe also CarMirror.

## Larger
- **Home pages / profiles**: e.g. "Work apps" and "Personal apps", switched by an edge swipe or by focus mode.
- **Inline notification reply**: use RemoteInput for messaging apps, from the home notification block.

## Process
- **On-device testing from the build host**: adb over Tailscale with Wireless debugging. The phone is on the tailnet as `andrs-m53`; this needs one pairing (code plus two ports).
