package eluajs.ai;

import eluajs.util.AppPaths;
import eluajs.util.FileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent per-project AI chat history, stored under ~/.eluajs/ai-chats. */
public final class ChatStore {
    public record Session(String id, String title, long updatedAt, List<AiMessage> messages) { }

    private static final Path ROOT = AppPaths.config().resolve("ai-chats");

    private ChatStore() {}

    public static String newId() {
        return System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static List<Session> list(Path projectRoot) {
        Path dir = projectDir(projectRoot);
        if (!Files.isDirectory(dir)) return List.of();
        List<Session> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                Session session = read(file);
                if (session != null) out.add(session);
            }
        } catch (IOException ignored) { }
        out.sort((a, b) -> Long.compare(b.updatedAt(), a.updatedAt()));
        return out;
    }

    public static Session latest(Path projectRoot) {
        List<Session> sessions = list(projectRoot);
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    public static void save(Path projectRoot, Session session) {
        if (session == null || session.id() == null || session.messages() == null) return;
        String title = session.title() == null || session.title().isBlank()
                ? titleFor(session.messages()) : session.title().trim();
        if (title.length() > 60) title = title.substring(0, 57) + "…";
        StringBuilder json = new StringBuilder();
        json.append("{\"id\":").append(Json.quote(session.id()))
                .append(",\"title\":").append(Json.quote(title))
                .append(",\"updatedAt\":").append(System.currentTimeMillis())
                .append(",\"messages\":[");
        for (int i = 0; i < session.messages().size(); i++) {
            AiMessage message = session.messages().get(i);
            if (message == null || message.content() == null) continue;
            if (i > 0) json.append(',');
            json.append("{\"role\":").append(Json.quote(message.role()))
                    .append(",\"content\":").append(Json.quote(message.content())).append('}');
        }
        json.append("]}");
        try {
            Path dir = projectDir(projectRoot);
            Files.createDirectories(dir);
            Path file = dir.resolve(safeId(session.id()) + ".json");
            FileUtil.writeStringAtomic(file, json.toString());
        } catch (Exception ignored) { }
    }

    public static String titleFor(List<AiMessage> messages) {
        if (messages == null) return "Chat";
        for (AiMessage message : messages) {
            if (message != null && "user".equals(message.role()) && message.content() != null && !message.content().isBlank()) {
                String title = message.content().replaceAll("\\s+", " ").trim();
                return title.length() > 52 ? title.substring(0, 49) + "…" : title;
            }
        }
        return "Chat";
    }

    private static Session read(Path file) {
        try {
            Object root = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            String id = Json.stringAt(root, "id");
            if (id == null || id.isBlank()) id = file.getFileName().toString().replaceFirst("\\.json$", "");
            String title = Json.stringAt(root, "title");
            long updatedAt = number(Json.at(root, "updatedAt"));
            List<AiMessage> messages = new ArrayList<>();
            Object array = Json.at(root, "messages");
            if (array instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof java.util.Map<?, ?> map)) continue;
                    Object role = map.get("role");
                    Object content = map.get("content");
                    if (role != null && content != null) {
                        messages.add(new AiMessage(String.valueOf(role), String.valueOf(content)));
                    }
                }
            }
            if (messages.isEmpty()) return null;
            if (title == null || title.isBlank()) title = titleFor(messages);
            return new Session(id, title, updatedAt, messages);
        } catch (Exception ex) {
            return null;
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string) {
            try { return Long.parseLong(string); } catch (NumberFormatException ignored) { }
        }
        return 0;
    }

    private static String safeId(String id) {
        return id.replaceAll("[^A-Za-z0-9._-]+", "-");
    }

    private static Path projectDir(Path projectRoot) {
        String name = "project";
        if (projectRoot != null) {
            Path fileName = projectRoot.getFileName();
            if (fileName != null) name = fileName.toString();
        }
        name = name.replaceAll("[^A-Za-z0-9._-]+", "-");
        if (name.isBlank()) name = "project";
        String hash = Integer.toHexString(projectRoot == null ? 0
                : projectRoot.toAbsolutePath().normalize().toString().hashCode());
        return ROOT.resolve(name + "-" + hash);
    }
}
