package com.codereviewer;

import java.util.List;
import java.util.Set;

final class Defaults {
    private Defaults() {}

    static final Set<String> DEFAULT_EXTENSIONS = Set.of(
            ".py", ".js", ".ts", ".tsx", ".jsx", ".java", ".kt", ".kts", ".scala", ".groovy",
            ".c", ".cpp", ".cc", ".cxx", ".h", ".hpp", ".hxx", ".cs", ".go", ".rs", ".rb",
            ".php", ".swift", ".m", ".mm", ".r", ".sql", ".sh", ".bash", ".zsh",
            ".html", ".htm", ".css", ".scss", ".sass", ".less", ".xml", ".json", ".yaml", ".yml",
            ".md", ".rst", ".tex", ".vue", ".svelte", ".dart", ".lua", ".pl", ".pm",
            ".make", ".mk", ".cmake", ".dockerfile", ".dockerignore", ".gitignore"
    );

    static final List<String> BINARY_EXTENSIONS = List.of(
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".pdf", ".zip"
    );

    static final String STYLESHEET = """
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

            /* highlight.js base styling */
            pre code.hljs { display: block; overflow-x: auto; padding: 1em; }
            code.hljs { padding: 3px 5px }

            /* A light theme (similar vibe to pygments default) */
            .hljs { background: #ffffff; color: #24292e; }
            .hljs-comment, .hljs-quote { color: #6a737d; font-style: italic; }
            .hljs-keyword, .hljs-selector-tag, .hljs-subst { color: #d73a49; font-weight: 600; }
            .hljs-string, .hljs-doctag { color: #032f62; }
            .hljs-title, .hljs-section, .hljs-selector-id { color: #6f42c1; font-weight: 600; }
            .hljs-type, .hljs-class .hljs-title { color: #005cc5; font-weight: 600; }
            .hljs-number, .hljs-literal { color: #005cc5; }
            .hljs-attr, .hljs-attribute, .hljs-name, .hljs-tag { color: #22863a; }
            .hljs-meta { color: #b31d28; }
            .hljs-addition { background-color: #e6ffed; }
            .hljs-deletion { background-color: #ffeef0; }
            """;

    // Java, HTML, and XML source pages are colored by Syntax when the review is generated.
    // This script only marks code blocks so the stylesheet colors apply. It must not rewrite
    // inner HTML: generated pages already contain escaped text and hljs-* spans.
    static final String HIGHLIGHT_JS_MIN = """
            (function(){function ready(fn){if(document.readyState!=='loading'){fn();}else{document.addEventListener('DOMContentLoaded',fn);}}
            ready(function(){
              document.querySelectorAll('pre code, table.source td.code code').forEach(function(block){
                block.classList.add('hljs');
              });
            });})();
            """;
}
