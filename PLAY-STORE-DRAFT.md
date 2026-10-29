# Play Store publication draft

**Version:** Koenekt 1.6.1 (11). Package: `za.co.koenekt.app`.
This is a submission preparation note, not a claim of store approval.

## Upload package and remaining Console work

The signed bundle is `dist/1.6.1/Koenekt-1.6.1.aab`. Its SHA-256 is
`d9f3c22512f3a00ae81e86ab56c8316d16706453ead18b67efd895c852e5c582`.
The prepared upload copy and listing images are in `dist/play-store/` in the
main checkout: `Koenekt-1.6.1.aab`, `icon-512.png`,
`feature-graphic-1024x500.png` and six PNGs in `screenshots/`.
These ignored local files must not be committed with signing credentials.

The user confirmed that the separate `demo` workspace exists. Review access
must use synthetic Demo Shop data, with no access to client workspaces. Set up
`play.reviewer` with Demo Shop only and People and Who's in permissions. Keep
its password out of Git, public notes and screenshots.

Access instructions for Play Console:

> On the sign-in screen tap Workspace · Change and enter demo. Sign in with
> the reviewer credentials supplied in Play Console. Open People to view the
> synthetic staff and Who's in to view attendance. Available features depend
> on the review account's permissions.

Before submission, verify the reviewer credentials work and complete Sign-in
details, Target audience (18 and over, as requested by the owner), Content
rating and the final Data safety save. Upload the graphics and bundle, review
the production release, and have the owner click Send for review. A saved draft,
an uploaded bundle and store approval are separate states; verify each in Console.
Pushing the 1.6.1 source and publishing it to F-Droid or Drive remain subject to
the owner's separate instruction.

## Listing text

**App name:** Koenekt

**Short description:** Staff, attendance and who's in, by shop, with your Koenekt account.

**Full description:**

Koenekt keeps your staff and attendance tools in one place on your phone.

Sign in with your Koenekt account to see who is at work right now, add new starters,
update employee details and manage leavers. The tools you see depend on what your
organisation has enabled for your account.

Who's in
- See who is at work, who is not in and who has clocked today.
- Split it by department or shop, and see each one's numbers at a glance.
- Filter to who has clocked out or has not clocked in yet.

People
- Search staff by name, clock PIN or department.
- Add an employee in three steps, with a suggested clock PIN.
- Edit details, shifts and clock passwords, and send changes to your clocks.
- Mark leavers, and remove them from People while their attendance history stays
  in your reports.

Your account
- Opens with your fingerprint or phone PIN after you sign in.
- Change your password, or delete your account from the app.

More tools

Reports, payroll, clock management and settings open inside the app when your
account has access. Koenekt management accounts can also manage customers and
licences and control Reporter updates on enrolled PCs.

A Koenekt workspace and an internet connection are required.

## Information still needed from the business

- Developer organisation and contact setup: Claude recorded completion in Play
  Console; verify the current account and saved listing before submission.
- Privacy policy: DONE, https://loudalley.github.io/koenekt-android/privacy.html (Koenekt (Pty) Ltd, POPIA,
  retention as supplied by the owner). Source is `docs/` on `main` (GitHub Pages).
- Public deletion-request URL: DONE, https://loudalley.github.io/koenekt-android/delete-account.html.
  In-app deletion: Account → Delete my account since 1.6.0. About & privacy also
  links to the external deletion and privacy pages.
- Review access to a synthetic customer workspace with instructions that do not
  depend on a reviewer's access to the operator's personal authenticator. Do not
  disable production master MFA to facilitate review.
- Six actual-app emulator screenshots with synthetic data, 512px store icon and
  1024×500 feature graphic: prepared in `dist/play-store/`; upload still to verify.
- Confirm chosen application ID is available in the developer account before
  the first upload. Keep it stable after publishing.

## Data safety preparation

Review the connected service's complete behaviour, including WebView forms and
the organisation's retention practices, before submitting these declarations.
"No analytics SDK" does not mean "no data collection": staff and account forms
send information to Koenekt services.

| Area | Behaviour to account for |
| --- | --- |
| Account/personal information | Username and credentials for account access; staff names, identifiers, contact and other fields where used by People |
| Attendance and employment | Attendance, shifts, categories, rates, payroll and audit activity when the account uses them |
| Files/photos | User-selected imports and logos are uploaded to the selected service; reports are exported to a chosen destination |
| Licensing/device information | Licence/customer data and existing enrolled-PC update status in the management interface |
| Security/diagnostics | Existing services process network/session/login/audit information; review their logs and retention |
| App SDKs | No app-provided advertising or analytics SDK; system WebView is used |
| Permissions | Internet and USE_BIOMETRIC for the system unlock prompt; no phone contacts, location, microphone or camera permission. No raw fingerprint data is collected. |

The app uses HTTPS. This does not establish at-rest encryption or determine the
server's retention policy; verify those statements before including them.

## Relevant official guidance

- [Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455)
- [Account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)
- [Data safety information](https://support.google.com/googleplay/answer/11416267)
- [Android App Bundles and bundletool](https://developer.android.com/tools/bundletool)

Start with an internal test release and real-device checks. Uploading an AAB is
not publication; store review and the remaining business declarations still apply.
