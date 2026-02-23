# CI build and required secrets

This project includes a GitHub Actions workflow to build a signed release APK.

Required repository Secrets (set in GitHub Settings → Secrets & variables → Actions):

- `KEYSTORE_BASE64` — Base64-encoded keystore binary (`.jks`).
- `KEYSTORE_PASSWORD` — Keystore store password.
- `KEY_ALIAS` — Key alias inside the keystore.
- `KEY_PASSWORD` — Key password for the alias.
- `SUPABASE_URL` — Supabase project URL.
- `SUPABASE_ANON_KEY` — Supabase anon/public key (safe client key).
- `WA_REMINDER_API_URL` — WhatsApp reminder API URL.
- `WA_REMINDER_API_KEY` — WhatsApp reminder API key.
- `OWNER_EMAIL` — Owner email used by the app.

How it works

- The workflow decodes `KEYSTORE_BASE64` to `keystore.jks`, writes `~/.gradle/gradle.properties` with required values, then runs `./gradlew assembleRelease` and uploads the generated APK as an artifact.

How to run

1. Push to `main` or trigger the workflow manually via GitHub UI (Actions → Build Release APK → Run workflow).
2. Download artifact `app-release-apk` from the workflow run.

Notes

- Ensure you DO NOT commit secrets into the repo.
- If you prefer not to store a keystore in secrets, you can remove the keystore step and configure Play App Signing instead.
