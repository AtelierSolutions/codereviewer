package com.codereviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Server-side syntax coloring for source pages. Java, HTML, and XML are highlighted into the
 * existing {@code hljs-*} classes. Other languages stay escaped plain text.
 */
final class Syntax {
    private Syntax() {}

    static List<String> highlightLines(String languageClass, String code) {
        List<String> lines = IO.splitLines(code == null ? "" : code);
        String lang = languageClass == null ? "" : languageClass;
        return switch (lang) {
            case "language-java" -> new JavaHighlighter().highlight(lines);
            case "language-xml" -> new MarkupHighlighter(false).highlight(lines);
            case "language-html" -> new MarkupHighlighter(true).highlight(lines);
            default -> plain(lines);
        };
    }

    private static List<String> plain(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(Html.escape(line));
        return out;
    }

    private static final class Run {
        private final StringBuilder html = new StringBuilder();
        private final StringBuilder text = new StringBuilder();
        private String cls;

        void push(String cls, String chunk) {
            if (chunk.isEmpty()) return;
            if (text.length() > 0 && !java.util.Objects.equals(this.cls, cls)) flush();
            this.cls = cls;
            text.append(chunk);
        }

        void push(String cls, char ch) {
            push(cls, String.valueOf(ch));
        }

        void flush() {
            if (text.length() == 0) return;
            String escaped = Html.escape(text.toString());
            if (cls == null) html.append(escaped);
            else html.append("<span class=\"").append(cls).append("\">").append(escaped).append("</span>");
            text.setLength(0);
        }

        String finish() {
            flush();
            return html.toString();
        }
    }

    private static final class JavaHighlighter {
        private enum Mode { CODE, LINE, BLOCK, STRING, TEXT, CHAR }

        private static final Set<String> KEYWORDS = Set.of(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
                "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "package", "private", "protected", "public",
                "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
                "throw", "throws", "transient", "try", "void", "volatile", "while", "var", "yield",
                "record", "sealed", "permits", "when"
        );
        private static final Set<String> LITERALS = Set.of("true", "false", "null");

        private Mode mode = Mode.CODE;
        private boolean opened;

        List<String> highlight(List<String> lines) {
            List<String> out = new ArrayList<>(lines.size());
            for (String line : lines) {
                out.add(highlightLine(line));
                if (mode == Mode.LINE) mode = Mode.CODE;
            }
            return out;
        }

        private String highlightLine(String line) {
            Run run = new Run();
            int i = 0;
            int n = line.length();
            while (i < n) {
                Mode before = mode;
                int next = switch (mode) {
                    case BLOCK -> block(line, i, run);
                    case STRING -> delimited(line, i, run, '"');
                    case CHAR -> delimited(line, i, run, '\'');
                    case TEXT -> textBlock(line, i, run);
                    default -> code(line, i, run);
                };
                if (next <= i && mode == before) next = i + 1;
                i = next;
            }
            return run.finish();
        }

