# Koenekt Android — shared handoff for Codex, Cursor and Claude

Last updated: **29 October 2026, Claude (Windows PC) — version 1.4.0 (8), native
Reporter updates screen, built and signed on the PC, pushed, published on the self-hosted
F-Droid repo and copied to Google Drive (`My Drive/Koenekt Android/`) at the user's
request; not yet tried on a phone.**
1.3.0 (7) is the latest released build (F-Droid, Drive). People server amendment INSTALLED
on the production People console. A separate accounts/leavers change (see the 20 October
entry from the PC) is NOT on `main` yet and needs a server update first.

## Repository and current state

- Authoritative checkout: `C:\Projects\koenekt-android`.
- Public remote: `https://github.com/loudalley/koenekt-android`, branch `main`.
- This repository contains Android source, synthetic tests, public server
  amendment tooling/instructions and shared agent notes. It has fresh Git
  history; the private People backend and its history were not imported.
- Previous Android code was in `koenekt-people/android-app` on a private feature
  branch. That location is retired. The People backend amendment remains in the
  separate backend repository (feature branch); the live console was patched
  directly with the surgical patcher, not from that branch.
- Local Android upload credentials moved with the app into ignored/restricted
  `.signing/`; their certificate matches the existing 1.0.0/1.0.1 APKs. Never
  regenerate them for an ordinary update, commit them or include them in releases.
- Self-hosted F-Droid repository LIVE: `https://loudalley.github.io/koenekt-fdroid/repo`
  (fingerprint `C6F6570B…D231577F`), serving 1.0.3 (4) with release notes, logo icon
  and banner. Publish with `tools/fdroid_repo.py`; details in `fdroid/README.md`.
  Repo key in ignored `.signing/fdroid/` (back it up with `.signing/`).
- Signed release files are kept locally under `dist/1.3.0/` (ignored; APK, AAB
  and SHA256SUMS); `dist/1.2.0/`, `dist/1.1.0/` and `dist/1.0.3/` are the previous builds. `dist/1.0.1/` is older; its server-amendment ZIP holds the
  patcher without the server-helper check, so do not use that copy.
  Build products are under `app/build/outputs/`. Local SDK configuration is
  ignored. A clean clone must configure its own SDK and signing credentials.

## Implemented behaviour

One native sign-in and permissions-based home opens the current People,
reports/live/payroll/clocks/users/settings screens. A successful independent
licence-service login enables customers, licences and Reporter update controls.
Clients receive only their server-granted company/role permissions.

1.0.2 keeps a phone with no screen lock signed in while it is in a file
picker, Save dialog or print screen opened by the app (it previously signed out,
breaking imports/exports/printing there). After unlock it returns to the open page
when that service's session is still valid, reloading it if it was mid-load,
instead of always going Home. Version strings now come from BuildConfig.

1.0.1 removes the workspace field. Default routing is the existing People site;
separate customers use confirmed `koenekt://workspace/<slug>` setup links once.
Session cookies are encrypted using Android Keystore in no-backup storage after
device authentication. Strong biometric/device credential prompts protect fresh
launch and background return; Android 8 uses device PIN/pattern/password.
Server sessions and current permissions are checked after restoring cookies.
Cancellation keeps content hidden; sign-out deletes the vault/key/cookies.
No passwords, authenticator codes/secrets, phone PINs or raw biometrics are saved.

Android file pickers, deferred results after unlock, blob/HTTP report saving and
printing are implemented. Website sign-out is intercepted locally so it does not
trigger the licence service's global sign-out. Release builds protect screenshots
and recent-app previews. HTTPS/origin rules, mixed-content blocking and no native
JavaScript bridge are retained. Target API 36, minimum 26, app ID
`za.co.koenekt.app`; AGP 8.13.2, Gradle 8.13, Java source 17.

## Server work — installed 20 October 2026

People's default session is 30 minutes. The supplied surgical amendment enables
strict boolean HTTPS `remember_device: true` after the normal authentication
checks, issuing both token and cookie for 30 days. Defaults, HTTP/legacy-token
logins, master MFA/replay, account disabling and scope checks are preserved.
The licence service already has a 30-day backstop and needs no change.

`SERVER-AMENDMENT.md` is the public, generic installation guide. Replace its
server/user/tenant placeholders with the deployment's real configuration. The
patcher defaults to dry-run, refuses changed/partial login code, preserves other
source, writes a backup and never restarts services itself. Since 1.0.2 it also
refuses (no change) unless the target imports `console_auth as cauth`, defines
`https_session`, and the sibling `console_auth.issue_session` accepts `seconds=`;
previously an older server would accept the patch and then fail every login.

