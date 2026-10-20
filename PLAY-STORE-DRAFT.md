# Play Store publication draft

**Version:** Koenekt 1.0.2 (3). Package: `za.co.koenekt.app`.
This is a submission preparation note, not a claim of store approval.

## Listing text

**App name:** Koenekt

**Short description:** Manage staff, attendance, licences and Reporter updates with your Koenekt login.

**Full description:**

Keep your Koenekt management tools in one place.

Sign in to your workspace to manage staff, add new starters, update employee
details, mark leavers and run attendance reports. Use the tools enabled for your
account, including live attendance, clock management and payroll.

Koenekt management accounts can also manage customers and licences and control
Reporter updates across enrolled PCs.

- Existing Koenekt accounts and permissions.
- Remembered login protected by fingerprint or device PIN.
- Customer access limited to the companies their account can manage.
- Staff import and export with Android file selection.
- Attendance reports and payslips saved to a location you choose.
- Connected clock workflows for staff enrollment and synchronisation.

A configured Koenekt workspace and internet connection are required. Available
features depend on the permissions and services enabled for your account.

## Information still needed from the business

- Verified developer account and exact legal/developer name.
- Public support contact and website.
- Privacy policy: DONE, https://loudalley.github.io/koenekt-android/privacy.html (Koenekt (Pty) Ltd, POPIA,
  retention as supplied by the owner). Source is `docs/` on `main` (GitHub Pages).
- Public deletion-request URL: DONE, https://loudalley.github.io/koenekt-android/delete-account.html.
  Still needed: an in-app route to it (e.g. a link from About & privacy), which
  needs an app update.
- Review access to a synthetic customer workspace with instructions that do not
  depend on a reviewer's access to the operator's personal authenticator. Do not
  disable production master MFA to facilitate review.
- Actual device screenshots, 512px store icon and 1024×500 feature graphic.
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
