"""Unit tests for index tree markup and persistence script contract."""
from __future__ import annotations

import unittest
from pathlib import Path

import codereviewer


class TreeMarkupTests(unittest.TestCase):
    def test_render_plain_tree_emits_folder_paths(self) -> None:
        tree = {
            "src": {
                "main": {"_files": ["App.java"]},
                "util": {"_files": ["Helper.java"]},
            }
        }
        html = codereviewer.render_plain_tree(tree, "", [0])
        self.assertIn('data-folder-path="src/"', html)
        self.assertIn('data-folder-path="src/main/"', html)
        self.assertIn('data-folder-path="src/util/"', html)

    def test_render_diff_tree_emits_folder_paths(self) -> None:
        tree = {"src": {"main": {"_files": ["App.java"]}}}
        entries = {
            "src/main/App.java": {
                "status": "M",
                "udiff": "src/main/App.java.udiff.html",
                "sdiff": "src/main/App.java.sdiff.html",
                "old": "src/main/App.java-.html",
                "new": "src/main/App.java.html",
            }
        }
        html = codereviewer.render_diff_tree(tree, entries, "", [0])
        self.assertIn('data-folder-path="src/"', html)
        self.assertIn('data-folder-path="src/main/"', html)

    def test_index_review_script_persists_tree_state(self) -> None:
        script = codereviewer.INDEX_REVIEW_SCRIPT
        self.assertIn("codereviewer-index-tree-v1", script)
        self.assertIn("codereviewerExpandAll", script)
        self.assertIn("codereviewerCollapseAll", script)
        self.assertIn("addEventListener('toggle'", script)
        self.assertIn("data-folder-path", script)
        self.assertIn("codereviewer-index-review-v1", script)

    def test_source_to_html_emits_line_comments(self) -> None:
        page = codereviewer.source_to_html(
            Path("src/App.py"),
            "def a():\n    return 1\n\n",
            "Source",
            "index.html",
            view="old",
        )
        self.assertIn('data-file-path="src/App.py"', page)
        self.assertIn('data-view="old"', page)
        self.assertIn('data-line="1"', page)
        self.assertIn('data-line="2"', page)
        self.assertIn('data-line="3"', page)
        self.assertNotIn('data-line="4"', page)
        self.assertIn('id="L1"', page)
        self.assertIn('id="L3"', page)
        self.assertIn('class="k"', page)
        self.assertIn("codereviewer-line-comments-v1", page)
        self.assertIn("review-comments.txt", page)
        self.assertIn('id="download-review-comments"', page)

    def test_line_comment_script_exports_review_file(self) -> None:
        script = codereviewer.LINE_COMMENT_SCRIPT
        self.assertIn("codereviewer-line-comments-v1", script)
        self.assertIn("review-comments.txt", script)
        self.assertIn("download(formatComments(load()))", script)
        self.assertNotIn("combine-review-comments", script)
        self.assertIn("[old]", script)
        self.assertIn("[new]", script)
        self.assertIn("Each line is path:line: comment.", script)
        toolbar = codereviewer.INDEX_TOOLBAR
        self.assertIn('id="download-review-comments"', toolbar)
        self.assertNotIn("combine-review-comments", toolbar)


if __name__ == "__main__":
    unittest.main()
