package com.codereviewer;

import java.nio.file.Path;

final class Lang {
    private Lang() {}

    static String languageClassFor(Path relPath) {
        if (relPath == null) return "hljs";
        String name = relPath.getFileName().toString().toLowerCase();
        String ext = "";
        int idx = name.lastIndexOf('.');
        if (idx >= 0) ext = name.substring(idx);

        // highlight.js convention: language-<id>
        return switch (ext) {
            case ".py" -> "language-python";
            case ".js", ".jsx" -> "language-javascript";
            case ".ts", ".tsx" -> "language-typescript";
            case ".java" -> "language-java";
            case ".kt", ".kts" -> "language-kotlin";
            case ".c", ".h" -> "language-c";
            case ".cpp", ".cc", ".cxx", ".hpp", ".hxx" -> "language-cpp";
            case ".cs" -> "language-csharp";
            case ".go" -> "language-go";
            case ".rs" -> "language-rust";
            case ".rb" -> "language-ruby";
            case ".php" -> "language-php";
            case ".swift" -> "language-swift";
            case ".m", ".mm" -> "language-objectivec";
            case ".r" -> "language-r";
            case ".sql" -> "language-sql";
            case ".sh", ".bash", ".zsh" -> "language-bash";
            case ".html", ".htm" -> "language-html";
            case ".css", ".scss", ".sass", ".less" -> "language-css";
            case ".xml" -> "language-xml";
            case ".json" -> "language-json";
            case ".yaml", ".yml" -> "language-yaml";
            case ".md" -> "language-markdown";
            case ".tex" -> "language-latex";
            default -> "hljs";
        };
    }
}
