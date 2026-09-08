"""Unit tests for index tree markup and persistence script contract."""
from __future__ import annotations

import unittest

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


if __name__ == "__main__":
    unittest.main()
