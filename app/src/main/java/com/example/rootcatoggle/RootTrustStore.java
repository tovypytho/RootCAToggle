package com.example.rootcatoggle;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

final class RootTrustStore {
    static final File SYSTEM_DIR = new File("/system/etc/security/cacerts");
    static final File REMOVED_DIR = new File("/data/misc/keychain/cacerts-removed");

    private final Context context;
    private final RootShell root;
    private final CertificateFactory factory;

    RootTrustStore(Context context, RootShell root) throws Exception {
        this.context = context.getApplicationContext();
        this.root = root;
        this.factory = CertificateFactory.getInstance("X.509");
        prepareDirectory();
    }

    private void prepareDirectory() throws Exception {
        String dir = REMOVED_DIR.getAbsolutePath();
        RootShell.Result r = root.exec("mkdir -p " + RootShell.q(dir) +
                " && chown 1000:1000 " + RootShell.q(dir) +
                " && chmod 0755 " + RootShell.q(dir) +
                " && (restorecon " + RootShell.q(dir) + " >/dev/null 2>&1 || true)");
        if (!r.ok()) throw new Exception("Cannot prepare cacerts-removed: " + r.output);
        if (!REMOVED_DIR.canRead()) throw new Exception("cacerts-removed is not readable after root setup");
    }

    File findDisabledMarker(X509Certificate cert) throws Exception {
        String hash = SubjectHashOld.calculate(cert.getSubjectX500Principal());
        for (int index = 0; index < 256; index++) {
            File marker = new File(REMOVED_DIR, hash + "." + index);
            if (!marker.isFile()) return null;
            X509Certificate existing = read(marker);
            if (existing != null && existing.equals(cert)) return marker;
        }
        return null;
    }

    boolean isDisabled(X509Certificate cert) throws Exception {
        return findDisabledMarker(cert) != null;
    }

    void setEnabled(X509Certificate cert, boolean enabled) throws Exception {
        File marker = findDisabledMarker(cert);
        if (enabled) {
            if (marker == null) return;
            enableMarker(cert, marker);
        } else {
            if (marker != null) return;
            disableCertificate(cert);
        }
    }

    private void disableCertificate(X509Certificate cert) throws Exception {
        String hash = SubjectHashOld.calculate(cert.getSubjectX500Principal());
        int index = firstFreeIndex(hash);
        File target = new File(REMOVED_DIR, hash + "." + index);
        File temp = new File(context.getCacheDir(), "ca-marker-" + System.nanoTime() + ".der");
        try {
            FileOutputStream out = new FileOutputStream(temp);
            out.write(cert.getEncoded());
            out.close();

            String cmd = "mkdir -p " + RootShell.q(REMOVED_DIR.getAbsolutePath()) +
                    " && chown 1000:1000 " + RootShell.q(REMOVED_DIR.getAbsolutePath()) +
                    " && chmod 0755 " + RootShell.q(REMOVED_DIR.getAbsolutePath()) +
                    " && cat " + RootShell.q(temp.getAbsolutePath()) + " > " + RootShell.q(target.getAbsolutePath()) +
                    " && chown 1000:1000 " + RootShell.q(target.getAbsolutePath()) +
                    " && chmod 0644 " + RootShell.q(target.getAbsolutePath()) +
                    " && (restorecon " + RootShell.q(target.getAbsolutePath()) + " >/dev/null 2>&1 || true)";
            RootShell.Result result = root.exec(cmd);
            if (!result.ok()) throw new Exception("Root write failed: " + result.output);
            if (!target.exists()) throw new Exception("Marker was not created: " + target.getName());
            if (findDisabledMarker(cert) == null) throw new Exception("Marker verification failed: " + target.getName());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private void enableMarker(X509Certificate cert, File marker) throws Exception {
        String name = marker.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) throw new Exception("Invalid marker filename: " + name);
        String hash = name.substring(0, dot);
        int index = Integer.parseInt(name.substring(dot + 1));

        // Remove the exact marker, then compact later entries for this hash so Conscrypt's
        // contiguous hash.N lookup cannot be broken by a gap.
        String dir = REMOVED_DIR.getAbsolutePath();
        String cmd = "rm -f " + RootShell.q(marker.getAbsolutePath()) +
                "; i=" + index +
                "; while [ -f " + RootShell.q(dir + "/" + hash + ".") + "$(($i+1)) ]; do " +
                "mv " + RootShell.q(dir + "/" + hash + ".") + "$(($i+1)) " + RootShell.q(dir + "/" + hash + ".") + "$i || exit 21; " +
                "i=$(($i+1)); done; " +
                "(restorecon -R " + RootShell.q(dir) + " >/dev/null 2>&1 || true)";
        RootShell.Result result = root.exec(cmd);
        if (!result.ok()) throw new Exception("Root remove failed: " + result.output);
        if (findDisabledMarker(cert) != null) throw new Exception("Certificate is still marked disabled");
    }

    private int firstFreeIndex(String hash) {
        for (int i = 0; i < 256; i++) {
            if (!new File(REMOVED_DIR, hash + "." + i).isFile()) return i;
        }
        throw new IllegalStateException("Too many certificates with subject hash " + hash);
    }

    private X509Certificate read(File file) {
        try {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
            X509Certificate cert = (X509Certificate) factory.generateCertificate(in);
            in.close();
            return cert;
        } catch (Exception e) {
            return null;
        }
    }

    void notifyTrustStoreChanged() {
        // Android 7's legacy trust/keychain storage-change broadcast.
        root.exec("am broadcast -a android.security.STORAGE_CHANGED >/dev/null 2>&1 || true");
    }
}
