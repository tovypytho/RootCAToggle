# Root CA Toggle

Minimal root-only Android 7 system trusted-CA toggle manager, intended for a rooted VPhoneGaGa/VPhoneOS-style virtual Android environment.

## What it does

- Reads every certificate file in `/system/etc/security/cacerts`.
- Shows the system CA as Enabled/Disabled.
- Toggle one system CA ON/OFF.
- Multi-select: Enable Selected / Disable Selected.
- Vendor filter and vendor bulk toggle (DigiCert, GlobalSign, SSL.com, Sectigo/COMODO, Entrust, Google Trust Services, ISRG/Let's Encrypt, etc.).
- Enable All / Disable All system CAs.
- Root is mandatory for changes.
- Bulk changes attempt rollback if an entry fails.
- Sends Android 7's `android.security.STORAGE_CHANGED` broadcast after a completed transaction.

**The original CA files in `/system/etc/security/cacerts` are never deleted or modified.**

Android/Conscrypt implements a disabled system CA by placing an exact encoded copy in `/data/misc/keychain/cacerts-removed`. Re-enabling removes that disabled marker. This app follows that model.

## Compatibility

- `minSdk 24` (Android 7.0)
- Works with Android 7.0 / 7.1 assuming the ROM uses the AOSP/Conscrypt trust-store layout.
- Pure Java: no native `.so`, so the APK itself is ABI-independent (ARM32 and ARM64).
- Build: Android Gradle Plugin 8.7.3, Gradle 8.9, Java 17, compileSdk 35.
- Runtime target is kept at API 28 for an old-Android-focused utility.

## Important scope

The toggle is **system-wide for applications that rely on Android's system CA trust store**. Apps that ship their own trust store, custom `TrustManager`, native TLS stack, or certificate pinning can ignore the platform CA state.

This v0.1 intentionally manages **system CAs only**. User-installed CAs are not given an ON/OFF switch because Android's user-CA removal semantics are different and the goal of this project is non-destructive disable/enable.

## Root behavior

On launch the app executes `su -c id`. Your root manager should show a root grant prompt. If root is denied, write controls remain disabled.

The app prepares `/data/misc/keychain/cacerts-removed` with AOSP-style readable permissions, writes DER encoded disabled markers through `su`, and calls `restorecon` when the command is available.

The filename is calculated using Android 7 Conscrypt's internal `X509_NAME_hash_old` via reflection. Android 7 predates hidden-API enforcement. If a heavily modified ROM removes or renames that implementation, the app refuses to toggle rather than guessing the marker filename.

## GitHub Actions: build the APK

1. Create a GitHub repository.
2. Upload/push **all files and directories from this project**, including `.github/workflows/build-apk.yml`.
3. Open the repository's **Actions** tab.
4. Choose **Build Android APK**.
5. Click **Run workflow**, or simply push to `main`/`master`.
6. When the job finishes, open the run and download the artifact named **RootCAToggle-debug-apk**.
7. Inside the artifact is `app-debug.apk`. It is debug-signed and installable directly.

The workflow runs on GitHub's Ubuntu runner, installs Java 17 and Gradle 8.9, runs `assembleDebug`, then uploads the APK artifact.

## Local build

On Linux/macOS:

```bash
./gradlew assembleDebug
```

If `gradle` is not already installed, the included lightweight `gradlew` bootstrap can download Gradle 8.9 using `curl` and `unzip`. Android SDK 35 must still be installed/configured locally.

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## First test on VPhoneGaGa

Do **not** start with `Disable All`. First:

1. Grant root.
2. Confirm the CA list loads and the enabled count looks plausible.
3. Pick one non-critical test CA.
4. Toggle it OFF.
5. Close/reopen the app and confirm it remains Disabled.
6. Toggle it ON and confirm it returns to Enabled.
7. Only after single-toggle behavior is verified on that ROM, test vendor bulk/global bulk.

`Enable All` is provided as the recovery path from overly broad CA disabling.
