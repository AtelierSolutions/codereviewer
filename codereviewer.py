#!/usr/bin/env python3
"""
Code Reviewer - An offline codereview utility for generating HTML code reviews with syntax highlighting.

Supports:
- Plain directory: Generate HTML versions of all code files with syntax highlighting
- Git repository: Generate diffs between revisions (--base, --head) with per-file views
- Subversion repository: Generate diffs between revisions (--base, --head) with per-file views
"""

import argparse
import difflib
import html
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Optional

try:
    from pygments import highlight
    from pygments.lexers import get_lexer_for_filename, guess_lexer_for_filename, TextLexer
    from pygments.formatters import HtmlFormatter
    from pygments.util import ClassNotFound
except ImportError:
    print("Error: Pygments is required. Install with: pip install pygments", file=sys.stderr)
    sys.exit(1)


# Default code file extensions to include (can be overridden)
DEFAULT_EXTENSIONS = {
    ".py", ".js", ".ts", ".tsx", ".jsx", ".java", ".kt", ".kts", ".scala", ".groovy",
    ".c", ".cpp", ".cc", ".cxx", ".h", ".hpp", ".hxx", ".cs", ".go", ".rs", ".rb",
    ".php", ".swift", ".m", ".mm", ".r", ".R", ".sql", ".sh", ".bash", ".zsh",
    ".html", ".htm", ".css", ".scss", ".sass", ".less", ".xml", ".json", ".yaml", ".yml",
    ".md", ".rst", ".tex", ".vue", ".svelte", ".dart", ".lua", ".pl", ".pm",
    ".make", ".mk", ".cmake", ".dockerfile", ".dockerignore", ".gitignore",
}


STYLESHEET = """
body { background-color: #f8f9fa; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 1em 2em; }
h1 { color: #333; border-bottom: 2px solid #0066cc; padding-bottom: 0.3em; }
h2 { color: #555; margin-top: 1.5em; }
hr { border: none; border-top: 1px solid #aaa; margin: 1.5em 0; }
pre { font-size: 0.85em; font-family: "Consolas", "Monaco", "Courier New", monospace; 
      background: #fff; padding: 1em; overflow-x: auto; border: 1px solid #ddd; border-radius: 4px; }
code { font-family: "Consolas", "Monaco", "Courier New", monospace; }
span.removed { color: #c00; background-color: #ffe6e6; }
span.added { color: #0a0; background-color: #e6ffe6; }
span.context { color: #666; }
a { color: #0066cc; text-decoration: none; }
a:hover { text-decoration: underline; }
table.index { border-collapse: collapse; width: 100%; margin-top: 1em; }
table.index th, table.index td { padding: 0.5em 1em; text-align: left; border-bottom: 1px solid #ddd; }
table.index th { background: #e9ecef; font-weight: 600; }
table.index tr:hover { background: #f1f3f5; }
.nav { margin: 1em 0; font-size: 0.9em; }
.badge { display: inline-block; padding: 0.2em 0.5em; border-radius: 4px; font-size: 0.8em; font-weight: 600; }
.badge-new { background: #c8e6c9; color: #2e7d32; }
.badge-modified { background: #bbdefb; color: #1565c0; }
.badge-deleted { background: #ffcdd2; color: #c62828; }
.summary { background: #fff; padding: 1em; border-radius: 4px; border: 1px solid #ddd; margin: 1em 0; }
.line-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1em; margin-top: 0.75em; max-width: 42rem; }
.line-stat { background: #fafbfc; border: 1px solid #dde3e8; border-radius: 6px; padding: 0.5em 0.75em; text-align: center; }
.line-stat-label { display: block; font-size: 0.8em; color: #555; margin-bottom: 0.25em; }
.line-stat-value { font-size: 1.25em; font-weight: 700; font-family: "Consolas", "Monaco", monospace; }
.line-stat-added .line-stat-value { color: #2e7d32; }
.line-stat-modified .line-stat-value { color: #1565c0; }
.line-stat-removed .line-stat-value { color: #c62828; }

/* Collapsible file tree */
.tree { margin-top: 1em; font-size: 0.95em; }
.tree details { margin-left: 1.25em; }
.tree details > summary { cursor: pointer; user-select: none; padding: 0.15em 0; }
.tree details > summary:hover { text-decoration: underline; }
.tree .file-row { display: grid; grid-template-columns: 1fr auto; gap: 1em; padding: 0.35em 0.5em; margin-left: 2.5em;
  border-bottom: 1px solid #dde3e8; cursor: pointer; user-select: none; border-radius: 4px; }
.tree .file-row.stripe-even:not([data-review]) { background: #e8eef2; }
.tree .file-row.stripe-odd:not([data-review]) { background: #e3f2fd; }
.tree .file-row[data-review="yellow"] { background: #fff9c4 !important; border-left: 4px solid #f9a825; }
.tree .file-row[data-review="red"] { background: #ffcdd2 !important; border-left: 4px solid #c62828; }
.tree .file-row[data-review="green"] { background: #c8e6c9 !important; border-left: 4px solid #2e7d32; }
.tree .file-name { font-family: "Consolas", "Monaco", "Courier New", monospace; }
.tree .file-links a { margin-left: 0.75em; white-space: nowrap; }
.tree .file-links a:first-child { margin-left: 0; }
.tree .file-meta { display: inline-flex; gap: 0.5em; align-items: center; flex-wrap: wrap; justify-content: flex-end; }
.tree .header { display: grid; grid-template-columns: 1fr auto; gap: 1em; padding: 0.4em 0; border-bottom: 1px solid #ddd; font-weight: 600; }
.tree .header div:last-child { text-align: right; }
.tree .toolbar { margin: 0.75em 0; display: flex; gap: 0.75em; flex-wrap: wrap; }
.tree button { border: 1px solid #bbb; background: #fff; border-radius: 4px; padding: 0.25em 0.6em; cursor: pointer; }
.tree button:hover { background: #f1f3f5; }

/* Per-line source comments */
table.source { border-collapse: collapse; width: 100%; background: #fff; border: 1px solid #ddd; border-radius: 4px; font-size: 0.85em; }
table.source td { vertical-align: top; }
table.source td.ln { width: 1%; white-space: nowrap; text-align: right; border-right: 1px solid #e6e6e6; background: #fafbfc; }
table.source button.ln-btn { border: 0; background: transparent; color: #666; font: inherit; font-family: "Consolas", "Monaco", "Courier New", monospace; cursor: pointer; padding: 0 0.65em; min-width: 3em; text-align: right; }
table.source button.ln-btn:hover { color: #0066cc; background: #eef5fb; }
table.source tr.has-comment > td.ln { background: #e3f2fd; }
table.source tr.has-comment > td.ln button.ln-btn { color: #1565c0; font-weight: 700; }
table.source td.code { padding: 0 0.75em; white-space: pre; background: #fff; }
table.source td.code code { white-space: pre; font-family: "Consolas", "Monaco", "Courier New", monospace; }
table.source td.code code.hljs { padding: 0; display: inline; background: transparent; }
table.source tr.comment-row td { background: #fff8e1; padding: 0.35em 0.75em 0.5em; }
table.source tr.comment-row textarea { width: 100%; box-sizing: border-box; min-height: 3.2em; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; font-size: 0.95em; border: 1px solid #e0c36a; border-radius: 4px; padding: 0.4em 0.5em; }
.review-actions { margin: 0.75em 0; display: flex; gap: 0.75em; flex-wrap: wrap; align-items: center; }
.review-actions button { border: 1px solid #bbb; background: #fff; border-radius: 4px; padding: 0.25em 0.6em; cursor: pointer; }
.review-actions button:hover { background: #f1f3f5; }
"""

