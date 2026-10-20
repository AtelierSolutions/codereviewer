package com.codereviewer;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeIndexTest {
    @Test
    void renderPlainEmitsFolderPathsAndToolbarHelpers() {
        List<PlainMode.IndexEntry> entries = List.of(
                new PlainMode.IndexEntry(Path.of("src", "main", "App.java"), Path.of("src", "main", "App.java.html")),
                new PlainMode.IndexEntry(Path.of("src", "util", "Helper.java"), Path.of("src", "util", "Helper.java.html"))
        );
        String html = TreeIndex.renderPlain(entries);
        assertTrue(html.contains("data-folder-path=\"src/\""));
        assertTrue(html.contains("data-folder-path=\"src/main/\""));
        assertTrue(html.contains("data-folder-path=\"src/util/\""));
        assertTrue(html.contains("onclick=\"codereviewerExpandAll()\""));
        assertTrue(html.contains("onclick=\"codereviewerCollapseAll()\""));
        assertTrue(html.contains("id=\"download-review-comments\""));
        assertFalse(html.contains("combine-review-comments"));
    }

    @Test
    void renderDiffEmitsFolderPathsAndToolbarHelpers() {
        List<Map<String, String>> entries = List.of(
                Map.of(
                        "path", "src/main/App.java",
                        "status", "M",
                        "udiff", "src/main/App.java.udiff.html",
                        "sdiff", "src/main/App.java.sdiff.html",
                        "old", "src/main/App.java-.html",
                        "new", "src/main/App.java.html"
                )
        );
        String html = TreeIndex.renderDiff(entries);
        assertTrue(html.contains("data-folder-path=\"src/\""));
        assertTrue(html.contains("data-folder-path=\"src/main/\""));
        assertTrue(html.contains("onclick=\"codereviewerExpandAll()\""));
        assertTrue(html.contains("onclick=\"codereviewerCollapseAll()\""));
        assertTrue(html.contains("id=\"download-review-comments\""));
        assertFalse(html.contains("combine-review-comments"));
    }
}
