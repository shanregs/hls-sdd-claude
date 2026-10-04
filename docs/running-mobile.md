# Running and testing the Android app (Windows 11)

How to run the HLS Android app (`mobile/`) on a Windows 11 PC with the Android emulator, and how to
test it. For the backend and web app see [running-locally.md](running-locally.md). The app is
specified in `specs/018-android-app-foundation/`.

The app uses native modules (secure storage, location, a rooted-device check), so **Expo Go does not
work**. You need a development build (`npx expo run:android`) on an emulator or a phone.

## 1. One-time setup

1. **Turn on virtualization.** Task Manager → Performance → CPU should say "Virtualization: Enabled".
   If not, enable it in the BIOS. Then in *Turn Windows features on or off* enable **Windows
   Hypervisor Platform** (and Hyper-V on Windows Pro) and reboot.
2. **Install Android Studio** (developer.android.com/studio). In the setup wizard accept the Android
   SDK, platform-tools and Emulator.
3. **Set environment variables** (PowerShell), then open a new terminal:
   ```powershell
   setx ANDROID_HOME "$env:LOCALAPPDATA\Android\Sdk"
   setx JAVA_HOME "C:\Program Files\Java\jdk-17"
   ```
   **Use JDK 17, not Android Studio's bundled Java (`jbr`).** Recent Android Studio versions bundle
   Java 25. One of the Android build tools prints a harmless Java 25 warning, and the Android Gradle
   plugin treats that as an error, so the build fails at `configureCMakeDebug` with "WARNING: A
   restricted method in java.lang.System has been called". Install JDK 17 (for example Temurin 17)
   if you do not have it, and check that `java -version` says 17 in the terminal you build from.
   Add `%ANDROID_HOME%\platform-tools` and `%ANDROID_HOME%\emulator` to your `PATH`.
   Check with `adb version` and `emulator -version`.
4. **Create an emulator.** Android Studio → Device Manager → Create Device → **Pixel 7**, system image
   **Android 14 (API 34) with Google APIs**. The app needs Android 10 (API 29) or later.
5. Node 20 or later, and Docker Desktop (for the database), as in running-locally.md.

## 2. Start the backend and database

From the repository root:

```powershell
.\scripts\start-db.ps1
.\scripts\run-backend.ps1
```

The backend serves `http://localhost:8080` with the demo data. If Windows Firewall asks, allow Java
on private networks. Keep it running while you use the app. Start the web app too
(`.\scripts\run-frontend.ps1`) if you want to look at the audit screens.

## 3. Run the app on the emulator

**Order matters: emulator online first, then the build.**

1. **Start the emulator and wait until it is online.** Use Device Manager's play button, or from a
   terminal (software graphics, no GPU use):
   ```powershell
   $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
   $env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
   emulator -avd Pixel_7 -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -feature -Vulkan -memory 3072 -cores 4
   ```
   (Add `-wipe-data` only the first time, or when the phone is stuck.) In a second window run
   `adb devices` until it shows `emulator-5554   device`. `offline` means it is still booting; the
   first boot can take 3 to 5 minutes.
2. **Build and install the app.** In a new terminal, from the **`mobile`** folder (not
   `mobile\android`):
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
   $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
   $env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
   java -version          # must say 17.0.x
   cd D:\me\work\HiCitizen\HLS-App\hls-018-android\mobile
   copy .env.example .env
   npm install
   npx expo run:android
   ```
   - The first build takes 10 to 20 minutes and needs the internet: it downloads Gradle, the Android
     platform, the NDK and CMake. Later runs take about a minute. It installs the app on the emulator
     and starts Metro. Edits under `mobile/src` reload automatically.
   - You will see `userInterfaceStyle: Install expo-system-ui`. That is only a warning; ignore it.
   - **Do not pin SDK versions in `app.config.ts`** (compileSdk, targetSdk, buildTools). The libraries
     need compile SDK 35 or 36, and Expo already chooses the right values. Pinning 34 makes the build
     fail at `checkDebugAarMetadata`. The only build setting in `app.config.ts` is `minSdkVersion: 29`.
3. `.env` already has `EXPO_PUBLIC_API_BASE_URL=http://10.0.2.2:8080`. **`10.0.2.2` is how the
   emulator reaches `localhost` on your PC.** Do not use `localhost` inside the emulator.