Installed at the user's request on 20 October 2026, 11:12 SAST, on the production
People server's single People console. Claude did the read-only checks, copied the
patcher to the admin's home folder and ran the dry run, which passed (including the
helper check). The user ran the sudo apply and the console-only restart. Claude then
verified: new console process, receiver process ID unchanged, all three services
active, repeat run "Already amended", no console warnings, `/api/brand` 200,
unauthenticated `/api/me` 401. Backup for rollback:
`/opt/koenekt/app/console/adms_console.py.before-mobile-20261020T091240287865Z`.
The tenant name and server address are in the admin's SSH config, not in this
public repo. Not yet confirmed: an actual 30-day login (needs the phone). Sign out/in
once in the app to get a new longer token. Rollback does not
revoke previously issued signed tokens.

## Verification and remaining work

Previous app validation: release build/lint passed with no lint issues, APK
signature and bundletool validation passed; 39 Java policy checks, shipped JS
export/sign-out/tab checks, 6 backend/mobile contract tests, 4 amendment tests and
37 existing backend auth/permission/identity tests passed. Backend tests used
synthetic temporary data. The backend integration suite is optional and requires
the separate People checkout; it must not be copied into this public repository.

1.0.2 checks run by Claude in this checkout: `assembleRelease bundleRelease
lintDebug` passed, lint "No issues found"; apksigner shows the release APK's
certificate SHA-256 `3e3a0931…501d75`, identical to the 1.0.1 APK, and aapt reports
versionCode 3 / 1.0.2; 39 policy checks, JS export checks, 5 amendment tests and
6 optional backend contract tests passed. Patcher dry run passed against the real
pre-amendment People source (`1d55b1e`) with its `console_auth.py`, and reports
"Already amended" on the backend feature branch. bundletool validation and the
37 backend auth tests were not rerun for 1.0.2. No real Android fingerprint/PIN/Keystore,
file-provider or lifecycle execution has been verified. A previous emulator-start
attempt was blocked by automatic approval review; no retry was made as part of
the repository move.

Next actions for Cursor/Claude:

00. 1.4.0 is released (F-Droid, Drive). On the phone, try Reporter updates with a master sign-in:
   check the list (names, IP, install date), a one-PC preview, then Cancel. Don't send a
   real update request or pause as a test without the user's say-so.
0. Install 1.3.0 on the phone (F-Droid or Drive) and check shift, ID number and
   birth date with a test person.
1. Open this repository and read AGENTS.md, then inspect actual Git state.
2. Server amendment is installed (see above). On the phone, sign out once and
   sign in again, then confirm the session survives more than 30 minutes. If login
   breaks, roll back using the backup above and `SERVER-AMENDMENT.md`.
3. On a test phone, install/upgrade the signed 1.1.0 app; verify initial login, biometric
   and PIN success/cancel, background/process restart, changed device lock,
   permissions, report/import picker return, expiry/revocation and sign-out.
   Also check 1.0.2: unlock returns to the open page; with no screen lock,
   import/save/print no longer signs out, but leaving via Home still does.
   1.0.3: About's Privacy policy and Delete account buttons open the browser.
4. Privacy policy and deletion-request pages are live on GitHub Pages from
   `docs/` (https://loudalley.github.io/koenekt-android/privacy.html and
   delete-account.html), linked from About in 1.0.3. Still to do: support/developer details, review access, actual-device screenshots and an internal Play test
   track. Public GitHub source is not Play publication/approval. Apple is deferred.

## Handoff log

**20 October 2026 — Codex:** User requested moving the app to its own repository
under C:\Projects, publishing it publicly on GitHub, and notes for Cursor/Claude.
Moved the complete local app directory, retaining ignored private signing files
locally. Created fresh public Git history from the app files only, portable
amendment fixtures, an optional sibling-backend integration path, shared rules,
this handoff and thin assistant entry points. Internal deployment identifiers are
removed from public instructions. No backend source/history, data or secrets are
published; the original private repository gets a migration pointer and handoff.
GitHub publication is verified: visibility PUBLIC, default branch main, with
initial app-source commit `75d5b3e`. The original People repository remains private;
its Android removal/migration pointer is committed and pushed there. The new
checkout is clean after reviewed source/documentation commits; local credentials,
SDK config, logs and release copies are ignored. Optional backend-test skipping
without a backend checkout was also checked successfully.

