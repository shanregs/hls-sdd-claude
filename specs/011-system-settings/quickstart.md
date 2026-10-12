# Quickstart: System Settings (spec 011)

Needs Docker (Testcontainers) and the dev seed users (a System user exists in the dev seed).

1. Backend tests: `mvn test` in `backend/`. Frontend: `npm test` and `npm run build` in `frontend/`.
2. Run the app, sign in as the System user.
3. Dashboard: health shows UP and migration 40; user counts match the Users screen; recent events list the sign-in.
4. System Settings: raise the minimum app version to 9.0.0 and save. `GET /api/v1/mobile/app-config` shows 9.0.0 at
   once. Set it back to 0.0.0.
5. Security Settings: set failed sign-ins before lockout to 3 and save. Three wrong passwords for a test user lock
   the account; set it back to 5.
6. Enter 2 for the password minimum length: the field shows the allowed range 8 to 64 and nothing is saved.
7. Audit > Change History: one entry per changed value, with your name, the key, old and new value.
8. Sign in as a Teacher: the two settings routes show "not authorized" and the API answers 403.
