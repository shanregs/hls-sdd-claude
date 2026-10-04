# Quickstart results: Android App Foundation

2026-10-04. Branch `feature/018-android-app-foundation` (worktree `hls-018-android`).

## Automated checks

| Area | Result |
| ---- | ------ |
| Mobile (`mobile/`): Jest + React Native Testing Library | 197 tests, 16 suites, passing. Typecheck and lint clean. `npm run check:manifest` passes (no background location, backups off, minSdk 29). |
| Web (`frontend/`): Vitest + axe | 275 tests, 42 files, passing (includes the new API Access page and its axe checks in light and dark). |
| Backend (`backend/`): JUnit + Testcontainers | Full suite run recorded at the end of this file. 56 tests were added by this feature. |

## Manual pass (quickstart.md scenarios 1-17): NOT RUN

No Android emulator or phone was available in the session that built this. The scenarios still need
to be run, in particular: TalkBack and 48 dp touch targets (scenario 17), the real system permission
prompt (9, 10), 10 minutes in the background with no location use (11), a rooted test device (14),
and SC-001 / SC-002 timings on a physical mid-range phone. The Maestro flow is in `mobile/e2e/smoke.yaml`.

## Open items

**Blockers for a production release (not for development):**
- Retention period for API Access entries and other location data (about 7 million API Access rows a
  year at 100 users; they are currently kept like other audit rows).
- HLS's approved privacy-notice wording for the permission explanation (`LOCATION_EXPLANATION` in
  `mobile/src/location/LocationConsent.tsx` is a working text).

**Other:**
- Distribution channel (Play Store or private), app name and icon, and the final Android application
  id (`com.hls.mobile` is a placeholder in `mobile/app.config.ts`).
- Whether to add Play Integrity attestation instead of the best-effort rooted check (research §14).
- `.gitignore` line `logs/` hides `backend/src/main/java/com/hls/audit/logs/` (and its test folder), so
  the Audit Logs source is not in git. Fix the pattern (for example `/logs/`) and commit those files.
- Two backend tests failed before this feature and are unrelated: `DevSeedTest` (dev seed counts).
  `ApplicationModuleBoundaryTest` was also failing and its expected-events list was updated here.
- Deviation from the plan: the app uses a drawer built from react-native-paper instead of
  `@react-navigation/drawer`.

## Full backend run (2026-10-04)

`mvn test`: 427 tests, 1 failure: `DevSeedTest.demoDataIsSeededConsistentlyAndSignInStillWorks`
(expected 1 but was 3). It fails identically on a clean checkout of `9d1a744`, before this feature.
Everything else passes, including the ArchUnit and Spring Modulith checks.
