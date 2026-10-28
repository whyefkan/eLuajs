package eluajs.ai;

import eluajs.util.I18n;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Minimal multi-provider AI client with no external dependencies. */
public final class AiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private AiClient() {}

    public static String complete(AiConfig cfg, String system, String user) throws Exception {
        return completeDetailed(cfg, system, List.of(AiMessage.user(user))).text();
    }

    public static String complete(AiConfig cfg, String system, List<AiMessage> messages) throws Exception {
        return completeDetailed(cfg, system, messages).text();
    }

    public static AiResult completeDetailed(AiConfig cfg, String system, List<AiMessage> messages) throws Exception {
        AiProvider provider = cfg.provider();
        String model = cfg.activeModel();
        String key = cfg.activeKey();
        String base = cfg.activeBaseUrl();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(I18n.t("No API key configured. Open Settings and add one."));
        }
        if (base == null || base.isBlank()) {
            throw new IllegalStateException(I18n.t("Base URL is required for this provider."));
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException(I18n.t("Enter a model name for this provider."));
        }

        HttpRequest request = switch (provider.kind()) {
            case OPENAI -> openAiRequest(provider, base, model, key, system, messages);
            case ANTHROPIC -> anthropicRequest(base, model, key, system, messages);
            case GEMINI -> geminiRequest(base, model, key, system, messages);
        };

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body() == null ? "" : response.body();
        if (response.statusCode() / 100 != 2) {
            throw new IOException(errorMessage(body, response.statusCode()));
        }
        Object root;
        try {
            root = Json.parse(body);
        } catch (Exception ex) {
            String trimmed = body.trim();
            if (trimmed.length() > 300) trimmed = trimmed.substring(0, 300) + "…";
            throw new IOException("Invalid response from provider: " + trimmed);
        }
        String text = switch (provider.kind()) {
            case OPENAI -> text(Json.at(root, "choices", 0, "message", "content"));
            case ANTHROPIC -> joinParts(Json.at(root, "content"));
            case GEMINI -> joinParts(Json.at(root, "candidates", 0, "content", "parts"));
        };
        if (text == null || text.isBlank()) {
            String err = Json.stringAt(root, "error", "message");
            throw new IOException(err != null ? err : I18n.t("Empty response from the provider."));
        }
        long prompt = 0, completion = 0, total = 0;
        if (provider.kind() == AiProvider.Kind.OPENAI) {
            prompt = number(Json.at(root, "usage", "prompt_tokens"));
            completion = number(Json.at(root, "usage", "completion_tokens"));
            total = number(Json.at(root, "usage", "total_tokens"));
        } else if (provider.kind() == AiProvider.Kind.ANTHROPIC) {
            prompt = number(Json.at(root, "usage", "input_tokens"));
            completion = number(Json.at(root, "usage", "output_tokens"));
        } else {
            prompt = number(Json.at(root, "usageMetadata", "promptTokenCount"));
            completion = number(Json.at(root, "usageMetadata", "candidatesTokenCount"));
            total = number(Json.at(root, "usageMetadata", "totalTokenCount"));
        }
        if (total <= 0) total = prompt + completion;
        return new AiResult(text.trim(), prompt, completion, total);
    }

    private static long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) { }
        }
        return 0;
    }

    private static HttpRequest openAiRequest(AiProvider provider, String base, String model, String key,
                                             String system, List<AiMessage> messages) {
        String url = endpoint(base, "/chat/completions");
        String body = "{\"model\":" + Json.quote(model)
                + ",\"messages\":" + openAiMessages(system, messages) + ",\"temperature\":0.2}";
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .header("User-Agent", "eLuajs/1.3")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (provider == AiProvider.OPENROUTER) {
            b.header("HTTP-Referer", "https://github.com/whyefkan/eLuajs");
            b.header("X-Title", "eLuajs");
        }
        return b.build();
    }

    private static HttpRequest anthropicRequest(String base, String model, String key, String system, List<AiMessage> messages) {
        String url = endpoint(base, "/messages");
        String body = "{\"model\":" + Json.quote(model)
                + ",\"max_tokens\":4096,\"temperature\":0.2"
                + ",\"system\":" + Json.quote(system)
                + ",\"messages\":" + anthropicMessages(messages) + "}";
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .header("User-Agent", "eLuajs/1.3")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private static HttpRequest geminiRequest(String base, String model, String key, String system, List<AiMessage> messages) {
        String m = model.startsWith("models/") ? model.substring("models/".length()) : model;
        String encodedModel = URLEncoder.encode(m, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
        String url = endpoint(base, "/models/" + encodedModel + ":generateContent?key=" + encodedKey);
        String body = "{\"system_instruction\":{\"parts\":[{\"text\":" + Json.quote(system) + "}]},"
                + "\"contents\":" + geminiContents(messages) + ","
                + "\"generationConfig\":{\"temperature\":0.2}}";
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("User-Agent", "eLuajs/1.3")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private static String openAiMessages(String system, List<AiMessage> messages) {
        StringBuilder b = new StringBuilder("[");
        b.append("{\"role\":\"system\",\"content\":").append(Json.quote(system)).append("}");
        for (AiMessage m : messages) {
            if (m == null || m.content() == null || m.content().isBlank()) continue;
            b.append(",{\"role\":").append(Json.quote(m.role()))
                    .append(",\"content\":").append(Json.quote(m.content())).append("}");
        }
        return b.append("]").toString();
    }

    private static String anthropicMessages(List<AiMessage> messages) {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (AiMessage m : messages) {
            if (m == null || m.content() == null || m.content().isBlank()) continue;
            String role = "assistant".equals(m.role()) ? "assistant" : "user";
            if (!first) b.append(',');
            first = false;
            b.append("{\"role\":").append(Json.quote(role))
                    .append(",\"content\":").append(Json.quote(m.content())).append("}");
        }
        if (first) b.append("{\"role\":\"user\",\"content\":\"(continue)\"}");
        return b.append("]").toString();
    }

    private static String geminiContents(List<AiMessage> messages) {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (AiMessage m : messages) {
            if (m == null || m.content() == null || m.content().isBlank()) continue;
            String role = "assistant".equals(m.role()) ? "model" : "user";
            if (!first) b.append(',');
            first = false;
            b.append("{\"role\":").append(Json.quote(role))
                    .append(",\"parts\":[{\"text\":").append(Json.quote(m.content())).append("}]}");
        }
        if (first) b.append("{\"role\":\"user\",\"parts\":[{\"text\":\"(continue)\"}]}");
        return b.append("]").toString();
    }

    private static String endpoint(String base, String suffix) {
        String b = base == null ? "" : base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (suffix.equals("/chat/completions") && b.endsWith("/chat/completions")) return b;
        if (suffix.equals("/messages") && b.endsWith("/messages")) return b;
        return b + suffix;
    }

    private static String text(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        if (value instanceof Map<?, ?> map) {
            Object t = map.get("text");
            return t == null ? null : text(t);
        }
        if (value instanceof List<?> list) return joinParts(list);
        return String.valueOf(value);
    }

    private static String joinParts(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        StringBuilder b = new StringBuilder();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String part = text(item);
                if (part != null && !part.isBlank()) {
                    if (!b.isEmpty()) b.append('\n');
                    b.append(part);
                }
            }
        } else {
            String part = text(value);
            if (part != null) b.append(part);
        }
        return b.isEmpty() ? null : b.toString();
    }

    private static String errorMessage(String body, int status) {
        try {
            Object root = Json.parse(body);
            Object msg = Json.at(root, "error", "message");
            if (msg != null) return "HTTP " + status + ": " + msg;
            Object detail = Json.at(root, "error");
            if (detail != null) return "HTTP " + status + ": " + detail;
        } catch (Exception ignored) { }
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.length() > 500) trimmed = trimmed.substring(0, 500) + "…";
        return "HTTP " + status + (trimmed.isEmpty() ? "" : ": " + trimmed);
    }
}
