package eluajs.android;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Release / Play Store AAB imzalama bilgileri. */
public record ReleaseSigningConfig(
        Path storeFile,
        String storePassword,
        String keyAlias,
        String keyPassword) {

    public ReleaseSigningConfig {
        if (storeFile != null) storeFile = storeFile.toAbsolutePath().normalize();
        storePassword = storePassword == null ? "" : storePassword;
        keyAlias = keyAlias == null ? "" : keyAlias.trim();
        keyPassword = keyPassword == null ? "" : keyPassword;
    }

    public void validate() {
        if (storeFile == null || !Files.isRegularFile(storeFile))
            throw new IllegalArgumentException("Keystore dosyası bulunamadı: " + storeFile);
        if (storePassword.isEmpty()) throw new IllegalArgumentException("Keystore şifresi boş olamaz.");
        if (keyAlias.isBlank()) throw new IllegalArgumentException("Key alias boş olamaz.");
        if (keyPassword.isEmpty()) throw new IllegalArgumentException("Anahtar şifresi boş olamaz.");
    }

    public Map<String,String> asEnvironment() {
        validate();
        Map<String,String> env = new HashMap<>();
        env.put("ELUAJS_STORE_FILE", storeFile.toString());
        env.put("ELUAJS_STORE_PASSWORD", storePassword);
        env.put("ELUAJS_KEY_ALIAS", keyAlias);
        env.put("ELUAJS_KEY_PASSWORD", keyPassword);
        return env;
    }
}
