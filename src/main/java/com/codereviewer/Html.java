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

    /** Per-line comments on source pages and index.html. Keep in sync with LINE_COMMENT_SCRIPT in codereviewer.py. */
    static String lineCommentScript() {
        return """
                <script>
                (function(){
                var KEY='codereviewer-line-comments-v1';
                function load(){try{return JSON.parse(localStorage.getItem(KEY)||'{}');}catch(e){return {};}}
                function save(m){try{localStorage.setItem(KEY,JSON.stringify(m));}catch(e){}}
                function viewTag(view){
                  if(view==='old') return '[old] ';
                  if(view==='new') return '[new] ';
                  return '';
                }
                function formatComments(map){
                  var items=[];
                  Object.keys(map||{}).forEach(function(key){
                    var idx=key.indexOf(':');
                    var view=idx<0?'source':key.slice(0,idx);
                    var path=idx<0?key:key.slice(idx+1);
                    var lines=map[key]||{};
                    Object.keys(lines).forEach(function(ln){
                      var text=String(lines[ln]||'').replace(/\\s+$/,'');
                      if(!text.trim()) return;
                      items.push({path:path, view:view, line:parseInt(ln,10)||0, text:text});
                    });
                  });
                  var order={source:0,old:1,new:2};
                  items.sort(function(a,b){
                    if(a.path<b.path) return -1;
                    if(a.path>b.path) return 1;
                    var va=Object.prototype.hasOwnProperty.call(order,a.view)?order[a.view]:9;
                    var vb=Object.prototype.hasOwnProperty.call(order,b.view)?order[b.view]:9;
                    if(va!==vb) return va-vb;
                    return a.line-b.line;
                  });
                  var body=items.map(function(it){
                    var parts=it.text.split(/\\r?\\n/);
                    var first=it.path+':'+it.line+': '+viewTag(it.view)+parts[0];
                    var rest=parts.slice(1).map(function(l){return '  '+l;});
                    return [first].concat(rest).join('\\n');
                  }).join('\\n');
                  var n=items.length;
                  var noun=n===1?'item':'items';
                  var out='Review comments\\n'+n+' '+noun+'. Each line is path:line: comment.\\n\\n';
                  if(body) out+=body+'\\n';
                  return out;
                }
                function download(text){
                  var blob=new Blob([text],{type:'text/plain;charset=utf-8'});
                  var a=document.createElement('a');
                  a.href=URL.createObjectURL(blob);
                  a.download='review-comments.txt';
                  document.body.appendChild(a);
                  a.click();
                  a.remove();
                  setTimeout(function(){URL.revokeObjectURL(a.href);},0);
                }
                function setComment(path, view, line, text){
                  var m=load();
                  var key=view+':'+path;
                  if(!m[key]) m[key]={};
                  if(text) m[key][String(line)]=text;
                  else delete m[key][String(line)];
                  if(!Object.keys(m[key]).length) delete m[key];
                  save(m);
                }
                function bindSource(table){
                  var path=table.getAttribute('data-file-path')||'';
                  var view=table.getAttribute('data-view')||'source';
                  var stored=(load()[view+':'+path])||{};
                  table.querySelectorAll('tr.source-line[data-line]').forEach(function(row){
                    var line=row.getAttribute('data-line');
                    var editor=table.querySelector('tr.comment-row[data-line="'+line+'"]');
                    var btn=row.querySelector('.ln-btn');
                    var ta=editor?editor.querySelector('textarea'):null;
                    if(stored[line]){
                      if(ta) ta.value=stored[line];
                      row.classList.add('has-comment');
                      if(editor) editor.removeAttribute('hidden');
                    }
                    if(btn && editor){
                      btn.addEventListener('click', function(){
                        if(editor.hasAttribute('hidden')){
                          editor.removeAttribute('hidden');
                          if(ta) ta.focus();
                        } else {
                          editor.setAttribute('hidden','');
                        }
                      });
                    }
                    if(ta){
                      ta.addEventListener('input', function(){
                        var text=ta.value.replace(/\\s+$/,'');
                        if(text.trim()){
                          setComment(path, view, line, text);
                          row.classList.add('has-comment');
                        } else {
                          setComment(path, view, line, '');
                          row.classList.remove('has-comment');
                        }
                      });
                    }
                  });
                }
                function bindDownload(){
                  var btn=document.getElementById('download-review-comments');
                  if(!btn || btn.getAttribute('data-bound')) return;
                  btn.setAttribute('data-bound','1');
                  btn.addEventListener('click', function(){ download(formatComments(load())); });
                }
                document.addEventListener('DOMContentLoaded', function(){
                  var table=document.querySelector('table.source');
                  if(table) bindSource(table);
                  bindDownload();
                });
                })();
                </script>
                """;
    }

    static String reviewActions() {
        return """
                <div class="review-actions">
                <button type="button" id="download-review-comments">Download review comments</button>
                </div>
                """;
    }

    static String sourcePage(String displayPath, String titlePrefix, String code, String languageClass,
                             String indexHref, String view) {
        String safeView = "old".equals(view) || "new".equals(view) ? view : "source";
        String filePath = PlainMode.toPosix(displayPath == null ? "" : displayPath);
        String lang = escape(languageClass == null ? "" : languageClass);
        var lines = Syntax.highlightLines(languageClass, code);
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            int n = i + 1;
            rows.append("<tr class=\"source-line\" data-line=\"").append(n).append("\">")
                    .append("<td class=\"ln\"><button type=\"button\" class=\"ln-btn\" id=\"L").append(n)
                    .append("\" aria-label=\"Comment on line ").append(n).append("\">").append(n).append("</button></td>")
                    .append("<td class=\"code\"><code class=\"").append(lang).append("\">")
                    .append(lines.get(i)).append("</code></td></tr>\n")
                    .append("<tr class=\"comment-row\" data-line=\"").append(n).append("\" hidden>")
                    .append("<td class=\"ln\"></td><td><textarea aria-label=\"Comment for line ").append(n)
                    .append("\" data-line=\"").append(n).append("\"></textarea></td></tr>\n");
        }
        String table = """
                <table class="source" data-file-path="%s" data-view="%s">
                <tbody>
                %s
                </tbody>
                </table>
                """.formatted(escape(filePath), escape(safeView), rows);
        String title = titlePrefix + " - " + (displayPath == null ? "" : displayPath);
        String h1 = escape(titlePrefix) + ": " + escape(displayPath);
        String head = "<script>" + Defaults.HIGHLIGHT_JS_MIN + "</script>";
        String body = reviewActions() + "\n" + table + "\n" + lineCommentScript();
        return doc(title, h1, navBackToIndex(indexHref), body, head);
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
