package com.codereviewer;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(
        name = "codereviewer",
        mixinStandardHelpOptions = true,
        version = "codereviewer 1.0.0",
        description = "Generate HTML code reviews with syntax highlighting.",
        footer = {
                "",
                "Examples:",
                "  # Plain directory - HTML of all code files:",
                "  codereviewer ./myproject -o ./review",
                "",
                "  # Git - diff between commits:",
                "  codereviewer ./myproject -o ./review --base main --head feature-branch",
                "",
                "  # Subversion - diff between revisions:",
                "  codereviewer ./myproject -o ./review --base 1000 --head 1050",
        }
)
public final class CodeReviewer implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", description = "Target directory (default: current)")
    private Path directory = Path.of(".");

    @Option(names = {"-o", "--output"}, description = "Output directory (default: ${DEFAULT-VALUE})")
    private Path output = Path.of("review");

    @Option(names = {"--base"}, description = "Base revision (git: commit, svn: rev number)")
    private String base;

    @Option(names = {"--head"}, description = "Head revision (git: commit, svn: rev number)")
    private String head;

    @Option(names = {"--worktree"}, description = "Git only: diff --base (default: HEAD) against staged+unstaged working tree changes")
    private boolean worktree;

    @Option(names = {"--plain"}, description = "Force plain mode (ignore VCS)")
    private boolean plain;

    @Option(
            names = {"--extensions"},
            arity = "0..*",
            description = "File extensions for plain mode (default: common code extensions)"
    )
    private List<String> extensions = new ArrayList<>(Defaults.DEFAULT_EXTENSIONS);

    @Override
    public Integer call() {
        Path root = directory.toAbsolutePath().normalize();
        if (!root.toFile().exists()) {
            System.err.println("Error: Directory not found: " + root);
            return 1;
        }

        Vcs vcs = plain ? Vcs.NONE : Vcs.detect(root);

        boolean requestedDiff = (base != null && !base.isBlank()) || (head != null && !head.isBlank());
        if (vcs == Vcs.GIT && (requestedDiff || worktree)) {
            return GitMode.run(root, output.toAbsolutePath().normalize(), base, head, worktree);
        }
        if (vcs == Vcs.SVN && requestedDiff) {
            return SvnMode.run(root, output.toAbsolutePath().normalize(), base, head);
        }

        Set<String> extSet = new LinkedHashSet<>();
        for (String e : extensions) {
            if (e == null || e.isBlank()) continue;
            String trimmed = e.trim();
            if (trimmed.startsWith(".")) extSet.add(trimmed.toLowerCase());
            else extSet.add(("." + trimmed).toLowerCase());
        }
        if (extSet.isEmpty()) extSet.addAll(Defaults.DEFAULT_EXTENSIONS);

        return PlainMode.run(root, output.toAbsolutePath().normalize(), extSet);
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new CodeReviewer()).execute(args);
        System.exit(exit);
    }
}
