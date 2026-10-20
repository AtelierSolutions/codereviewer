package com.codereviewer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntaxTest {
    @Test
    void javaSourceColorsKeywordsStringsCommentsAndTypes() {
        String html = Html.sourcePage("A.java", "Source",
                "public class A {\n  // note\n  String s = \"hi\";\n}\n",
                "language-java", "index.html", "source");
        assertTrue(html.contains("<span class=\"hljs-keyword\">public</span>"));
        assertTrue(html.contains("<span class=\"hljs-keyword\">class</span>"));
        assertTrue(html.contains("<span class=\"hljs-comment\">// note</span>"));
        assertTrue(html.contains("<span class=\"hljs-string\">&quot;hi&quot;</span>"));
        assertTrue(html.contains("<span class=\"hljs-type\">String</span>"));
    }

    @Test
    void javaBlockCommentAndGenericsStayIntactAcrossLines() {
        List<String> lines = Syntax.highlightLines("language-java", "/* hello\nworld */\nList<String> xs;\n");
        assertTrue(lines.get(0).contains("hljs-comment"));
        assertTrue(lines.get(0).contains("hello"));
        assertTrue(lines.get(1).contains("hljs-comment"));
        assertTrue(lines.get(1).contains("world"));
        assertTrue(lines.get(2).contains("<span class=\"hljs-type\">List</span>"));
        assertTrue(lines.get(2).contains("&lt;"));
        assertTrue(lines.get(2).contains("<span class=\"hljs-type\">String</span>"));
    }

    @Test
    void xmlColorsTagsAttributesAndValues() {
        String html = Html.sourcePage("p.xml", "Source", "<root id=\"a\">\n</root>\n",
                "language-xml", "index.html", "source");
        assertTrue(html.contains("<span class=\"hljs-name\">root</span>"));
        assertTrue(html.contains("<span class=\"hljs-attr\">id</span>"));
        assertTrue(html.contains("<span class=\"hljs-string\">&quot;a&quot;</span>"));
        assertTrue(html.contains("<span class=\"hljs-tag\">&lt;/</span>")
                || (html.contains("hljs-tag") && html.contains("hljs-name\">root</span>")));
    }

    @Test
    void htmlColorsTagsAndLeavesScriptBodyPlain() {
        List<String> lines = Syntax.highlightLines("language-html",
                "<p class=\"x\">Hi</p>\n<script>\nif (a < b) {}\n</script>\n");
        assertTrue(lines.get(0).contains("<span class=\"hljs-name\">p</span>"));
        assertTrue(lines.get(0).contains("<span class=\"hljs-attr\">class</span>"));
        assertTrue(lines.get(0).contains("hljs-string"));
        assertTrue(lines.get(2).contains("a &lt; b"));
        assertFalse(lines.get(2).contains("hljs-name"));
        assertTrue(lines.get(3).contains("<span class=\"hljs-name\">script</span>"));
    }

    @Test
    void markupCommentContinuesOnTheNextLine() {
        List<String> lines = Syntax.highlightLines("language-xml", "<!-- hello\nworld -->\n");
        assertTrue(lines.get(0).contains("hljs-comment"));
        assertTrue(lines.get(1).contains("hljs-comment"));
        assertTrue(lines.get(1).contains("world"));
    }
}
