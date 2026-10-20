package com.codereviewer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class TreeIndex {
    private TreeIndex() {}

    static String toolbarHtml() {
        return """
                <div class="toolbar">
                  <button type="button" onclick="codereviewerExpandAll()">Expand all</button>
                  <button type="button" onclick="codereviewerCollapseAll()">Collapse all</button>
                  <button type="button" id="download-review-comments">Download review comments</button>
                </div>
                """;
    }

    static String renderPlain(List<PlainMode.IndexEntry> entries) {
        Node root = Node.root();
        for (PlainMode.IndexEntry e : entries) {
            String p = PlainMode.toPosix(e.relPath().toString());
            root.addPath(p, Map.of("view", PlainMode.toPosix(e.htmlPath().toString())));
        }
        int[] stripe = new int[]{0};
        String content = root.renderPlain("", stripe);
        return """
                <div class="tree">
                  %s
                  <div class="header"><div>File</div><div>Source</div></div>
                  %s
                </div>
                """.formatted(toolbarHtml(), content);
    }

    static String renderDiff(List<Map<String, String>> entries) {
        Node root = Node.root();
        for (Map<String, String> e : entries) {
            String p = e.get("path");
            if (p == null || p.isBlank()) continue;
            String posix = PlainMode.toPosix(p);
            root.addPath(posix, e);
        }
        int[] stripe = new int[]{0};
        String content = root.renderDiff("", stripe);
        return """
                <div class="tree">
                  %s
                  <div class="header"><div>File</div><div>Links</div></div>
                  %s
                </div>
                """.formatted(toolbarHtml(), content);
    }

    private static final class Node {
        final TreeMap<String, Node> dirs = new TreeMap<>();
        final TreeMap<String, Map<String, String>> files = new TreeMap<>();

        static Node root() { return new Node(); }

        void addPath(String posixPath, Map<String, String> payload) {
            if (posixPath == null) return;
            String[] parts = posixPath.split("/");
            Node n = this;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part == null || part.isBlank()) continue;
                boolean last = (i == parts.length - 1);
                if (last) {
                    n.files.put(part, payload);
                } else {
                    n = n.dirs.computeIfAbsent(part, k -> new Node());
                }
            }
        }

        String renderPlain(String basePath, int[] stripe) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Node> d : dirs.entrySet()) {
                String name = d.getKey();
                String full = basePath + name + "/";
                sb.append("<details open data-folder-path=\"")
                        .append(Html.escape(PlainMode.toPosix(full))).append("\"><summary>")
                        .append(Html.escape(name)).append("/</summary>\n")
                        .append(d.getValue().renderPlain(full, stripe))
                        .append("</details>\n");
            }
            for (Map.Entry<String, Map<String, String>> f : files.entrySet()) {
                String fname = f.getKey();
                String rel = basePath + fname;
                String relPosix = PlainMode.toPosix(rel);
                String view = f.getValue().getOrDefault("view", Html.escape(rel) + ".html");
                int idx = stripe[0]++;
                String stripeCls = (idx % 2 == 0) ? "stripe-even" : "stripe-odd";
                sb.append("<div class=\"file-row ").append(stripeCls).append("\" data-file-path=\"")
                        .append(Html.escape(relPosix)).append("\">")
                        .append("<div class=\"file-name\">").append(Html.escape(rel)).append("</div>")
                        .append("<div class=\"file-links\"><a href=\"").append(Html.escape(view)).append("\">View</a></div>")
                        .append("</div>\n");
            }
            return sb.toString();
        }

        String renderDiff(String basePath, int[] stripe) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Node> d : dirs.entrySet()) {
                String name = d.getKey();
                String full = basePath + name + "/";
                sb.append("<details open data-folder-path=\"")
                        .append(Html.escape(PlainMode.toPosix(full))).append("\"><summary>")
                        .append(Html.escape(name)).append("/</summary>\n")
                        .append(d.getValue().renderDiff(full, stripe))
                        .append("</details>\n");
            }
            for (Map.Entry<String, Map<String, String>> f : files.entrySet()) {
                String fname = f.getKey();
                String rel = basePath + fname;
                String relPosix = PlainMode.toPosix(rel);
                Map<String, String> e = f.getValue();
                String status = e.getOrDefault("status", "M");
                String statusKey = status.isEmpty() ? "M" : status.substring(0, 1);
                String cls = switch (statusKey) {
                    case "A" -> "badge-new";
                    case "D" -> "badge-deleted";
                    case "R", "M" -> "badge-modified";
                    default -> "badge-modified";
                };

                String ud = linkOrDash("Udiffs", e.get("udiff"));
                String sd = linkOrDash("Sdiffs", e.get("sdiff"));
                String oldL = linkOrDash("Old", e.get("old"));
                String newL = linkOrDash("New", e.get("new"));

                int idx = stripe[0]++;
                String stripeCls = (idx % 2 == 0) ? "stripe-even" : "stripe-odd";
                sb.append("<div class=\"file-row ").append(stripeCls).append("\" data-file-path=\"")
                        .append(Html.escape(relPosix)).append("\">")
                        .append("<div class=\"file-name\">").append(Html.escape(rel)).append("</div>")
                        .append("<div class=\"file-meta\">")
                        .append(Html.badge(status, cls))
                        .append("<span class=\"file-links\">")
                        .append(ud).append(" ").append(sd).append(" ").append(oldL).append(" ").append(newL)
                        .append("</span>")
                        .append("</div>")
                        .append("</div>\n");
            }
            return sb.toString();
        }

        private static String linkOrDash(String label, String href) {
            if (href == null || href.isBlank()) return "—";
            return "<a href=\"" + Html.escape(PlainMode.toPosix(href)) + "\">" + Html.escape(label) + "</a>";
        }
    }
}

