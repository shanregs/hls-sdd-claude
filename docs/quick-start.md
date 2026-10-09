# HLS quick start: database, backend, frontend, mobile

One-page cheat sheet. Full detail: [running-locally.md](running-locally.md) (backend and web) and
[running-mobile.md](running-mobile.md) (Android, troubleshooting).

Run every command from the repository root unless it says otherwise. Use PowerShell.

## Ports

| Part | URL / port |
| --- | --- |
| PostgreSQL (Docker, project `hls-local`) | `localhost:5433`, db/user/password `hls` |
| Backend (Spring Boot) | http://localhost:8080 |
| Frontend (Vite) | http://localhost:5173 (proxies `/api` to 8080) |
| Mobile Metro bundler | 8081 |
| Emulator to PC | `10.0.2.2:8080` (never `localhost` inside the emulator) |

## Prerequisites

Docker Desktop (running), JDK 25 + Maven for the backend, Node 20+. Mobile also needs Android Studio,
the emulator and **JDK 17** (see [running-mobile.md](running-mobile.md) section 1).

## 1. Database (always first)

```powershell
.\scripts\start-db.ps1
```

Flyway creates the tables when the backend starts. To wipe local data and start clean:

```powershell
docker compose -p hls-local down -v
.\scripts\start-db.ps1
```

## 2. Backend (terminal 1)

```powershell
.\scripts\run-backend.ps1
```

Ready when the log shows `Started ...Application`. It enables the demo data and `COOKIE_SECURE=false`.
Stop with `Ctrl+C`.

## 3. Frontend (terminal 2)

```powershell
.\scripts\run-frontend.ps1
```

Open http://localhost:5173. The first run does `npm install`. Stop with `Ctrl+C`.

## 4. Mobile (Android, terminal 3)

The app uses native modules, so Expo Go does not work. Use a development build.

1. Start the emulator and wait until `adb devices` shows `emulator-5554   device` (first boot 3 to 5 min):
   ```powershell
   $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
   $env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
   emulator -avd Pixel_7 -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -feature -Vulkan -memory 3072 -cores 4
   ```
2. **First time** (or after changing native packages / `app.config.ts`), from `mobile`:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
   $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
   $env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
   cd mobile
   copy .env.example .env      # EXPO_PUBLIC_API_BASE_URL=http://10.0.2.2:8080
   npm install
   npx expo run:android        # 10-20 min the first time
   ```
3. **Every other day** (app already installed): emulator + database + backend running, then
   ```powershell
   cd mobile
   npx expo start              # press "a" to open on the emulator
   ```

Real phone: same Wi-Fi as the PC, set `EXPO_PUBLIC_API_BASE_URL=http://<PC-LAN-IP>:8080` in
`mobile/.env`, allow port 8080 in Windows Firewall, then `npx expo run:android`.

## Demo logins (password `Password123!`)

| Who | Sign in with | Where |
| --- | --- | --- |
| Asha Admin | `asha.admin` or `9800000001` | Web only |
| Divya Director | `9800000002` | Web + mobile |
| Manoj Manager | `manoj.manager` or `9800000003` | Web + mobile |
| Tara Teacher | `9800000004` | Web + mobile |
| Sunil System | `sunil.system` or `9800000005` | Web only (audit screens) |

One-time codes are not sent in dev: read them from the backend console
(`[DEV SMS STUB] To <phone>: your HLS sign-in/reset code is NNNNNN`).

## Tests and checks

```powershell
cd backend;  mvn test
cd frontend; npm test; npm run lint; npm run build
cd mobile;   npm test; npm run lint; npm run typecheck; npm run check:manifest
```

## Quick fixes

| Problem | Fix |
| --- | --- |
| Backend cannot connect to DB | Docker Desktop running? Re-run `.\scripts\start-db.ps1`. Port is 5433, not 5432. |
| Port 8080 or 5173 in use | Find it with `netstat -ano \| findstr :8080`, then `taskkill /PID <pid> /F`. |
| Mobile: "No connection" | Backend running? `.env` uses `10.0.2.2`? Rebuild after changing `.env`. |
| Mobile build fails on "restricted method" | JDK 25 is being used. Set `JAVA_HOME` to JDK 17. |
| Metro port 8081 busy | `npx expo start --port 8082`. |
| Stale native build | Run `npx expo run:android` again. |

More in [running-mobile.md](running-mobile.md) section 8.