# Inline on index.html only: row review marks + folder expand state (keep in sync with Html.indexReviewScript() in Java).
INDEX_REVIEW_SCRIPT = """
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
"""

# Inline on source pages and index.html. Keep in sync with Html.lineCommentScript() in Java.
LINE_COMMENT_SCRIPT = r"""
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
      var text=String(lines[ln]||'').replace(/\s+$/,'');
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
    var parts=it.text.split(/\r?\n/);
    var first=it.path+':'+it.line+': '+viewTag(it.view)+parts[0];
    var rest=parts.slice(1).map(function(l){return '  '+l;});
    return [first].concat(rest).join('\n');
  }).join('\n');
  var n=items.length;
  var noun=n===1?'item':'items';
  var out='Review comments\n'+n+' '+noun+'. Each line is path:line: comment.\n\n';
  if(body) out+=body+'\n';
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
        var text=ta.value.replace(/\s+$/,'');
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
"""

INDEX_TOOLBAR = """  <div class="toolbar">
    <button type="button" onclick="codereviewerExpandAll()">Expand all</button>
    <button type="button" onclick="codereviewerCollapseAll()">Collapse all</button>
    <button type="button" id="download-review-comments">Download review comments</button>
  </div>"""

REVIEW_ACTIONS = """<div class="review-actions">
<button type="button" id="download-review-comments">Download review comments</button>
</div>"""


def href_to_index(output_file_rel: Path) -> str:
    """Relative URL from a generated HTML file to index.html at the output root."""
    depth = len(output_file_rel.parent.parts)
    prefix = "../" * depth
    return f"{prefix}index.html"


def partition_line_stats_from_patch(patch: str) -> tuple[int, int, int]:
    """
    From a unified diff: per file-chunk, min(insertions, deletions) counts as modified;
    remaining + lines are added, remaining - lines removed. Returns (added, modified, removed).
    """
    if not patch or not patch.strip():
        return (0, 0, 0)
    if re.search(r"^diff --git ", patch, re.MULTILINE):
        chunks = re.split(r"(?m)^(?=diff --git )", patch)
    elif re.search(r"^Index: ", patch, re.MULTILINE):
        chunks = re.split(r"(?m)^(?=Index: )", patch)
    else:
        chunks = [patch]
    total_added = total_modified = total_removed = 0
    for chunk in chunks:
        if not chunk.strip():
            continue
        a = r = 0
        for line in chunk.splitlines():
            if line.startswith("+++") or line.startswith("---"):
                continue
            if line.startswith("@@"):
                continue
            if line.startswith("\\"):
                continue
            if line.startswith("+"):
                a += 1
            elif line.startswith("-"):
                r += 1
        m = min(a, r)
        total_added += a - m
        total_removed += r - m
        total_modified += m
    return (total_added, total_modified, total_removed)


def format_line_stats_html(added: int, modified: int, removed: int) -> str:
    """HTML fragment for index summary (diff modes)."""
    return f"""<div class="line-stats" aria-label="Line change estimates from patch">
  <div class="line-stat line-stat-added"><span class="line-stat-label">Lines added</span><span class="line-stat-value">{added}</span></div>
  <div class="line-stat line-stat-modified"><span class="line-stat-label">Lines modified</span><span class="line-stat-value">{modified}</span></div>
  <div class="line-stat line-stat-removed"><span class="line-stat-label">Lines removed</span><span class="line-stat-value">{removed}</span></div>
</div>
"""


