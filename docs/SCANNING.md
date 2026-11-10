# Phone scanning and signing

In Settings → Advanced, select **VirusTotal** or **VirusDetec**, turn scanning on,
and choose **Normal** or **Strict**. No provider is selected automatically, including
when upgrading from the previous build. Automatic hash lookup remains disabled until
that choice is made. Turning scanning off prevents new lookups and scans.

An explicit tap on **Security Scan** is different from an automatic lookup: it always
runs. If no provider has been chosen it uses VirusDetec, which needs no key; if
VirusTotal is chosen but has no key saved, the scan reports exactly that instead of
returning silently. Every outcome — disabled, missing key, rate limited, too large,
network failure — is reported to the UI.

- **VirusTotal:** direct HTTPS access to `www.virustotal.com/api/v3`; requires a personal
  API key saved in Settings. Automatic lookup sends the hash, not APK metadata or file
  bytes. The explicit Scan action may upload the file if the hash is unknown. The client
  follows VirusTotal's documented size routing: files up to 32 MB go to `POST /files`,
  and anything larger is posted to the one-time pre-signed URL returned by
  `GET /files/upload_url`, up to the real 650 MB ceiling. Both paths stream the APK from
  disk, so a large file is never read into memory, and the pre-signed URL is accepted
  only for `virustotal.com` hosts. Scans stop on rate limiting rather than retrying.
  The key is stored using the existing app-private DataStore key setting; it is not
  included in pending-install records or sent to VirusDetec. This is not a new encrypted
  credential vault.
- **VirusDetec:** the existing independent service at `virusdetect.pwhs.app`, with its
  existing workers.dev fallback and built-in client key. Hash lookup also sends package
  metadata. Explicit scanning can upload the APK. It is not presented as VirusTotal;
  findings are not advertised as results from 70 antivirus engines.
- Selecting one never silently falls back to the other. Previously displayed results
  retain their original provider label. Scan again to obtain a result from a new provider.
- Normal keeps installation as the main action. Strict asks for confirmation when a
  verdict is unavailable; neither mode is proof that an APK is safe.
- Local scan UI strings use resources. VirusDetec requests Vietnamese only for a
  Vietnamese app locale; other locales request English. Server-provided report text
  still depends on the service honoring that request. Saved reports retain their original
  language; rescan for new localized server text.
- Pending-install records retain the complete report, provider and engine details;
  interrupted scans restore as an error rather than a clean result.

The pre-release workflow now requires repository signing secrets: `KEYSTORE_BASE64`,
`STORE_PASSWORD` (or legacy `KEYSTORE_PASSWORD`), `KEY_ALIAS`, and `KEY_PASSWORD`.
There is no disposable-key fallback. Future builds signed with the same key can update
one another. The first stable-key build cannot update an earlier disposable-key APK;
back up settings before uninstalling that old build.

CI tests provider selection, locale mapping, HTTP error handling, direct request routing,
missing-key behavior, no retry/fallback on rate limiting, verdict parsing, and complete
report persistence. CI runs unit tests, all debug builds, and an R8 phone release build
before verifying the APK signature and publishing. Real service calls and device UI
behavior still require device testing; no personal key is used in automated tests.
