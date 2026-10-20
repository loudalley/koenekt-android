# Koenekt for Android

One Android app for the existing People, licensing, customer and Reporter update services.
Version **1.0.1 (2)**; application ID **za.co.koenekt.app**; Android 8.0 or later;
compile/target API 36. Java 17, Android Gradle Plugin 8.13.2, Gradle 8.13.

Standalone public repository: [loudalley/koenekt-android](https://github.com/loudalley/koenekt-android).
For Cursor, Claude and Codex, start with `AGENTS.md` and `README-HANDOFF.md`.

## What is implemented

The app supplies a native sign-in, permissions-based home, workspace switching,
back navigation, connection errors, file picker, report saving and Android printing.
Product screens run in a restricted Android WebView connected to the existing
HTTPS services. That preserves the complete current website workflows and makes
future server-side product improvements available without duplicating their rules.

| Access | Available workflows, when the service grants them |
| --- | --- |
| People | Directory, new hires, editing, leavers, company transfers, bulk changes, categories, shifts, pay fields, import preview/commit, export/template, resend/push staff to clocks and clock-user reconciliation |
| Reports | Attendance and hours, report types, date/company/department filters, PDF/Excel/CSV, shift templates/rules, holidays and attendance settings |
| Live | Current attendance, in/out status and live board |
| Payroll | Settings, calculate/adjust/finalise/discard runs, payslips and summary export |
| Clocks | Connection/delivery status; clock actions only with the existing clock-control permission |
| Administration | Company/shop settings and branding, user permissions/company access, activity for master or an all-company user administrator |
| Koenekt master | All authorised People sections, plus licences/machines, customers/workspaces, licensing activity and Reporter updates |
| Reporter updates | Existing PC/release status, preview/update one or all, cancel, pause and resume |

The server remains responsible for all company, tenant, role, licence and write
permission checks. Selecting a screen in the app grants no additional access.
Only a successful **licence-service authentication** enables operator sections.

## Sign in

1. Install the signed APK on a test phone, or distribute the AAB through a Play test track.
   It upgrades the directly installed 1.0.0 APK using the same signing key.
2. Enter your existing username and password. Master uses username `master`,
   the existing master password and current authenticator code.
3. Confirm your fingerprint/strong biometric or the phone's PIN, pattern or
   password to protect the remembered login. Android 8 uses the device credential.
4. Reopening/returning to the app requires device authentication; the app then
   checks the saved session and current permissions with the services.

There is **no workspace field** on the login screen. The app uses the existing
People site by default. For a separately hosted customer, open a one-time
`koenekt://workspace/example` organisation setup link; the app asks before
switching and forgets the previous saved account. The host is internally
restricted to the reviewed People domain; arbitrary server addresses are rejected.

For master, the app submits the one form to the two independent service login
endpoints. It does not issue tokens, weaken OTP replay checks or use an embedded
operator key. If only one service succeeds, only that service's sections open,
with an explanation. A later login attempt may need the next authenticator code.
Named People admins do not gain licence/operator rights. Server-expired or revoked
sessions require full sign-in again; fingerprint/PIN never bypasses server checks.

**Server amendment required for longer People sign-in:** apply the supplied
`server-amendment/apply_remembered_sessions.py` using `SERVER-AMENDMENT.md`.
It enables an explicit HTTPS `remember_device: true` login for up to 30 days;
ordinary website logins keep their 30-minute lifetime. The app sends this flag
only when the phone has a secure device lock. The licence service already has a
30-day session backstop and needs no change. Without the People amendment, saved
People sessions still expire at the current server limit. After installing the
amendment, sign out once and sign in again to obtain the longer session.

**Client configuration:** in People → Users, grant the customer's own companies
and the intended permissions. For adding/editing/removing staff and reporting,
use View people, Hire/import, Edit people, **Deactivate/transfer**, Reports and
optionally Live board/View clocks. Branch Manager does not include
Deactivate/transfer by default; tick it explicitly where required. New viewer
accounts do not automatically receive hiring or removal rights. Keep unrelated
companies and global permissions unassigned.

Removing a staff member follows the existing leaver/deactivation workflow. It
preserves attendance history and clock identity. PINs are not renumbered.
Biometric enrollment uses the functions available on the connected clocks;
the app does not capture fingerprint templates through the phone sensor.
An internet connection is required. No background Reporter installation runs on
the phone: Reporter update controls act through the existing Windows update service.

## Files and privacy

The Android system picker handles uploads, imports and branding images. Exports
are saved through Android's Save dialog to a chosen destination; no broad storage
permission is requested. Blob-based report downloads are supported, including
immediate URL revocation by the existing Reports page. Exports are limited to
20 MB; use a shorter range for larger reports. Temporary export files are private
and deleted after saving, cancelling, sign-out or the next fresh launch.

The app never saves passwords, authenticator codes or raw biometric information.
Session tokens are stored as AES-GCM ciphertext in the app's no-backup directory.
The non-exportable Android Keystore key requires recent strong biometric/device
credential authentication for encryption/decryption. A local unlock prompt is
required on each fresh launch or return from the background, regardless of that
key's short cryptographic validity window. The app hides its entire content,
pauses WebView timers and clears cookies in the background. File-picker results
wait for unlock and server session revalidation before being delivered to the page.
Fresh app processes clear the WebView cookie jar before showing any content;
only after successful device authentication are saved cookies restored and checked.
The routing address is remembered internally. Local sign-out deletes the saved
ciphertext/key, cookies and WebView storage. It deliberately
does not call the licence service's global logout endpoint, which would revoke
other operator sessions. Cloud backup and device transfer of app data
are excluded. Release builds prevent screenshots/recent-app previews of staff data.
Debug builds allow screenshots of synthetic data for development.

HTTPS is required; untrusted main-frame links are offered in an external app;
certificate errors and mixed content are blocked. There is no native JavaScript
bridge, advertising SDK, analytics SDK or embedded service/signing credential.
The app requests Internet and Android's normal USE_BIOMETRIC permission. Phone
PIN/pattern/password entry is handled by Android; this app does not collect it.
If the phone has no secure screen lock, remembered sign-in is unavailable.

## Build and signing

Use JDK 17 or newer, Android SDK platform 36 and Build Tools 35.0.0. Set
`ANDROID_HOME` or a local `sdk.dir` in `local.properties` (not tracked).

```powershell
# Once, on the authorised release machine. Never replace an existing upload key.
.\tools\create-upload-key.ps1 -JavaHome $env:JAVA_HOME
.\gradlew.bat assembleRelease bundleRelease lintDebug
```

The private Android upload key and passwords live only in `.signing/`, ignored by
Git and restricted to the current Windows user. Back this directory up securely
before publishing. These are new **Android upload credentials**, separate from
the existing People/Reporter licence-signing keys. No licence keys were changed.
The public upload certificate can be exported for Play; private credentials must
never be included with source archives or app downloads. Use Play App Signing
when enrolling the bundle. Play installs use Play's app-signing certificate and
may not replace a directly installed APK signed by the upload key; uninstall the
test APK before switching to the Play build if Android reports a signature mismatch.

Build output:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`

Checks:

```powershell
javac -d app/build/policy-checks app/src/main/java/za/co/koenekt/app/AppPolicy.java tools/PolicyChecks.java
java -cp app/build/policy-checks PolicyChecks
node tools/export-checks.cjs
python tools/test_mobile_login.py -q
python tools/test_server_amendment.py -q
```

The Java, JavaScript and amendment tests are self-contained. The optional mobile
contract tests require a separate People backend checkout and its FastAPI/test
dependencies. They use `../koenekt-people` by default; override with
`KOENEKT_PEOPLE_REPO`. With no backend checkout they skip. Do not copy private
backend source/history into this public app repository. All test data is synthetic
and tests never contact production.

## Release state, 20 October 2026 — 1.0.1

Signed APK and AAB built; the final Android lint report has no issues.
APK signature verified; bundletool 1.18.3 validates the AAB.
39 Java policy checks, the export-script checks, six mobile/session contract
tests, four surgical-amendment tests and 37 focused People backend tests passed.
The previous 1.0.0 release also passed 40 licence/update GUI tests; those services
are unchanged in 1.0.1. No production login,
customer, licence, update instruction or clock mutation was used for verification.
Public HTTPS checks returned 200 for People branding, the admin page and updates.js.

**Not yet device-tested or published to Play.** Automatic approval review blocked starting
the Android emulator (reason returned: "blocked by policy"). A real phone check
is still required for fingerprint/PIN success/cancel, process restart, background
locking, lock-setting changes, imports/exports after unlock, expiry and logout.
The supplied People server amendment is **prepared and tested, not installed**,
as requested. See `SERVER-AMENDMENT.md` for exact commands and rollback.

Before Play submission: confirm the application ID/name, complete the developer
account and store listing, publish an actual privacy-policy URL with the correct
business contact/retention details, provide review access, complete Data safety
and other content declarations, and test the signed bundle through an internal
track. Screenshots must come from the actual app with synthetic data.
Account creation can be reached through the existing website login flow; provide
the required in-app and external account-deletion request paths before publishing.
The About screen currently explains contacting the workspace administrator;
this is not a completed Play account-deletion flow. See `PLAY-STORE-DRAFT.md`.
