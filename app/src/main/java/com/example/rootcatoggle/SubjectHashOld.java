package com.example.rootcatoggle;

import java.lang.reflect.Method;
import java.util.Locale;
import javax.security.auth.x500.X500Principal;

final class SubjectHashOld {
    private SubjectHashOld() {}

    static String calculate(X500Principal principal) throws Exception {
        Exception last = null;
        String[] classes = {"com.android.org.conscrypt.NativeCrypto", "org.conscrypt.NativeCrypto"};
        for (String className : classes) {
            try {
                Class<?> c = Class.forName(className);
                Method m = c.getDeclaredMethod("X509_NAME_hash_old", X500Principal.class);
                m.setAccessible(true);
                int hash = ((Number) m.invoke(null, principal)).intValue();
                return String.format(Locale.US, "%08x", hash);
            } catch (Exception e) {
                last = e;
            }
        }
        throw new Exception("X509_NAME_hash_old unavailable on this ROM", last);
    }
}
