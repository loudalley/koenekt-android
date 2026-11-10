# Koenekt Android — shared handoff for Codex, Cursor and Claude

Last updated: **10 November 2026, Claude (Windows PC) — version 1.10.1 (17), built, NOT released: fixes My stores
jumping on an account with many stores (pages jumped back and forth as live figures came in, the list re-sorted under
the finger so a tap opened the wrong store, a till page closed when its till skipped a read). Replaces the unreleased
1.10.0 (16); same release conditions (Reporter 1.9.106 relay on srv1 first).** Details in the 1.10.1 section below.

Last updated: **10 November 2026, Claude (Windows PC) — version 1.10.0 (16), built, NOT released: My stores in
sections as on the website (Dashboard, Live, Compare, Reports, Groups). Needs the Reporter 1.9.106 Link relay on srv1
(William installs the bundle) and Reporter 1.9.106 on the shop PCs; until then Dashboard/Compare say no past days
have arrived and Reports say the store's Reporter is older than 1.9.106. Release (Play, F-Droid, website) waits for
the owner after the relay is installed.** Details in the 1.10.0 section below; 1.9.0 (15) stays the live release.

Last updated: **9 November 2026, Claude (Windows PC) — version 1.9.0 (15): join My stores with a QR code or a
typed code and choose your own username and password; owners' Team screen; change/delete your store login; and
open any till in My stores (its own sales, payments, refunds, hours and items, as on the website; needs
Reporter 1.9.105+). Rebuilt and signed (`dist/1.9.0/`, the first 1.9.0 build moved to
`dist/1.9.0/superseded-first-build/`, never distributed), pushed to `main`, queued in Play for the owner to
submit AFTER the Link relay bundle `koenekt-link-stage-20261109c.zip` is installed on srv1 (until then Join
answers 404: checked 9 Nov, `/api/join/check`, `/j` and `/.well-known/assetlinks.json` all 404 on srv1).
Copied to Google Drive. NOT on F-Droid yet. UPDATE 9 Nov: the owner installed relay bundle 20261109c on srv1 (all OK)
and promoted Reporter 1.9.105; checked from the PC: /health, /.well-known/assetlinks.json (za.co.koenekt.app, certs
02:D0:04… and 3E:3A:09…) and /j answer 200, /api/join/check answers "not valid or has expired" for a made-up code,
the licence site's link.js has Invite. SUBMITTED to Play 9 Nov 18:14 (in review; publishes on approval), published on
F-Droid (koenekt-fdroid 273fc69; its first Pages build failed in a GitHub Actions outage, a fresh Pages build after
the outage (run 42679182622) succeeded: the live index lists 1.9.0 (15), APK a99b816a…) and on the website
(koenekt.co.za/downloads/files/Koenekt.apk = a99b816a…, deploy 20261109181552; downloads text now mentions invites).**
Before that: version 1.8.1 (14), People and licence pages follow the app's Appearance; saved for later in Play
(1.9.0 replaces it).
**Earlier the same day:** 1.8.0 (13), the koenekt.co.za look and Account → Appearance, is live on Google Play (published 6 November).
Play Console: production release 14 (1.8.1) saved with the 14.txt notes (only warning: no deobfuscation file);
HELD by the owner (9 Nov) to ship with the next feature release: the change is "saved for later" in Publishing
overview, not submitted. The fix stays on `main`; the next release (versionCode 15+) carries it and replaces draft 14.

Previous release: **29 October 2026, Claude (Windows PC) — version 1.6.0 (10), account and
leaver controls (change/delete your account, forced password change after an admin reset,
delete leavers, clock passwords), built and signed on the PC, pushed, published on the
self-hosted F-Droid repo and copied to Google Drive at the user's request; not yet tried on
a phone. Its People server update is installed.** People server
amendment and the accounts server update are both INSTALLED on the production People console.


## 1.10.1 (17) — 10 November 2026, Claude (built, not released)

- William: "on the master account ... the pages jump back and forth as the monetary updates roll in, so itll throw me
  out of stores and enter stores randomly. this is a major issue". Causes found (all in 1.9.0 and 1.10.0):
  every store's live answer rebuilt the whole page (dozens a minute with many stores); each rebuild made a new
  ScrollView drawn at the top before the position came back a frame later (the jump); the store list re-ranked on
  every answer, so the row under the finger changed between looking and tapping (the wrong store opened); Reports
  re-rendered every poll (back to the top); a till page closed whenever its till was not read in a round; Refresh
  went back to the top; and the 4-connection pool was filled by live asks (each held up to 20 s on the relay), so
  the dashboard, reports and opening a store waited behind them (the first Dashboard sat on a spinner for 15 s+).
- Fix: `StoresScreen.redraw()` coalesces (400 ms) and waits until the screen has been left alone 1.5 s
  (`MainActivity.untilQuiet()`: finger down, touches and scroll/fling tracked in `dispatchTouchEvent` and the page's
  scroll listener); it also drops redraws while another tab shows. `MainActivity.redraw()` rebuilds the content
  inside the SAME ScrollView (`page()` reuses it), so the position never moves. The Live list keeps a steady order
  (`steady()`/`rankAgain()`): ranked when the section opens, the sort or scope changes, Back, Refresh, or a round of
  answers is complete; a tap within 700 ms of the rows moving is ignored. Report polls go through the same redraw.
  A till page keeps the till's last figures (marked "last figures") when it is not read. Refresh keeps the place.
  Live asks (`…/live`) run on their own 6-connection pool (`liveWorker`).
- Checks: PolicyChecks 123, StoreChecks 51; `assembleRelease bundleRelease lintDebug`, lint "No issues found".
  Emulator (`koenekt-test`, debug build) against the REAL relay code with 24 made-up stores answering at random
  times with changing figures (scratchpad `stores_many_relay.py`, a variant of the demo relay): over three
  60-second rounds while scrolled down the page never moved, figures changed in place, the list re-sorted once per
  completed round; a store opened as tapped and stayed open through a round (figures updated); a till page held
  through a round; the Dashboard showed in under 6 s with live asks running (was 10–15 s+).