**20 October 2026 — Claude:** User asked to review the repo, fix problems and apply
the server fix. Fixed: (1) the patcher now verifies the target server's
`console_auth.issue_session(seconds=)`, `https_session` and `cauth` import before
changing anything, with new tests and corrected guide; (2) phones without a screen
lock were signed out by the app's own file picker/Save/print screens; (3) every
unlock went Home and lost the open page. Bumped to 1.0.2 (3), built/signed with the
existing upload key and copied to ignored `dist/1.0.2/`. Server NOT applied: the
read-only SSH probe of srv1 was denied by the permission policy, and nothing was
copied or run there. Not device-tested.

**20 October 2026 — Claude (server):** User asked to apply the server fix on srv1.
The srv1 key needed the Windows ssh-agent; the user started it and added the key.
Read-only check found one People console tenant, the expected source path, no
earlier backups, and sudo requiring a password. Copied the 1.0.2 patcher (hash
matches the repo) and the dry run passed. The user ran the apply and restart with
sudo; Claude verified the result (details under "Server work"). No receiver, clock,
drain, data or licence-service change. Next: phone sign-out/sign-in test.

**20 October 2026 — Claude (privacy pages):** User asked for a privacy policy on
GitHub Pages. Added `docs/` (privacy policy, account-deletion request page, landing
page) served from `main` /docs. Business facts were supplied by the user: Koenekt
(Pty) Ltd, contact and information officer william@koenekt.co.za, POPIA, hosting
Absolute Hosting (South Africa), deletion within about 30 days, audit logs 6 months,
attendance/payroll at least 1 year. App behaviour statements were checked against
source: the audit log stores IP addresses, and People can only disable (not delete)
users, so deletion is by request. Not legal advice; the owner should review the
wording, especially the 1-year minimum against statutory employment/tax record periods.

**20 October 2026 — Claude (1.0.3):** Added Privacy policy and Delete account
buttons to the About dialog; the account-removal sentence now points to Delete
account. URLs are constants in AppPolicy; new policy checks confirm they are the
published HTTPS pages and open outside the trusted WebView. Version 1.0.3 (4); release
build/lint clean, same signing certificate, 43 policy checks plus export, amendment
(5) and backend contract (6) suites pass. Copied to ignored `dist/1.0.3/`.
Not device-tested.

**20 October 2026 — Claude (F-Droid):** The user chose a self-hosted F-Droid repo
over the official F-Droid store (which builds from source, needs an open-source
licence and uses its own signing key).
- Added fastlane metadata (title, descriptions, release notes for versionCodes 3
  and 4, icon and 1024×500 banner rendered from the user's vector logo
  `koenekt-logo.svg`) and `fdroid/za.co.koenekt.app.yml`.
- `tools/fdroid_repo.py` has two commands. `init` creates the PKCS12 repo key in
  `.signing/fdroid/` and refuses replacement; `publish` adds the APK and notes,
  runs fdroidserver 2.4.5 from `build/fdroid-venv` and mirrors the result to
  `../koenekt-fdroid`.
- Windows workarounds: `apksigner.bat`, forward-slash glob/walk paths, `status/`
  excluded.
- Created public repo `loudalley/koenekt-fdroid` with Pages from main root.
- Verified live: the index checksum matches the signed `entry.json`, the APK is
  byte-identical to `dist/1.0.3`, and the signer is the existing upload
  certificate, so it updates directly installed copies.
- A second push (adding `.gitattributes` `* -text` to the site) was blocked by the
  auto-mode permission policy; the next `publish --push` includes it. The current
  live files were checked byte-for-byte and are correct.
- Not yet tried in the F-Droid app on a phone.

**20 October 2026 — Claude (1.1.0 redesign):** The user said the app felt cramped,
like the mobile website squeezed into an app; adding an employee was hard; and the
F-Droid release notes did not render properly.
- First actual rendering: an Android 15 emulator (AVD `koenekt-test` in the local
  SDK) plus the People console run locally with synthetic data (scratch launcher;
  nothing added to either repo). Release builds block screenshots via FLAG_SECURE,
  so use debug builds.
- Findings: two stacked headers. People showed no employee on the first screen
  (filters, six buttons, bulk panel). The Add form had 25 fields, starting with
  clock PIN and privilege. The server only needs name, company and PIN.
- The user chose native everyday screens. Added `Ui.java` (design kit),
  `PeopleScreen`, `AddEmployeeFlow` (three steps; same `/api/employees` payload as
  the website: push_device true or "save only", push_all_company true) and
  `LiveScreen`.
