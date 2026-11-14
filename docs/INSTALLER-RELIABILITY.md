# Installer reliability pre-release

## Changes (Phone)

- Wait up to two seconds for asynchronous Shizuku binder delivery before choosing a fallback, both in the dialog and installation controller.
- Save successful default-installer/uninstaller choices; restore them on Shizuku connection/permission grant and ready Root startup. Toggle the setting once after upgrading to record your choice.
- Register MIME-typed APK filters using `addPreferredActivity` (Android 12+: `removeExisting=true` replaces matching filters without clearing unrelated defaults). Do not use `replacePreferredActivity` for APKs: Android rejects MIME types in that API. The scheme-only uninstaller filter can still use it.
- Provide an Android battery-optimization exemption switch. Default-handler opt-in also attempts deviceidle and background AppOps exemptions through the authorized backend; OEM refusals are logged, not fatal. Turning the battery switch off opts out of future automatic exemptions and opens Android's exemption list.
- Keep install/uninstall dialogs alive across temporary focus changes.
- Persist pending-install metadata atomically in private cache, including split selection and risk flags. Records expire after one hour. Cached APKs can still be evicted; external OBB/source URI grants cannot be made durable by saving a string. In-progress scans are not represented as completed clean scans after restart.
- Make session UI state a process-scoped StateFlow repository with no activity SavedStateHandle dependency. Ackpine remains responsible for actual durable installation sessions.
- Perform Root preferred-activity calls with effective UID 1000 in the isolated privileged service, restoring the previous UID afterward.

## Verification

The pre-release workflow gates publication on:

```
./gradlew :app:testOpensourceDebugUnitTest assembleDebug :app:assembleOpensourceRelease
```

It verifies the APK signature and publishes checksums alongside the APK. Tests cover pending-record process recreation, replacement, expiration, deletion and path traversal, plus session repository cold construction and updates. Compilation and tests do **not** validate RedMagicOS behavior on hardware.

Local Gradle could not start in the Arena sandbox because Java/Android build tooling is not installed. Use the workflow's test report and build logs as the build verification record.

## Device acceptance checklist (not yet executed)

1. Enable Shizuku and grant Multiversal-installer permission. Enable default installer and ignore battery optimizations. Keep RedMagic background/autostart permissions enabled.
2. Open an APK from a file manager, install, swipe Multiversal-installer away, dismiss completion notifications, then open another APK. Repeat after screen-off/Doze.
3. Kill the background process (not force-stop), then open an APK. Verify no premature backend-unavailable warning and that Shizuku is used.
4. Stop/restart Shizuku, reopen Multiversal-installer, then open an APK. Verify reconnection and default-handler recovery. Deny permission and verify fallback remains available.
5. Stage a notification install with selected/unselected splits, kill the process, and tap the notification body. Verify split selection and warnings. Test Install, Cancel, expired cache, and denied notification permission.
6. Enable both default installer and uninstaller. Verify both independently; retest after reconnect.
7. On a rooted test device, verify persistent preferred registration and effective UID restoration after success/failure.

A true Android force-stop blocks app callbacks until the app is launched again. Deviceidle exemption is **not proof** that an OEM's Recents cleaner will stop force-stopping the app. No permanent foreground service is added, and no guarantee of OEM immunity is made.

## Signing

Use KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD repository secrets for a stable release identity. Without them CI generates a disposable experimental signing key, deletes it after building, and labels the release accordingly. Such APKs are directly installable but **cannot update upstream or another workflow run in place**. Back up settings before removing any existing installation. For production distribution, provision and securely retain a stable key and complete device acceptance testing first.

The existing Play deployment workflow and Fastfile are unchanged: Phone uses bundlePlayRelease / assembleOpensourceRelease; TV uses bundlePlayRelease; Wear OS uses bundleRelease. This pre-release changes Phone only and does not deploy to Play.

## Default-installer regression hotfix

The first pre-release incorrectly used `replacePreferredActivity` for the APK MIME filter.
AOSP `PreferredActivityHelper.replacePreferredActivity` rejects any data types before
checking caller permission, so even an authorized Shizuku connection cannot make that
call succeed. The hotfix restores `addPreferredActivity`, selecting the six-argument
Android 12+ signature with `removeExisting=true` and the legacy five-argument signature
on older Android. Unit tests cover this API selection and reject replacement-only proxies.
Device validation is still required; these tests do not emulate the OEM package manager.
