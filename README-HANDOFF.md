# Koenekt Android — shared handoff for Codex, Cursor and Claude

Last updated: **20 October 2026, Claude — version 1.0.2 (3): patcher safety
check plus two app lifecycle fixes. App built/signed; no device test, server
install or Play publication has occurred.**

## Repository and current state

- Authoritative checkout: `C:\Projects\koenekt-android`.
- Public remote: `https://github.com/loudalley/koenekt-android`, branch `main`.
- This repository contains Android source, synthetic tests, public server
  amendment tooling/instructions and shared agent notes. It has fresh Git
  history; the private People backend and its history were not imported.
- Previous Android code was in `koenekt-people/android-app` on a private feature
  branch. That location is retired. The People backend amendment remains in the
  separate backend repository, prepared/tested and not deployed.
- Local Android upload credentials moved with the app into ignored/restricted
  `.signing/`; their certificate matches the existing 1.0.0/1.0.1 APKs. Never
  regenerate them for an ordinary update, commit them or include them in releases.
- Signed release files are kept locally under `dist/1.0.2/` (ignored; APK, AAB
  and SHA256SUMS). `dist/1.0.1/` is older; its server-amendment ZIP holds the
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

## Server work — instructions only

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
previously an older server would accept the patch and then fail every login. The user requested
instructions, not deployment. No server has been amended/restarted. Sign out/in
once after applying the amendment to get a new longer token. Rollback does not
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

1. Open this repository and read AGENTS.md, then inspect actual Git state.
2. Apply the server amendment on srv1 using `SERVER-AMENDMENT.md` and this
   checkout's patcher (not the 1.0.1 ZIP copy). The user asked Claude on
   20 October to apply it, but Claude's SSH probe of srv1 was blocked by the
   auto-mode permission policy, so nothing ran on the server. Next step: the user
   runs the guide's dry run/apply/console-only restart, or allows srv1 SSH for an
   agent. Start with the dry run; stop if it refuses.
3. On a test phone, install/upgrade the signed 1.0.2 app; verify initial login, biometric
   and PIN success/cancel, background/process restart, changed device lock,
   permissions, report/import picker return, expiry/revocation and sign-out.
   Also check 1.0.2: unlock returns to the open page; with no screen lock,
   import/save/print no longer signs out, but leaving via Home still does.
4. Complete privacy-policy URL, account-deletion request paths, support/developer
   details, review access, actual-device screenshots and an internal Play test
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