        private int code(String line, int i, Run run) {
            int n = line.length();
            char c = line.charAt(i);
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/') {
                run.push("hljs-comment", line.substring(i));
                mode = Mode.LINE;
                return n;
            }
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*') {
                mode = Mode.BLOCK;
                return i;
            }
            if (c == '"' && i + 2 < n && line.charAt(i + 1) == '"' && line.charAt(i + 2) == '"') {
                mode = Mode.TEXT;
                opened = false;
                return i;
            }
            if (c == '"') {
                mode = Mode.STRING;
                opened = false;
                return i;
            }
            if (c == '\'') {
                mode = Mode.CHAR;
                opened = false;
                return i;
            }
            if (c == '@') {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(line.charAt(j))) j++;
                if (j > i + 1) {
                    run.push("hljs-meta", line.substring(i, j));
                    return j;
                }
            }
            if (isNumberStart(line, i)) return number(line, i, run);
            if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(line.charAt(j))) j++;
                String word = line.substring(i, j);
                run.push(wordClass(word), word);
                return j;
            }
            run.push(null, c);
            return i + 1;
        }

        private static String wordClass(String word) {
            if (KEYWORDS.contains(word)) return "hljs-keyword";
            if (LITERALS.contains(word)) return "hljs-literal";
            if (Character.isUpperCase(word.charAt(0))) return "hljs-type";
            return null;
        }

        private int block(String line, int i, Run run) {
            int end = line.indexOf("*/", i);
            if (end < 0) {
                run.push("hljs-comment", line.substring(i));
                return line.length();
            }
            run.push("hljs-comment", line.substring(i, end + 2));
            mode = Mode.CODE;
            return end + 2;
        }

        private int delimited(String line, int i, Run run, char quote) {
            int n = line.length();
            int start = i;
            if (!opened) {
                if (i < n && line.charAt(i) == quote) i++;
                opened = true;
            }
            while (i < n) {
                char c = line.charAt(i);
                if (c == '\\' && i + 1 < n) {
                    i += 2;
                    continue;
                }
                i++;
                if (c == quote) {
                    mode = Mode.CODE;
                    opened = false;
                    break;
                }
            }
            run.push("hljs-string", line.substring(start, i));
            return i;
        }

        private int textBlock(String line, int i, Run run) {
            int n = line.length();
            int start = i;
            if (!opened) {
                if (line.startsWith("\"\"\"", i)) i += 3;
                opened = true;
            }
            int close = line.indexOf("\"\"\"", i);
            if (close < 0) {
                run.push("hljs-string", line.substring(start));
                return n;
            }
            run.push("hljs-string", line.substring(start, close + 3));
            mode = Mode.CODE;
            opened = false;
            return close + 3;
        }

        private static boolean isNumberStart(String line, int i) {
            char c = line.charAt(i);
            if (Character.isDigit(c)) return true;
            return c == '.' && i + 1 < line.length() && Character.isDigit(line.charAt(i + 1));
        }

        private static int number(String line, int i, Run run) {
            int n = line.length();
            int j = i;
            if (line.charAt(j) == '0' && j + 1 < n && (line.charAt(j + 1) == 'x' || line.charAt(j + 1) == 'X')) {
                j += 2;
                while (j < n && isHex(line.charAt(j))) j++;
            } else {
                j = digits(line, j);
                if (j < n && line.charAt(j) == '.') {
                    int k = digits(line, j + 1);
                    if (k > j + 1) j = k;
                }
                if (j < n && (line.charAt(j) == 'e' || line.charAt(j) == 'E')) {
                    int k = j + 1;
                    if (k < n && (line.charAt(k) == '+' || line.charAt(k) == '-')) k++;
                    int e = digits(line, k);
                    if (e > k) j = e;
                }
            }
            if (j < n && "fFdDlL".indexOf(line.charAt(j)) >= 0) j++;
            run.push("hljs-number", line.substring(i, j));
            return j;
        }

        private static int digits(String line, int j) {
            int n = line.length();
            while (j < n && (Character.isDigit(line.charAt(j)) || line.charAt(j) == '_')) j++;
            return j;
        }

        private static boolean isHex(char c) {
            return Character.isDigit(c) || c == '_' || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        }
    }

    private static final class MarkupHighlighter {
        private enum Mode { TEXT, TAG, COMMENT, CDATA, PI, DECL }

        private final boolean html;
        private Mode mode = Mode.TEXT;
        private boolean readingName;
        private boolean afterName;
        private boolean expectValue;
        private boolean quoted;
        private boolean quoteStarted;
        private char quote;
        private boolean closing;
        private final StringBuilder openName = new StringBuilder();
        private String rawUntil;

        MarkupHighlighter(boolean html) {
            this.html = html;
        }

        List<String> highlight(List<String> lines) {
            List<String> out = new ArrayList<>(lines.size());
            for (String line : lines) out.add(highlightLine(line));
            return out;
        }

        private String highlightLine(String line) {
            Run run = new Run();
            int i = 0;
            int n = line.length();
            while (i < n) {
                Mode before = mode;
                String rawBefore = rawUntil;
                int next = rawUntil != null ? raw(line, i, run) : switch (mode) {
                    case COMMENT -> until(line, i, run, "-->", "hljs-comment");
                    case CDATA -> until(line, i, run, "]]>", "hljs-string");
                    case PI -> until(line, i, run, "?>", "hljs-meta");
                    case DECL -> until(line, i, run, ">", "hljs-meta");
                    case TAG -> tag(line, i, run);
                    default -> text(line, i, run);
                };
                if (next <= i && mode == before && java.util.Objects.equals(rawBefore, rawUntil)) next = i + 1;
                i = next;
            }
            return run.finish();
        }

        private int text(String line, int i, Run run) {
            int lt = line.indexOf('<', i);
            if (lt < 0) {
                run.push(null, line.substring(i));
                return line.length();
            }
            if (lt > i) run.push(null, line.substring(i, lt));
            return begin(line, lt);
        }

        private int begin(String line, int i) {
            if (line.startsWith("<!--", i)) {
                mode = Mode.COMMENT;
                return i;
            }
            if (line.regionMatches(true, i, "<![CDATA[", 0, 9)) {
                mode = Mode.CDATA;
                return i;
            }
            if (line.startsWith("<?", i)) {
                mode = Mode.PI;
                return i;
            }
            if (line.startsWith("<!", i)) {
                mode = Mode.DECL;
                return i;
            }
            mode = Mode.TAG;
            readingName = false;
            afterName = false;
            expectValue = false;
            quoted = false;
            closing = false;
            openName.setLength(0);
            return i;
        }

        private int until(String line, int i, Run run, String marker, String cls) {
            int end = line.indexOf(marker, i);
            if (end < 0) {
                run.push(cls, line.substring(i));
                return line.length();
            }
            int to = end + marker.length();
            run.push(cls, line.substring(i, to));
            mode = Mode.TEXT;
            return to;
        }

        private int tag(String line, int i, Run run) {
            int n = line.length();
            char c = line.charAt(i);
            if (quoted) {
                int searchFrom = i;
                if (!quoteStarted) {
                    if (i < n && line.charAt(i) == quote) searchFrom = i + 1;
                    quoteStarted = true;
                }
                int end = line.indexOf(quote, searchFrom);
                if (end < 0) {
                    run.push("hljs-string", line.substring(i));
                    return n;
                }
                run.push("hljs-string", line.substring(i, end + 1));
                quoted = false;
                quoteStarted = false;
                expectValue = false;
                return end + 1;
            }
            if (Character.isWhitespace(c)) {
                run.push(null, c);
                return i + 1;
            }
            if (c == '<') {
                run.push("hljs-tag", "<");
                int j = i + 1;
                if (j < n && line.charAt(j) == '/') {
                    run.push("hljs-tag", "/");
                    closing = true;
                    j++;
                }
                readingName = true;
                return j;
            }
            if (readingName && isNameChar(c)) {
                int j = i + 1;
                while (j < n && isNameChar(line.charAt(j))) j++;
                String name = line.substring(i, j);
                run.push("hljs-name", name);
                openName.append(name);
                readingName = false;
                afterName = true;
                return j;
            }
            readingName = false;
            if (c == '=' && afterName) {
                run.push(null, "=");
                expectValue = true;
                return i + 1;
            }
            if (expectValue && (c == '"' || c == '\'')) {
                quoted = true;
                quote = c;
                quoteStarted = false;
                expectValue = false;
                return tag(line, i, run);
            }
            if (expectValue && c != '>' && c != '/') {
                int j = i + 1;
                while (j < n) {
                    char d = line.charAt(j);
                    if (Character.isWhitespace(d) || d == '>' || d == '/') break;
                    j++;
                }
                run.push("hljs-string", line.substring(i, j));
                expectValue = false;
                return j;
            }
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '>') {
                run.push("hljs-tag", "/>");
                finishTag(false);
                return i + 2;
            }
            if (c == '>') {
                run.push("hljs-tag", ">");
                finishTag(!closing);
                return i + 1;
            }
            if (isNameChar(c)) {
                int j = i + 1;
                while (j < n && isNameChar(line.charAt(j))) j++;
                run.push("hljs-attr", line.substring(i, j));
                return j;
            }
            run.push(null, c);
            return i + 1;
        }

        private void finishTag(boolean opening) {
            mode = Mode.TEXT;
            if (html && opening) {
                String name = openName.toString();
                if (name.equalsIgnoreCase("script")) rawUntil = "script";
                else if (name.equalsIgnoreCase("style")) rawUntil = "style";
            }
            openName.setLength(0);
            closing = false;
            readingName = false;
            afterName = false;
            expectValue = false;
        }

        private int raw(String line, int i, Run run) {
            String needle = "</" + rawUntil;
            int end = indexOfIgnoreCase(line, needle, i);
            if (end < 0) {
                run.push(null, line.substring(i));
                return line.length();
            }
            if (end > i) run.push(null, line.substring(i, end));
            rawUntil = null;
            mode = Mode.TAG;
            readingName = false;
            afterName = false;
            expectValue = false;
            quoted = false;
            closing = false;
            openName.setLength(0);
            return end;
        }

        private static boolean isNameChar(char c) {
            return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '-' || c == '.';
        }

        private static int indexOfIgnoreCase(String line, String needle, int from) {
            int last = line.length() - needle.length();
            for (int i = from; i <= last; i++) {
                if (line.regionMatches(true, i, needle, 0, needle.length())) return i;
            }
            return -1;
        }
    }
}
