#!/usr/bin/env python3
"""Self-hosted Koenekt F-Droid repository (binary repo on GitHub Pages).

  init     Once per release machine: create the repo signing key and config in
           .signing/fdroid/. Refuses to replace an existing key.
  publish  Add the signed dist/<version>/Koenekt-<version>.apk with its fastlane
           release notes, re-sign the index and mirror repo/ to the Pages checkout.
           Pushes only with --push.

The repo key and its passwords stay in ignored .signing/fdroid/; only the signed
index, APKs and icons are published. See fdroid/README.md.
"""
import argparse
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / '.signing' / 'fdroid'
APP_ID = 'za.co.koenekt.app'
REPO_URL = 'https://loudalley.github.io/koenekt-fdroid/repo'
DEFAULT_SITE = ROOT.parent / 'koenekt-fdroid'
FDROID = ROOT / 'build' / 'fdroid-venv' / ('Scripts/fdroid.exe' if os.name == 'nt' else 'bin/fdroid')


def sdk_path():
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    props = ROOT / 'local.properties'
    if not sdk and props.exists():
        match = re.search(r'^sdk\.dir=(.+)$', props.read_text(encoding='utf-8'), re.M)
        if match: sdk = match.group(1).replace('\\:', ':').replace('\\\\', '\\').strip()
    if not sdk or not Path(sdk).is_dir(): sys.exit('Set ANDROID_HOME or sdk.dir in local.properties.')
    return Path(sdk)


def apksigner(sdk):
    # fdroidserver only looks for a bare "apksigner"; on Windows it is apksigner.bat.
    tools = sorted((sdk / 'build-tools').iterdir(), reverse=True)
    for folder in tools:
        for name in ('apksigner', 'apksigner.bat'):
            if (folder / name).is_file(): return folder / name
    sys.exit('apksigner not found in the Android SDK build-tools.')


def app_version():
    gradle = (ROOT / 'app' / 'build.gradle').read_text(encoding='utf-8')
    return re.search(r"versionName '([^']+)'", gradle).group(1), int(re.search(r'versionCode (\d+)', gradle).group(1))


def init(_args):
    config = WORK / 'config.yml'
    if config.exists() or (WORK / 'keystore.p12').exists():
        sys.exit('F-Droid repo key already exists in .signing/fdroid; it must never be replaced.')
    sdk = sdk_path()
    keytool = Path(os.environ['JAVA_HOME']) / 'bin' / ('keytool.exe' if os.name == 'nt' else 'keytool') if os.environ.get('JAVA_HOME') else 'keytool'
    WORK.mkdir(parents=True, exist_ok=True)
    password = secrets.token_urlsafe(32)
    dname = 'CN=Koenekt (Pty) Ltd, O=Koenekt (Pty) Ltd, C=ZA'
    subprocess.run([str(keytool), '-genkeypair', '-noprompt', '-storetype', 'PKCS12', '-keystore', str(WORK / 'keystore.p12'),
                    '-alias', 'koenekt-fdroid', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10000',
                    '-dname', dname, '-storepass:env', 'FDROID_KEY_PASS'],
                   check=True, env={**os.environ, 'FDROID_KEY_PASS': password})
    shutil.copy2(ROOT / 'fastlane/metadata/android/en-US/images/icon.png', WORK / 'icon.png')
    config.write_text(f"""# Private: contains the repo signing passwords. Never commit or publish.
repo_url: {REPO_URL}
repo_name: Koenekt
repo_description: >-
  Android apps from Koenekt (Pty) Ltd. Add this repository in F-Droid to receive
  Koenekt app updates. Privacy policy:
  https://loudalley.github.io/koenekt-android/privacy.html
repo_icon: icon.png
archive_older: 3
sdk_path: {sdk.as_posix()}
apksigner: {apksigner(sdk).as_posix()}
repo_keyalias: koenekt-fdroid
keystore: keystore.p12
keystorepass: {password}
keypass: {password}
keydname: {dname}
""", encoding='utf-8')
    print('Created the F-Droid repo key and config in .signing/fdroid/. Back this folder up with .signing/.')


# fdroidserver splits glob/os.walk results on '/', which breaks on Windows backslash
# paths. Windows accepts '/', so return those two functions' paths in that form.
WINDOWS_PATHS = '''
import glob, os, sys
_glob, _iglob, _walk = glob.glob, glob.iglob, os.walk
glob.glob = lambda *a, **k: [p.replace('\\\\', '/') for p in _glob(*a, **k)]
glob.iglob = lambda *a, **k: (p.replace('\\\\', '/') for p in _iglob(*a, **k))
def walk(*a, **k):
    for root, dirs, files in _walk(*a, **k): yield root.replace('\\\\', '/'), dirs, files
os.walk = walk
from fdroidserver.__main__ import main
sys.argv = ['fdroid'] + sys.argv[1:]
main()
'''


