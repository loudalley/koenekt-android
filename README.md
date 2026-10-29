# Koenekt for Android

One Android app for the existing People, licensing, customer and Reporter update services.
Version **1.5.0 (9)**; application ID **za.co.koenekt.app**; Android 8.0 or later;
compile/target API 36. Java 17, Android Gradle Plugin 8.13.2, Gradle 8.13.

Standalone public repository: [loudalley/koenekt-android](https://github.com/loudalley/koenekt-android).
For Cursor, Claude and Codex, start with `AGENTS.md` and `README-HANDOFF.md`.

## What is implemented

The everyday screens are native: sign-in, a Home with quick actions and tools, a
bottom bar (Home, People, Who's in, Account), a searchable People list with person
summaries, a three-step **Add employee** (name, workplace, suggested clock PIN),
**Edit details** (name, department, job title, start date, shift, ID or passport
number and birth date; the clock PIN is shown but permanent), **Mark as left** and a
**Who's in** live board (by department, i.e. shop, with status filters), plus a native **Reporter updates** screen for Koenekt
management. They call the same People endpoints as the website
(`/api/companies`, `/api/people/directory`, `/api/people/next-pin`,
`/api/frappe/departments`, `/api/employees`, `/api/shifts/assign`,
`/api/people/extras`, `/api/employees/deactivate`, `/api/dashboard/live`), so server
permissions apply unchanged: Edit details needs Edit people, Mark as left needs
Deactivate/transfer. Reporter updates uses the licence site's own Updates routes
(`/licences/api/updates/devices`, `preview`, `force`, `cancel` and
`devices/<id>/pause`), so the licence site still holds the update-service token,
checks the management session, logs every action in Licence activity and refuses
a multi-PC or fleet request without the confirmation the preview stands for. Release
uploads, promotion and tidy-up stay on the web page (menu: "Releases and full page").
The design kit is
`Ui.java`; there are no third-party UI libraries.

Everything else (People records, reports, payroll, clocks, settings, users and the
licence tools) runs in a restricted Android WebView connected to the existing
HTTPS services, with the website's own header hidden inside the app. That preserves the complete current website workflows and makes
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
| Reporter updates | Native: each PC's shop, Windows name, IP and install date, status and why it is waiting; tick PCs and preview/update or cancel just those, one or every PC, with an optional release; pause and resume. Web page: releases and everything else on the Updates tab |

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
preserves attendance history and clock identity. PINs are not renumbered. The
server records the leave date as the day it is marked; it has no field for an
earlier date. Editing a leaver never sends them back to the clocks. A native edit
sends the person's clock privilege and card back unchanged and leaves pay, tax and
staff category alone (the website's full form resets privilege and card to the
form's blank defaults). The shift and extras calls are made only when the shift
changed or a birth date was cleared.
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

F-Droid: each release is also published to the self-hosted repository
`https://loudalley.github.io/koenekt-fdroid/repo`, with release notes from
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. See `fdroid/README.md`.

Build output:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab`

Checks:

```powershell
javac -d app/build/policy-checks app/src/main/java/za/co/koenekt/app/AppPolicy.java tools/PolicyChecks.java
java -cp app/build/policy-checks PolicyChecks
node tools/export-checks.cjs
python tools/test_mobile_login.py -q
python tools/test_mobile_updates.py -q
python tools/test_server_amendment.py -q
```

**UI testing without production data:** a debug build can point at a People
console running locally with synthetic data, seen from the emulator as
`10.0.2.2`. Release builds ignore this, and plain HTTP stays blocked there.

```powershell
.\gradlew.bat assembleDebug -PdevOrigin=http://10.0.2.2:8765
```

Without `-PdevOrigin`, debug builds behave like release builds apart from allowing
screenshots. Debug builds include a network config that permits HTTP only to
`10.0.2.2`; it is not in the release manifest.

The Java, JavaScript and amendment tests are self-contained. The optional mobile
contract tests require a separate People backend checkout and its FastAPI/test
dependencies. They use `../koenekt-people` by default; override with
`KOENEKT_PEOPLE_REPO`. With no backend checkout they skip. Do not copy private
backend source/history into this public app repository. All test data is synthetic
and tests never contact production.

## Release state, 29 October 2026 — 1.5.0

1.5.0 splits Who's in by department (the shop, for now): a department chooser
(remembered per company), one row per department with how many are at work and have
clocked today, the tiles counting only the chosen department, new Clocked out and Not
clocked today filters, and tappable At work / Not in tiles. No server change: the app
loads `/api/dashboard/live?presence=all` once and filters on the phone. Built, signed
and linted on the PC and tried on the emulator against a synthetic local console with
synthetic punches. Not yet tried on a phone.

### 1.4.0 — 29 October 2026

1.4.0 adds a native Reporter updates screen (Home, for a management sign-in) for the
licence site's Updates changes of 26 October: PC names, IP and install date, ticked
PCs and the waiting reasons. Built and signed on the PC, released, and confirmed
working on the user's phone.

### 1.3.0 — 20 October 2026

1.3.0 adds shift, ID or passport number and birth date to Edit details, and shows
birth date and today's shift on the person summary. Tried on the Android 15 emulator
against the synthetic local console: setting all three, clearing all three, an
invalid ID (the server's message is shown) and a saved form reopening with the new
values. The contract test now covers these calls for an edit-only user. Not yet
tried on a physical phone.

### 1.2.0

1.2.0 adds native **Edit details** and **Mark as left** to the person summary,
replacing the button that opened the full People records form. Tried on the Android
15 emulator against a local People console with synthetic data: edit with "Save and
update clocks" (one clock update queued with the new name), "Save without updating
clocks" (nothing queued), department and date pickers, the discard prompt, Mark as
left (status Left, today's date, clock removal queued), and editing a leaver (Save
only, nothing queued). A new contract test checks the app's exact payloads against
the People backend. Not tested on a physical phone or against production.

### 1.1.0

1.1.0 redesigns the app: native Home, People, Add employee and Who's in screens,
an Account tab, a bottom bar, your brand logo and an adaptive launcher icon, and
web screens without the duplicate header. It was tried on an Android 15 emulator
against a local People console with synthetic data: sign-in, Home, People list
and search, person summary, the full Add employee flow (created and queued to a
clock), Who's in, Account and Reports. Release build signed with the same key;
lint clean; 48 policy checks and the other suites passed. Not tested on a
physical phone or against production.

### 1.0.3

1.0.3 adds Privacy policy and Delete account buttons to About & privacy. They open
the published pages (`docs/`, GitHub Pages) in the phone's browser. Built, signed
with the same upload key, lint clean; 43 policy checks and the other portable
suites passed. Not device-tested.

### 1.0.2

1.0.2 keeps a phone with no screen lock signed in while it is in a file picker,
Save dialog or print screen opened by the app. After unlocking, the app returns to the
page that was open when its session is still valid instead of always going Home.
The server patcher now refuses servers whose login helpers are too old for it.

Signed APK and AAB built with the existing upload key (same certificate as
1.0.1); the Android lint report has no issues. 39 Java policy checks, the
export-script checks, six mobile/session contract tests and five
surgical-amendment tests passed. bundletool and the 37 focused People backend
tests were last run for 1.0.1 and were not rerun for 1.0.2.
The previous 1.0.0 release also passed 40 licence/update GUI tests; those services
are unchanged. No production login,
customer, licence, update instruction or clock mutation was used for verification.
Public HTTPS checks returned 200 for People branding, the admin page and updates.js.

**Not yet device-tested or published to Play.** Automatic approval review blocked starting
the Android emulator (reason returned: "blocked by policy"). A real phone check
is still required for fingerprint/PIN success/cancel, process restart, background
locking, lock-setting changes, imports/exports after unlock, expiry and logout.
The People server amendment was **applied to the production People console on
20 October 2026**. See `SERVER-AMENDMENT.md` for the commands and rollback.

Before Play submission: confirm the application ID/name, complete the developer
account and store listing, publish an actual privacy-policy URL with the correct
business contact/retention details, provide review access, complete Data safety
and other content declarations, and test the signed bundle through an internal
track. Screenshots must come from the actual app with synthetic data.
Account creation can be reached through the existing website login flow; provide
the required in-app and external account-deletion request paths before publishing.
The About screen currently explains contacting the workspace administrator;
this is not a completed Play account-deletion flow. See `PLAY-STORE-DRAFT.md`.
