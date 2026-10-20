# Shared working rules — Koenekt Android

This is the authoritative Android app repository at `C:\Projects\koenekt-android`.
GitHub: `https://github.com/loudalley/koenekt-android` (public), branch `main`.
It was extracted from the private People repository on 20 October 2026. Do not
resume Android development in its former `koenekt-people/android-app` location.

Codex, Cursor and Claude share this file and `README-HANDOFF.md`. Claude's
`CLAUDE.md` and Cursor's `.cursor/rules/koenekt-handoff.mdc` are pointers here.

1. Read the handoff first, fetch and inspect Git status/diff before edits. Preserve
   other work; do not reset, force-push or overwrite changes to get a clean tree.
2. Work in this repository. Keep scratch/build files under ignored `build/` or
   `dist/`; keep paths relative in source and public docs.
3. Update the shared handoff before returning work, including orientation-only
   sessions. State current version, changes, checks actually run, release paths,
   unresolved work and the exact next action. Distinguish built, device-tested,
   server-installed and store-published states. Commit reviewed changes and push
   to the existing remote; say explicitly if pushing fails.
4. This repo is public. Never commit private upload keys, signing passwords,
   service credentials, tokens, customer/staff data, local SDK config, backend
   checkouts/history, internal deployment notes or build output. Inspect the
   staged file list before committing. `.signing/` is local and ignored.
5. Preserve the existing Android upload key locally; do not run key creation for
   a routine update. The key-creation script refuses replacement. No private key
   belongs in a source archive or GitHub Release. Increment versionCode for a new
   distributed build; retain application ID `za.co.koenekt.app`.
6. People and licence/update services enforce actual permissions. Device unlock
   must not bypass server checks. Never store passwords, OTPs or raw biometrics;
   keep session tokens encrypted with device-authentication-bound Keystore keys.
7. The server amendment is prepared, NOT installed. The user asked for
   instructions rather than live deployment. Do not restart clocks/receivers,
   enable drain, alter production records or send Reporter update commands as
   tests. Use synthetic data. Do not weaken password/MFA/replay checks.
8. Run checks appropriate to changes. The app has not been device-tested; Android
   build/lint and JVM/Python checks are not proof of fingerprint/PIN behaviour.

Build/check commands and optional backend integration setup are in README.md.
There is no configured automation/CI deployment or Play publication workflow.
