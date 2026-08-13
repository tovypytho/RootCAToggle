package com.example.rootcatoggle;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

final class CertificateUtil {
    private CertificateUtil() {}

    static String dnValue(String dn, String key) {
        if (dn == null) return null;
        for (String part : splitDn(dn)) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String k = part.substring(0, eq).trim();
            if (key.equalsIgnoreCase(k)) {
                return unescape(part.substring(eq + 1).trim());
            }
        }
        return null;
    }

    private static List<String> splitDn(String dn) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        boolean quoted = false;
        for (int i = 0; i < dn.length(); i++) {
            char c = dn.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                current.append(c);
                escaped = true;
            } else if (c == '"') {
                current.append(c);
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    private static String unescape(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value.replace("\\,", ",").replace("\\=", "=").replace("\\\\", "\\");
    }

    static String sha256(X509Certificate cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(cert.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", digest[i] & 0xff));
        }
        return sb.toString();
    }

    static String classifyVendor(String cn, String org) {
        String s = ((cn == null ? "" : cn) + " " + (org == null ? "" : org)).toLowerCase();
        if (s.contains("digicert")) return "DigiCert";
        if (s.contains("globalsign")) return "GlobalSign";
        if (s.contains("ssl.com") || s.contains("ssl corporation")) return "SSL.com";
        if (s.contains("sectigo") || s.contains("comodo")) return "Sectigo / COMODO";
        if (s.contains("entrust")) return "Entrust";
        if (s.contains("google trust")) return "Google Trust Services";
        if (s.contains("internet security research group") || s.contains("isrg") || s.contains("let's encrypt")) return "ISRG / Let's Encrypt";
        if (s.contains("godaddy") || s.contains("go daddy")) return "GoDaddy";
        if (s.contains("amazon")) return "Amazon";
        if (s.contains("wisekey")) return "WISeKey";
        if (s.contains("dhimyotis") || s.contains("certigna")) return "Dhimyotis / Certigna";
        if (s.contains("disig")) return "Disig";
        if (s.contains("baltimore cybertrust")) return "Baltimore";
        if (s.contains("microsoft")) return "Microsoft";
        if (s.contains("identrust")) return "IdenTrust";
        return "Other";
    }
}
