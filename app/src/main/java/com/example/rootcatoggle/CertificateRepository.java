package com.example.rootcatoggle;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class CertificateRepository {
    private final RootTrustStore store;
    private final CertificateFactory factory;

    CertificateRepository(RootTrustStore store) throws Exception {
        this.store = store;
        this.factory = CertificateFactory.getInstance("X.509");
    }

    List<TrustedCa> loadSystemCertificates() throws Exception {
        File[] files = RootTrustStore.SYSTEM_DIR.listFiles();
        if (files == null) throw new Exception("Cannot read " + RootTrustStore.SYSTEM_DIR);
        List<TrustedCa> out = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile()) continue;
            X509Certificate cert;
            try {
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
                cert = (X509Certificate) factory.generateCertificate(in);
                in.close();
            } catch (Exception malformed) {
                // Malformed/unreadable entries are skipped, matching the platform's tolerant behavior.
                continue;
            }
            String subject = cert.getSubjectX500Principal().getName("RFC2253");
            String cn = CertificateUtil.dnValue(subject, "CN");
            String org = CertificateUtil.dnValue(subject, "O");
            String issuer = cert.getIssuerX500Principal().getName("RFC2253");
            boolean enabled = !store.isDisabled(cert);
            out.add(new TrustedCa(file.getName(), cn, org, issuer,
                    CertificateUtil.sha256(cert), CertificateUtil.classifyVendor(cn, org),
                    cert, enabled));
        }
        Collections.sort(out, new Comparator<TrustedCa>() {
            @Override public int compare(TrustedCa a, TrustedCa b) {
                return a.title().compareToIgnoreCase(b.title());
            }
        });
        return out;
    }
}