- MainActivity now has an app bar, a bottom bar and Home/Account screens, keeps
  every lock, vault, cookie and routing rule, and shows server error details
  (FastAPI `detail`). After unlock, tabs reload and the add flow keeps its answers.
- mobile.js hides `header.kp-header`. The web People page is renamed "People
  records".
- Brand vectors (`ic_logo`, `ic_brand_mark`) and an adaptive launcher icon now
  come from the user's `koenekt-logo.svg`.
- Debug-only `-PdevOrigin` lets a debug build use `http://10.0.2.2:<port>` (policy
  checks prove the default stays HTTPS-only). The release BuildConfig has
  DEBUG=false and DEV_ORIGIN="", and the release manifest has no debug network
  config.
- Emulator walkthrough passed (see README release state). A bug it found was
  fixed: the keyboard stayed open across screens.
- F-Droid notes: no version-heading line, `•` bullets, one sentence per line (the
  description is HTML-rendered with newlines turned into breaks). The changelog
  rendering was not seen in the F-Droid app; the user was asked for a screenshot.
- Not tested on a physical phone or with production data. Next: install 1.1.0 on
  a phone and check the new screens with a real account.

**20 October 2026 — Claude (1.2.0 edit and leaver):** Asked by the session that
built 1.1.0: replace the summary's "Edit in People records" button with native screens.
- Person summary now has **Edit details** (needs people_edit) and **Mark as left**
  (needs people_leave, active people only). New `EditPersonFlow`: name, department
  picker, job title, start date; the clock PIN is shown but cannot be changed.
- Edit uses the website's `/api/employees` update with the `employee` key. "Save and
  update clocks" sends push_device true; "Save without updating clocks" sends false.
  A leaver only gets "Save" (false), because pushing their name would put them back
  on the clocks. Differences from the website's form: the app does not send ID number,
  birth date, shift, rates or extras, so the server keeps them. It sends the
  directory's privilege and card number back unchanged. The website's form resets
  both to blank defaults on every save (pre-existing website behaviour, not changed).
