# People server amendment for remembered Android sign-in

**Prepared and tested on 20 October 2026. NOT applied to the live server.**
Server installation is a separate operator action; only instructions are supplied.

The updated APK is Koenekt **1.0.1 (2)**. It removes the workspace field and saves
session tokens encrypted by Android Keystore after fingerprint/device-PIN
validation. Returning to the app requires another device unlock and live session
validation. No Koenekt password, authenticator secret/code or phone PIN is saved.

## What needs changing

The current People server issues 30-minute sessions. Persisting that token on the
phone alone cannot extend its server expiry. The supplied amendment enables an
explicit `remember_device: true` flag on an authenticated **HTTPS** `/api/login`
request, issuing a signed token **and cookie** for 30 days. The app sends the flag
when the phone has a secure device lock. Normal website logins, non-boolean flag
values, plain HTTP and legacy API-token logins retain their existing 30-minute
lifetime. Password checks, master MFA/replay protection, live account disabling,
company scope and permission checks remain in force. This is a remembered-login
option, not remote proof of phone biometrics; Android locally protects its copy.

The licence service already has a 30-day session backstop. **It needs no amendment
or restart.** No changes to signing keys, databases, receiver, drain, clocks,
attendance history, tenant configuration, DNS or proxy are needed.

The script edits only three small areas of the installed People
`adms_console.py`: the remembered duration constant, the cookie helper's optional
duration and the authenticated login's token/cookie lifetime. It verifies exact
anchors and preserves unrelated runtime edits. It refuses changed/partial login
implementations, writes a timestamped backup, compiles the changed source before
writing and is safe to rerun. It never restarts services.

## Transfer the amendment

Use `server-amendment/apply_remembered_sessions.py` from this checkout, or the
same script from the standalone support ZIP. From PowerShell in the
`server-amendment` folder (or extracted support folder):

```powershell
ssh deploy-user@YOUR_SERVER "mkdir -p /home/deploy-user/mobile-session-20261020"
scp .\apply_remembered_sessions.py deploy-user@YOUR_SERVER:/home/deploy-user/mobile-session-20261020/
```

Replace `YOUR_SERVER`, `YOUR_TENANT` and `deploy-user` with your authorised
deployment's server, tenant and administrator account before running commands.
No signing key or credentials need to be copied. **Do not copy an entire old
console source tree or use the general server installer for this amendment.**

## Apply from an administrator shell on srv1

First confirm the existing target unit and source path:

```sh
systemctl show adms-console@YOUR_TENANT.service -p WorkingDirectory -p ExecStart
```

These instructions expect `/opt/koenekt/app/console/adms_console.py`, as in the
current server template. If the actual unit uses another path, stop and adjust
`--console` to that unit's real source file before proceeding.

Dry run (no file changes):

```sh
sudo /opt/koenekt/venv/bin/python /home/deploy-user/mobile-session-20261020/apply_remembered_sessions.py --console /opt/koenekt/app/console/adms_console.py
```

Expected: `Dry run passed...`. If it reports different source, it changes nothing;
compare only the named login functions against the source amendment. Do not
replace newer runtime work to force the patch through.

Apply, then restart **only the intended People console**:

```sh
receiver_before=$(systemctl show adms-receiver@YOUR_TENANT.service -p MainPID --value)
sudo /opt/koenekt/venv/bin/python /home/deploy-user/mobile-session-20261020/apply_remembered_sessions.py --console /opt/koenekt/app/console/adms_console.py --apply
```

The script prints `Amended only: ...` and the exact backup filename. Keep that
filename for rollback. Only after that succeeds, run:

```sh
sudo systemctl restart adms-console@YOUR_TENANT.service
systemctl is-active adms-console@YOUR_TENANT.service adms-receiver@YOUR_TENANT.service koenekt-licences.service
receiver_after=$(systemctl show adms-receiver@YOUR_TENANT.service -p MainPID --value)
test "$receiver_before" = "$receiver_after" && echo "Receiver PID unchanged"
curl --fail --silent --show-error https://people.koenekt.co.za/api/brand
```

Expect all three services active, the receiver PID unchanged and a branding JSON
response. Do not restart the receiver or enable drain. Other hosted People
consoles that use the same shared source pick up the amendment on their next
deliberate console restart; restart only the customer consoles you intend to
update, one at a time. The amendment does not create or migrate tenants.

## Phone check after installation

1. Install the signed 1.0.1 APK over the directly installed 1.0.0 APK.
2. Sign out once, then sign in with the existing account/password (master also
   uses its authenticator code). Previously issued 30-minute tokens stay short;
   a new sign-in after the amendment is required for the longer session.
3. Confirm the system fingerprint/device-PIN prompt. Leave the app and reopen it;
   content must stay hidden until successful unlock. Cancel must leave it locked.
4. Force-stop/reopen, verify unlock restores the correct account and permissions,
   and check again after more than 30 minutes. An internet connection is required.
5. Test device PIN fallback, staff import/report save with the Android picker,
   return from the picker, and Sign out. Sign out must forget the saved login.
6. In a synthetic test account, verify disabling the account blocks restoration.
   Do not use real staff modifications or Reporter update commands as a smoke test.

The server may expire/revoke a session before/at its maximum. Full Koenekt login
is required again after expiry, key invalidation or removal of the device lock.
The app's fingerprint/PIN does not enrol staff biometrics; clocks still do that.

For separately hosted customers only, send a one-time app setup link such as
`koenekt://workspace/example`. It selects the organisation internally after a
confirmation. The usual login screen has no workspace/address input. `people`
is the default site. These custom links require the app already installed and a
link-opening app/browser that supports custom schemes; no DNS/App Links change
is required.

## Rollback

Use the exact `.before-mobile-...` backup printed by the apply step. Replace
`EXACT_BACKUP_FILENAME` below with that full path; do not select a backup by a
wildcard or remove any data:

```sh
sudo cp --preserve=mode,ownership,timestamps EXACT_BACKUP_FILENAME /opt/koenekt/app/console/adms_console.py
sudo systemctl restart adms-console@YOUR_TENANT.service
systemctl is-active adms-console@YOUR_TENANT.service adms-receiver@YOUR_TENANT.service
```

Rollback stops issuing new 30-day People sessions. Already-issued signed tokens
retain the expiry embedded in them; restoring old source does not revoke those
tokens. Account disabling still blocks named users. Licence operator sign-out
already revokes all operator sessions and is a separate, global action. Do not
rotate session/signing secrets as a routine rollback.

## Validation performed

Release build and Android lint pass; APK signature and bundle structure are
checked separately with the deliverables. Six mobile/server contract tests cover
default vs remembered lifetime, HTTPS/strict opt-in, two-hour restore, 31-day
expiry, disabled accounts and master MFA/replay. Four amendment tests cover exact
patch result, unrelated-change preservation, refusal, dry-run, backup and repeat
application. 37 existing focused People auth/permission/identity tests and 39 Java
policy checks pass. No live server was amended or restarted.

Real phone fingerprint/PIN/Keystore and lifecycle behaviour still need device
verification. The previous emulator-start attempt was blocked by automatic review;
no new emulator-start attempt or claim of device verification is made here.
