package com.example.rootcatoggle;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Persists checkbox selection only. This never changes Android trust state.
 * The config is intentionally human-readable and matched by SHA-256 fingerprint,
 * so it survives CA filename/hash differences across compatible Android images.
 */
final class SelectionConfigStore {
    static final String FILE_NAME = "selection_config.txt";

    private final File configFile;

    SelectionConfigStore(Context context) {
        configFile = new File(resolveFilesDir(context), FILE_NAME);
    }

    SelectionConfigStore(File configFile) {
        this.configFile = configFile;
    }

    private static File resolveFilesDir(Context context) {
        // Use Context#getFilesDir when available. Reflection keeps the tiny static
        // compile harness independent from a complete Android SDK stub jar.
        try {
            Object value = Context.class.getMethod("getFilesDir").invoke(context);
            if (value instanceof File) return (File) value;
        } catch (Exception ignored) {
        }
        File fallback = new File("/data/data/" + context.getPackageName() + "/files");
        if (!fallback.exists()) fallback.mkdirs();
        return fallback;
    }

    String path() {
        return configFile.getAbsolutePath();
    }

    int save(List<TrustedCa> all) throws Exception {
        List<TrustedCa> selected = new ArrayList<>();
        for (TrustedCa ca : all) {
            if (ca.selected) selected.add(ca);
        }
        Collections.sort(selected, (a, b) -> a.title().compareToIgnoreCase(b.title()));

        File tmp = new File(configFile.getParentFile(), FILE_NAME + ".tmp");
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp, false), "UTF-8"))) {
            out.write("# RootCAToggle saved selection v1");
            out.newLine();
            out.write("# One SHA-256 certificate fingerprint per selected system CA.");
            out.newLine();
            out.write("# Lines beginning with # are comments. Trust state is NOT stored here.");
            out.newLine();
            out.newLine();
            for (TrustedCa ca : selected) {
                out.write("# " + sanitizeComment(ca.title()));
                out.newLine();
                out.write(ca.sha256);
                out.newLine();
            }
        }

        File backup = new File(configFile.getParentFile(), FILE_NAME + ".bak");
        if (backup.exists()) backup.delete();
        if (configFile.exists() && !configFile.renameTo(backup)) {
            tmp.delete();
            throw new Exception("Cannot rotate existing selection config");
        }
        if (!tmp.renameTo(configFile)) {
            if (backup.exists()) backup.renameTo(configFile);
            tmp.delete();
            throw new Exception("Cannot commit selection config");
        }
        if (backup.exists()) backup.delete();
        return selected.size();
    }

    LoadResult applyTo(List<TrustedCa> all) throws Exception {
        File readable = configFile;
        if (!readable.isFile()) {
            File backup = new File(configFile.getParentFile(), FILE_NAME + ".bak");
            if (!backup.isFile()) return new LoadResult(false, 0, 0, 0);
            readable = backup;
        }

        Set<String> saved = loadFingerprints(readable);
        int matched = 0;
        for (TrustedCa ca : all) {
            boolean selected = saved.contains(normalize(ca.sha256));
            ca.selected = selected;
            if (selected) matched++;
        }
        return new LoadResult(true, saved.size(), matched, Math.max(0, saved.size() - matched));
    }

    private Set<String> loadFingerprints(File source) throws Exception {
        Set<String> out = new LinkedHashSet<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(source), "UTF-8"))) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String fp = normalize(line);
                if (fp.length() == 64 && fp.matches("[0-9A-F]{64}")) out.add(fp);
            }
        }
        return out;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replace(":", "").replace(" ", "").trim().toUpperCase(Locale.US);
    }

    private static String sanitizeComment(String value) {
        if (value == null) return "(unnamed CA)";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    static final class LoadResult {
        final boolean exists;
        final int savedCount;
        final int matchedCount;
        final int missingCount;

        LoadResult(boolean exists, int savedCount, int matchedCount, int missingCount) {
            this.exists = exists;
            this.savedCount = savedCount;
            this.matchedCount = matchedCount;
            this.missingCount = missingCount;
        }
    }
}
