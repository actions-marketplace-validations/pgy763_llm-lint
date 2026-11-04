package io.github.pgy763.llmlint.engine;

import java.io.IOException;
import java.io.InputStream;
import io.github.pgy763.llmlint.lang.Messages;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 给 {@code --since} 用：算出从某个 git 引用以来，改动过的 {@code .java} 文件。
 *
 * <p>「改动」交给 git 自己算——merge-base(ref, HEAD) 到工作区的差异，加上没被
 * ignore 的未跟踪文件。这样两种场景都对得上：PR 里 detached HEAD 看的是相对目标分支的改动；
 * 本地「刚改了几个文件还没提交」也能直接扫到。
 */
public final class ChangedFiles {

    /** git 跑不起来、不在仓库里、或者 ref 不存在时抛这个，消息直接给用户看。 */
    public static final class GitException extends Exception {
        public GitException(String message) {
            super(message);
        }
    }

    private ChangedFiles() {
    }

    /**
     * 在 {@code repoDir} 里算 {@code ref} 以来改动的 {@code .java} 文件，返回绝对路径，
     * 去重、按路径排序。没有改动的 Java 文件时返回空列表。
     */
    public static List<Path> collect(Path repoDir, String ref) throws GitException {
        if (ref == null || ref.isBlank()) {
            throw new GitException(Messages.t("--since 后面要跟一个 git 引用，比如 origin/main、HEAD~1 或 v0.6.0",
                    "--since needs a git ref, e.g. origin/main, HEAD~1 or v0.6.0"));
        }
        Path dir = repoDir == null ? Path.of("") : repoDir;
        Path root = repositoryRoot(dir);
        // 先找分叉点，再从它 diff 到工作区：这样「PR 相对目标分支的改动」和「本地改了一半还没提交」
        // 是同一套算法。只写 `git diff <ref>` 会把目标分支自己后来的提交也算进来，不是我们要的。
        String base = run(root, "merge-base", ref, "HEAD").strip();
        String tracked = run(root, "diff", "--name-only", "--diff-filter=ACMR", base);
        String untracked = run(root, "ls-files", "--others", "--exclude-standard");
        return parse(tracked + "\n" + untracked, root);
    }

    /**
     * 只留下落在 {@code roots} 里的文件：{@code roots} 里的目录收其下所有文件，文件则只收它自己。
     * 用户同时写了路径和 {@code --since} 时，用这个把「整个仓库的改动」收窄回他圈定的范围。
     */
    public static List<Path> under(List<Path> files, List<Path> roots) {
        if (roots == null || roots.isEmpty() || files == null) {
            return files;
        }
        List<Path> normalized = new ArrayList<>();
        for (Path root : roots) {
            normalized.add(root.toAbsolutePath().normalize());
        }
        List<Path> kept = new ArrayList<>();
        for (Path file : files) {
            Path abs = file.toAbsolutePath().normalize();
            for (Path root : normalized) {
                if (abs.equals(root) || abs.startsWith(root)) {
                    kept.add(file);
                    break;
                }
            }
        }
        return kept;
    }

    /** 仓库根目录；不在仓库里时给一句人话。 */
    static Path repositoryRoot(Path dir) throws GitException {
        String output = run(dir, "rev-parse", "--show-toplevel");
        String line = output.strip();
        if (line.isEmpty()) {
            throw new GitException(Messages.f("--since 得在 git 仓库里跑：当前目录 %s 往上找不到 .git",
                    "--since has to run inside a git repo: no .git above %s", dir.toAbsolutePath()));
        }
        return Path.of(line);
    }

    /** 把 {@code git diff --name-only} / {@code git ls-files} 的输出变成文件列表。纯函数，好测。 */
    static List<Path> parse(String stdout, Path root) {
        LinkedHashSet<Path> files = new LinkedHashSet<>();
        if (stdout != null) {
            for (String line : stdout.split("\\R")) {
                String name = line.strip();
                if (name.isEmpty() || !name.toLowerCase(Locale.ROOT).endsWith(".java")) {
                    continue;
                }
                Path file = root.resolve(name).normalize();
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        }
        List<Path> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparing(Path::toString));
        return List.copyOf(sorted);
    }

    /**
     * 跑一条 git 命令，返回 stdout。失败时把 git 自己的报错带回去——用户需要看见
     * 「unknown revision」这种原文，才知道 ref 写错了还是仓库不对。
     */
    private static String run(Path dir, String... args) throws GitException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(dir.toAbsolutePath().toString());
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            int code = process.waitFor();
            if (code != 0) {
                throw new GitException(Messages.f("--since 读不出来：%s（ref 写成 origin/main、HEAD~1 或某个 tag；当前目录得在 git 仓库里）",
                        "--since failed: %s (use a ref like origin/main, HEAD~1 or a tag; "
                                + "and run inside a git repo)", firstLine(output)));
            }
            return output;
        } catch (IOException e) {
            throw new GitException(Messages.f("--since 得用 git，但它跑不起来：%s（机器上装 git 了吗？）",
                    "--since needs git, but it will not start: %s (is git installed?)", e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException(Messages.t("--since 等 git 的时候被打断了", "--since was interrupted while waiting for git"));
        }
    }

    private static String firstLine(String output) {
        String text = output.strip();
        if (text.isEmpty()) {
            return Messages.t("git 没说原因", "git gave no reason");
        }
        int newline = text.indexOf('\n');
        String line = newline < 0 ? text : text.substring(0, newline);
        return line.strip();
    }
}