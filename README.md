# Salat POCO

An Android home-screen **4×1 native prayer-time widget** for POCO HyperOS, Android 16. No root, permanent Termux daemon or custom launcher required.

## What works

- Five columns: **Subuh, Zuhur, Asar, Magrib, Isya**, time shown directly below each name.
- At **30 minutes before** the next prayer the same widget focuses on the prayer name + time. Layout is updated by an Android alarm **2 seconds after the H-30 boundary**; Android battery management may delay an inexact alarm.
- Tap focused widget: **previous → next → all five → focused again**. Tap normal widget to preview the upcoming prayer.
- Location requested once via the app and refreshed daily. HTTPS daily prayer timetable from [AlAdhan](https://aladhan.com/prayer-times-api), calculation **method=20, Kementerian Agama Republik Indonesia**.
- **Android exact-alarm permission is optional**. If denied, widget uses inexact alarms that the OS may delay. Times are not counted down by continuously running a service.
- Cached prayer times are shown only for the correct **local calendar date**. No invented fallback times. If location or network is unavailable, open the app to reattempt.
- Daily refresh via WorkManager and an additional after-midnight alarm; refresh on reboot or timezone change.

## Install

1. Open **Actions → Android APK**, download the artifact named `poco-prayer-widget-debug-apk` from the successful `main` build.
2. Unzip the artifact and install `app-debug.apk` on your POCO (allow installing APKs from your chosen file manager).
3. Open **Salat POCO**, grant location access and wait for the current day's timings.
4. Optional: tap **Aktifkan alarm tepat waktu** for a more punctual H-30 switch.
5. Long-press your POCO home screen → **Widget** → **Lebih banyak widget** → **Salat POCO** and add the 4×1 widget.

## Build

GitHub Actions uses Java 17, Gradle 8.13, Android Gradle Plugin 8.11.1 and Android SDK 36.

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Accuracy & privacy

Device coordinates are only sent to **api.aladhan.com** over HTTPS when fetching the schedule. Coordinates and times are cached privately in app storage and not committed to Git. No advertising or analytics. A coarse-only location, a stale cached location, the AlAdhan calculation algorithm, and local mosque adjustments can yield differences from published local schedules. **Method 20 represents Kemenag calculation parameters, not a guarantee that each mosque's iqamah or jadwal printout is identical.** For other countries the calculation method will need configuration.

GitHub development workflow: [Issue #1](https://github.com/Asyraf2003/poco-prayer-widget/issues/1) → feature branch → pull request → CI → merge main.
