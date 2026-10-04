# HLS Mobile (Android)

React Native (Expo) client for Teachers, Managers and the Director. Spec:
[`specs/018-android-app-foundation`](../specs/018-android-app-foundation/spec.md).

## Run

```powershell
cd mobile
copy .env.example .env      # set EXPO_PUBLIC_API_BASE_URL (emulator: http://10.0.2.2:8080)
npm install
npx expo run:android        # development build on an emulator or phone (Android 10+)
```

## Test and check

```powershell
npm test                    # Jest + React Native Testing Library
npm run lint
npm run typecheck
npm run check:manifest      # no background location, backups off, minSdk 29
```

## Build

`eas build --profile development|preview|production` (see `eas.json`).

## Layout

`src/api` HTTP client and endpoint modules · `src/auth` sign-in, session, renewal, logout ·
`src/access` access model and the route-to-screen registry · `src/location` per-call location ·
`src/security` secure storage and device checks · `src/navigation` · `src/screens` · `src/theme`.

Menus come only from the server's access model (`GET /api/v1/me/access-model`); the app never
decides a menu by role name.
