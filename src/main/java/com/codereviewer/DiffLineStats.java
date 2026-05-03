package com.codereviewer;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Estimates line counts from a unified diff patch: per file-chunk, pairs of insertions and deletions
 * count as "modified"; remaining {@code +} lines are "added" and remaining {@code -} lines "removed".
 */
final class DiffLineStats {
    private static final Pattern GIT_FILE = Pattern.compile("^diff --git ", Pattern.MULTILINE);
    private static final Pattern SVN_FILE = Pattern.compile("^Index: ", Pattern.MULTILINE);

    record Result(int added, int modified, int removed) {}

    private DiffLineStats() {}

    static Result fromUnifiedPatch(String patch) {
        if (patch == null || patch.isBlank()) {
            return new Result(0, 0, 0);
        }
        List<String> chunks = splitChunks(patch);
        int totalAdded = 0;
        int totalModified = 0;
        int totalRemoved = 0;
        for (String chunk : chunks) {
            if (chunk == null || chunk.isBlank()) continue;
            int[] ar = countPlusMinus(chunk);
            int a = ar[0];
            int r = ar[1];
            int m = Math.min(a, r);
            totalAdded += a - m;
            totalRemoved += r - m;
            totalModified += m;
        }
        return new Result(totalAdded, totalModified, totalRemoved);
    }

    private static List<String> splitChunks(String patch) {
        if (GIT_FILE.matcher(patch).find()) {
            return Arrays.stream(patch.split("(?m)(?=^diff --git )"))
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        if (SVN_FILE.matcher(patch).find()) {
            return Arrays.stream(patch.split("(?m)(?=^Index: )"))
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return List.of(patch);
    }

    private static int[] countPlusMinus(String chunk) {
        int a = 0;
        int r = 0;
        for (String line : chunk.split("\\R")) {
            if (line.startsWith("+++") || line.startsWith("---")) continue;
            if (line.startsWith("@@")) continue;
            if (line.startsWith("\\")) continue;
            if (line.startsWith("+")) a++;
            else if (line.startsWith("-")) r++;
        }
        return new int[]{a, r};
    }
}