def build_file_tree(paths: list[str]) -> dict:
    """Build a nested dict tree from posix-like paths."""
    root: dict = {"_files": []}
    for p in paths:
        parts = [x for x in p.split("/") if x]
        node = root
        for part in parts[:-1]:
            node = node.setdefault(part, {"_files": []})
        node["_files"].append(parts[-1])
    return root


def render_plain_tree(tree: dict, base_path: str, stripe_counter: list[int]) -> str:
    """Render collapsible tree for plain mode."""
    html_parts: list[str] = []

    # directories
    for name in sorted(k for k in tree.keys() if k != "_files"):
        sub = tree[name]
        full = f"{base_path}{name}/"
        attr_folder = html.escape(full.replace("\\", "/"))
        html_parts.append(
            f'<details open data-folder-path="{attr_folder}"><summary>{html.escape(name)}/</summary>'
        )
        html_parts.append(render_plain_tree(sub, full, stripe_counter))
        html_parts.append("</details>")

    # files
    for fname in sorted(tree.get("_files", [])):
        rel = f"{base_path}{fname}"
        link = f"{html.escape(rel)}.html"
        idx = stripe_counter[0]
        stripe_counter[0] += 1
        stripe_cls = "stripe-even" if idx % 2 == 0 else "stripe-odd"
        attr_path = html.escape(rel.replace("\\", "/"))
        html_parts.append(
            f'<div class="file-row {stripe_cls}" data-file-path="{attr_path}">'
            f'<div class="file-name">{html.escape(rel)}</div>'
            f'<div class="file-links"><a href="{link}">View</a></div>'
            "</div>"
        )
    return "\n".join(html_parts)


def render_diff_tree(tree: dict, entries_by_path: dict[str, dict], base_path: str, stripe_counter: list[int]) -> str:
    """Render collapsible tree for git/svn diff mode."""
    html_parts: list[str] = []

    for name in sorted(k for k in tree.keys() if k != "_files"):
        sub = tree[name]
        full = f"{base_path}{name}/"
        attr_folder = html.escape(full.replace("\\", "/"))
        html_parts.append(
            f'<details open data-folder-path="{attr_folder}"><summary>{html.escape(name)}/</summary>'
        )
        html_parts.append(render_diff_tree(sub, entries_by_path, full, stripe_counter))
        html_parts.append("</details>")

    for fname in sorted(tree.get("_files", [])):
        rel = f"{base_path}{fname}"
        e = entries_by_path.get(rel, {})
        status = e.get("status", "M")
        status_badges = {"A": "badge-new", "M": "badge-modified", "D": "badge-deleted", "R": "badge-modified"}
        status_cls = status_badges.get(status[0], "badge-modified")

        def link(label: str, key: str) -> str:
            return f'<a href="{html.escape(e[key])}">{label}</a>' if key in e else "—"

        links = " ".join([
            link("Udiffs", "udiff"),
            link("Sdiffs", "sdiff"),
            link("Old", "old"),
            link("New", "new"),
        ])

        idx = stripe_counter[0]
        stripe_counter[0] += 1
        stripe_cls = "stripe-even" if idx % 2 == 0 else "stripe-odd"
        attr_path = html.escape(rel.replace("\\", "/"))
        html_parts.append(
            f'<div class="file-row {stripe_cls}" data-file-path="{attr_path}">'
            f'<div class="file-name">{html.escape(rel)}</div>'
            f'<div class="file-meta"><span class="badge {status_cls}">{html.escape(status)}</span>'
            f'<span class="file-links">{links}</span></div>'
            "</div>"
        )

    return "\n".join(html_parts)


def detect_vcs(root: Path) -> Optional[str]:
    """Detect if directory is a git or subversion repository."""
    if (root / ".git").exists():
        return "git"
    if (root / ".svn").exists():
        return "svn"
    # Check parent directories for git
    for parent in root.parents:
        if (parent / ".git").exists():
            return "git"
        if (parent / ".svn").exists():
            return "svn"
    return None


def get_lexer_for_file(filepath: Path, content: str):
    """Get appropriate Pygments lexer for a file."""
    try:
        return guess_lexer_for_filename(str(filepath), content)
    except ClassNotFound:
        try:
            return get_lexer_for_filename(str(filepath))
        except ClassNotFound:
            return TextLexer()


def highlighted_source_lines(filepath: Path, content: str) -> list[str]:
    """Pygments HTML for each source line. A trailing newline does not add a row."""
    lines = content.splitlines()
    if not lines:
        return []
    lexer = get_lexer_for_file(filepath, content)
    formatter = HtmlFormatter(nowrap=True)
    highlighted = highlight(content, lexer, formatter)
    parts = highlighted.split("\n")
    if len(parts) == len(lines) + 1 and parts[-1] == "":
        parts = parts[:-1]
    if len(parts) != len(lines):
        return [html.escape(line) for line in lines]
    return parts


