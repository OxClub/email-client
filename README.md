# EmailClient (Android, Kotlin + Jetpack Compose)

A native Android email client that connects to real IMAP/SMTP accounts —
Gmail, Outlook, Yahoo, iCloud, or any generic provider.

## What it does

- **Continue with Google** — real OAuth2 sign-in for Gmail accounts via
  AppAuth: taps into the actual Google consent screen, exchanges the
  authorization code for tokens, and stores the refresh token securely.
  Access tokens are silently refreshed as needed (no re-login prompts) and
  IMAP/SMTP authenticate via SASL XOAUTH2 instead of a password.
- **Add account (password)** — still available as a fallback / for non-Google
  providers: pick a provider preset (or enter custom IMAP/SMTP hosts), sign
  in with email + password/app password.
- **Inbox** — syncs the most recent messages over IMAP, shows sender, subject,
  preview, date, read/unread state, star/flag.
- **Read a message** — fetches the full body (plain text, with HTML fallback)
  on open, marks it read on the server.
- **Compose / Reply** — sends real mail via SMTP, threads replies using
  `In-Reply-To`/`References` headers.
- **Multiple accounts** — switch between accounts from the top bar; each
  account's messages are cached locally (Room) for fast reopening.

## Architecture

- **UI**: Jetpack Compose, single-Activity, simple in-memory screen state
  (`MainActivity.kt`) — no navigation library needed for this screen count.
- **data/**: Room entities (`Account`, `EmailMessage`), DAOs, and
  `EmailRepository`, which is the single source of truth the UI talks to.
- **network/MailService.kt**: all actual IMAP/SMTP calls, built on
  `com.sun.mail:android-mail` (JavaMail ported for Android). Supports both
  password auth and OAuth2 (SASL XOAUTH2).
- **oauth/GoogleAuthManager.kt**: the AppAuth-based Google sign-in flow —
  builds the consent-screen intent, exchanges the auth code for tokens, and
  refreshes access tokens transparently via the stored refresh token.
- **Credentials**: passwords and OAuth refresh tokens are both stored in
  `EncryptedSharedPreferences` (`CredentialStore.kt`), never in the Room
  database, never in plaintext.

## Google OAuth Client ID

`GoogleAuthManager.kt` has the Android OAuth Client ID hardcoded
(`CLIENT_ID`). It's tied to this app's package name (`com.example.emailclient`)
and the signing certificate's SHA-1 fingerprint — if you change either of
those, you'll need to register a new OAuth client in Google Cloud Console
(APIs & Services → Credentials) and update `CLIENT_ID` to match.

While the OAuth consent screen is in "Testing" status (the default until you
submit for verification), only Google accounts added as test users in Cloud
Console → Audience can complete sign-in — everyone else sees an
"access blocked" screen. Submitting for verification is free but requires a
review process (and, since this requests the full-access
`https://mail.google.com/` scope, an annual third-party security
assessment) before it's open to the public.

## Setup

1. Install **Android Studio** (Koala or newer recommended).
2. Open this folder as a project (`File > Open`, select `EmailClient/`).
   Let Gradle sync — it will download the dependencies listed in
   `app/build.gradle.kts` automatically.
3. Run on an emulator or a physical device (`minSdk 26`, i.e. Android 8.0+).

No API keys or client IDs are required — this uses plain IMAP/SMTP auth, not
OAuth.

## Building with GitHub Actions instead

A workflow is already included at `.github/workflows/build.yml`. To use it:

1. Push this project to a new GitHub repo (root of the repo should be this
   `EmailClient/` folder's contents — i.e. `build.gradle.kts` at the repo root).
2. The workflow runs automatically on every push to `main`/`master`, on pull
   requests, and can also be triggered manually from the **Actions** tab
   (`workflow_dispatch`).
3. It builds two jobs: a debug APK and an unsigned release APK. When the run
   finishes, open it in the **Actions** tab and download the APK from the
   **Artifacts** section at the bottom of the run summary.
4. Install the debug APK on a device with `adb install app-debug.apk`, or
   just transfer the file to your phone and tap it (you'll need to allow
   "install from unknown sources" once).

Note: this repo doesn't check in the Gradle wrapper's binary jar file (that's
normal — it's a binary and I can't generate one), so the workflow installs
Gradle directly via `gradle/actions/setup-gradle` rather than calling
`./gradlew`. If you later open the project in Android Studio and it asks to
regenerate the wrapper, that's fine and won't affect the CI build.

### Signing a release build (optional)

The `build-release` job in the workflow produces an **unsigned** release APK,
which Android won't let you install as-is. To get an installable signed
release build via CI:

1. Generate a keystore locally: `keytool -genkeypair -v -keystore release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000`
2. Base64-encode it and add it as a repo secret (`Settings > Secrets and
   variables > Actions`), e.g. `RELEASE_KEYSTORE_BASE64`, plus secrets for
   the store password, key alias, and key password.
3. Add a signing step to the workflow that decodes the secret to a file and
   configures `signingConfigs` in `app/build.gradle.kts` to point at it via
   environment variables.

This is a reasonable next step but adds real complexity (keeping the
keystore secret safe, matching signing configs), so it's left out of the
default workflow — the debug APK is enough for installing on your own device
to test.

## Signing in to real accounts

### Gmail
Gmail blocks plain-password IMAP login by default. You have two options:
- **App password** (recommended, works with this app as-is): enable 2-Step
  Verification on the Google account, then create an
  [App Password](https://myaccount.google.com/apppasswords) and use that as
  the password here.
- Alternatively, in an older/less-secure account you can enable
  "IMAP access" and allow less secure apps — Google is phasing this out, so
  app passwords are the reliable path.

### Outlook / Office 365, Yahoo, iCloud
Same idea — if the account has 2FA on, generate an app-specific password from
that provider's account security settings and use it here. Regular
passwords work if 2FA is off and IMAP is enabled on the account.

## Known limitations / good next steps

- Only the `INBOX` folder is synced in this version — `MailService.listFolders()`
  already exists, so wiring up a folder switcher (Sent, Drafts, etc.) is a
  small addition in `InboxScreen` + `EmailViewModel`.
- No push notifications for new mail — sync is manual (pull-to-refresh /
  refresh button) or would need IMAP IDLE + a foreground service, or
  WorkManager periodic sync (the dependency is already included but not
  wired up).
- No attachment support yet — `MailService.extractBodies` only pulls
  text/plain and text/html parts.
- Error states are minimal (messages are logged to a StateFlow but not shown
  as a Snackbar yet — hook `errorMessage` up in `MainActivity.kt`).

## OAuth setup notes

This repo includes `keystore/debug.keystore` — a fixed, non-secret debug
signing key (checked into the repo on purpose, unlike a real release
keystore). The debug build type in `app/build.gradle.kts` is pinned to use
it, so every build — locally or in GitHub Actions — is signed with the same
key. That means its SHA-1 fingerprint never changes, which is required for
registering an Android OAuth Client ID in Google Cloud Console.

**Debug keystore SHA-1**: `5B:0C:BC:BF:0B:25:5B:41:46:B2:99:8E:51:7F:66:CC:9F:05:9A:A3`

Use that value (and package name `com.example.emailclient`) when creating
the Android OAuth Client ID in Google Cloud Console → APIs & Services →
Credentials.

## Permissions

Only `INTERNET` and `ACCESS_NETWORK_STATE`. All connections are forced to
TLS/SSL (`network_security_config.xml` disables cleartext traffic app-wide).
