package com.example.rootcatoggle;

import java.security.cert.X509Certificate;

public final class TrustedCa {
    public final String fileName;
    public final String commonName;
    public final String organization;
    public final String issuer;
    public final String sha256;
    public final String vendor;
    public final X509Certificate certificate;
    public boolean enabled;
    public boolean selected;

    public TrustedCa(String fileName, String commonName, String organization,
                     String issuer, String sha256, String vendor,
                     X509Certificate certificate, boolean enabled) {
        this.fileName = fileName;
        this.commonName = commonName;
        this.organization = organization;
        this.issuer = issuer;
        this.sha256 = sha256;
        this.vendor = vendor;
        this.certificate = certificate;
        this.enabled = enabled;
    }

    public String title() {
        if (commonName != null && !commonName.isEmpty()) return commonName;
        if (organization != null && !organization.isEmpty()) return organization;
        return fileName;
    }
}
