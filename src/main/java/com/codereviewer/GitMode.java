package com.codereviewer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GitMode {
    private GitMode() {}

    static int run(Path root, Path outputDir, String baseArg, String headArg, boolean worktree) {
        try {
            Path repoRoot = getRepoRoot(root);
            if (repoRoot == null) {
                System.err.println("Not a git repository. Use plain mode or specify a git directory.");
                return 1;
            }

            String base = (baseArg == null || baseArg.isBlank()) ? (worktree ? "HEAD" : "HEAD~1") : baseArg;
            String head = worktree ? "WORKTREE" : ((headArg == null || headArg.isBlank()) ? "HEAD" : headArg);

            IO.mkdirs(outputDir);

            String patchText = "";

            List<ChangedFile> changed = worktree ? getWorktreeChangedFiles(repoRoot, base) : getChangedFiles(repoRoot, base, head);
            if (changed.isEmpty()) {
                System.out.println("No changes found between revisions.");
                return 0;
            }

            System.out.println("Processing " + changed.size() + " changed files...");

            // changes.patch
            if (worktree) {
                StringBuilder patchBuf = new StringBuilder();
                IO.ExecResult cached = IO.exec(List.of("git", "diff", "--cached", base), repoRoot);
                if (cached.exitCode() == 0 && !cached.stdout().isBlank()) patchBuf.append(cached.stdout());
                IO.ExecResult unstaged = IO.exec(List.of("git", "diff", base), repoRoot);
                if (unstaged.exitCode() == 0 && !unstaged.stdout().isBlank()) {
                    if (!patchBuf.isEmpty() && !patchBuf.toString().endsWith("\n")) patchBuf.append("\n");
                    patchBuf.append(unstaged.stdout());
                }
                patchText = patchBuf.toString();
                if (!patchText.isBlank()) {
                    IO.writeText(outputDir.resolve("changes.patch"), patchText);
                }
            } else {
                IO.ExecResult patch = IO.exec(List.of("git", "diff", base + "..." + head), repoRoot);
                if (patch.exitCode() == 0) {
                    patchText = patch.stdout();
                    IO.writeText(outputDir.resolve("changes.patch"), patchText);
                }
            }

            List<Map<String, String>> indexEntries = new ArrayList<>();
            for (ChangedFile cf : changed) {
                if (cf.path == null || cf.path.isBlank() || "/dev/null".equals(cf.path)) continue;
                if (isBinaryLike(cf.path)) continue;

                String oldContent = (!cf.status.startsWith("A")) ? getFileContent(repoRoot, base, cf.oldPath) : "";
                String newContent = worktree ? readWorktreeFile(repoRoot, cf.path) : ((!cf.status.startsWith("D")) ? getFileContent(repoRoot, head, cf.path) : "");

                Path relPath = Path.of(cf.path);
                String pathPosix = PlainMode.toPosix(relPath.toString());
                Path outDirForFile = outputDir.resolve(relPath).getParent();
                if (outDirForFile != null) IO.mkdirs(outDirForFile);

                Map<String, String> entry = new HashMap<>();
                entry.put("path", cf.path);
                entry.put("status", cf.status);
                entry.put("old_path", cf.oldPath);

                if (!oldContent.isEmpty()) {
                    Path oldOutRel = Path.of(pathPosix + "-.html");
                    String oldHtml = Html.sourcePage(relPath.toString(), "Old (" + base + ")", oldContent,
                            Lang.languageClassFor(relPath), Html.hrefToIndex(oldOutRel), "old");
                    IO.writeText(outputDir.resolve(oldOutRel), oldHtml);
                    entry.put("old", pathPosix + "-.html");
                }
                if (!newContent.isEmpty()) {
                    Path newOutRel = Path.of(pathPosix + ".html");
                    String newHtml = Html.sourcePage(relPath.toString(), "New (" + head + ")", newContent,
                            Lang.languageClassFor(relPath), Html.hrefToIndex(newOutRel), "new");
                    IO.writeText(outputDir.resolve(newOutRel), newHtml);
                    entry.put("new", pathPosix + ".html");
                }
                if (!oldContent.isEmpty() && !newContent.isEmpty()) {
                    Path udiffOutRel = Path.of(pathPosix + ".udiff.html");
                    Path sdiffOutRel = Path.of(pathPosix + ".sdiff.html");
                    String udiffHtml = DiffViews.unifiedDiffPage(relPath, oldContent, newContent, Html.hrefToIndex(udiffOutRel));
                    String sdiffHtml = DiffViews.sideBySideDiffPage(relPath, oldContent, newContent, base, head,
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
            String indexHtml = buildIndex(repoRoot.getFileName().toString(), base, head, indexEntries, lineStats);
            IO.writeText(outputDir.resolve("index.html"), indexHtml);

            System.out.println("\nDone. Output: " + outputDir);
            System.out.println("Open " + outputDir.resolve("index.html") + " in a browser.");
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static String buildIndex(String repoName, String base, String head, List<Map<String, String>> entries,
                                     DiffLineStats.Result lineStats) {
        String summary = """
                <div class="summary">
                <p><strong>Base:</strong> %s &nbsp;|&nbsp; <strong>Head:</strong> %s</p>
                <p><strong>Files changed:</strong> %d</p>
                <p><a href="changes.patch">Download patch</a></p>
                %s
                </div>
                """.formatted(Html.escape(base), Html.escape(head), entries.size(),
                Html.lineStatsSummary(lineStats.added(), lineStats.modified(), lineStats.removed()));
        String body = summary + TreeIndex.renderDiff(entries) + "<hr>\n" + Html.indexReviewScript()
                + Html.lineCommentScript();
        return Html.doc("Code Review - " + repoName, "Code Review: " + Html.escape(repoName), "", body, "");
    }

    private static boolean isBinaryLike(String path) {
        String p = path.toLowerCase();
        for (String ext : Defaults.BINARY_EXTENSIONS) {
            if (p.endsWith(ext)) return true;
        }
        return false;
    }

    private static Path getRepoRoot(Path path) throws Exception {
        IO.ExecResult res = IO.exec(List.of("git", "rev-parse", "--show-toplevel"), path);
        if (res.exitCode() == 0) {
            String s = res.stdout().trim();
            if (!s.isEmpty()) return Path.of(s);
        }
        return null;
    }

    private static List<ChangedFile> getChangedFiles(Path repoRoot, String base, String head) throws Exception {
        IO.ExecResult res = IO.exec(List.of("git", "diff", "--name-status", base + "..." + head), repoRoot);
        if (res.exitCode() != 0) {
            res = IO.exec(List.of("git", "diff", "--name-status", base, head), repoRoot);
        }
        if (res.exitCode() != 0) {
            throw new RuntimeException("git diff failed: " + res.stderr());
        }

        List<ChangedFile> out = new ArrayList<>();
        for (String line : res.stdout().split("\\R")) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 3);
            String status = parts.length > 0 ? parts[0] : "";
            if (status.startsWith("R") && parts.length > 2) {
                out.add(new ChangedFile(parts[2], status, parts[1]));
            } else {
                String p = parts.length > 1 ? parts[1] : "";
                out.add(new ChangedFile(p, status, p));
            }
        }
        return out;
    }

    private static List<ChangedFile> getWorktreeChangedFiles(Path repoRoot, String base) throws Exception {
        IO.ExecResult staged = IO.exec(List.of("git", "diff", "--name-status", "-M", "--cached", base), repoRoot);
        IO.ExecResult unstaged = IO.exec(List.of("git", "diff", "--name-status", "-M", base), repoRoot);
        if (staged.exitCode() != 0 && unstaged.exitCode() != 0) {
            throw new RuntimeException("git diff failed: " + (unstaged.stderr().isBlank() ? staged.stderr() : unstaged.stderr()));
        }

        Map<String, ChangedFile> merged = new HashMap<>();
        for (ChangedFile cf : parseNameStatus(staged.stdout())) {
            if (cf.path != null && !cf.path.isBlank()) merged.put(cf.path, cf);
        }
        for (ChangedFile cf : parseNameStatus(unstaged.stdout())) {
            if (cf.path != null && !cf.path.isBlank()) merged.put(cf.path, cf);
        }
        return new ArrayList<>(merged.values());
    }

    private static List<ChangedFile> parseNameStatus(String stdout) {
        List<ChangedFile> out = new ArrayList<>();
        if (stdout == null || stdout.isBlank()) return out;
        for (String line : stdout.split("\\R")) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 3);
            String status = parts.length > 0 ? parts[0] : "";
            if (status.startsWith("R") && parts.length > 2) {
                out.add(new ChangedFile(parts[2], status, parts[1]));
            } else {
                String p = parts.length > 1 ? parts[1] : "";
                out.add(new ChangedFile(p, status, p));
            }
        }
        return out;
    }

    private static String getFileContent(Path repoRoot, String rev, String path) {
        if (path == null || path.isBlank()) return "";
        try {
            IO.ExecResult res = IO.exec(List.of("git", "show", rev + ":" + path), repoRoot);
            if (res.exitCode() == 0) return res.stdout();
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String readWorktreeFile(Path repoRoot, String path) {
        if (path == null || path.isBlank()) return "";
        try {
            Path p = repoRoot.resolve(path);
            if (Files.exists(p) && Files.isRegularFile(p)) {
                return IO.readText(p);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static final class ChangedFile {
        final String path;
        final String status;
        final String oldPath;

        ChangedFile(String path, String status, String oldPath) {
            this.path = path;
            this.status = status == null ? "" : status;
            this.oldPath = oldPath == null ? (path == null ? "" : path) : oldPath;
        }
    }
}
