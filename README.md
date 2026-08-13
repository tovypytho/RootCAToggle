# RootCAToggle v0.2.4

> **GitHub Web Upload Friendly package:** this source archive intentionally does **not** contain `.github/`. Upload/replace the project files through GitHub Web, then edit `.github/workflows/build-apk.yml` separately using the companion `build-apk-v0.2.4.yml` file supplied with this release.

# Root CA Toggle v0.2.4 — Tommy hardened build

Minimal root-only Android 7 system trusted-CA toggle manager for rooted VPhoneGaGa/VPhoneOS-style environments.

## v0.2.4 changes

- Adds **SAVE SELECTION**. The currently checked system CAs are stored as a manual selection profile.
- Adds **LOAD SAVED** for manually re-applying the saved checkbox profile without restarting the app.
- The saved profile is **automatically loaded every time the system CA list is loaded**, including normal app startup after root is granted.
- Closing, force-stopping, or rebooting the Android VM does not remove the saved checkbox profile.
- The profile is matched by **SHA-256 certificate fingerprint**, not CA filename, so it is robust against filename/hash differences on compatible images.
- The profile is a small human-readable file named `selection_config.txt` under the app's internal files directory. The app displays the exact absolute path after saving. Typical Android 7 paths are `/data/user/0/com.example.rootcatoggle/files/selection_config.txt` or its `/data/data/com.example.rootcatoggle/files/selection_config.txt` compatibility path.
- Saving a profile stores **selection only**. It never changes CA Enabled/Disabled trust state.
- To intentionally replace the saved profile with an empty profile: press **CLEAR SELECTION**, then **SAVE SELECTION**.
- Keeps the Tommy preset, watermark, individual/vendor/global CA toggles, rollback, R8 hardening, Maven mirror CI fix, targetSdk-28 lint exception, and optional signature enforcement from v0.2.3.

## Saved selection behavior

Example flow:

```text
SELECT TOMMY PRESET
        ↓
manually add/remove checkmarks if desired
        ↓
SAVE SELECTION
        ↓
selection_config.txt is saved transactionally
        ↓
close / force-stop / reboot
        ↓
open RootCAToggle + grant root
        ↓
saved fingerprint list is loaded automatically
        ↓
matching CA checkboxes are selected again
```

`selection_config.txt` contains comments plus one SHA-256 certificate fingerprint per selected CA. The CA common name is written as a comment for readability, but restore matching uses only the SHA-256 fingerprint. If a saved fingerprint is absent from the current ROM, the app skips it rather than selecting a different CA.

The saved file belongs to app-private storage. It survives closing/restarting the app and normal VM reboots, but Android will remove it if the app is uninstalled or its app data is cleared.

## v0.2.3 build fixes retained

The project keeps `targetSdk 28` for the intended direct-sideload Android 7 environment while suppressing only Android Lint's `ExpiredTargetSdkVersion` rule. Other fatal release lint checks remain enabled. GitHub Actions also prefers the Google-hosted Maven Central mirror used to work around the hosted-runner Maven Central HTTP 403 observed in earlier builds.

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

This web-upload package intentionally omits `.github`. Upload/replace the source files first. Then open the existing `.github/workflows/build-apk.yml` in GitHub's web editor and replace its contents with the separately supplied `build-apk-v0.2.4.yml`. After committing both changes, open:

**Actions → Build Hardened Android APK → Run workflow**

The workflow produces:

```text
RootCAToggle-v0.2.4-hardened-apk
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