def render_source_table(filepath: Path, content: str, view: str) -> str:
    """One row per source line, with a line-number button and a hidden comment row."""
    file_path = filepath.as_posix()
    rows: list[str] = []
    for i, line_html in enumerate(highlighted_source_lines(filepath, content), start=1):
        rows.append(
            f'<tr class="source-line" data-line="{i}">'
            f'<td class="ln"><button type="button" class="ln-btn" id="L{i}" '
            f'aria-label="Comment on line {i}">{i}</button></td>'
            f'<td class="code">{line_html}</td></tr>'
            f'<tr class="comment-row" data-line="{i}" hidden>'
            f'<td class="ln"></td>'
            f'<td><textarea aria-label="Comment for line {i}" data-line="{i}"></textarea></td></tr>'
        )
    body = "\n".join(rows)
    return (
        f'<div class="highlight"><table class="source" data-file-path="{html.escape(file_path)}" '
        f'data-view="{html.escape(view)}">\n<tbody>\n{body}\n</tbody>\n</table></div>'
    )


def source_to_html(
    filepath: Path,
    content: str,
    title: str = "Source",
    index_href: str = "index.html",
    view: str = "source",
) -> str:
    """Convert source code to HTML with syntax highlighting and per-line comments."""
    if view not in {"source", "old", "new"}:
        view = "source"
    css = HtmlFormatter(cssclass="highlight").get_style_defs(".highlight")
    table = render_source_table(filepath, content, view)
    return f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>{html.escape(title)} - {html.escape(str(filepath))}</title>
<style>{css}</style>
<style>{STYLESHEET}</style>
</head>
<body>
<h1>{html.escape(title)}: {html.escape(str(filepath))}</h1>
<div class="nav"><a href="{html.escape(index_href)}">← Back to index</a></div>
<hr>
{REVIEW_ACTIONS}
{table}
{LINE_COMMENT_SCRIPT}
</body>
</html>"""


def diff_to_html_unified(
    filepath: Path,
    old_content: str,
    new_content: str,
    old_label: str = "Old",
    new_label: str = "New",
    index_href: str = "index.html",
) -> str:
    """Generate unified diff HTML with syntax highlighting."""
    diff_lines = list(difflib.unified_diff(
        old_content.splitlines(keepends=True),
        new_content.splitlines(keepends=True),
        fromfile=f"a/{filepath}",
        tofile=f"b/{filepath}",
        lineterm="",
    ))
    diff_text = "".join(diff_lines) if diff_lines else "(no changes)"
    
    lexer = get_lexer_for_file(Path("diff.diff"), diff_text)
    formatter = HtmlFormatter(
        full=False,
        cssclass="highlight",
        linenos=False,
    )
    highlighted = highlight(diff_text, lexer, formatter)
    css = formatter.get_style_defs(".highlight")
    
    return f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Diff - {html.escape(str(filepath))}</title>
<style>{css}</style>
<style>{STYLESHEET}</style>
</head>
<body>
<h1>Unified Diff: {html.escape(str(filepath))}</h1>
<div class="nav"><a href="{html.escape(index_href)}">← Back to index</a></div>
<hr>
{highlighted}
</body>
</html>"""


def diff_to_html_side_by_side(
    filepath: Path,
    old_content: str,
    new_content: str,
    old_label: str = "Old",
    new_label: str = "New",
    index_href: str = "index.html",
) -> str:
    """Generate side-by-side diff HTML."""
    old_lines = old_content.splitlines()
    new_lines = new_content.splitlines()
    matcher = difflib.SequenceMatcher(None, old_lines, new_lines)
    
    def get_lexer():
        return get_lexer_for_file(filepath, content)
    
    def highlight_line(line: str, filepath: Path):
        try:
            lexer = get_lexer_for_file(filepath, line)
            # nowrap=True emits white-space: nowrap which collapses runs of spaces; keep whitespace visible.
            formatter = HtmlFormatter(full=False, cssclass="highlight", nowrap=False, stripnl=False)
            return highlight(line or " ", lexer, formatter)
        except Exception:
            return f'<span class="sdiff-plain">{html.escape(line or "")}</span>'
    
    rows = []
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag == "equal":
            for i in range(i1, i2):
                rows.append((
                    f'<span class="context">{i+1}</span>',
                    highlight_line(old_lines[i], filepath),
                    f'<span class="context">{i+1}</span>',
                    highlight_line(old_lines[i], filepath),
                    "same",
                ))
        elif tag == "replace":
            max_len = max(i2 - i1, j2 - j1)
            for k in range(max_len):
                old_line = old_lines[i1 + k] if i1 + k < i2 else ""
                new_line = new_lines[j1 + k] if j1 + k < j2 else ""
                old_ln = i1 + k + 1 if i1 + k < i2 else ""
                new_ln = j1 + k + 1 if j1 + k < j2 else ""
                rows.append((
                    f'<span class="removed">{old_ln}</span>',
                    f'<span class="removed">{highlight_line(old_line, filepath)}</span>',
                    f'<span class="added">{new_ln}</span>',
                    f'<span class="added">{highlight_line(new_line, filepath)}</span>',
                    "diff",
                ))
        elif tag == "delete":
            for i in range(i1, i2):
                rows.append((
                    f'<span class="removed">{i+1}</span>',
                    f'<span class="removed">{highlight_line(old_lines[i], filepath)}</span>',
                    "",
                    "",
                    "del",
                ))
        elif tag == "insert":
            for j in range(j1, j2):
                rows.append((
                    "",
                    "",
                    f'<span class="added">{j+1}</span>',
                    f'<span class="added">{highlight_line(new_lines[j], filepath)}</span>',
                    "ins",
                ))
    
    table_rows = "".join(
        f'<tr class="{cls}"><td>{ln1}</td><td>{c1}</td><td>{ln2}</td><td>{c2}</td></tr>'
        for ln1, c1, ln2, c2, cls in rows
    )
    
    return f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Sdiff - {html.escape(str(filepath))}</title>
