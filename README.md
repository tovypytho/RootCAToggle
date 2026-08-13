# Root CA Toggle v0.2.2 — Tommy hardened build

Minimal root-only Android 7 system trusted-CA toggle manager for rooted VPhoneGaGa/VPhoneOS-style environments.

## v0.2.2 changes
* Fixes GitHub Actions builds affected by Maven Central HTTP 403 on shared runner egress by preferring Google's documented Maven Central mirror.
* Adds a CI mirror preflight check with bounded retries and a clear failure point.
* Uses Gradle Actions v6 with the open-source `basic` cache provider.
* Keeps every v0.2.1 Tommy preset, watermark, trust-store toggle, rollback, R8 hardening and optional signature enforcement feature unchanged.

- Adds a small **Tommy** watermark in the top-right of the app header.
- Adds **SELECT TOMMY PRESET**: selection-only bulk preset for the requested CA list. It never disables certificates by itself.
- Adds **CLEAR SELECTION** to reset all checkboxes quickly.
- Preset application clears the previous selection, selects matching CAs, and reports entries/rules that are absent on the current ROM.
- Keeps all v0.1 functionality: individual toggle, multi-select, vendor bulk, Enable All, Disable All, rollback, and trust-store broadcast.
- Release builds now use **R8 minification/optimization/obfuscation** and resource shrinking.
- Application implementation classes are repackaged/renamed by R8; source-file metadata is reduced.
- Optional **anti-repackaging signature enforcement** is enabled automatically when a private signing keystore is supplied to GitHub Actions.
- CI uploads only the APK, not the R8 `mapping.txt` file.

No Android APK can be made literally impossible to decompile or patch. These measures are defense-in-depth intended to make casual decompilation/repackaging substantially harder. If the source repository is public, anyone can still read the original source regardless of APK obfuscation, so use a **private repository** for meaningful source protection.

## What it does

- Reads every certificate file in `/system/etc/security/cacerts`.
- Shows each system CA as Enabled/Disabled.
- Toggle one system CA ON/OFF.
- Multi-select: Enable Selected / Disable Selected.
- Vendor filter and vendor bulk toggle.
- Enable All / Disable All system CAs.
- Root is mandatory for changes.
- Bulk changes attempt rollback if an entry fails.
- Sends Android 7's `android.security.STORAGE_CHANGED` broadcast after a completed transaction.

**Original CA files under `/system/etc/security/cacerts` are never deleted or modified.**

The disabled state follows Android/Conscrypt's system-CA model by placing an exact encoded certificate marker under `/data/misc/keychain/cacerts-removed`. Re-enabling removes the matching disabled marker.


## Tommy selection preset

Press **SELECT TOMMY PRESET** after the system CA list has loaded. The app clears any previous checkbox selection and selects matching system CAs from this preset. **No trust state changes occur at this point.** Review the checked entries and then press **DISABLE SELECTED** to perform the existing confirmed/transactional bulk disable.

The preset contains:

- All Amazon roots detected by Amazon identity (covers Amazon Root CA 1–4 on the requested Android 7 image).
- Baltimore CyberTrust Root.
- AAA Certificate Services; COMODO Certification Authority; COMODO ECC Certification Authority; COMODO RSA Certification Authority.
- DigiCert Assured ID Root CA/G2/G3; DigiCert Global Root CA/G2/G3; DigiCert High Assurance EV Root CA; DigiCert Trusted Root G4.
- Entrust Root Certification Authority; EC1; G2; Entrust.net Certification Authority (2048).
- All GlobalSign roots detected by GlobalSign identity, including GlobalSign Root CA variants.
- Go Daddy Root Certificate Authority - G2.
- SSL.com EV Root Certification Authority ECC; RSA R2; SSL.com Root Certification Authority ECC; RSA.
- Dhimyotis / Certigna and Dhimyotis / Certigna Root CA.
- Disig a.s. / CA Disig Root R2.
- All WISeKey roots detected by WISeKey identity.

Matching is based on normalized X.509 Common Name / Organization identity rather than CA filename hashes, so it is less sensitive to ROM-specific file naming. For explicitly listed roots, matching is intentionally narrow. For the requested **ALL** groups (Amazon, GlobalSign, WISeKey), all matching roots present on the ROM are selected.

If an expected identity is not found, the app reports it after applying the preset instead of silently disabling something else.

## Compatibility

