package com.codereviewer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SvnMode {
    private SvnMode() {}

    static int run(Path root, Path outputDir, String baseArg, String headArg) {
        try {
            Path wcRoot = getWcRoot(root);
            if (wcRoot == null) {
                System.err.println("Not a Subversion working copy.");
                return 1;
            }

            String base = (baseArg == null || baseArg.isBlank()) ? "BASE" : baseArg;
            String head = (headArg == null || headArg.isBlank()) ? "HEAD" : headArg;

            IO.mkdirs(outputDir);

            List<ChangedFile> changed = getChangedFiles(wcRoot, base, head);
            if (changed.isEmpty()) {
                System.out.println("No changes found between revisions.");
                return 0;
            }

            // changes.patch
            IO.ExecResult patch = IO.exec(List.of("svn", "diff", "-r", base + ":" + head), wcRoot);
            String patchText = (patch.exitCode() == 0) ? patch.stdout() : "";
            if (patch.exitCode() == 0) {
                IO.writeText(outputDir.resolve("changes.patch"), patchText);
            }

            List<Map<String, String>> indexEntries = new ArrayList<>();
            for (ChangedFile cf : changed) {
                if (cf.path == null || cf.path.isBlank()) continue;
                if (isBinaryLike(cf.path)) continue;

                Path relPath = Path.of(cf.path);
                String oldContent = getFileContent(wcRoot, base, cf.path);
                String newContent = getFileContent(wcRoot, head, cf.path);

                String pathPosix = PlainMode.toPosix(relPath.toString());
                Path outDirForFile = outputDir.resolve(relPath).getParent();
                if (outDirForFile != null) IO.mkdirs(outDirForFile);

                Map<String, String> entry = new HashMap<>();
                entry.put("path", cf.path);
                entry.put("status", cf.status);

                if (!oldContent.isEmpty()) {
                    Path oldOutRel = Path.of(pathPosix + "-.html");
                    String oldHtml = Html.sourcePage(relPath.toString(), "Old (r" + base + ")", oldContent,
                            Lang.languageClassFor(relPath), Html.hrefToIndex(oldOutRel));
                    IO.writeText(outputDir.resolve(oldOutRel), oldHtml);
                    entry.put("old", pathPosix + "-.html");
                }
                if (!newContent.isEmpty()) {
                    Path newOutRel = Path.of(pathPosix + ".html");
                    String newHtml = Html.sourcePage(relPath.toString(), "New (r" + head + ")", newContent,
                            Lang.languageClassFor(relPath), Html.hrefToIndex(newOutRel));
                    IO.writeText(outputDir.resolve(newOutRel), newHtml);
                    entry.put("new", pathPosix + ".html");
                }
                if (!oldContent.isEmpty() && !newContent.isEmpty()) {
                    Path udiffOutRel = Path.of(pathPosix + ".udiff.html");
                    Path sdiffOutRel = Path.of(pathPosix + ".sdiff.html");
                    String udiffHtml = DiffViews.unifiedDiffPage(relPath, oldContent, newContent, Html.hrefToIndex(udiffOutRel));
                    String sdiffHtml = DiffViews.sideBySideDiffPage(relPath, oldContent, newContent, "r" + base, "r" + head,
                            Html.hrefToIndex(sdiffOutRel));
                    IO.writeText(outputDir.resolve(udiffOutRel), udiffHtml);
                    IO.writeText(outputDir.resolve(sdiffOutRel), sdiffHtml);
                    entry.put("udiff", pathPosix + ".udiff.html");
                    entry.put("sdiff", pathPosix + ".sdiff.html");
                }

                indexEntries.add(entry);
                System.out.println("  " + cf.path + " (" + cf.status + ")");
            }

            DiffLineStats.Result lineStats = DiffLineStats.fromUnifiedPatch(patchText);
            String indexHtml = buildIndex(wcRoot.getFileName().toString(), base, head, indexEntries, lineStats);
            IO.writeText(outputDir.resolve("index.html"), indexHtml);
            System.out.println("\nDone. Output: " + outputDir);
            System.out.println("Open " + outputDir.resolve("index.html") + " in a browser.");
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static String buildIndex(String name, String base, String head, List<Map<String, String>> entries,
                                     DiffLineStats.Result lineStats) {
        String summary = """
                <div class="summary">
                <p><strong>Base:</strong> r%s &nbsp;|&nbsp; <strong>Head:</strong> r%s</p>
                <p><strong>Files changed:</strong> %d</p>
                <p><a href="changes.patch">Download patch</a></p>
                %s
                </div>
                """.formatted(Html.escape(base), Html.escape(head), entries.size(),
                Html.lineStatsSummary(lineStats.added(), lineStats.modified(), lineStats.removed()));
        String body = summary + TreeIndex.renderDiff(entries) + "<hr>\n" + Html.indexReviewScript();
        return Html.doc("Code Review - " + name, "Code Review: " + Html.escape(name), "", body, "");
    }

    private static boolean isBinaryLike(String path) {
        String p = path.toLowerCase();
        for (String ext : Defaults.BINARY_EXTENSIONS) {
            if (p.endsWith(ext)) return true;
        }
        return false;
    }

    private static Path getWcRoot(Path path) throws Exception {
        IO.ExecResult res = IO.exec(List.of("svn", "info", "--show-item", "wc-root"), path);
        if (res.exitCode() == 0) {
            String s = res.stdout().trim();
            if (!s.isEmpty()) return Path.of(s);
        }
        return null;
    }

    private static List<ChangedFile> getChangedFiles(Path wcRoot, String base, String head) throws Exception {
        IO.ExecResult res = IO.exec(List.of("svn", "diff", "--summarize", "-r", base + ":" + head), wcRoot);
        if (res.exitCode() != 0) {
            throw new RuntimeException("svn diff failed: " + res.stderr());
        }
        List<ChangedFile> out = new ArrayList<>();
        for (String line : res.stdout().split("\\R")) {
            if (line == null || line.isBlank()) continue;
            String trimmed = line.trim();
            String[] parts = trimmed.split("\\s+", 2);
            String status = parts.length > 0 ? parts[0] : "";
            String p = parts.length > 1 ? parts[1] : "";
            out.add(new ChangedFile(p, status));
        }
        return out;
    }

    private static String getFileContent(Path wcRoot, String rev, String path) {
        if (path == null || path.isBlank()) return "";
        try {
            IO.ExecResult res = IO.exec(List.of("svn", "cat", "-r", rev, path), wcRoot);
            if (res.exitCode() == 0) return res.stdout();
        } catch (Exception ignored) {
        }
        return "";
    }

    private static final class ChangedFile {
        final String path;
        final String status;

        ChangedFile(String path, String status) {
            this.path = path;
            this.status = status == null ? "" : status;
        }
    }
}