def run_fdroid(*args):
    env = {**os.environ, 'ANDROID_HOME': str(sdk_path())}
    if os.name == 'nt':
        command = [str(FDROID.with_name('python.exe')), '-c', WINDOWS_PATHS, *args]
    else:
        command = [str(FDROID), *args]
    subprocess.run(command, cwd=WORK, check=True, env=env)


def publish(args):
    if not (WORK / 'config.yml').exists(): sys.exit('Run "init" first.')
    name, code = app_version()
    apk = ROOT / 'dist' / name / f'Koenekt-{name}.apk'
    fastlane = ROOT / 'fastlane' / 'metadata' / 'android'
    notes = fastlane / 'en-US' / 'changelogs' / f'{code}.txt'
    if not apk.is_file(): sys.exit(f'Missing {apk.relative_to(ROOT)}; build and copy the signed release first.')
    if not notes.is_file(): sys.exit(f'Missing release notes {notes.relative_to(ROOT)}.')
    for folder in ('repo', 'metadata'): (WORK / folder).mkdir(exist_ok=True)
    shutil.copy2(apk, WORK / 'repo' / f'{APP_ID}_{code}.apk')
    # Release notes (changelogs/<code>.txt) only attach when the metadata names the current
    # version and a matching Builds entry. Never use "disable": update then deletes the APK.
    commit = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()
    metadata = (ROOT / 'fdroid' / f'{APP_ID}.yml').read_text(encoding='utf-8').rstrip('\n')
    metadata += (f"\nCurrentVersion: '{name}'\nCurrentVersionCode: {code}\n"
                 f"Builds:\n  - versionName: '{name}'\n    versionCode: {code}\n    commit: {commit}\n")
    (WORK / 'metadata' / f'{APP_ID}.yml').write_text(metadata, encoding='utf-8')
    localized = WORK / 'metadata' / APP_ID
    if localized.exists(): shutil.rmtree(localized)
    shutil.copytree(fastlane, localized)
    run_fdroid('update', '--verbose' if args.verbose else '--quiet')

    site = Path(args.site).resolve()
    if not (site / '.git').is_dir(): sys.exit(f'{site} is not a Git checkout of the Pages repository.')
    for folder in ('repo', 'archive'):
        if (site / folder).exists(): shutil.rmtree(site / folder)
        # status/ holds local tool paths and is not used by F-Droid clients.
        if (WORK / folder).exists(): shutil.copytree(WORK / folder, site / folder, ignore=shutil.ignore_patterns('*.idsig', 'status'))
    (site / '.nojekyll').touch()
    # entry.json signs the exact bytes of the index; Git must never rewrite line endings.
    (site / '.gitattributes').write_text('* -text\n', encoding='utf-8')
    # The site root forwards to fdroidserver's repo page (QR code and "add repository" link).
    (site / 'index.html').write_text('<!doctype html><meta charset="utf-8"><title>Koenekt F-Droid repository</title>'
                                     '<meta http-equiv="refresh" content="0; url=repo/">'
                                     '<p><a href="repo/">Koenekt F-Droid repository</a></p>\n', encoding='utf-8')
    (site / 'README.md').write_text(f'# Koenekt F-Droid repository\n\nAdd `{REPO_URL}` in the F-Droid app, or open '
                                    f'{REPO_URL}/ on the phone and use its link or QR code.\n\nGenerated by '
                                    '`tools/fdroid_repo.py` in https://github.com/loudalley/koenekt-android; '
                                    'do not edit by hand.\n', encoding='utf-8')
    print(f'Mirrored the signed repo for Koenekt {name} ({code}) to {site}.')
    if args.push:
        subprocess.run(['git', 'add', '-A'], cwd=site, check=True)
        if subprocess.run(['git', 'diff', '--cached', '--quiet'], cwd=site).returncode == 0:
            print('Nothing changed; not pushing.'); return
        subprocess.run(['git', 'commit', '-q', '-m', f'Publish Koenekt {name} ({code})'], cwd=site, check=True)
        subprocess.run(['git', 'push', '-q', 'origin', 'HEAD'], cwd=site, check=True)
        print(f'Pushed. F-Droid clients see it at {REPO_URL} after GitHub Pages rebuilds (about a minute).')
    else:
        print('Not pushed. Review the site checkout, then rerun with --push.')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('init').set_defaults(func=init)
    p = sub.add_parser('publish'); p.set_defaults(func=publish)
    p.add_argument('--site', default=str(DEFAULT_SITE), help='Git checkout of the Pages repository (default: ../koenekt-fdroid)')
    p.add_argument('--push', action='store_true', help='Commit and push the site checkout')
    p.add_argument('--verbose', action='store_true')
    args = parser.parse_args(); args.func(args)


if __name__ == '__main__': main()
