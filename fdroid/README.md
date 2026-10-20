# Koenekt F-Droid repository

Koenekt publishes its own F-Droid repository on GitHub Pages:

- Repository: `https://loudalley.github.io/koenekt-fdroid/repo`
- Signing-key fingerprint (SHA-256):
  `C6F6570B4A817C6C3DAE3A4699BA87BB925C2CF68F5858C8754A1C88D231577F`
- Published files: [loudalley/koenekt-fdroid](https://github.com/loudalley/koenekt-fdroid)
  (generated; never edit by hand)

**On a phone:** open the repository address in the phone's browser and tap the
F-Droid link or scan its QR code, or add the address under F-Droid → Settings →
Repositories. Updates then show up in F-Droid automatically. F-Droid's
"non-free network" label appears because the app needs a Koenekt account.

The APK is the same signed release as `dist/`, using the existing upload key. It
updates a directly installed Koenekt app in place. A Play Store install has a
different signature and must be uninstalled first.

## What is where

| Item | Location | Public |
| --- | --- | --- |
| Title, descriptions, release notes, icon, banner | `fastlane/metadata/android/en-US/` | yes |
| App metadata (author, links, licence, anti-features) | `fdroid/za.co.koenekt.app.yml` | yes |
| Repo signing key, passwords, fdroid working folder | `.signing/fdroid/` | **no** (ignored) |
| Published index and APKs | `../koenekt-fdroid` checkout → GitHub Pages | yes |

Back up `.signing/fdroid/` with the rest of `.signing/`. If the repo key is lost,
phones must remove and re-add the repository.

## Publish a new version

1. Bump `versionCode`/`versionName`, build and sign as in `README.md`, and copy the
   APK to `dist/<version>/Koenekt-<version>.apk`.
2. Write the release notes, at most 500 characters, in
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Commit, then run:

   ```powershell
   python tools/fdroid_repo.py publish          # build the signed index and mirror it; no push
   python tools/fdroid_repo.py publish --push   # commit and push ../koenekt-fdroid
   ```

The script refuses if the APK or release notes are missing. The `Builds` entry it
adds records the current commit of this repository, so commit first.

## One-time setup on a new release machine

- `python -m venv build/fdroid-venv` then `build/fdroid-venv/Scripts/python -m pip install fdroidserver`
  (tested with 2.4.5).
- Restore `.signing/fdroid/` from backup. Run `python tools/fdroid_repo.py init`
  only for a brand-new repository; it refuses to replace an existing key.
- Clone `loudalley/koenekt-fdroid` next to this repository as `../koenekt-fdroid`.

**Windows notes:**
- fdroidserver officially targets Linux. The script works round its two Windows
  problems: it points `config.yml` at `apksigner.bat`, and it runs `fdroid` with
  forward-slash paths from `glob` and `os.walk`.
- The "unsafe permissions on config.yml" warning is a Unix file-mode check. On
  Windows, `.signing/` is restricted to the current user.
- `status/` holds local tool paths, so the script does not publish it.
- `* -text` in the site's `.gitattributes` keeps Git from changing the signed
  index bytes.