<style>{STYLESHEET}</style>
<style>
table.sdiff {{ border-collapse: collapse; width: 100%; font-family: monospace; font-size: 0.85em; }}
table.sdiff td {{ padding: 2px 8px; vertical-align: top; border: 1px solid #ddd; }}
table.sdiff td:nth-child(1) {{ width: 3em; text-align: right; color: #666; }}
table.sdiff td:nth-child(2) {{ width: 50%; background: #fff; white-space: pre-wrap; word-break: break-word; tab-size: 4; }}
table.sdiff td:nth-child(3) {{ width: 3em; text-align: right; color: #666; }}
table.sdiff td:nth-child(4) {{ width: 50%; background: #fff; white-space: pre-wrap; word-break: break-word; tab-size: 4; }}
table.sdiff td:nth-child(2) .highlight,
table.sdiff td:nth-child(4) .highlight {{ white-space: pre-wrap; }}
table.sdiff td:nth-child(2) .highlight pre,
table.sdiff td:nth-child(4) .highlight pre {{ white-space: pre-wrap !important; word-wrap: break-word; margin: 0; padding: 0; border: none; background: transparent; }}
table.sdiff .sdiff-plain {{ white-space: pre-wrap; font-family: "Consolas", "Monaco", "Courier New", monospace; }}
table.sdiff tr.del td:nth-child(2) {{ background: #ffe6e6; }}
table.sdiff tr.ins td:nth-child(4) {{ background: #e6ffe6; }}
table.sdiff tr.diff td:nth-child(2) {{ background: #ffe6e6; }}
table.sdiff tr.diff td:nth-child(4) {{ background: #e6ffe6; }}
</style>
</head>
<body>
<h1>Side-by-Side Diff: {html.escape(str(filepath))}</h1>
<div class="nav"><a href="{html.escape(index_href)}">← Back to index</a></div>
<hr>
<table class="sdiff">
<thead><tr><th>{html.escape(old_label)}</th><th></th><th>{html.escape(new_label)}</th><th></th></tr></thead>
<tbody>
{table_rows}
</tbody>
</table>
</body>
</html>"""


def collect_files(root: Path, extensions: set, exclude_dirs: set) -> list[Path]:
    """Collect code files from directory."""
    files = []
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        if any(part in exclude_dirs for part in path.relative_to(root).parts):
            continue
        if path.suffix.lower() in extensions or path.name.lower() in extensions:
            files.append(path.relative_to(root))
    return sorted(files)


def run_plain_mode(args: argparse.Namespace) -> None:
    """Generate HTML for all code files in directory (no VCS)."""
    root = Path(args.directory).resolve()
    output_dir = Path(args.output).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    
    extensions = set(e.lower() if e.startswith(".") else f".{e}" for e in args.extensions)
    exclude_dirs = {"__pycache__", ".git", ".svn", "node_modules", ".venv", "venv", "dist", "build"}
    
    files = collect_files(root, extensions, exclude_dirs)
    if not files:
        print(f"No code files found in {root}", file=sys.stderr)
        return
    
    print(f"Processing {len(files)} files...")
    
    index_entries = []
    for rel_path in files:
        full_path = root / rel_path
        try:
            content = full_path.read_text(encoding="utf-8", errors="replace")
        except Exception as e:
            print(f"  Skipping {rel_path}: {e}", file=sys.stderr)
            continue
        
        out_path = output_dir / rel_path.with_suffix(rel_path.suffix + ".html")
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_rel = rel_path.with_suffix(rel_path.suffix + ".html")

        html_content = source_to_html(rel_path, content, "Source", href_to_index(out_rel), view="source")
        out_path.write_text(html_content, encoding="utf-8")
        
        index_entries.append((str(rel_path), rel_path.with_suffix(rel_path.suffix + ".html")))
        print(f"  {rel_path}")
    
    # Write index
    tree = build_file_tree([name.replace("\\", "/") for name, _ in index_entries])
    tree_html = render_plain_tree(tree, "", [0])
    index_html = f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Code Review - {html.escape(str(root.name))}</title>
<style>{STYLESHEET}</style>
</head>
<body>
<h1>Code Review: {html.escape(str(root.name))}</h1>
<p>Prepared for offline review. {len(index_entries)} files with syntax highlighting.</p>
<div class="tree">
{INDEX_TOOLBAR}
  <div class="header"><div>File</div><div>Source</div></div>
  {tree_html}
</div>
<hr>
{INDEX_REVIEW_SCRIPT}
{LINE_COMMENT_SCRIPT}
</body></html>"""
    
    (output_dir / "index.html").write_text(index_html, encoding="utf-8")
    print(f"\nDone. Output: {output_dir}")
    print(f"Open {output_dir / 'index.html'} in a browser.")


def get_git_repo_root(path: Path) -> Optional[Path]:
    """Find git repository root."""
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        cwd=path,
        capture_output=True,
        text=True,
    )
    if result.returncode == 0:
        return Path(result.stdout.strip())
    return None


def get_git_changed_files(root: Path, base: str, head: str) -> list[tuple[str, str, str]]:
    """Get list of (path, status, old_path) for changed files between base and head."""
    result = subprocess.run(
        ["git", "diff", "--name-status", f"{base}...{head}"],
        cwd=root,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        result = subprocess.run(
            ["git", "diff", "--name-status", base, head],
            cwd=root,
            capture_output=True,
            text=True,
        )
    if result.returncode != 0:
        raise RuntimeError(f"git diff failed: {result.stderr}")
    
    files = []
    for line in result.stdout.strip().splitlines():
        if not line.strip():
            continue
        parts = line.split("\t", 2)
        status = parts[0]
        if status.startswith("R") and len(parts) > 2:
            # Rename: R100 old_path new_path
            old_path, path = parts[1], parts[2]
        else:
            path = parts[1] if len(parts) > 1 else ""
            old_path = path
        files.append((path, status, old_path))
    return files


def get_git_worktree_changed_files(root: Path, base: str) -> list[tuple[str, str, str]]:
    """Get list of (path, status, old_path) for changes from base -> index/worktree."""
    def parse(name_status: str) -> list[tuple[str, str, str]]:
        out: list[tuple[str, str, str]] = []
        for line in name_status.strip().splitlines():
            if not line.strip():
                continue
            parts = line.split("\t", 2)
            status = parts[0]
            if status.startswith("R") and len(parts) > 2:
                old_path, path = parts[1], parts[2]
            else:
                path = parts[1] if len(parts) > 1 else ""
                old_path = path
            out.append((path, status, old_path))
        return out

    unstaged = subprocess.run(
        ["git", "diff", "--name-status", "-M", base],
        cwd=root,
        capture_output=True,
        text=True,
    )
    staged = subprocess.run(
        ["git", "diff", "--name-status", "-M", "--cached", base],
        cwd=root,
        capture_output=True,
        text=True,
    )
    if unstaged.returncode != 0 and staged.returncode != 0:
        raise RuntimeError(f"git diff failed: {unstaged.stderr or staged.stderr}")

    merged: dict[str, tuple[str, str, str]] = {}
    for path, status, old_path in parse(staged.stdout):
        if path:
            merged[path] = (path, status, old_path)
    for path, status, old_path in parse(unstaged.stdout):
        if path:
            merged[path] = (path, status, old_path)
    return list(merged.values())


def get_worktree_file_content(repo_root: Path, rel_path: str) -> str:
    """Read file from disk (worktree)."""
    try:
        p = repo_root / rel_path
        if p.exists() and p.is_file():
            return p.read_text(encoding="utf-8", errors="replace")
    except Exception:
        pass
    return ""


def get_git_file_content(root: Path, rev: str, path: str) -> str:
    """Get file content at given revision."""
    try:
        result = subprocess.run(
            ["git", "show", f"{rev}:{path}"],
            cwd=root,
            capture_output=True,
            text=True,
        )
        if result.returncode == 0:
            return result.stdout
    except Exception:
        pass
    return ""


def run_git_mode(args: argparse.Namespace) -> None:
    """Generate review for Git repository."""
    root = Path(args.directory).resolve()
    repo_root = get_git_repo_root(root)
    if not repo_root:
        print("Not a git repository. Use plain mode or specify a git directory.", file=sys.stderr)
        sys.exit(1)
    
    worktree = bool(getattr(args, "worktree", False))
    base = args.base or ("HEAD" if worktree else "HEAD~1")
    head = "WORKTREE" if worktree else (args.head or "HEAD")
    output_dir = Path(args.output).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    
    try:
        changed = get_git_worktree_changed_files(repo_root, base) if worktree else get_git_changed_files(repo_root, base, head)
    except RuntimeError as e:
        print(str(e), file=sys.stderr)
        sys.exit(1)
    
    if not changed:
        print("No changes found between revisions.")
        return
    
    print(f"Processing {len(changed)} changed files...")

    patch_text = ""
    # Get full diff for patch file
    if worktree:
        patch_cached = subprocess.run(
            ["git", "diff", "--cached", base],
            cwd=repo_root,
            capture_output=True,
            text=True,
        )
        patch_unstaged = subprocess.run(
            ["git", "diff", base],
            cwd=repo_root,
            capture_output=True,
            text=True,
        )
        if patch_cached.returncode == 0 and patch_cached.stdout.strip():
            patch_text += patch_cached.stdout
        if patch_unstaged.returncode == 0 and patch_unstaged.stdout.strip():
            if patch_text and not patch_text.endswith("\n"):
                patch_text += "\n"
            patch_text += patch_unstaged.stdout
        if patch_text:
            (output_dir / "changes.patch").write_text(patch_text, encoding="utf-8")
    else:
        patch_result = subprocess.run(
            ["git", "diff", f"{base}...{head}"],
            cwd=repo_root,
            capture_output=True,
            text=True,
        )
        if patch_result.returncode == 0:
            patch_text = patch_result.stdout
            (output_dir / "changes.patch").write_text(patch_text, encoding="utf-8")

    n_add, n_mod, n_rem = partition_line_stats_from_patch(patch_text)
    line_stats_html = format_line_stats_html(n_add, n_mod, n_rem)

    index_entries = []
    for path, status, old_path in changed:
        if not path or path == "/dev/null":
            continue
        # Skip binary/non-text files
        if any(path.endswith(ext) for ext in (".png", ".jpg", ".jpeg", ".gif", ".ico", ".pdf", ".zip")):
            continue
            
        old_content = get_git_file_content(repo_root, base, old_path) if status != "A" else ""
        if worktree:
            new_content = "" if status == "D" else get_worktree_file_content(repo_root, path)
        else:
            new_content = get_git_file_content(repo_root, head, path) if status != "D" else ""
        
        rel_path = Path(path)
        out_base = output_dir / rel_path
        
        # Create subdirs
        out_base.parent.mkdir(parents=True, exist_ok=True)
        
        entry = {"path": path, "status": status, "old_path": old_path}
        
        path_posix = rel_path.as_posix()
        if old_content or new_content:
            if old_content:
                old_out_rel = Path(f"{path_posix}-.html")
                old_html = source_to_html(
                    rel_path, old_content, f"Old ({base})", href_to_index(old_out_rel), view="old"
                )
                (output_dir / old_out_rel).write_text(old_html, encoding="utf-8")
                entry["old"] = old_out_rel.as_posix()
            if new_content:
                new_out_rel = Path(f"{path_posix}.html")
                new_html = source_to_html(
                    rel_path, new_content, f"New ({head})", href_to_index(new_out_rel), view="new"
                )
                (output_dir / new_out_rel).write_text(new_html, encoding="utf-8")
                entry["new"] = new_out_rel.as_posix()

            if old_content and new_content:
                udiff_out_rel = Path(f"{path_posix}.udiff.html")
                sdiff_out_rel = Path(f"{path_posix}.sdiff.html")
                udiff_html = diff_to_html_unified(
                    rel_path,
                    old_content,
                    new_content,
                    base,
                    head,
                    index_href=href_to_index(udiff_out_rel),
                )
                sdiff_html = diff_to_html_side_by_side(
                    rel_path,
                    old_content,
                    new_content,
                    base,
                    head,
                    index_href=href_to_index(sdiff_out_rel),
                )
                (output_dir / udiff_out_rel).write_text(udiff_html, encoding="utf-8")
                (output_dir / sdiff_out_rel).write_text(sdiff_html, encoding="utf-8")
                entry["udiff"] = udiff_out_rel.as_posix()
                entry["sdiff"] = sdiff_out_rel.as_posix()
        
        index_entries.append(entry)
        print(f"  {path} ({status})")
    
    # Build index
    status_badges = {"A": "badge-new", "M": "badge-modified", "D": "badge-deleted", "R": "badge-modified"}
    entries_by_path = {e["path"].replace("\\", "/"): e for e in index_entries if "path" in e}
    tree = build_file_tree(list(entries_by_path.keys()))
    tree_html = render_diff_tree(tree, entries_by_path, "", [0])
    index_html = f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Code Review - {html.escape(str(repo_root.name))}</title>
<style>{STYLESHEET}</style>
</head>
<body>
<h1>Code Review: {html.escape(str(repo_root.name))}</h1>
<div class="summary">
<p><strong>Base:</strong> {html.escape(base)} &nbsp;|&nbsp; <strong>Head:</strong> {html.escape(head)}</p>
<p><strong>Files changed:</strong> {len(index_entries)}</p>
<p><a href="changes.patch">Download patch</a></p>
{line_stats_html}
</div>
<div class="tree">
{INDEX_TOOLBAR}
  <div class="header"><div>File</div><div>Links</div></div>
  {tree_html}
</div>
<hr>
{INDEX_REVIEW_SCRIPT}
{LINE_COMMENT_SCRIPT}
</body></html>"""
    
    (output_dir / "index.html").write_text(index_html, encoding="utf-8")
    print(f"\nDone. Output: {output_dir}")
    print(f"Open {output_dir / 'index.html'} in a browser.")


def get_svn_repo_root(path: Path) -> Optional[Path]:
    """Find SVN repository root (working copy)."""
    result = subprocess.run(
        ["svn", "info", "--show-item", "wc-root"],
        cwd=path,
        capture_output=True,
        text=True,
    )
    if result.returncode == 0:
        return Path(result.stdout.strip())
    return None


def get_svn_changed_files(root: Path, base: str, head: str) -> list[tuple[str, str]]:
    """Get list of changed files between SVN revisions."""
    result = subprocess.run(
        ["svn", "diff", "--summarize", "-r", f"{base}:{head}"],
        cwd=root,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"svn diff failed: {result.stderr}")
    
    files = []
    for line in result.stdout.strip().splitlines():
        if not line.strip():
            continue
        parts = line.split(None, 1)
        status = parts[0] if len(parts) > 0 else ""
        path = parts[1] if len(parts) > 1 else ""
        files.append((path, status))
    return files


def get_svn_file_content(root: Path, rev: str, path: str) -> str:
    """Get file content at given SVN revision."""
    result = subprocess.run(
        ["svn", "cat", "-r", rev, path],
        cwd=root,
        capture_output=True,
        text=True,
    )
    if result.returncode == 0:
        return result.stdout
    return ""


def run_svn_mode(args: argparse.Namespace) -> None:
    """Generate review for Subversion repository."""
    root = Path(args.directory).resolve()
    wc_root = get_svn_repo_root(root)
    if not wc_root:
        print("Not a Subversion working copy.", file=sys.stderr)
        sys.exit(1)
    
    base = args.base or "BASE"
    head = args.head or "HEAD"
    output_dir = Path(args.output).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    
    try:
        changed = get_svn_changed_files(wc_root, base, head)
    except RuntimeError as e:
        print(str(e), file=sys.stderr)
        sys.exit(1)
    
    if not changed:
        print("No changes found between revisions.")
        return
    
    # Get full diff
    patch_result = subprocess.run(
        ["svn", "diff", "-r", f"{base}:{head}"],
        cwd=wc_root,
        capture_output=True,
        text=True,
    )
    patch_text = patch_result.stdout if patch_result.returncode == 0 else ""
    if patch_text:
        (output_dir / "changes.patch").write_text(patch_text, encoding="utf-8")

    n_add, n_mod, n_rem = partition_line_stats_from_patch(patch_text)
    line_stats_html = format_line_stats_html(n_add, n_mod, n_rem)

    index_entries = []
    for path, status in changed:
        if not path:
            continue
        rel_path = Path(path)
        if not rel_path.suffix or rel_path.suffix.lower() in (".png", ".jpg", ".jpeg", ".gif", ".ico", ".pdf"):
            continue
        
        old_content = get_svn_file_content(wc_root, base, path)
        new_content = get_svn_file_content(wc_root, head, path)
        
        out_base = output_dir / rel_path
        out_base.parent.mkdir(parents=True, exist_ok=True)
        
        entry = {"path": path, "status": status}
        path_posix = rel_path.as_posix()
        if old_content or new_content:
            if old_content:
                old_out_rel = Path(f"{path_posix}-.html")
                old_html = source_to_html(
                    rel_path, old_content, f"Old (r{base})", href_to_index(old_out_rel), view="old"
                )
                (output_dir / old_out_rel).write_text(old_html, encoding="utf-8")
                entry["old"] = old_out_rel.as_posix()
            if new_content:
                new_out_rel = Path(f"{path_posix}.html")
                new_html = source_to_html(
                    rel_path, new_content, f"New (r{head})", href_to_index(new_out_rel), view="new"
                )
                (output_dir / new_out_rel).write_text(new_html, encoding="utf-8")
                entry["new"] = new_out_rel.as_posix()
            if old_content and new_content:
                udiff_out_rel = Path(f"{path_posix}.udiff.html")
                sdiff_out_rel = Path(f"{path_posix}.sdiff.html")
                udiff_html = diff_to_html_unified(
                    rel_path,
                    old_content,
                    new_content,
                    f"r{base}",
                    f"r{head}",
                    index_href=href_to_index(udiff_out_rel),
                )
                sdiff_html = diff_to_html_side_by_side(
                    rel_path,
                    old_content,
                    new_content,
                    f"r{base}",
                    f"r{head}",
                    index_href=href_to_index(sdiff_out_rel),
                )
                (output_dir / udiff_out_rel).write_text(udiff_html, encoding="utf-8")
                (output_dir / sdiff_out_rel).write_text(sdiff_html, encoding="utf-8")
                entry["udiff"] = udiff_out_rel.as_posix()
                entry["sdiff"] = sdiff_out_rel.as_posix()
        
        index_entries.append(entry)
        print(f"  {path} ({status})")
    
    status_badges = {"A": "badge-new", "M": "badge-modified", "D": "badge-deleted"}
    entries_by_path = {e["path"].replace("\\", "/"): e for e in index_entries if "path" in e}
    tree = build_file_tree(list(entries_by_path.keys()))
    tree_html = render_diff_tree(tree, entries_by_path, "", [0])
    index_html = f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Code Review - {html.escape(str(wc_root.name))}</title>
<style>{STYLESHEET}</style>
</head>
<body>
<h1>Code Review: {html.escape(str(wc_root.name))}</h1>
<div class="summary">
<p><strong>Base:</strong> r{base} &nbsp;|&nbsp; <strong>Head:</strong> r{head}</p>
<p><strong>Files changed:</strong> {len(index_entries)}</p>
<p><a href="changes.patch">Download patch</a></p>
{line_stats_html}
</div>
<div class="tree">
{INDEX_TOOLBAR}
  <div class="header"><div>File</div><div>Links</div></div>
  {tree_html}
</div>
<hr>
{INDEX_REVIEW_SCRIPT}
{LINE_COMMENT_SCRIPT}
</body></html>"""
    
    (output_dir / "index.html").write_text(index_html, encoding="utf-8")
    print(f"\nDone. Output: {output_dir}")


def main():
    parser = argparse.ArgumentParser(
        description="Generate HTML code reviews with syntax highlighting.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Plain directory - HTML of all code files:
  codereviewer.py ./myproject -o ./review

  # Git - diff between commits:
  codereviewer.py ./myproject -o ./review --base main --head feature-branch

  # Git - diff between working tree and HEAD:
  codereviewer.py ./myproject -o ./review --base HEAD --head

  # Subversion - diff between revisions:
  codereviewer.py ./myproject -o ./review --base 1000 --head 1050
"""
    )
    parser.add_argument("directory", nargs="?", default=".", help="Target directory (default: current)")
    parser.add_argument("-o", "--output", default="review", help="Output directory (default: review)")
    parser.add_argument("--base", help="Base revision (git: commit, svn: rev number)")
    parser.add_argument("--head", help="Head revision (git: commit, svn: rev number)")
    parser.add_argument("--worktree", action="store_true",
                        help="Git only: diff --base (default: HEAD) against staged+unstaged working tree changes")
    parser.add_argument("--plain", action="store_true", help="Force plain mode (ignore VCS)")
    parser.add_argument("--extensions", nargs="*", default=list(DEFAULT_EXTENSIONS),
                        help="File extensions for plain mode (default: common code extensions)")
    
    args = parser.parse_args()
    
    root = Path(args.directory).resolve()
    if not root.exists():
        print(f"Error: Directory not found: {root}", file=sys.stderr)
        sys.exit(1)
    
    vcs = None if args.plain else detect_vcs(root)
    
    if vcs == "git" and (args.base or args.head or args.worktree):
        run_git_mode(args)
    elif vcs == "svn" and (args.base or args.head):
        run_svn_mode(args)
    else:
        run_plain_mode(args)


if __name__ == "__main__":
    main()
