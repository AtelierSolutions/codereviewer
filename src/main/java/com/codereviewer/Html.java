package com.codereviewer;

import java.nio.file.Path;
import java.util.Map;

final class Html {
    private Html() {}

    static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 32);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#x27;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String doc(String title, String h1, String navHtml, String bodyHtml, String extraHeadHtml) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>%s</style>
                %s
                </head>
                <body>
                <h1>%s</h1>
                %s
                <hr>
                %s
                </body>
                </html>
                """.formatted(escape(title), Defaults.STYLESHEET, extraHeadHtml == null ? "" : extraHeadHtml,
                h1 == null ? "" : h1, navHtml == null ? "" : navHtml, bodyHtml == null ? "" : bodyHtml);
    }

    /**
     * Relative URL from a generated HTML file (path relative to output root) to {@code index.html} at the output root.
     */
    static String hrefToIndex(Path outputFileRel) {
        Path normalized = outputFileRel.normalize();
        int depth = normalized.getNameCount() > 0 ? normalized.getNameCount() - 1 : 0;
        if (depth <= 0) {
            return "index.html";
        }
        return "../".repeat(depth) + "index.html";
    }

    static String navBackToIndex(String indexHref) {
        return "<div class=\"nav\"><a href=\"" + escape(indexHref == null ? "index.html" : indexHref)
                + "\">&#8592; Back to index</a></div>";
    }

    /** Line-change summary for diff-mode index pages (from unified patch). */
    static String lineStatsSummary(int added, int modified, int removed) {
        return """
                <div class="line-stats" aria-label="Line change estimates from patch">
                  <div class="line-stat line-stat-added"><span class="line-stat-label">Lines added</span><span class="line-stat-value">%d</span></div>
                  <div class="line-stat line-stat-modified"><span class="line-stat-label">Lines modified</span><span class="line-stat-value">%d</span></div>
                  <div class="line-stat line-stat-removed"><span class="line-stat-label">Lines removed</span><span class="line-stat-value">%d</span></div>
                </div>
                """.formatted(added, modified, removed);
    }

    /** Inline on index.html only; keep in sync with INDEX_REVIEW_SCRIPT in codereviewer.py. */
    static String indexReviewScript() {
        return """
                <script>
                (function(){
                var KEY='codereviewer-index-review-v1';
                var TREE_KEY='codereviewer-index-tree-v1';
                var ORDER=['','yellow','red','green'];
                function load(){try{return JSON.parse(localStorage.getItem(KEY)||'{}');}catch(e){return {};}}
                function save(m){try{localStorage.setItem(KEY,JSON.stringify(m));}catch(e){}}
                function loadTree(){try{return JSON.parse(localStorage.getItem(TREE_KEY)||'{}');}catch(e){return {};}}
                function saveTree(m){try{localStorage.setItem(TREE_KEY,JSON.stringify(m));}catch(e){}}
                function apply(){
                  var m=load();
                  document.querySelectorAll('.tree .file-row[data-file-path]').forEach(function(row){
                    var p=row.getAttribute('data-file-path');
                    if(m[p]) row.setAttribute('data-review',m[p]); else row.removeAttribute('data-review');
                  });
                }
                function applyTree(){
                  var collapsed=loadTree();
                  document.querySelectorAll('.tree details[data-folder-path]').forEach(function(d){
                    var p=d.getAttribute('data-folder-path');
                    d.open=!collapsed[p];
                  });
                }
                function persistTreeState(){
                  var collapsed={};
                  document.querySelectorAll('.tree details[data-folder-path]').forEach(function(d){
                    var p=d.getAttribute('data-folder-path');
                    if(p&&!d.open) collapsed[p]=true;
                  });
                  saveTree(collapsed);
                }
                function cycle(row){
                  var p=row.getAttribute('data-file-path');
                  if(!p) return;
                  var cur=row.getAttribute('data-review')||'';
                  var i=ORDER.indexOf(cur); if(i<0) i=0;
                  var next=ORDER[(i+1)%ORDER.length];
                  var m=load();
                  if(next) m[p]=next; else delete m[p];
                  save(m);
                  if(next) row.setAttribute('data-review',next); else row.removeAttribute('data-review');
                }
                window.codereviewerExpandAll=function(){
                  document.querySelectorAll('.tree details').forEach(function(d){d.open=true;});
                  saveTree({});
                };
                window.codereviewerCollapseAll=function(){
                  document.querySelectorAll('.tree details').forEach(function(d){d.open=false;});
                  persistTreeState();
                };
                document.addEventListener('DOMContentLoaded',function(){
                  apply();
                  applyTree();
                  var tree=document.querySelector('.tree');
                  if(tree){
                    tree.addEventListener('toggle',function(e){
                      var d=e.target;
                      if(!d||d.tagName!=='DETAILS'||!d.hasAttribute('data-folder-path')) return;
                      persistTreeState();
                    },true);
                  }
                });
                document.addEventListener('click',function(e){
                  var tree=document.querySelector('.tree');
                  if(!tree||!tree.contains(e.target)) return;
                  if(e.target.closest('a')||e.target.closest('button')) return;
                  var row=e.target.closest('.file-row');
                  if(row) cycle(row);
                });
                })();
                </script>
                """;
    }

    static String sourcePage(String displayPath, String titlePrefix, String code, String languageClass, String indexHref) {
        String title = titlePrefix + " - " + displayPath;
        String h1 = escape(titlePrefix) + ": " + escape(displayPath);
        String head = """
                <script>%s</script>
                """.formatted(Defaults.HIGHLIGHT_JS_MIN);
        String codeHtml = "<pre><code class=\"%s\">%s</code></pre>"
                .formatted(escape(languageClass == null ? "" : languageClass), escape(code == null ? "" : code));
        return doc(title, h1, navBackToIndex(indexHref), codeHtml, head);
    }

    static String tableIndex(String[] headers, String rowsHtml) {
        StringBuilder th = new StringBuilder();
        for (String h : headers) {
            th.append("<th>").append(escape(h)).append("</th>");
        }
        return """
                <table class="index">
                <thead><tr>%s</tr></thead>
                <tbody>
                %s
                </tbody>
                </table>
                """.formatted(th, rowsHtml == null ? "" : rowsHtml);
    }

    static String badge(String status, String cls) {
        return "<span class=\"badge %s\">%s</span>".formatted(escape(cls), escape(status));
    }

    static String attrs(Map<String, String> attrs) {
        if (attrs == null || attrs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            if (e.getKey() == null) continue;
            sb.append(" ").append(escape(e.getKey())).append("=\"").append(escape(e.getValue())).append("\"");
        }
        return sb.toString();
    }
}
