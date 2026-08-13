package com.example.rootcatoggle;

import java.io.BufferedReader;
import java.io.InputStreamReader;

final class RootShell {
    static final class Result {
        final int exitCode;
        final String output;
        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output.trim();
        }
        boolean ok() { return exitCode == 0; }
    }

    Result exec(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) out.append('\n');
                out.append(line);
            }
            int code = process.waitFor();
            return new Result(code, out.toString());
        } catch (Exception e) {
            return new Result(-1, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }

    boolean requestRoot() {
        Result r = exec("id");
        return r.ok() && r.output.contains("uid=0");
    }

    static String q(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
