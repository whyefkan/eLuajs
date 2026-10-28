package eluajs.model;

import java.nio.file.Path;

public record ProjectMeta(
        String appName,
        String versionName,
        int versionCode,
        String packageName,
        Path logoPath,
        Path rootDir
) {}
