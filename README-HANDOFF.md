# Koenekt Android — shared handoff for Codex, Cursor and Claude

Last updated: **20 October 2026, Codex — extracted to a standalone public
repository, version 1.0.1 (2). App built/signed; no device test, server install or
Play publication has occurred.**

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
- Existing signed release files are kept locally under `dist/1.0.1/` (ignored).
  Build products are under `app/build/outputs/`. Local SDK configuration is
  ignored. A clean clone must configure its own SDK and signing credentials.

## Implemented behaviour

One native sign-in and permissions-based home opens the current People,
reports/live/payroll/clocks/users/settings screens. A successful independent
licence-service login enables customers, licences and Reporter update controls.
Clients receive only their server-granted company/role permissions.

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
source, writes a backup and never restarts services itself. The user requested
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

The extraction changes only repository layout, portable tests and documentation;
app runtime source is unchanged. In the new checkout, release APK/AAB build and
lint passed; 39 policy checks, JS export checks, 4 portable amendment tests and
6 optional backend contract tests all passed. No real Android fingerprint/PIN/Keystore,
file-provider or lifecycle execution has been verified. A previous emulator-start
attempt was blocked by automatic approval review; no retry was made as part of
the repository move.

Next actions for Cursor/Claude:

1. Open this repository and read AGENTS.md, then inspect actual Git state.
2. Follow the server amendment guide in the authorised deployment context; the
   current user request authorised instructions only, not a live installation.
3. On a test phone, install/upgrade the signed app; verify initial login, biometric
   and PIN success/cancel, background/process restart, changed device lock,
   permissions, report/import picker return, expiry/revocation and sign-out.
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
