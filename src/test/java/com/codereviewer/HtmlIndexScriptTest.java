package com.codereviewer;

import org.junit.jupiter.api.Test;

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
}
