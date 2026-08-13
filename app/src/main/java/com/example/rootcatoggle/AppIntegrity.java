package com.example.rootcatoggle;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * Lightweight anti-repackaging guard for signed hardened builds.
 * This is intentionally defense-in-depth: no client-side check can make an APK
 * impossible to patch, but it raises the cost of simple re-sign/repackage attempts.
 */
final class AppIntegrity {
    private AppIntegrity() {}

    static boolean isValid(Context context) {
        if (!BuildConfig.SIGNATURE_ENFORCEMENT) return true;
        String expected = normalize(BuildConfig.EXPECTED_SIGNER_SHA256);
        if (expected.isEmpty()) return false;
        if (!BuildConfig.APPLICATION_ID.equals(context.getPackageName())) return false;
        try {
            PackageManager pm = context.getPackageManager();
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return false;
                signatures = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
            } else {
                @SuppressWarnings("deprecation")
                PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                @SuppressWarnings("deprecation")
                Signature[] legacy = info.signatures;
                signatures = legacy;
            }
            if (signatures == null || signatures.length == 0) return false;
            for (Signature signature : signatures) {
                if (expected.equals(sha256(signature.toByteArray()))) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    static void enforce(Context context) throws SecurityException {
        if (!isValid(context)) throw new SecurityException("APK signature integrity check failed");
    }

    private static String sha256(byte[] input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format(Locale.US, "%02X", b & 0xff));
        return out.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace(":", "").replace(" ", "").trim().toUpperCase(Locale.US);
    }
}
