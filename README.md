# Code Reviewer

An offline code review utility for generating HTML code reviews with syntax highlighting.

Inspired by [OpenJDK's webrev](https://openjdk.org/projects/code-tools/webrev/) tool.

## Features

- **Plain directory mode**: Generate syntax-highlighted HTML for all code files in a directory
- **Git support**: Generate diffs between commits with per-file old/new views and unified/side-by-side diffs
- **Subversion support**: Generate diffs between revisions with the same per-file views
- **Offline-friendly**: Self-contained HTML output—no server required; share as a zip or folder

Note: the Java port keeps output self-contained. Java, HTML, and XML source pages are syntax-colored when the review is generated. Other languages use the same layout with plain code styling.

## Installation

### Java (JDK 17)

Build a runnable CLI JAR:

```bash
mvn -q -DskipTests package
```

Run it:

```bash
java -jar target/codereviewer-1.0.0-shaded.jar --help
```

### Python (original)

```bash
pip install -r requirements.txt
```

Or install Pygments directly:

```bash
pip install pygments
```

## Usage

### Java CLI

Plain directory mode:

```bash
java -jar target/codereviewer-1.0.0-shaded.jar ./myproject -o ./review
```

Git diff mode:

```bash
java -jar target/codereviewer-1.0.0-shaded.jar . -o ./review --base main --head feature-branch
```

SVN diff mode:

```bash
java -jar target/codereviewer-1.0.0-shaded.jar . -o ./review --base 1000 --head 1050
```

### Plain Directory (no VCS)

Generate HTML versions of all code files with syntax highlighting:

```bash
python codereviewer.py ./myproject -o ./review
```

### Git Repository

Compare two commits (base and head):

```bash
# Diff between main and feature branch
python codereviewer.py ./myproject -o ./review --base main --head feature-branch

# Diff between previous commit and HEAD
python codereviewer.py . -o ./review --base HEAD~1 --head HEAD

# Diff between two specific commits
python codereviewer.py . -o ./review --base abc123 --head def456
```

### Subversion Repository

Compare two revisions:

```bash
# Diff between revision 1000 and 1050
python codereviewer.py ./myproject -o ./review --base 1000 --head 1050

# Diff between BASE (working copy base) and HEAD (latest)
python codereviewer.py . -o ./review --base BASE --head HEAD
```

### Force Plain Mode

Ignore VCS and treat as plain directory:

```bash
python codereviewer.py ./git-repo -o ./review --plain
```

## Output Structure

### Plain Mode

- `index.html` — Index of all files with links to syntax-highlighted source
- `path/to/file.py.html` — HTML version of each code file

On **`index.html`** (Python and Java): file rows use alternating light gray and light blue backgrounds so each file lines up with its links. Click a row **outside** a link or button to cycle review highlight: unmarked, then yellow, red, green, then clear again. That state is saved in the browser under the `localStorage` key `codereviewer-index-review-v1`, so it survives refresh while you keep the same output folder path. Folder expand/collapse is likewise persisted under `codereviewer-index-tree-v1` (collapsed folders only; default remains expanded), including after you open a file and return via **Back to index**. Expand all / Collapse all update that stored state as well.

Each generated source page numbers every line. Click a line number to leave a comment. Comments stay in the browser under `codereviewer-line-comments-v1` and remain on that line after a reload. **Download review comments**, on a source page or on `index.html`, writes one `review-comments.txt` from every comment stored in that browser: a list of `path:line: comment` items to send to the author. Old and new revisions are tagged `[old]` and `[new]`.

### Git/SVN Diff Mode

- `index.html` — Summary with links to each changed file's views, plus **line statistics** (added / modified / removed) estimated from the unified patch: within each file, paired `+` and `-` lines count toward **modified**, and any surplus counts toward **added** or **removed**.
- `changes.patch` — Full unified diff
- Per-file:
  - `path/to/file.py-.html` — Old version (syntax highlighted, per-line comments tagged `[old]` in the review file)
  - `path/to/file.py.html` — New version (syntax highlighted, per-line comments tagged `[new]` in the review file)
  - `path/to/file.py.udiff.html` — Unified diff view
  - `path/to/file.py.sdiff.html` — Side-by-side diff view

## Options

| Option | Description |
|--------|-------------|
| `directory` | Target directory (default: current) |
| `-o`, `--output` | Output directory (default: `review`) |
| `--base` | Base revision for diff (commit hash or SVN rev) |
| `--head` | Head revision for diff |
| `--plain` | Force plain mode, ignore VCS |
| `--extensions` | File extensions for plain mode (space-separated) |

## Supported File Types

Plain mode includes common code extensions by default: `.py`, `.js`, `.ts`, `.java`, `.c`, `.cpp`, `.go`, `.rs`, `.rb`, `.php`, `.html`, `.css`, `.json`, `.yaml`, `.md`, and many more. Use `--extensions` to customize.

## Requirements

- Python 3.10+
- Pygments (for syntax highlighting)
- Git or Subversion (for diff mode; must be in PATH)