**Next days (the app is already installed):** start the emulator, the database and backend, then run
`npx expo start` in `mobile` and press `a` to open the app on the emulator. You only need
`npx expo run:android` again after changing native packages or `app.config.ts`.

If the app shows "No connection" over plain `http://`, allow cleartext traffic for the dev build by
adding `usesCleartextTraffic: true` to the `expo-build-properties` android settings in
`mobile/app.config.ts`, then rebuild.

## 4. Test accounts

From the demo data (see running-locally.md for the full list):

| Account | Sign in with | Roles | Use it to check |
| ------- | ------------ | ----- | --------------- |
| Tara Teacher | phone `9800000004` / `Password123!`, or a one-time code | Teacher | Teacher menu, password or code login |
| Manoj Manager | `manoj.manager` / `Password123!` | Manager | Manager menu, password login |
| Divya Director | `9800000002` / `Password123!` | Director | Director menu |
| Asha Admin | `asha.admin` / `Password123!` | Admin | Refused on the app (web-only role) |
| Sunil System | `sunil.system` / `Password123!` | System | Refused on the app; sees the audit screens on the web |

The one-time code is printed in the backend console: `[DEV SMS STUB] To 9800000004: your HLS
sign-in/reset code is NNNNNN`.

## 5. Manual test checklist

Details and expected results are in `specs/018-android-app-foundation/quickstart.md`. In short:

1. **Sign in**: Teacher with a code, Manager with a password. Wrong password shows the generic
   message; five wrong passwords show the lockout time. No role chooser appears.
2. **Admin or System account**: shows "Your account uses the HLS web application." and no session.
3. **Stay signed in**: close and reopen the app; you go straight to Home.
4. **Menus**: Teacher, Manager and Director each see their own menu. On the web, as Admin, change a
   role's permissions, then restart the app (or leave it in the background for 5 minutes): the menu
   follows with no new build.
5. **Location** (emulator `...` button → Location → set a point → *Send*):
   - First sign-in shows the "Location and privacy" explanation, then the system prompt. Allow it.
   - On the web as Admin, open **AUDIT → Login History** and **AUDIT → API Access**: the rows show
     source "Android app", the app version and the coordinates.
   - Choose "Not now" or deny the permission, or switch location off in the emulator's quick
     settings: sign-in must still work, and the rows show "Location unavailable — <reason>".
   - Leave the app idle, and then in the background, for 10 minutes: no new API Access rows.
6. **Sessions**: Profile → *Signed-in devices*. Sign in on the web too: both sessions are listed, the
   current one is marked. End the web one from the app; the browser is signed out on its next request.
7. **Logout**: Log out, press Back, reopen the app: Sign In, and no previous name or data.
8. **Offline**: turn on airplane mode in the emulator and open the app while signed in: "No
   connection" with Retry, and no menu. Turn it off and press Retry.
9. **Theme**: Profile → Appearance: Device, Light, Dark. It is remembered after a restart.
10. **Accessibility**: turn on TalkBack (Settings → Accessibility). Every control on Sign In, Home,
    Profile and Signed-in devices should be announced with a name; touch targets are at least 48 dp.
11. **Version gate**: set `HLS_MOBILE_MIN_APP_VERSION=9.0.0` in the backend's environment and restart
    it: the app shows "Please update the HLS app". Unset it afterwards.

## 6. Automated tests

No device needed:

```powershell
cd mobile
npm test              # Jest + React Native Testing Library (about 200 tests)
npm run lint
npm run typecheck
npm run check:manifest   # no background location, backups off, minSdk 29
```

Or run all four with `.\scripts\check-mobile.ps1` from the repository root.

Backend and web tests for this feature: `mvn test` in `backend/` (needs Docker) and `npx vitest run`
in `frontend/`.