- Mark as left: a confirmation dialog, then `/api/employees/deactivate` with
  url/company/employee/pin, the same as the website. The endpoint has no leave-date
  field (local mode stores the server's today; Frappe mode only sets Inactive), so
  the dialog shows "Today" instead of a date picker. Picking an earlier date would
  need server work. If the server marks someone left but can't queue the clock removal
  (502, e.g. a company with no clocks), the app reloads the list and explains.
- `MainActivity.Flow` generalises the add flow, so edit answers also survive
  lock/unlock (not exercised: the emulator has no screen lock).
- Emulator (debug, `-PdevOrigin`) against the previous session's synthetic local
  console: every case below worked and the synthetic database matched.
  - Edit and save to clocks: one USERINFO update queued with the new name.
  - Save without updating clocks: nothing queued.
  - Department and date pickers, and the discard prompt.
  - Mark as left: status left, today's date, DELETE USERINFO queued.
  - A leaver's edit: Save only, nothing queued.
  The error dialog after a failed leave and the people_edit-without-people_leave view
  were not tried on the emulator. The backend covers that permission split, and so
  does the new contract test.
- New contract test `test_native_edit_and_leaver_payloads` in
  `tools/test_mobile_login.py` checks four things against the real backend: the
  app's exact payloads, PIN permanence, that ID number and privilege are kept, and
  that edit and leave need separate permissions.
- Checks: `assembleRelease bundleRelease lintDebug` passed and lint reports "No issues
  found". 48 policy checks, the export checks, 7 contract tests and 5 amendment tests
  passed. The APK is signed with the same certificate (`3e3a0931…501d75`), and aapt
  reports versionCode 6 / 1.2.0. Copied to `dist/1.2.0/`. Changelog
  `fastlane/.../changelogs/6.txt` written.
- Phone feedback relayed by the main session: 1.1.0 is installed on the user's phone
  (from Google Drive). The user said the general theme "looks very good", but that
  "Edit in People records" brought the web version back into play. That is what 1.2.0
  replaces. No other device checks were reported.
- Released at the user's request: pushed `main`, and `tools/fdroid_repo.py publish
  --push` published it (`koenekt-fdroid` commit `76ac4d4`). The live index lists 1.2.0
  (6), and the downloaded APK's SHA-256 `d134d2f8…6bf91d7` matches `dist/1.2.0`.
  Copied the same APK to the Google Drive for desktop folder
  `G:\My Drive\Koenekt Android\` (next to 1.0.3 and 1.1.0; hash checked).
  The API connector returned 401, so the copy went through the sync folder.
- The user installed 1.2.0 on their phone and reports Edit details works. They are
  double-checking because "not all fields were there". The native form has only name,
  department, job title and start date by design. Ask which other fields they want
  (ID number, birth date, shift, staff category, pay/tax, card, company transfer).
  Mark as left not yet reported.

**20 October 2026 — Claude (1.3.0 shift, ID, birth date):** On the phone, 1.2.0's
Edit worked but "not all fields were there". The user chose to add ID number, birth
date and shift.
- `EditPersonFlow` now has Shift (Automatic or a template; templates come from the
  directory's `shift_templates`), ID or passport number, and Birth date (picker limited
  to 1920 through today, with Clear). The person summary also shows Birth date and
  Shift today.
- Saving uses the website's own calls:
  - ID number and birth date ride on the `/api/employees` update, sent only when the
    directory listed them.
  - The shift goes through `PUT /api/shifts/assign`, and only when it changed.
  - The update ignores an empty birth date, so clearing one also calls
    `PUT /api/people/extras` with `{employee, birth_date: ""}`. That endpoint merges, so
    pay, tax and category are untouched.
  - If a follow-up call fails, the form stays open and says the details were saved but
    the shift or birth date was not.
- `MainActivity.request` takes a method (for PUT).
- The form keeps its scroll position when it redraws after a picker, and scrolls to an
  error.
- Emulator, against the synthetic console (debug build), checked against the synthetic
  files:
  - Setting all three, then reopening: the values show.
  - Invalid ID `AB.12`: the server's "letters and digits only" message is shown.
  - Clearing all three: the extras row is removed and the shift assignment is gone.
  Found and fixed on the way: a cleared birth date wasn't saved at first.
- Contract test extended (set, invalid, clear as an edit-only user).
- Note for the backend (not changed): `PUT /api/shifts/assign` checks people_edit but
  not the employee's company, unlike `/api/people/extras`.
- Released at the user's request: pushed `main` (`85c5bc7`) and published to F-Droid
  (`koenekt-fdroid` `589c57f`). The live index lists 1.3.0 (7), and the APK downloads
  with SHA-256 `4a56ce1f…f2b17e8f`, matching `dist/1.3.0`. The same APK is copied to
  `G:\My Drive\Koenekt Android\` (hash checked).

**29 October 2026 — Claude (1.4.0 Reporter updates, cloud session):** The user said they
had changed the server and asked for "those functions" in the app ("it's the main app").
- Granted read access to the private People repo. Its `main` has only handoff commits
  since 20 October, and nothing changed in the People console. The changes are on the
  licence site, branch `deploy/licence-updates-gui-on-cea23fe`:
  - `50ff824`: tick PCs, then update or cancel just those.
  - `759d5c3`: waiting PCs are shown as waiting, with the reason.
  - `61e8db7`: each PC's Windows name, IP address and install date.
  The licence site's Releases section is not on GitHub yet, so it was not built
  natively. It is still on the web page. PC names, IP and install date only appear once
  the server sends them; until then the app shows the short PC id, like the website.
- New `UpdatesScreen.java`, opened from Home (a tile when there's room, and the Tools
  row "Reporter updates"). It replaces the web Updates tab for everyday use:
  - Totals.
  - PCs by shop, with the Windows name, version and last contact.
  - Tick boxes, and a bottom bar to Update…/Cancel… just the ticked PCs.
  - PC details: status, why it's waiting, request, offered release, IP, install date,
    updater, plus Update this PC / Cancel request / Pause / Resume.
  - Menu: Tick every PC, Update every PC…, Cancel every request…, and "Releases and
    full page (web)".
  - Optional release chip (the website's "Only release" box).
  - It uses the licence site's own routes and payloads. Every action is previewed
    first. Multi-PC actions send `confirm: "selected"`, fleet actions send
    `confirm: "all"`, and pause asks first, all as on the website.
- `MainActivity`:
  - `adminRequest` calls the licence site. A 401 there ends only the management
    session.
  - `TAB_UPDATES` is a screen without a bottom-bar tab, so unlock returns to it.
  - The read timeout is 90 s for `/licences/api/updates/`, because ticked PCs are asked
    one at a time.
  - The licence site's 5xx `detail` is now shown, e.g. "The update service is not
    reachable".
- New `ic_update` icon. Changelog `8.txt`. README updated.
- Checks actually run (Linux cloud container, no Android SDK; dl.google.com is blocked):
  - All app sources compile with `javac --release 17` against Robolectric's
    `android-all` 14 jar, with R/BuildConfig stubs (API 34 classes, not 36).
  - API use was checked by hand against minSdk 26 (no `List.of`).
  - 48 policy checks, the export checks and the amendment tests pass.
  - New `tools/test_mobile_updates.py` (6 tests): passes against the deploy branch's
    licence site with the update service replaced by a recorder; skips on `main`.
  - `test_mobile_login.py`: 7 pass against `feat/koenekt-android-app`. Against People
    `main` the same 3 fail with or without this change (main lacks the amendment).
  - NOT run: Gradle build, Android lint, signing, emulator or phone.
- Merged to `main` (fast-forward) at the user's request before any Gradle build, after
  they were told `main` would then hold unbuilt code.
- Not done: Gradle build/lint, signed build, `dist/1.4.0`, F-Droid, Drive.

**29 October 2026 — Claude (server contract check, cloud session):** The user asked
whether every function in the app matches the server.
- Every People and licence-site route the app calls exists, with the same method, in
  People `main`, `feat/koenekt-android-app` and the licence-site deploy branch. So do
  the web pages it opens, the capability names, the licence tabs and the
  `kp-header`/`#logout` hooks in `mobile.js`.
- Tested setup: People `main` console with this repo's amendment applied, plus the
  deploy branch's licence site, in a scratch checkout. That is the nearest copy of
  production in git, but production's console is a server-only build, so it is not
  byte-identical.
- Results there: `test_mobile_login.py` passes 8 tests and `test_mobile_updates.py`
  passes 6.
- New test `test_native_add_employee_and_live_board` covers the calls that had no test:
  - `/api/me` fields and `/api/companies`;
  - departments and next PIN;
  - the Add employee payload, the reply the app's done screen reads, and that a used
    PIN is refused;
  - the Who's in totals and rows;
  - another company's next PIN is refused (403).
  It also passes against `feat/koenekt-android-app`.
- Not covered: the `/api/sites` fallback (only used when `/api/companies` is empty)
  and Frappe-mode companies; the tests use local mode. Nothing was run against the
  live servers.

**29 October 2026 — Claude (1.4.0 build on the Windows PC):** The user pasted the cloud
session's report and asked to check and build.
- This PC's `main` had two unpushed commits: the 20 October accounts/leavers "1.4.0", which
  also used versionCode 8 and `changelogs/8.txt`. That work is on hold for its server update.
  It must become 1.5.0 (9) and be rebased onto this `main`.
- To leave that untouched and not switch branches in the shared checkout, the build used a
  separate worktree (`../koenekt-android-build`) at `origin/main` `9758abe`. `.signing`
  there is a directory junction to the real one (not a copy), and `local.properties` was
  copied.
- First Gradle build of the cloud session's code: it compiled. Android lint found 4
  `SetTextI18n` warnings in `UpdatesScreen` (text built inside `setText`). Fixed by building
  the strings first (`73478c2`, branch `release-1.4.0`, no behaviour change); then lint
  reported "No issues found".
- Code review of `3000e09`: licence-site calls stay origin-restricted. A management 401 ends
  only that session. Every update/cancel is previewed by the server first, multi-PC and fleet
  requests send `confirm`, and pause asks first.
- Checks:
  - 48 policy checks, export checks, amendment tests.
  - `test_mobile_login` (8) and `test_mobile_updates` (6) pass against a scratch composite:
    People `main` `03582a7` console with the amendment, plus the licence-site deploy branch
    `61e8db7`.
- Emulator (debug build) against that composite with synthetic data: sign-in, Home, People,
  person summary, Who's in. Reporter updates was not tried (it needs master and the real
  licence site).
- Signed release: same certificate `3e3a0931…`, aapt 8 / 1.4.0.
  - APK SHA-256 `848ff565…6ef6d`.
  - Copied to `dist/1.4.0/`. The unreleased accounts build moved to `dist/accounts-unreleased/`.
- Released at the user's request:
  - Pushed `main` (`9758abe..9813ac2`).
  - `tools/fdroid_repo.py publish --push` ran from the build worktree, so the 1.4.0 release
    notes were used. Its venv was linked by a junction. Result: `koenekt-fdroid` `2089d9e`.
  - The live index lists 1.4.0 (8), and the downloaded APK hash matches `848ff565…`.
  - Copied to `G:\My Drive\Koenekt Android\` (hash checked).