- `minSdk 24` — Android 7.0.
- Intended for Android 7.0 / 7.1 ROMs using the AOSP/Conscrypt trust-store layout.
- Pure Java; no bundled native `.so`, so one APK is ABI-independent for ARM32/ARM64.
- Android Gradle Plugin 8.7.3, Gradle 8.9, Java 17, compileSdk 35.
- `targetSdk 28` for old-Android-focused behavior.

The trust-store change is system-wide for apps that use Android's system CA trust store. Apps with their own trust store, custom TrustManager/native TLS implementation, or certificate pinning can use different trust decisions.

## GitHub Actions — immediate hardened build

Push this complete project to GitHub, including `.github/workflows/build-apk.yml`, then open:

**Actions → Build Hardened Android APK → Run workflow**

The workflow produces:

```text
RootCAToggle-v0.2.2-hardened-apk
└── app-release.apk
```

Even without signing secrets, `app-release.apk` is built with R8 minification + optimization + resource shrinking and is installable using the runner's fallback debug signer.

### Recommended: stable private signing + anti-repackaging

For the strongest build mode, create one private keystore once and keep it off the repository.

Example:

```bash
keytool -genkeypair -v \
  -keystore rootcatoggle-release.jks \
  -alias rootcatoggle \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

Encode the keystore for GitHub Secrets:

Linux:

```bash
base64 -w 0 rootcatoggle-release.jks
```

macOS:

```bash
base64 < rootcatoggle-release.jks | tr -d '\n'
```

In **GitHub → repository Settings → Secrets and variables → Actions**, create these four repository secrets:

```text
ANDROID_KEYSTORE_BASE64      = the base64 keystore text
ANDROID_KEYSTORE_PASSWORD    = keystore password
ANDROID_KEY_ALIAS            = rootcatoggle
ANDROID_KEY_PASSWORD         = private-key password
```

When all four secrets exist, the workflow automatically:

1. Decodes the private keystore only inside the Actions runner.
2. Extracts the signing certificate.
3. Calculates the signer SHA-256.
4. Embeds that SHA-256 into the obfuscated release build.
5. Signs the APK with the private key.
6. Enables the runtime signature integrity guard.
7. Verifies the final APK using `apksigner`.
8. Uploads only the final APK artifact.

If someone simply modifies and re-signs that APK with a different key, the app detects the signer mismatch and locks root/write controls. This is not mathematically unpatchable, but it blocks straightforward re-sign/repackage attempts.

### Keep the signing key permanently

Do not regenerate the release keystore between builds. Android app updates must be signed with the same key. Back up the `.jks` and its passwords somewhere private.

If v0.1 currently installed on VPhoneGaGa was built with a GitHub runner debug key, the first privately signed v0.2 may require uninstalling v0.1 before installation because Android will reject an update signed by a different key. After that, keep using the same v0.2 private keystore for future updates.

## Source-protection note

The repository at build time contains the source required to compile the APK. Therefore:

- **Private GitHub repo:** APK obfuscation + private signing gives useful protection.
- **Public GitHub repo:** R8 still makes the distributed APK harder to reverse, but the unobfuscated source itself remains publicly readable.
- Do not commit the keystore, passwords, decoded signer certificate, or R8 `mapping.txt`.

## Local build

Hardened fallback release:

```bash
./gradlew assembleRelease
```

Output:

```text
app/build/outputs/apk/release/app-release.apk
```

For local private signing, set the same environment variables consumed by `app/build.gradle`:

```text
ANDROID_KEYSTORE_PATH
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
EXPECTED_SIGNER_SHA256
```

For GitHub Actions you do not need to calculate `EXPECTED_SIGNER_SHA256`; the workflow calculates it automatically from the keystore.

## First test

Do not begin with `Disable All` on a new ROM/build. First grant root, toggle one non-critical CA OFF, reopen the app to verify persistence, toggle it ON again, then test vendor/global bulk operations.

## GitHub Actions Maven Central 403 note

If a hosted runner receives HTTP 403 from `repo.maven.apache.org`, that happens before application compilation and is not an Android source/R8 error. This project prefers Google's documented Maven Central mirror in `settings.gradle`, with the normal Maven Central endpoint retained only as a final fallback for conventional local development.

The workflow also checks one known AGP transitive dependency from the mirror before invoking Gradle. If that preflight passes but the build later fails, inspect the first `* What went wrong:` block rather than the repeated stack-trace tail.