- Release files (upload key 3E:3A:09…), built from the clean tree at 4ebaba2, which also holds 320bab4 (Dashboard and
  Compare include today's live figures, another session's work): `dist/1.10.1/Koenekt-1.10.1.apk`
  4dd2389397fe9206e1962bb0a28baeaad2ce5495a7ebf445d99391a6635ca71a, `dist/1.10.1/Koenekt-1.10.1.aab`
  06177a063da076a94506a3c0aa2b9212e55c324eb836305f001e98331823f6f2; Play notes `changelogs/17.txt` (425 characters,
  carries 16's notes plus the fix). Not done: release (Play, F-Droid, website, Drive), test on a real phone with
  the real master account.

## 1.10.0 (16) — 10 November 2026, Claude (built, not released)

- William: "build the reports we have on reporter into the website for historical and order reports and rosi ... i
  also want to be able to do it from the android app. features we add to the reporter app should be available on the
  android app also. the shop dashboard as we have it should be called Live, the historicals and month to date and shop
  comparisons ... a different section so we can pit the shops/shop types against each other, every owner should have a
  customisable dashboard ... default layout ... reset layout button and save layout button. shop grouping". Decisions
  (asked): daily totals stored on srv1; all four reports; reports owners only; owners make their own groups.
- API and the shared dashboard layout: Reporter repo `docs/LINK-REPORTS-API.md` (relay `server/link_data.py`,
  `server/link_relay.py`; Reporter `app/linkreports.py`). Website: `koenekt-website/site/assets/js/portal-more.js`.
- App: the Stores tab has a section row. Dashboard (`DashboardScreen`, owners land here; staff land on Live): tiles
  from the shared layout (kpi, trend, ranking, table, live), period and comparison chips, Customise (tune icon) with
  per-tile chips, Up/Down/Remove, Add a tile, Save layout (POST /api/me/dashboard) and Reset layout. Live is the old
  My stores screen, renamed (its phone-only tiles/types/grouping unchanged). Compare (`PeriodScreen`): period,
  against last year or the period before, store/group/shop type (relay type families)/detailed type, rank by any
  figure, top-6 lines by day, every figure per row and "Together". Reports (`ReportsScreen`, owners): Range,
  Ordering, ROSI, Turnover; store, dates (quick periods or date pickers), cover days, ROSI by group/dept and month
  sheet; polls "pending" every few seconds while open. Groups (`GroupsScreen`, owners): make, rename, choose stores,
  delete. `PastData` loads /api/history (one call widened to cover every tile, cached 10 min), /api/groups and the
  layout; `HistoryMath` (plain Java) does periods and sums exactly as the website: totals add, GP % from the sums,
  stock from each store's last day that has it, per trading day, stock days, % change or GP points.
- `AppPolicy.linkAllowed`: adds stores/{id}/(range|order|rosi|turnover), history, groups, groups/delete, me/dashboard;
  a query only on /api/history and only `from=YYYY-MM-DD&to=YYYY-MM-DD`. `Chart.days()` draws a line per day with gaps
  for closed days (a lone day is a dot).
- Checks run: PolicyChecks 123, StoreChecks 45 (20 new for HistoryMath); `assembleDebug`, lint clean; debug build on
  the `koenekt-test` emulator against the REAL relay code in a local stand-in (made-up shops, People pointed at an
  unused local port so no login went anywhere else): Dashboard figures matched the website exactly (R 823 454,
  25.4%, +10.2%), Compare, a Range report, Groups made on the website, Customise → tile scope → Save layout read back
  from the relay. The emulator's old release-signed copy of the app was uninstalled for the debug build.
- Release files (signed with the upload key 3E:3A:09…, lint clean): `dist/1.10.0/Koenekt-1.10.0.apk`
  96edace03e116d839c674bbc2275353d27dd05a28a2b8a13f49d1b798f9b90be, `dist/1.10.0/Koenekt-1.10.0.aab`
  a5a8cdf836fb6a34487085b0ca605452a41df6eebc62b8456444d1bfd2afbb1f; Play notes `fastlane/.../changelogs/16.txt`
  (417 characters).
- Not done: release (Play, F-Droid, website download), real-device test. The Live section's phone-only "store types"
  are still separate from Koenekt's shop types on the relay.

## 1.9.0 (15) — 9 November 2026, Claude

- William: "plan out qr code joining ... type code manually, they then create their own user and password stored
  on the server", then "go with your recommendations" (O1 owners invite staff only; O2 7 days Koenekt / 48 h
  owners, single use; O3 reset codes, Koenekt's typed/View password kept for logins it makes; O4 a code for
  another customer needs a new login; O5 no shop-PC pairing now).
- Relay (Reporter repo, `server/link_relay.py`, `server/qr.py`): invites (SHA-256 only), `/api/join/check`,
  `/api/join`, `/api/me`, `/api/me/password|delete`, owner `/api/invites`, `/api/team`, `/api/team/remove|reset`,
  `/j`, `/.well-known/assetlinks.json` (02:D0:04… Play App Signing, 3E:3A:09… upload key). Licence site Link tab:
  Invite (QR card, print), invite list, Reset code.
- App: `JoinFlow` (code → what it is for → username/password; reset codes: password only), `TeamFlow` (owners),
  `QrView` (paints the relay's QR rows, black on white), Account rows (Join with a code / Connect; Team; Change
  store password; Delete my store login), sign-in "Join My stores with a code", manifest App Link
  (`https://link.koenekt.co.za/j`, `autoVerify`) and `koenekt://join`. A join link while locked opens the join
  screen after unlocking (`resumeScreen` now resumes `JoinFlow`, and `TeamFlow` while the store session is valid).
- Privacy page (effective 9 Nov): self-made store logins (hash only, no email/phone), codes as hashes, the
  phone's camera reads the QR; retention. Delete-account page: Account → Delete my store login. Full description
  +1 line; changelog 15.txt; PLAY-STORE-DRAFT Data safety note (account created in the app, deletable).
- Checks: `assembleRelease bundleRelease lintDebug` ("No issues found"), PolicyChecks 109 (41 new), StoreChecks 23,
  export checks, test_server_amendment OK. Upload key `3e3a0931…`; aapt 15 / 1.9.0; APK `1a68017a…`, AAB
  `31e3a43c…` (`dist/1.9.0/SHA256SUMS`).
