package com.codereviewer;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class DiffViews {
    private DiffViews() {}

    static String unifiedDiffText(Path relPath, String oldContent, String newContent) {
        List<String> original = IO.splitLines(oldContent == null ? "" : oldContent);
        List<String> revised = IO.splitLines(newContent == null ? "" : newContent);

        Patch<String> patch = DiffUtils.diff(original, revised);
        List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(
                "a/" + PlainMode.toPosix(relPath.toString()),
                "b/" + PlainMode.toPosix(relPath.toString()),
                original,
                patch,
                3
        );
        if (unified.isEmpty()) return "(no changes)";
        StringBuilder sb = new StringBuilder();
        for (String l : unified) sb.append(l).append('\n');
        return sb.toString();
    }

    static String unifiedDiffPage(Path relPath, String oldContent, String newContent, String indexHref) {
        String diff = unifiedDiffText(relPath, oldContent, newContent);
        String title = "Diff - " + relPath;
        String h1 = "Unified Diff: " + Html.escape(relPath.toString());
        String head = "<script>" + Defaults.HIGHLIGHT_JS_MIN + "</script>";
        String body = "<pre><code class=\"language-diff\">" + Html.escape(diff) + "</code></pre>";
        return Html.doc(title, h1, Html.navBackToIndex(indexHref), body, head);
    }

    static String sideBySideDiffPage(Path relPath, String oldContent, String newContent, String oldLabel, String newLabel,
                                     String indexHref) {
        List<String> original = IO.splitLines(oldContent == null ? "" : oldContent);
        List<String> revised = IO.splitLines(newContent == null ? "" : newContent);

        DiffRowGenerator generator = DiffRowGenerator.create()
                .showInlineDiffs(false)
                .inlineDiffByWord(false)
                .ignoreWhiteSpaces(false)
                .columnWidth(Integer.MAX_VALUE)
                .build();

        List<DiffRow> rows = generator.generateDiffRows(original, revised);

        List<String> oldLineNos = lineNumbers(original.size());
        List<String> newLineNos = lineNumbers(revised.size());
        int oldIdx = 0;
        int newIdx = 0;

        StringBuilder tr = new StringBuilder();
        for (DiffRow r : rows) {
            DiffRow.Tag tag = r.getTag();
            String cls = switch (tag) {
                case EQUAL -> "same";
                case INSERT -> "ins";
                case DELETE -> "del";
                case CHANGE -> "diff";
            };

            String ln1 = "";
            String ln2 = "";
            String c1 = "";
            String c2 = "";

            if (tag == DiffRow.Tag.EQUAL || tag == DiffRow.Tag.DELETE || tag == DiffRow.Tag.CHANGE) {
                if (oldIdx < oldLineNos.size()) ln1 = "<span class=\"" + (tag == DiffRow.Tag.EQUAL ? "context" : "removed") + "\">" + oldLineNos.get(oldIdx) + "</span>";
                oldIdx++;
                c1 = wrapCell(r.getOldLine(), tag == DiffRow.Tag.EQUAL ? "context" : "removed", relPath);
            }
            if (tag == DiffRow.Tag.EQUAL || tag == DiffRow.Tag.INSERT || tag == DiffRow.Tag.CHANGE) {
                if (newIdx < newLineNos.size()) ln2 = "<span class=\"" + (tag == DiffRow.Tag.EQUAL ? "context" : "added") + "\">" + newLineNos.get(newIdx) + "</span>";
                newIdx++;
                c2 = wrapCell(r.getNewLine(), tag == DiffRow.Tag.EQUAL ? "context" : "added", relPath);
            }

            tr.append("<tr class=\"").append(cls).append("\">")
                    .append("<td>").append(ln1).append("</td>")
                    .append("<td>").append(c1).append("</td>")
                    .append("<td>").append(ln2).append("</td>")
                    .append("<td>").append(c2).append("</td>")
                    .append("</tr>\n");
        }

        String table = """
                <table class="sdiff">
                <thead><tr><th>%s</th><th></th><th>%s</th><th></th></tr></thead>
                <tbody>
                %s
                </tbody>
                </table>
                """.formatted(Html.escape(oldLabel), Html.escape(newLabel), tr);

        String extraStyle = """
                <style>
                table.sdiff { border-collapse: collapse; width: 100%; font-family: monospace; font-size: 0.85em; }
                table.sdiff td { padding: 2px 8px; vertical-align: top; border: 1px solid #ddd; }
                table.sdiff td:nth-child(1) { width: 3em; text-align: right; color: #666; }
                table.sdiff td:nth-child(2) { width: 50%; background: #fff; white-space: pre-wrap; word-break: break-word; tab-size: 4; }
                table.sdiff td:nth-child(3) { width: 3em; text-align: right; color: #666; }
                table.sdiff td:nth-child(4) { width: 50%; background: #fff; white-space: pre-wrap; word-break: break-word; tab-size: 4; }
                table.sdiff td:nth-child(2) code, table.sdiff td:nth-child(4) code {
                  white-space: pre-wrap; display: inline-block; width: 100%; box-sizing: border-box;
                  font-family: "Consolas", "Monaco", "Courier New", monospace;
                }
                table.sdiff tr.del td:nth-child(2) { background: #ffe6e6; }
                table.sdiff tr.ins td:nth-child(4) { background: #e6ffe6; }
                table.sdiff tr.diff td:nth-child(2) { background: #ffe6e6; }
                table.sdiff tr.diff td:nth-child(4) { background: #e6ffe6; }
                </style>
                """;

        String title = "Sdiff - " + relPath;
        String h1 = "Side-by-Side Diff: " + Html.escape(relPath.toString());
        String head = extraStyle + "<script>" + Defaults.HIGHLIGHT_JS_MIN + "</script>";
        return Html.doc(title, h1, Html.navBackToIndex(indexHref), table, head);
    }

    private static List<String> lineNumbers(int n) {
        List<String> out = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) out.add(Integer.toString(i));
        return out;
    }

    private static String wrapCell(String line, String cls, Path relPath) {
        String lang = Lang.languageClassFor(relPath);
        String safe = Html.escape(line == null ? "" : line);
        return "<span class=\"" + Html.escape(cls) + "\"><code class=\"" + Html.escape(lang) + "\">" + safe + "</code></span>";
    }
}
