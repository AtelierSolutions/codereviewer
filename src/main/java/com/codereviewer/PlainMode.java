package com.codereviewer;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class PlainMode {
    private PlainMode() {}

    static int run(Path root, Path outputDir, Set<String> extensions) {
        try {
            IO.mkdirs(outputDir);
            Set<String> excludeDirs = Set.of("__pycache__", ".git", ".svn", "node_modules", ".venv", "venv", "dist", "build");

            List<Path> files = collectFiles(root, extensions, excludeDirs);
            if (files.isEmpty()) {
                System.err.println("No code files found in " + root);
                return 0;
            }

            System.out.println("Processing " + files.size() + " files...");

            List<IndexEntry> index = new ArrayList<>();
            for (Path rel : files) {
                Path full = root.resolve(rel);
                try {
                    String content = IO.readText(full);
                    Path outRel = withExtraSuffix(rel, ".html");
                    Path out = outputDir.resolve(outRel);
                    String html = Html.sourcePage(rel.toString(), "Source", content, Lang.languageClassFor(rel),
                            Html.hrefToIndex(outRel));
                    IO.writeText(out, html);
                    index.add(new IndexEntry(rel, withExtraSuffix(rel, ".html")));
                    System.out.println("  " + rel);
                } catch (Exception e) {
                    System.err.println("  Skipping " + rel + ": " + e.getMessage());
                }
            }

            StringBuilder rows = new StringBuilder();
            String body = "<p>Prepared for offline review. " + index.size() + " files with syntax highlighting.</p>\n"
                    + TreeIndex.renderPlain(index)
                    + "<hr>\n"
                    + Html.indexReviewScript();
            String indexHtml = Html.doc("Code Review - " + root.getFileName(), "Code Review: " + Html.escape(root.getFileName().toString()),
                    "", body, "");
            IO.writeText(outputDir.resolve("index.html"), indexHtml);

            System.out.println("\nDone. Output: " + outputDir);
            System.out.println("Open " + outputDir.resolve("index.html") + " in a browser.");
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static List<Path> collectFiles(Path root, Set<String> extensions, Set<String> excludeDirs) throws IOException {
        List<Path> out = new ArrayList<>();
        Files.walk(root, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS).forEach(p -> {
            try {
                if (!Files.isRegularFile(p)) return;
                Path rel = root.relativize(p);
                for (Path part : rel) {
                    if (excludeDirs.contains(part.toString())) return;
                }

                String name = p.getFileName().toString().toLowerCase();
                String ext = "";
                int idx = name.lastIndexOf('.');
                if (idx >= 0) ext = name.substring(idx);

                if (extensions.contains(ext) || extensions.contains(name)) {
                    out.add(rel);
                }
            } catch (Exception ignored) {
            }
        });
        out.sort(Comparator.comparing(Path::toString));
        return out;
    }

    static Path withExtraSuffix(Path rel, String extraSuffix) {
        String s = rel.toString();
        return Path.of(s + extraSuffix);
    }

    static String toPosix(String path) {
        return path.replace('\\', '/');
    }

    record IndexEntry(Path relPath, Path htmlPath) {
        String displayName() {
            return relPath.toString();
        }
    }
}
