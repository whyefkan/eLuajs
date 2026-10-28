package eluajs.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Creates normal Java source classes inside an Android app source set. */
public final class ProjectJavaClassManager {
    private ProjectJavaClassManager() {}

    public static Path create(Path projectRoot, String packageName, String className) throws IOException {
        String pkg = packageName == null ? "" : packageName.trim();
        String cls = className == null ? "" : className.trim();
        validatePackage(pkg);
        validateIdentifier(cls, "Sınıf adı");

        Path dir = projectRoot.resolve("app/src/main/java").resolve(pkg.replace('.', '/'));
        Files.createDirectories(dir);
        Path file = dir.resolve(cls + ".java");
        if (Files.exists(file)) throw new IOException("Bu Java sınıfı zaten var: " + cls);
        String code = "package " + pkg + ";\n\n" +
                "public class " + cls + " {\n" +
                "    \n" +
                "}\n";
        Files.writeString(file, code, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return file;
    }

    public static void validatePackage(String pkg) {
        if (pkg == null || pkg.isBlank()) throw new IllegalArgumentException("Paket adı boş olamaz.");
        for (String part : pkg.split("\\.")) validateIdentifier(part, "Paket adı");
    }

    private static void validateIdentifier(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " boş olamaz.");
        if (!Character.isJavaIdentifierStart(value.charAt(0))) throw new IllegalArgumentException(label + " geçerli bir Java adı değil: " + value);
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) throw new IllegalArgumentException(label + " geçerli bir Java adı değil: " + value);
        }
        if (isKeyword(value)) throw new IllegalArgumentException(label + " Java anahtar kelimesi olamaz: " + value);
    }

    private static boolean isKeyword(String s) {
        return switch (s) {
            case "abstract","assert","boolean","break","byte","case","catch","char","class","const","continue","default","do","double","else","enum","extends","final","finally","float","for","goto","if","implements","import","instanceof","int","interface","long","native","new","package","private","protected","public","return","short","static","strictfp","super","switch","synchronized","this","throw","throws","transient","try","void","volatile","while","true","false","null","record","sealed","permits","var","yield" -> true;
            default -> false;
        };
    }
}
