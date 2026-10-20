package com.codereviewer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlIndexScriptTest {
    @Test
    void indexReviewScriptPersistsTreeExpandState() {
        String script = Html.indexReviewScript();
        assertTrue(script.contains("codereviewer-index-tree-v1"));
        assertTrue(script.contains("codereviewerExpandAll"));
        assertTrue(script.contains("codereviewerCollapseAll"));
        assertTrue(script.contains("addEventListener('toggle'"));
        assertTrue(script.contains("data-folder-path"));
        assertTrue(script.contains("codereviewer-index-review-v1"));
    }

    @Test
    void sourcePageEmitsPerLineComments() {
        String html = Html.sourcePage("src\\App.java", "Source", "a\nb\nc\n", "language-java", "index.html", "source");
        assertTrue(html.contains("data-file-path=\"src/App.java\""));
        assertTrue(html.contains("data-view=\"source\""));
        assertTrue(html.contains("data-line=\"1\""));
        assertTrue(html.contains("data-line=\"2\""));
        assertTrue(html.contains("data-line=\"3\""));
        assertFalse(html.contains("data-line=\"4\""));
        assertTrue(html.contains("id=\"L1\""));
        assertTrue(html.contains("id=\"L3\""));
        assertTrue(html.contains("<code class=\"language-java\">a</code>"));
        assertTrue(html.contains("codereviewer-line-comments-v1"));
        assertTrue(html.contains("review-comments.txt"));
        assertTrue(html.contains("id=\"download-review-comments\""));
    }

    @Test
    void lineCommentScriptExportsReviewFile() {
        String script = Html.lineCommentScript();
        assertTrue(script.contains("codereviewer-line-comments-v1"));
        assertTrue(script.contains("review-comments.txt"));
        assertTrue(script.contains("download(formatComments(load()))"));
        assertFalse(script.contains("combine-review-comments"));
        assertTrue(script.contains("[old]"));
        assertTrue(script.contains("[new]"));
        assertTrue(script.contains("Each line is path:line: comment."));
        assertTrue(script.contains("replace(/\\s+$/,'')"));
        assertTrue(script.contains("split(/\\r?\\n/)"));
    }
}
