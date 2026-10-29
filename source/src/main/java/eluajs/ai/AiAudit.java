package eluajs.ai;

import eluajs.util.AppPaths;
import eluajs.util.FileUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Local, content-free audit trail for AI file changes. */
public final class AiAudit {
    private AiAudit() {}

    public static void log(Path projectRoot, String action, String relative, String detail) {
        if (projectRoot == null) return;
        try {
            Path file = projectRoot.resolve(".eluajs/ai-audit.log");
            Files.createDirectories(file.getParent());
            FileUtil.secureDir(file.getParent());
            String line = Instant.now() + "\t" + action + "\t" + relative + "\t" + (detail == null ? "" : detail) + "\n";
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            FileUtil.secureFile(file);
        } catch (Exception ignored) { }
    }
}
