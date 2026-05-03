package com.codereviewer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class IO {
    private IO() {}

    static String readText(Path path) throws IOException {
        // "errors=replace" equivalent: decode with replacement.
        // Java's UTF-8 decoder replaces malformed sequences by default.
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    static void writeText(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    static void mkdirs(Path dir) throws IOException {
        Files.createDirectories(dir);
    }

    static ExecResult exec(List<String> command, Path cwd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (cwd != null) pb.directory(cwd.toFile());
        pb.redirectErrorStream(false);
        Process p = pb.start();
        String out = readAll(p.getInputStream(), StandardCharsets.UTF_8);
        String err = readAll(p.getErrorStream(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        return new ExecResult(code, out, err);
    }

    private static String readAll(InputStream in, Charset cs) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, cs))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) >= 0) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        }
    }

    record ExecResult(int exitCode, String stdout, String stderr) {}

    static List<String> splitLines(String s) {
        if (s == null || s.isEmpty()) return List.of();
        // Keep behavior close to Python splitlines() (without keepends).
        String[] parts = s.split("\\R", -1);
        List<String> out = new ArrayList<>(parts.length);
        for (String p : parts) out.add(p);
        // Python splitlines() does not keep a trailing empty line when string ends with newline.
        // Here we do: if input ends with newline, Java split gives trailing empty; drop it.
        if ((s.endsWith("\n") || s.endsWith("\r")) && !out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }
}
