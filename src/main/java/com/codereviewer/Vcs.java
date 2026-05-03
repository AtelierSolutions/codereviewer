package com.codereviewer;

import java.nio.file.Files;
import java.nio.file.Path;

enum Vcs {
    NONE,
    GIT,
    SVN;

    static Vcs detect(Path root) {
        if (root == null) return NONE;
        if (exists(root.resolve(".git"))) return GIT;
        if (exists(root.resolve(".svn"))) return SVN;
        Path p = root.toAbsolutePath().normalize();
        while (p != null) {
            if (exists(p.resolve(".git"))) return GIT;
            if (exists(p.resolve(".svn"))) return SVN;
            p = p.getParent();
        }
        return NONE;
    }

    private static boolean exists(Path p) {
        try {
            return Files.exists(p);
        } catch (Exception e) {
            return false;
        }
    }
}