**End-to-end smoke test** (needs the emulator, the dev build installed, and the backend running):

```powershell
winget install --id=Maestro.Maestro   # or follow maestro.mobile.dev
maestro test mobile\e2e\smoke.yaml -e PHONE=9800000003 -e PASSWORD=Password123!
```
That signs in as the Manager, opens the menu and Profile, and logs out.

## 7. Using a real phone instead

1. On the phone: Settings → About → tap *Build number* 7 times; then Developer options → turn on
   **USB debugging**.
2. Connect it by USB and accept the prompt. `adb devices` should list it.
3. Put your PC's LAN address in `mobile/.env` (find it with `ipconfig`), for example
   `EXPO_PUBLIC_API_BASE_URL=http://192.168.1.20:8080`. The phone and PC must be on the same Wi-Fi,
   and Windows Firewall must allow port 8080 on the private network.
4. Run `npx expo run:android`.

## 8. Troubleshooting

| Problem | Fix |
| ------- | --- |
| `adb` or `emulator` not found | Check the PATH entries from step 1 and open a new terminal. |
| Emulator is very slow or will not start | Virtualization is off, or Windows Hypervisor Platform is not enabled. Redo step 1. |
| Build fails with "A restricted method in java.lang.System has been called" | Java 25 is being used (Android Studio's `jbr` is Java 25). Set `JAVA_HOME` to JDK 17, check that `java -version` says 17, then rebuild. |
| Build fails with another Java or Gradle error | Use JDK 17. Run `cd mobile\android && .\gradlew clean`, then retry. |
| Gradle download times out | Raise `networkTimeout` (for example to 300000) in `mobile\android\gradle\wrapper\gradle-wrapper.properties`, or check VPN or proxy settings. |
| `No Android connected device found` | The emulator is not online yet. Run `adb devices` until it shows `device`, then run the build again. |
| `adb devices` shows `offline`, or the emulator says it cannot connect | Wait a few minutes (first boot). Run `adb kill-server` then `adb start-server`. Make sure `where.exe adb` lists only the SDK's `adb.exe`. Restart the emulator with the command in step 3 (software graphics). Note that `-gpu angle_indirect` is not a valid option. |
| `ConfigError: ... package.json does not exist` | You ran the command inside `mobile\android`. Run `npx expo run:android` from the `mobile` folder. |
| `app:checkDebugAarMetadata` fails: "requires ... compile against version 35 or later" | `compileSdkVersion`, `targetSdkVersion` or `buildToolsVersion` is pinned to 34 in `app.config.ts` or `mobile\android\gradle.properties`. Remove the pin (keep only `minSdkVersion: 29`) and rebuild. If `android\gradle.properties` still has the old values, delete those lines or run `npx expo prebuild --platform android --clean` (this regenerates `android`, so re-apply any timeout edit). |
| Build needs a missing Android platform, NDK or CMake | Gradle downloads them automatically on the first build (licences are accepted in `Sdk\licenses`). It needs the internet. If it fails, run the build again, or install them from Android Studio → SDK Manager. |
| App cannot reach the backend | Backend running? `.env` uses `10.0.2.2`, not `localhost`? Rebuild after changing `.env`. See the cleartext note in step 3. |
| Code login shows nothing | Read the code from the backend console (the dev SMS stub). Codes expire; there is a 30 second resend wait. |
| Location is always "unavailable" | Emulator: send a location from `...` → Location. Check the app's permission under Settings → Apps → HLS → Permissions. |
| Metro port 8081 busy | Close the other Metro window, or `npx expo start --port 8082`. |
| Stale build after adding a native package | `npx expo run:android` again (a plain reload does not rebuild native code). |

## 9. Release builds (later)

Distribution is not decided yet. Builds use EAS profiles in `mobile/eas.json` (`development`,
`preview`, `production`): `eas build --profile preview --platform android`. Before a production
release: set the final Android application id in `mobile/app.config.ts`, get HLS's approved
privacy-notice wording and a retention period for location data, and run the manual checklist above
on a physical phone. See `specs/018-android-app-foundation/quickstart-results.md`.
