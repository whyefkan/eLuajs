package eluajs.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Dependency-free Git integration using the system git executable. */
public final class GitManager {
    private GitManager() {}

    public record Result(int exitCode, String output) {
        public boolean ok() { return exitCode == 0; }
    }

    public record Change(String code, String path, String display) {
        @Override public String toString() { return code + "  " + display; }
    }

    public static boolean available() {
        return run(null, "git", "--version").ok();
    }

    public static boolean isRepository(Path root) {
        if (root == null) return false;
        if (Files.isDirectory(root.resolve(".git"))) return true;
        return run(root, "git", "rev-parse", "--is-inside-work-tree").ok();
    }

    public static Result init(Path root) {
        return run(root, "git", "init");
    }

    public static Result status(Path root) {
        return run(root, "git", "status", "--porcelain=v1");
    }

    public static List<Change> changes(Path root) {
        Result result = status(root);
        if (!result.ok()) return List.of();
        List<Change> changes = new ArrayList<>();
        for (String line : result.output().split("\\R")) {
            if (line.length() < 4) continue;
            String code = line.substring(0, 2).trim();
            String path = line.substring(3).trim();
            String display = path;
            int rename = path.indexOf(" -> ");
            if (rename >= 0) display = path.substring(rename + 4) + "  ←  " + path.substring(0, rename);
            changes.add(new Change(code, path, display));
        }
        return changes;
    }

    public static String branch(Path root) {
        Result result = run(root, "git", "rev-parse", "--abbrev-ref", "HEAD");
        return result.ok() ? result.output().trim() : "";
    }

    public static String remote(Path root) {
        Result result = run(root, "git", "remote", "-v");
        if (!result.ok() || result.output().isBlank()) return "";
        String first = result.output().lines().findFirst().orElse("");
        return first.replaceAll("\\s+", " ").trim();
    }

    public static Result stageAll(Path root) {
        return run(root, "git", "add", "-A");
    }

    public static Result commit(Path root, String message) {
        if (message == null || message.isBlank()) return new Result(1, "Commit message is empty.");
        Result first = run(root, "git", "commit", "-m", message.trim());
        if (first.ok()) return first;
        String out = first.output().toLowerCase(java.util.Locale.ROOT);
        boolean identityProblem = out.contains("author identity unknown") || out.contains("please tell me who you are")
                || out.contains("yazar kimli") || out.contains("kim olduğunuzu")
                || out.contains("e-posta adresi") || out.contains("email address");
        if (identityProblem) {
            return run(root, "git", "-c", "user.name=eLuajs", "-c", "user.email=eluajs@localhost", "commit", "-m", message.trim());
        }
        return first;
    }

    public static Result pull(Path root) {
        return run(root, "git", "pull", "--ff-only");
    }

    public static Result push(Path root) {
        Result upstream = run(root, "git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}");
        if (upstream.ok()) return run(root, "git", "push");
        String branch = branch(root);
        if (branch.isBlank() || branch.equals("HEAD")) return new Result(1, "No current branch.");
        return run(root, "git", "push", "-u", "origin", branch);
    }

    public static Result log(Path root, int count) {
        return run(root, "git", "log", "--oneline", "--decorate", "-n", String.valueOf(Math.max(1, count)));
    }

    public static Result branches(Path root) {
        return run(root, "git", "branch", "--list");
    }

    public static Result createBranch(Path root, String name) {
        if (name == null || name.isBlank()) return new Result(1, "Branch name is empty.");
        return run(root, "git", "checkout", "-b", name.trim());
    }

    public static Result checkout(Path root, String name) {
        if (name == null || name.isBlank()) return new Result(1, "Branch name is empty.");
        return run(root, "git", "checkout", name.trim());
    }

    public static Result diff(Path root, String path) {
        Result unstaged = run(root, "git", "diff", "--no-color", "--", path);
        Result staged = run(root, "git", "diff", "--no-color", "--cached", "--", path);
        StringBuilder out = new StringBuilder();
        if (!staged.output().isBlank()) out.append(staged.output()).append('\n');
        if (!unstaged.output().isBlank()) out.append(unstaged.output());
        if (out.isEmpty()) out.append(run(root, "git", "diff", "--no-color", "HEAD", "--", path).output());
        return new Result(0, out.toString());
    }

    public static Result run(Path root, String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (root != null) builder.directory(root.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(124, output + "\nGit command timed out.");
            }
            return new Result(process.exitValue(), output);
        } catch (IOException ex) {
            return new Result(127, "Git is not available: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(130, "Git command interrupted.");
        }
    }
}