- Emulator (debug, `-PdevLink=http://10.0.2.2:18298`, local relay with a made-up PC "Demo Shops" / Northgate,
  Southside): QR link via `am start -d https://link.koenekt.co.za/j#…` opens Join with the code checked (owner,
  2 stores); created demo.owner → signed in; Team lists logins; Invite staff → QR + code + Share sheet (the
  screenshot's QR decodes with OpenCV to the link); typed code (lower case, stray 0 dropped) and `koenekt://join/…`
  both work; weak password refused by the relay with the message shown above the fields; staff sees both stores;
  Delete my store login → signed out, the relay refuses that login. Not tried: a physical phone, App Link
  verification (needs the relay on srv1), Change store password and owner reset on the phone (relay-tested).
- **Next:** William installs the relay bundle on srv1 (Reporter handoff has the command); check
  `https://link.koenekt.co.za/.well-known/assetlinks.json` and `/j`; then upload 1.9.0 to Play (replaces the
  saved-for-later 1.8.1), and F-Droid/Drive if wanted.

## 1.8.1 (14) — 9 November 2026, Claude

- Cause: `assets/mobile.js` injected `body{background:#f6f8f5}` into every People page, so in dark mode the page
  background stayed light around dark cards. Now `body{background:var(--bg,#F7F7F4)}` (People's theme token).
- People (`static/koenekt-theme.js`) and the licence site (`admin.html` inline script) keep their own
  System/Light/Dark in localStorage `koenekt-theme`, read before first paint, and decide System by the WebView's
  `prefers-color-scheme`, which need not match the app. `MainActivity.syncWebAppearance()` (on
  `onPageCommitVisible`, `onPageFinished`, and from `applyAppearance`) sets that key and `<html data-theme>` to the
  app's resolved `light`/`dark`, calls `KoenektTheme.choose` when present, and hides `select[data-kp-theme]` and
  `#themeSwitch` via element styles (the licence CSP blocks injected `<style>`, not DOM styles). Later pages paint
  in the right colours from the stored key. No JS bridge added.
- `applyAppearance` also recolours the web bar's refresh/menu icons and the WebView background.
- Checks: `assembleRelease bundleRelease lintDebug` (lint "No issues found"), PolicyChecks 68, StoreChecks 23,
  export checks, test_server_amendment OK. Upload key `3e3a0931…`, aapt 14 / 1.8.1; APK `ecafe211…`, AAB
  `dd2a2246…` (`dist/1.8.1/SHA256SUMS`).
- Emulator (debug, local People console from `C:\Projects\koenekt-people-look` = People `main` c5d4485, synthetic
  staff): phone light + app Dark → Reports and People records fully dark; app Light → light; System + `cmd uimode
  night yes/no` with Reports open → the page and bar icons flip live both ways.
- Not tested: physical phone, the licence pages (operator login), Android 8-10.

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
trigger the licence service's global sign-out. Release builds protect screenshots,
screen recording and recent-app previews unless Account → Allow screenshots on this phone
is on (unreleased, see the 5 November 2026 log entry). HTTPS/origin rules, mixed-content blocking and no native
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

0000. **My stores: built 3 Nov 2026, NOT released** (owner's request: live till sales per store,
   a customisable multi-store dashboard grouped by store type, store vs store and type vs type).
   Native `StoresScreen`/`StoreScreen`/`CompareScreen`/`Chart`/`StoreMath`, Stores tab, Account →
   Connect My stores; relay `https://link.koenekt.co.za` (installed on srv1 3 Nov). Before a release:
   bump to 1.7.0 (12); Play Data safety and the privacy page must mention business sales figures shown
   (none stored); add a demo store login for the Play reviewer; try it on the owner's phone against the
   pilot shop once its Reporter has `link_enabled`. Spec: Reporter repo
   `docs/proposals/KOENEKT-REPORTS-IN-APP.md` sections 1, 2b, 7.1. Keep real shop names and figures out of
   this public repo. The screenshots switch (5 Nov 2026) ships in the same release: add it to the 1.7.0
   changelog, and on a real phone check that turning it on asks for fingerprint/PIN, that a screenshot and a
   recording then work, and that the recent-apps preview shows/hides with the switch.
000. 1.6.0 (accounts and leavers) is released (F-Droid, Drive). On the phone, with test people: Account → Change password; an admin's Reset password
   in Users followed by the forced change; Delete from People on a test leaver; a clock
   password on a test person. The local `main` in `C:\Projects\koenekt-android` still has the
   old unpushed "1.4.0" accounts commits (`70495a6`, `8c7fba2`); they are superseded by 1.6.0
   and can be dropped once 1.6.0 is on `main`.
00. 1.4.0 is released and on the user's phone; they report Reporter updates works. Still
   worth a careful look next time (no test sends without the user's say-so):
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
- 29 October: the user installed 1.4.0 on their phone and reported "Reporter updates works".
  No other device checks reported.

**29 October 2026 — Claude (1.5.0 Who's in by department, Windows PC):** The user asked to
separate who's in and the other status checks by department ("shop is dept at the moment").
- No server change. `/api/dashboard/live` already returns each person's `department`,
  `at_work` and `seen_today`. The app now loads it once with `presence=all` and filters on
  the phone, so switching is instant and the per-department counts are always there.
- Who's in (`LiveScreen`):
  - A department chip (All departments or one), remembered per company in app preferences.
  - With all departments: one row per department ("2 of 4 at work · 3 clocked today"); tap
    to choose it.
  - Tiles count only the chosen department. At work and Not in are tappable filters.
  - Show filter adds Clocked out (clocked today, not in now) and Not clocked today.
  - The chip row scrolls sideways when the names are long.
- Version 1.5.0 (9), `changelogs/9.txt`. The accounts/leavers work (local `main`, waiting
  for its server update) must now become 1.6.0 (10).
- Checks: `assembleRelease bundleRelease lintDebug`, lint "No issues found"; 48 policy
  checks, export checks; `test_mobile_login` (8, now also asserting `seen_today` per
  person), `test_mobile_updates` (6), amendment tests (5) against the scratch composite
  (People `main` `03582a7` with the amendment, which matches production).
- Emulator (debug build) against a synthetic local console with synthetic punches: department
  rows and counts, choosing Kitchen, Not clocked today, the choice kept after leaving the
  tab, All departments, and the Not in tile.
- Signed release: same certificate `3e3a0931…`, aapt 9 / 1.5.0. APK SHA-256
  `53df030f…87a31e`. Copied to `dist/1.5.0/` here and in the main checkout.
- Released at the user's request: pushed `main` (`750f0c5..6339680`); `tools/fdroid_repo.py
  publish --push` from the build worktree, giving `koenekt-fdroid` `dca4229`. The live index
  lists 1.5.0 (9) with the APK hash `53df030f…`. Copied to `G:\My Drive\Koenekt Android\`
  (hash checked).
- 29 October: the user installed 1.5.0 on their phone and reported "departments work".

**29 October 2026 — Claude (accounts server update installed):** The 20 October package
stopped in its dry run: the live `adms_console.py` was an older upstream version (without
three later, unrelated upstream changes: the shop-PC login guard, the favicon route and the
recent-punch ordering). Rebuilt the package on the server's own copy with a three-way merge.
The changed lines are identical to the original account change, and the three upstream
changes stay absent. 389 backend tests: three failures, two for the absent shop-PC guard and
one backup-export test that fails the same way without the change. Rehearsed dry run, apply
and rollback on a local copy. The user ran the dry run (passed), the sudo apply and a
console-only restart. Console and receiver both active; receiver not restarted. Backend
package and merge are in the private People worktree (`dist/accounts-update-20261029`),
not in this repository. The app side becomes 1.6.0 (10).

**29 October 2026 — Claude (1.6.0 accounts and leavers, Windows PC):** The user confirmed
the site works after the server update and asked to build 1.6.0.
- Cherry-picked the 20 October accounts commit (`70495a6`) onto `main` after 1.5.0, in the
  build worktree (branch `accounts-1.6.0`). `MainActivity`, `PeopleScreen` and
  `EditPersonFlow` merged cleanly with the Reporter updates and Who's in changes.
- Conflicts resolved: version 1.6.0 (10); the accounts release notes moved from
  `changelogs/8.txt` (kept as 1.4.0's) to `10.txt`, without the "needs the server update"
  line; README; both contract tests kept in `test_mobile_login`.
- Checks: lint "No issues found"; 48 policy checks, export checks; `test_mobile_login` (9),
  `test_mobile_updates` (6), amendment tests (5). They ran against a scratch copy of the
  installed server: the account branch's console with the rebuilt `adms_console.py`.
- Emulator (debug build) against a synthetic console from the same code. The demo has
  `ADMS_ADMIN_TOKEN` set, so its localhost no-login master mode is off.
  - Staff: Account shows Change password and Delete my account. After an admin reset, signing
    in with the temporary password opens "Choose your own password". Saving it goes Home.
  - Delete my account with the password signs out and says the log is kept.
  - Admin: a clock password was set in Edit details ("Sent to 1 clock").
  - Delete from People for a leaver removed them from the Left list.
  - Who's in by department still works.
  - Typing note: with the keyboard open the footer button rides up over the third field.
- Signed release: same certificate `3e3a0931…`, aapt 10 / 1.6.0. APK SHA-256
  `dd0e303c…69a653`. Copied to `dist/1.6.0/` here and in the main checkout.
- Released at the user's request: pushed `main` (`3388db0..7483de1`); `tools/fdroid_repo.py
  publish --push` from the build worktree, giving `koenekt-fdroid` `59028fe`. The live index
  lists 1.6.0 (10) with the APK hash `dd0e303c…`. Copied to Google Drive (hash checked).

**29 October 2026 — Claude (Google Play listing, 1.6.1 workspace choice):** The user registered
a Play developer account (organisation, D-U-N-S verified) and asked to publish with screenshots.
- Play Console app "Koenekt" (`za.co.koenekt.app`) created by the user. Website verified through
  Search Console (koenekt.co.za already had Google's DNS record); phones verified by the user.
- Filled and saved, not sent for review: store text, category Business, contact details, privacy
  policy, ads (none), advertising ID (no), government (no), financial (none), health (none), Data
  safety (draft: name, user IDs, other personal info, other financial info, files and docs, app
  interactions; collected, not shared, encrypted in transit, deletion URL).
- Remaining in Play: Sign in details (reviewer login), Target audience, Content rating, Data
  safety final save, graphics and AAB upload (the browser pane cannot upload local files),
  production release and Send for review (the user clicks).
- Store graphics in ignored `dist/play-store/`: 6 screenshots 1080x1920 from the emulator with
  synthetic Demo Bakery data, the F-Droid icon (as RGBA) and feature graphic, and the AAB.
- Reviewer data must not be a client's: `people.koenekt.co.za` is the Chamakala/Five workspace.
  The user chose a separate demo workspace (admin site → Customers → New customer, e.g. `demo`).
  The app could only switch workspace through a setup link, so 1.6.1 (11) adds Workspace ·
  Change on the sign-in screen. Built, signed (`3e3a0931…`, APK `2adb5fc9…`), lint clean, checks
  pass, tried on the emulator. Branch `workspace-1.6.1` in the build worktree; not pushed.

**29 October 2026 — Codex (Play continuation, in progress):**
- Fetched and inspected Git. Authoritative main is `e152430`; Claude's newer 1.6.1
  commit is `e79d301` on the existing build worktree's `workspace-1.6.1` branch.
  The stale OneDrive checkout and its old unpushed accounts commits were left intact.
- Locally ran `bundleRelease lintDebug` successfully; lint reports "No issues found".
  48 Java policy checks, JS export checks and five amendment tests passed. Verified
  APK signature (existing certificate `3e3a0931…501d75`), package `za.co.koenekt.app`,
  version 11 / 1.6.1, minimum API 26 and target 36; AAB JAR signature verifies.
- Upload AAB is `dist/play-store/Koenekt-1.6.1.aab` in the main checkout; matches
  `dist/1.6.1/Koenekt-1.6.1.aab` and its recorded SHA-256
  `d9f3c22512f3a00ae81e86ab56c8316d16706453ead18b67efd895c852e5c582`.
  Checked all eight PNG dimensions: 512x512 icon, 1024x500 feature graphic and
  six 1080x1920 screenshots. No new emulator/phone or backend integration run.
- Owner signed into Play Console in the in-app browser. Uploaded the icon, feature
  graphic and all six synthetic-data screenshots; saved Default store listing,
  verified "Ready to send for review". Original logo/rendered app screenshots do
  not use AI image generation. Content rating completed after the owner explicitly
  approved IARC Terms of Use: ESRB Everyone, PEGI 3, IARC 3+ (distinct from 18+ audience).
- Owner confirmed the separate `demo` workspace and registered `play.reviewer`.
  Set Demo Shop only; custom permissions: dashboard, people_read, people_edit,
  people_hire and people_leave. Reviewer sign-in, People (four synthetic staff)
  and Live (two departments) verified in the browser. No reviewer credentials in Git.
- Added Naledi Demo and Sipho Example as synthetic staff with Save only; no clock
  commands. No production-client data changes, service restarts or server deployment.
- Play refuses to save Sign-in details without the full-feature-access confirmation.
  Current reviewer lacks Reports, Payroll and Settings; asked the owner whether to
  expand to available demo administrator permissions. Demo currently reports a
  30-day licence grace period; asked the owner to issue lasting demo review access.
- Prepared sign-in instructions with Workspace · Change → demo and the owner's
  existing reviewer credentials in Console only; full-access box remains unticked.
  Data safety draft retains Claude's six categories; also selected the ordinary
  Username and password account method alongside password plus other authentication.
  Final save is blocked until target audience is completed; audience depends on
  Sign-in details. No false full-access declaration submitted.
- Created production release draft and started uploading the signed 1.6.1 bundle,
  with English release notes. Play accepted code 11 / 1.6.1, minimum API 26,
  target 36. Selected South Africa for the initial production country, which
  resolved the only blocking release error. The remaining deobfuscation warning
  is expected (`minifyEnabled false`). Saved the release to Publishing overview;
  this does not send it for review. The owner requested to click Send for review
  themselves. Reordered the six listing screenshots to 01 through 06 and saved.
- Publishing overview lists eight changes, including the production release,
  country and listing. Quick checks are running (up to 14 minutes shown initially).
  Its three reported blockers are missing Sign-in details, Target audience and
  Data safety. Sign-in details must be finished first to unlock Target audience.
  Managed publishing is off:
  once submitted, approval will publish the release automatically.
- Updated `PLAY-STORE-DRAFT.md` and corrected stale deletion/device-testing text
  in README. Keep scratch proof/screenshots in ignored `build/play-upload/`.
- Exact next action: finish demo review access with the owner's answer, save
  Sign-in details, set 18+ audience and final Data safety save; verify Play accepted
  code 11, resolve release validation errors, and leave the review-ready result for
  the owner. 1.6.1 is built/signed and previously emulator-tested by Claude; physical
  phone testing and Play review/publication remain unconfirmed. The accounts server
  update was already installed by the owner before this session.
- At handoff: pending owner approval to expand the review account beyond its five
  permissions, and pending lasting demo licence. Do not tick the full-feature
  confirmation while access is restricted. Reviewer password is supplied privately
  in the Console form; never copy it into notes. Preserved browser tabs: Publishing
  overview, Sign-in details (prepared form), Data safety (saved draft), Demo Shop
  Users and the verified reviewer Live screen. Quick checks were still running
  (up to 10 minutes remaining), so recheck their final result before submission.
  No change has been sent for review. 1.6.1 source/F-Droid/Drive release stays held;
  only shared handoff documentation is to be pushed from the main checkout.

**30 October 2026 — Codex (Play preparation complete; owner submission next):**
- The owner approved expanding the demo reviewer to the available Demo Shop
  administrator permissions and confirmed the licence was issued. Signed in as
  Demo Admin using the supplied existing demo credentials. Set the reviewer to
  Head office admin (12/13 permissions), keeping company access at Demo Shop only.
  Clock control is not held by Demo Admin and was not granted or exercised.
- Users → Check now retrieved the issued licence. Verified Permanent,
  complimentary and Never expires, replacing the 29-day grace-period message.
  No new licence issued here, no service restart or backend deployment.
- Signed in again as the reviewer; verified Reports and Payroll open, the Payroll
  company selector is Demo Shop only, and the expanded navigation is present.
  No payroll calculation, clock commands, account deletion or data edits used
  as tests. No real customer records were accessed or changed.
- Saved Sign-in details with the verified reviewer credentials in Play Console
  only, Workspace · Change → demo, no authenticator requirement, device-local
  PIN/biometric instructions and the synthetic workspace's administrator access.
  Optional Google/partner-device feedback testing was left off; the credentials
  remain available for the required Play review. No credentials saved in Git.
- Saved Target audience as 18 and over. Final-saved the Data safety questionnaire
  after confirming its existing account/encryption/deletion answers and six data
  categories. Each save was confirmed by Console. Content rating and the uploaded
  listing/release remain as prepared on 29 October.
- Publishing overview now has an enabled "Submit 10 changes for review" button,
  with production 1.6.1 and South Africa, the store listing, adult audience and
  Data safety present. Sign-in details appears under "What you've told us".
  No current blocking issues shown. Quick checks restarted and were still running
  (up to 14 minutes shown); this is not a claim that they passed or review completed.
- Exact next action: the owner opens the preserved Publishing overview tab and
  clicks Submit 10 changes for review. Console says changes will be sent when
  quick checks succeed; address any new issues it reports. Managed publishing
  remains off, so approval will automatically publish the release in South Africa.
  No review submission was clicked by Codex.
- Checks this session: Git fetch/status/diff and browser verification of the licence,
  permissions, reviewer sign-in, Reports/Payroll, saved declarations and enabled
  submission control. No app source changes, build/test rerun or phone test;
  29 October's successful local checks still apply to the unchanged bundle.
- Local release files remain `dist/1.6.1/` and `dist/play-store/` in the main checkout.
  Proof is in ignored `build/play-upload/ready-for-review-20261030.png` and
  `demo-admin-licence.png`. 1.6.1 source/F-Droid/Drive publication stays held.

**30 October 2026 — Claude (1.6.1 release, Windows PC):** The owner submitted the Play
changes; Publishing overview shows "Changes in review" for production 1.6.1 in South Africa
with the listing, declarations and Sign-in details. The user then asked to push and publish.
- GitHub `main` had only Codex's handoff commits (`10c9f48`, `8cd5929`), whose handoff text
  was identical to the build worktree's. Merged them with `-s ours` (`c85a2dd`, no content
  change) and pushed `8cd5929..c85a2dd`, bringing `e79d301` (1.6.1 code) and Codex's
  Play-notes commits to `main`. Scanned the pushed docs: no reviewer password or other secret.
- `tools/fdroid_repo.py publish --push` from the build worktree, giving `koenekt-fdroid`
  `cba4d63`. The live index lists 1.6.1 (11) with APK hash `2adb5fc9…e0d0`.
- Copied the same APK to `G:\My Drive\Koenekt Android\` (hash checked) and `dist/1.6.1/` to
  the main checkout, which was fast-forwarded.
- Watch for: Google may try Delete my account during review; if `play.reviewer` disappears,
  recreate it (Demo Shop head-office access) before the next submission. Play-signed and
  F-Droid/Drive installs use different signing keys, so a phone cannot update across them.

**3 November 2026 — Claude:** Play review passed. The public listing for
`za.co.koenekt.app` shows Koenekt with an Install button in South Africa (0+ downloads), and
IARC sent a live rating notice. Play Console itself had signed out in the browser pane, so
the Console status page was not re-read.

**3 November 2026 — Codex (public website review only):**
- Reviewed the public homepage (including Services, Work, About and Contact),
  pricing, new-store quote, downloads, callout and privacy pages at koenekt.co.za
  in the native browser, using desktop and phone-sized layouts. Client Login
  opens two "coming soon" entries; phone navigation hides Services, Work,
  Pricing, New store and Downloads without a replacement menu.
- Main design opportunities: installation photos and illustrated job stories,
  People/Reporter previews using synthetic data, a shorter homepage, clearer
  product enquiry buttons, and a friendlier introduction to the six-section
  new-store form. Website recommendations only; no website source changed.
- Browser checks: pricing calculator changed 20 km to R550; a public-landmark
  callout lookup returned an address, route, R550 callout and R1,200 total with
  one hour labour. No booking, enquiry, message or install was submitted.
- Verified the public Google Play listing has an Install button and app
  screenshots; website Downloads still says "Google Play coming soon".
- Android remains 1.6.1 (11), built/signed and published as recorded above;
  no Android build, test, physical-device check or server operation this session.
  Existing release files remain in the primary checkout at `dist/1.6.1/` and
  `dist/play-store/`. Existing server installations are unchanged.
- Exact next action: in the website repository, add a mobile navigation menu,
  update Downloads to link the live Play listing, resolve the Client Login
  placeholders, then prepare a homepage revision with installation photos and
  software previews. No website implementation or deployment was requested here.
- Reviewed and committed this handoff in an isolated worktree from origin/main;
  preserved the pre-existing local Android planning edits and stale OneDrive
  checkout. Publication of this documentation is separate from an app release.

**3 November 2026 — Claude (planning only, Windows PC):** The owner asked for "My stores": pick
a store, see live sales from its tills, history from the back office and total stock, inside
this app. Planned only, with no code change here; see Next actions 0000. Recommended native
`StoresScreen` / `StoreScreen` built with `Ui.java` like `LiveScreen`, a `TAB_STORES` bottom-bar
tab, a third sign-in against the Reporter relay, and a demo relay for development and the Play
reviewer. A mockup was shown in chat and not saved.

**3 November 2026 — Claude (My stores, Windows PC):** Built the My stores screens; no release, version
still 1.6.1 (11). Sign-in form also tries the Link relay (a refusal there is silent, most People users
have no store login); store-only logins get Home/Stores/Account; the relay token sits in the same
device-bound `SessionVault` JSON (`link`, `link_user`, `link_role`), is checked with `GET /api/stores` on
unlock (kept on a network error, dropped on 401), cleared from memory on lock, and logged out at the relay
on Sign out. `AppPolicy.linkAllowed` allows only `/api/login|logout|stores|stores/<id>/live` on the relay
over HTTPS (debug `-PdevLink` emulator host only). Dashboard: tiles chosen on the phone, rows per store,
grouping by store type with type totals and share, sort by any figure, scope chip (all, a type, a store);
layout, store types and hidden stores kept per store login in preferences (no figures stored). Store
screen: Today / Items / Tills (cashier on each till now). Compare: ranking, head to head (winner per row;
fewer is better for refunds, voids, no-sales, overrides), hour-by-hour running totals. Refresh every 60 s
while open, keeps the scroll position, stops when the tab is left or the app locks. Checks run: lint 0
issues; PolicyChecks 64 and new StoreChecks 23 passed; export checks passed; test_server_amendment OK;
test_mobile_login OK against People `feat/account-lifecycle` (it fails against older People branches,
unrelated). Emulator (koenekt-test, API 35, no screen lock, so no remembered sign-in) against a local Link
relay with four made-up stores, People pointed at a dead local port: owner login sees all four, offline PC
shown, one till not read shown, types grouped, compare store vs store and type vs type, store tills tab;
staff login sees no GP anywhere. Not tested: a real shop PC, fingerprint unlock with a saved store
session, a physical phone.

**5 November 2026 — Claude (screenshots switch, Windows PC):** The owner said screenshots and screen
recording don't work in the APK (FLAG_SECURE on release builds, there for POPIA: People shows ID numbers,
pay and payroll). Asked him to choose between a per-phone switch, always allowing, or keeping it blocked; he
chose the per-phone switch. Built: Account → "Allow screenshots on this phone", off by default, saved in this
phone's preferences (`allow_screenshots`; backups are off, so it never moves to another phone; it survives
sign-out). Turning it on shows a warning (gallery, other apps and backups can reach screenshots), then asks for
the phone's lock (BiometricPrompt strong biometric or device credential; Android 8 the KeyguardManager PIN
screen); turning it off needs nothing. One window flag covers screenshots, recording and recent-app previews,
so all three follow the switch. `AppPolicy.protectScreen(secureBuild, allowed)` is the rule; leaving the app
while that PIN prompt is open and then cancelling locks it as leaving normally would; About text mentions the
switch. Debug builds still allow screenshots and hide the switch; new `-PsecureScreens` makes a debug build
behave like release. Version stays 1.6.1 (11); ships with 1.7.0. Checks run: `lintDebug` "No issues found",
`assembleRelease` built (local test output only, not a release), PolicyChecks 68 (4 new), StoreChecks 23.
Emulator (koenekt-test, API 35; debug `-PsecureScreens -PdevLink`, local Link relay with one made-up owner login,
People pointed at a dead port; a test PIN set for the run and cleared afterwards): window flag SECURE and a black
`screencap` while off; warning Cancel and PIN-prompt Back both leave it off with the app still open; PIN
confirms, switch on, flag gone, `screencap` shows the Account screen; still on after force-stop and relaunch;
off again gives a black capture; Home during the PIN prompt then returning asks to unlock Koenekt and the switch
stays off. Not tested: fingerprint, Android 8-10 paths, a screen recording, recent-apps thumbnail, a physical
phone. Committed on local `main`, not pushed.

**5 November 2026 — Claude (1.7.0 for Google Play, Windows PC):** The owner: "push to play store no one
downloaded it yet", then "probably screenshots and a short whats new with old features added".
- 1.7.0 (12) = My stores (`9b2af1f`) + the screenshots switch (`389713e`, was local only) + a plural fix on a
  till's event line ("1 voided line", not "1 voided lines"; `StoreScreen.EVENTS` has a singular column).
- `changelogs/12.txt` (460 bytes): My stores, screenshots switch, then a line of earlier features.
  Short description now "Staff, attendance and live store sales with your Koenekt account." (Play and
  fastlane); full descriptions gained a My stores section and the screenshots line.
- `docs/privacy.html` (effective 5 Nov): My stores row (figures for store logins only; Link keeps an answer
  in memory until the phone has it, at most 15 min, and logs that a request happened, not figures); the
  phone keeps only layout choices; screenshots blocked by default.
- Checks: `assembleRelease bundleRelease lintDebug` (lint "No issues found"), PolicyChecks 68, StoreChecks 23,
  export checks, test_server_amendment OK. Signed with the same upload key `3e3a0931…`; aapt 12 / 1.7.0.
  APK `ca75316e…`, AAB `d3b33c51…` (`dist/1.7.0/SHA256SUMS`; AAB also in `dist/play-store/`). Release APK
  smoke test on the emulator: opens on Sign in, workspace people.koenekt.co.za, window SECURE.
- Screenshots (`dist/play-store/screenshots-1.7.0/`, 1080x1920): 01 home, 02 My stores, 03 a store's Tills,
  04 Compare, 05 Who's in, 06 People, 07 person, 08 Add (01, 05-08 reused from 1.6.1; 03-kitchen dropped
  for Play's limit of 8). The store shots come from a debug build (`-PdevLink=http://10.0.2.2:18198
  -PdevOrigin=http://10.0.2.2:18999`) against the Reporter repo's real `server/link_relay.py` on localhost
  with made-up stores (Northgate, Southside, Main Depot, Station Kiosk) and made-up cashiers; nothing from a
  real shop.
- Play Console (owner's Chrome, william@koenekt.co.za): production release 12 (1.7.0) created with the AAB
  and these release notes (only warning: no deobfuscation file, expected without R8); default listing short
  and full descriptions and the 8 phone screenshots changed (AI asset declaration: "Don't label assets":
  real app captures, icon/banner drawn by script). Publishing overview shows "Submit 4 changes for review"
  (release + short description + full description + screenshots), left for the owner to click. Data safety
  not changed. Console also asks for the trader status declaration (deadline February 2027).
- Play reviewer: My stores needs a store login; the reviewer's People demo login shows no Stores tab, which is
  correct behaviour. No demo store on the real relay yet (would need a pretend shop PC agent running there).

**5 November 2026 — Codex (Apple App Store orientation only):**
- Checked current official Apple enrollment, Xcode, review, privacy, deletion,
  TestFlight and payment documentation. No iOS source, Apple account enrollment,
  purchase, build, submission or server change was performed.
- Main requirements: organization enrollment (D-U-N-S, authority, domain email,
  working website; USD99 per membership year, regional pricing at enrollment),
  access to a supported Mac/Xcode setup and physical iPhone testing, an iOS
  implementation, review credentials with useful synthetic data, store metadata,
  privacy disclosures and applicable account deletion/export-compliance answers.
- Main design risk is review guideline 4.2 (minimum functionality). Recommend
  porting useful People and Stores screens, with iPhone authentication/storage,
  rather than assuming a website wrapper will pass. Existing service APIs and
  design can inform the port; Android binaries cannot serve as the iOS build.
- Payment assessment is conditional: guideline 3.1.3(c) allows qualifying direct
  organization/group subscriptions; consumer/single-user sales have different
  requirements. Own-company login does not itself require Sign in with Apple.
- Sources: https://developer.apple.com/programs/enroll/ and
  https://developer.apple.com/app-store/review/guidelines/ ; supporting Apple
  documentation was linked in the chat answer. Approval is not guaranteed.
- Release state remains Android 1.6.1 (11) published as recorded above; the
  primary checkout has ongoing 1.7.0 (12) work belonging to another session.
  Preserved that work. No new app build/tests/device checks or publication here;
  existing release paths remain `dist/1.6.1/` and `dist/play-store/`.
- Exact next action if the owner chooses iOS: confirm supported Mac/Xcode and
  iPhone access, enroll Koenekt as an organization, then scope the iOS app and
  its synthetic reviewer workspace. This was advice, not an instruction to start.

**5 November 2026 — Codex (iOS VM and language clarification):**
- Owner confirms an iPhone is available. Java remains a supported native Android
  language; the app's Android-specific screens and security need iOS equivalents.
  Google recommends Kotlin for new Android development and still supports Java.
- Checked official Android language support, Apple Simulator documentation and
  macOS Tahoe licence section 2B(iii): macOS development VMs are permitted on
  Apple-branded hosts. A Windows-PC VM is not the licensed Apple-hardware route.
  Remote access to rented physical Mac hardware is an available alternative;
  use the owner's iPhone for final testing, with TestFlight delivery if needed.
- Advisory follow-up only; no VM installation, rental, code/build/test, account,
  server or release action. Current handoff records Android 1.7.0 (12) built/signed
  at `dist/1.7.0/` and pushed, Play submission in progress, not device-tested or on
  F-Droid/Drive; 1.6.1 remains the recorded published version. Other work preserved.
- Exact next action: if proceeding with iOS, select supported Mac access, then
  scope the iOS implementation and use the available iPhone for device validation.

**5 November 2026 — Codex (remote Mac price check):**
- Compared official MacinCloud checkout, Scaleway Apple Silicon pricing/FAQ and
  MacStadium pricing. Advertised starting prices: MacinCloud managed USD25/month
  (usage conditions, no admin) and dedicated USD49/month (base configuration);
  Scaleway M2 16 GB EUR0.17/hour or advertised EUR115/month; MacStadium M4 16 GB
  USD149/month. Confirm final configuration/availability before purchase.
- Scaleway requires 24 hours minimum (M2 EUR4.08) and bills until deletion, even
  when powered off. Xcode is preinstalled per its FAQ. Estimated rand figures
  use Xe mid-market rates checked today: USD/ZAR 16.3362, EUR/ZAR 18.3955;
  exclude applicable tax and card conversion fees. Apple membership is separate.
- Sources: https://checkout.macincloud.com/select ,
  https://www.scaleway.com/en/pricing/apple-silicon/ ,
  https://www.scaleway.com/en/docs/apple-silicon/faq/ and
  https://macstadium.com/pricing . Advice only; no rental/payment/account action.
- Android remains 1.7.0 (12) built/signed at `dist/1.7.0/`, with publication/device
  state as recorded in the header. No new build/tests/server/store action here.
- Exact next action if iOS proceeds: check a 24-hour M2 rental's available
  macOS/Xcode image and desktop access before booking; budget about R75 plus tax
  for that allocation. Preserve project/signing files before releasing a host.
- 5 Nov, later: the owner submitted the 4 changes on Play. Then "push to F-Droid and Drive too":
  `tools/fdroid_repo.py publish --push` from the main checkout gave `koenekt-fdroid` `d57dcf8`; the live index
  lists 1.7.0 (12) with APK hash `ca75316e…`. Same APK copied to `G:\My Drive\Koenekt Android\` (hash checked).

**6 November 2026 — Claude (1.8.0, site look and appearance, Windows PC):** The owner: "we should keep to
design language, please put fonts and the theme and icons, dark, light and system options on the app. show me
mock ups"; approved the mockups (https://claude.ai/artifact/3DKJfKREjHCcF4yYCyHaRb, private to the owner) with
"apply it push and play store and git and my drive".
- Tokens are the website's `site.css` and People branch `koenekt-people-look` `console/static/koenekt-theme.css`
  (light bg #F7F7F4, accent #5FA300 / text #4B8300; dark bg #1B1B1B, accent #83CC17 / text #9AD635).
- `Ui.java`: colours are no longer constants; `Ui.apply(activity, dark)` sets both palettes' values, loads the
  fonts and colours the window and system bars. Old names kept (BRAND = accent text, LIME = accent fill, INK, MUTED,
  LINE, BG, SURFACE, TINT, DANGER, WARN...) plus SURFACE2, BORDER_STRONG, FIELD, TINT_LINE, ON_ACCENT. Every
  `shape()` corner is capped at 2dp. `Ui.title` uses Big Shoulders Display at 22sp and up (x1.18), Plex Sans
  SemiBold below; `Ui.label` and buttons use Plex Mono capitals. Avatars are square tints; a dot in `Ui.OUT` is
  a hollow ring. `Ui.dialog()` replaces all 36 `new AlertDialog.Builder` (KoenektDialog / .Dark styles).
  `Chart.series()` replaces the static SERIES so compare colours follow the mode.
- Fonts in `res/font/` (theme default `@font/ibm_plex_sans`), copied with their OFL licences from the People look
  branch; licences ship in `assets/licences/` and About names them. APK 170 KB -> 509 KB.
- Appearance: Account → Appearance (segmented System / Light / Dark, icons `ic_phone`, `ic_sun`, `ic_moon`),
  saved in the activity preferences as `appearance`. `applyAppearance()` repaints the shell and re-shows the
  current screen without `recreate()` (the unlocked session lives in the activity); a half-filled form or an
  open web page keeps its state and the next screen is in the new colours. `onConfigurationChanged` follows the
  phone's dark mode when System is chosen (the manifest already handles uiMode). Pale `ic_logo_pale` and
  `ic_brand_mark_pale` for dark.
- Icons: the 14 line icons now use square caps, miter joins, 1.75 stroke.
- Checks: `assembleRelease bundleRelease lintDebug` (lint "No issues found"), PolicyChecks 68, StoreChecks 23,
  export checks, test_server_amendment OK. Same upload key `3e3a0931…`, aapt 13 / 1.8.0; APK `f8bf9420…`,
  AAB `4fc24624…` (`dist/1.8.0/SHA256SUMS`).
- Emulator (koenekt-test, debug against the local Link relay with made-up stores and a local People console
  run from `C:\Projects\koenekt-people` with a temporary ADMS_HOME of synthetic staff): sign-in, Home, People,
  person, Add employee, Who's in, My stores, store tills, Compare, Account in light and dark; Appearance switches
  repaint at once; with System, `cmd uimode night yes/no` flips the app both ways. Fixed on the way: nav labels lost
  their capitals to `setSingleLine` (now `setMaxLines(1)`), segment labels centred, "Tools" heading as a label,
  compare column headings back to plain text (mono capitals broke "Southside"). Release APK: opens on Sign in,
  window SECURE, survives a night-mode switch with no crash.
- Play screenshots `dist/play-store/screenshots-1.8.0/` (1080x1920): 01 Home, 02 My stores, 03 store tills,
  04 Compare (dark), 05 Who's in, 06 People, 07 person, 08 Add employee. All names and figures made up.
- Not tested: a physical phone, Android 8-10, the WebView pages (People's own pages keep their own theme switch).
- Play Console (owner's Chrome, william@koenekt.co.za): 1.7.0 had been published on 5 November. Production release
  13 (1.8.0) created with the AAB and the 13.txt notes (only warning: no deobfuscation file). Listing: the 8 phone
  screenshots replaced (uploaded as v18-*.png, ordered as above), full description gained "Light, dark or follow
  your phone."; AI asset declaration "Don't label assets". Publishing overview shows "Submit 3 changes for review",
  left for the owner. APK copied to G:\My Drive\Koenekt Android\ (hash checked). F-Droid not updated.

**9 November 2026 — Claude (1.9.0 till pages and release, Windows PC):** The owner: "you know what we did on the
website for the tills. we need that, we also need the qr joining. lets roll it".
- The QR/code joining was already built as 1.9.0 (`c58729a`). Added till pages to it: `StoreScreen.tillPage` opens
  a till from the Tills tab when the answer carries `tills[].detail` (Reporter 1.9.105, `bdeb6d0`); tiles (Sales,
  Receipts, Average basket, GP % for owners or Last sale for staff), "On this till" (cashier now, worked it today,
  events), then Today (by hour, payments, refunds and money out, "On this till today") and Items (names and
  departments looked up by item number in the store's items; departments from the detail). `StoreMath.qty` keeps
  weighed quantities' decimals in every items list. Staff answers have GP stripped by the PC and the relay, per till too.
- Checks: lint "No issues found", PolicyChecks 109, StoreChecks 25 (2 new: qty), export checks, test_server_amendment OK.
  Same key `3e3a0931…`; 1.9.0 (15) APK `a99b816a…`, AAB `7cda0b0c…`.
- Emulator (debug against the Reporter repo's real relay on localhost, made-up shops whose pretend PCs now also send
  per-till detail as 1.9.105 does): Southside → Tills → Open this till → Till 1 page, Today and Items (a weighed item
  "7.708 sold"), Back to the Tills tab. Release APK opens on Sign in with Join my stores with a code; no crash.
- Rollout state checked: website till pages are live (srv1 `portal.js` = local); Reporter 1.9.105 is uploaded and
  MEGAFRESH (pilot) runs it; `update_admin.py promote rel_1.9.105_105` was refused for the agent by the auto-mode
  check (production deploy), so the owner runs it. Link relay on srv1 is still the old one (join endpoints 404).
- Play Console (owner's Chrome): production release 15 (1.9.0) created with the AAB and the 15.txt notes (only warning:
  no deobfuscation file); it replaces held draft 14 (1.8.1). Publishing overview: "Submit 1 change for review", left
  for the owner, to click only after the relay bundle 20261109c is installed on srv1. Listing and screenshots unchanged.
  APK copied to Google Drive (hash checked).
