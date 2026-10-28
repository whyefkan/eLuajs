package eluajs.util;

public final class AppInfo {
    public static final String FALLBACK_VERSION = "1.4.2";
    private AppInfo() {}

    /** Reads Implementation-Version from the packaged jar, falling back to the source default. */
    public static String version() {
        try {
            String v = AppInfo.class.getPackage().getImplementationVersion();
            if (v != null && !v.isBlank()) return v.trim();
        } catch (Exception ignored) { }
        return FALLBACK_VERSION;
    }
}
