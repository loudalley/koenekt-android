#!/usr/bin/env python3
"""Surgical People session amendment. Dry-run by default; never restarts services."""
import argparse
import ast
from datetime import datetime, timezone
import os
from pathlib import Path
import shutil
import tempfile

MARKER = 'REMEMBERED_DEVICE_SECONDS = 30 * 24 * 60 * 60'
OLD_LOGIN = '''    _set_session_cookie(
        body, request, cauth.issue_session(base_dir(), user), role=user.get("role"))'''
NEW_LOGIN = '''    # Explicit opt-in only, after the normal password/MFA checks. The Android app
    # protects its saved token with device-authentication-bound Keystore storage.
    # Plain HTTP and legacy API-token logins never receive a remembered session.
    duration = (REMEMBERED_DEVICE_SECONDS
                if payload.get("remember_device") is True and https_session(request)
                else SESSION_COOKIE_MAX_AGE)
    _set_session_cookie(
        body, request, cauth.issue_session(base_dir(), user, seconds=duration),
        role=user.get("role"), max_age=duration)'''
OLD_SIGNATURE = '    response: JSONResponse, request: Request, token: str, *, role: str | None,\n'
NEW_SIGNATURE = OLD_SIGNATURE + '    max_age: int | None = None,\n'
OLD_AGE = '"max_age": cookie_max_age_for_role(role),'
NEW_AGE = '"max_age": cookie_max_age_for_role(role) if max_age is None else max_age,'


def amend(source):
    text = source.replace('\r\n', '\n')
    tree = ast.parse(text)
    functions = {node.name: node for node in tree.body if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))}
    for name in ('api_login', '_set_session_cookie'):
        if name not in functions: raise ValueError('Expected People login functions were not found. No change made.')
    lines = text.splitlines(keepends=True)
    def body(name):
        node = functions[name]
        return ''.join(lines[node.lineno-1:node.end_lineno])
    login, cookie = body('api_login'), body('_set_session_cookie')
    if MARKER in text:
        if NEW_LOGIN in login and NEW_SIGNATURE in cookie and NEW_AGE in cookie:
            return text, False
        raise ValueError('A partial/different remembered-session implementation exists. Review it before applying.')
    if login.count(OLD_LOGIN) != 1 or cookie.count(OLD_SIGNATURE) != 1 or cookie.count(OLD_AGE) != 1:
        raise ValueError('Installed login source differs from the reviewed version. No change made; compare the login functions manually.')
    new_cookie = cookie.replace(OLD_SIGNATURE, NEW_SIGNATURE).replace(OLD_AGE, NEW_AGE)
    new_login = login.replace(OLD_LOGIN, NEW_LOGIN)
    # Add only the new constant; preserve every other source line and function.
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'SESSION_COOKIE_MAX_AGE' for t in node.targets):
            location = sum(len(line) for line in lines[:node.end_lineno])
            break
    else: raise ValueError('Session lifetime constant was not found. No change made.')
    updated = text[:location] + MARKER + '\n' + text[location:]
    updated = updated.replace(cookie, new_cookie, 1).replace(login, new_login, 1)
    compile(updated, '<amended adms_console.py>', 'exec')
    return updated, True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--console', type=Path, default=Path('/opt/koenekt/app/console/adms_console.py'))
    parser.add_argument('--apply', action='store_true', help='Write a backup and amend the one console source file')
    args = parser.parse_args()
    path = args.console.resolve(strict=True)
    original = path.read_text(encoding='utf-8')
    updated, changed = amend(original)
    if not changed: print('Already amended; no file or service changed.'); return
    if not args.apply:
        print('Dry run passed. Would add HTTPS opt-in 30-day remembered sessions; ordinary logins remain 30 minutes.')
        print('No files or services changed. Rerun with --apply to write the amendment.'); return
    info = path.stat()
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    backup = path.with_name(path.name + '.before-mobile-' + stamp)
    shutil.copy2(path, backup)
    tmp = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', newline='\n', dir=path.parent, prefix='.mobile-session-', delete=False) as out:
            tmp = Path(out.name); out.write(updated); out.flush(); os.fsync(out.fileno())
        os.chmod(tmp, info.st_mode)
        if hasattr(os, 'chown'): os.chown(tmp, info.st_uid, info.st_gid)
        os.replace(tmp, path); tmp = None
    finally:
        if tmp is not None: tmp.unlink(missing_ok=True)
    print('Amended only: ' + str(path))
    print('Backup: ' + str(backup))
    print('No services restarted. Restart the intended People console(s), then check health and unchanged receiver PIDs.')


if __name__ == '__main__': main()
