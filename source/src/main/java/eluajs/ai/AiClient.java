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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Minimal multi-provider AI client with no external dependencies. */
public final class AiClient {
    private static final AtomicReference<AutoCloseable> ACTIVE_STREAM = new AtomicReference<>();
    private static final AtomicBoolean CANCELLED = new AtomicBoolean();

    public static void cancelActive() {
        CANCELLED.set(true);
        AutoCloseable stream = ACTIVE_STREAM.getAndSet(null);
        if (stream != null) try { stream.close(); } catch (Exception ignored) { }
    }

    private static void beginRequest() {
        CANCELLED.set(false);
        ACTIVE_STREAM.set(null);
    }

    private static void checkCancelled() throws InterruptedException {
        if (CANCELLED.get() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("AI request cancelled");
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private AiClient() {}

    private static final int MAX_ATTEMPTS = 3;

    private static <T> HttpResponse<T> sendWithRetry(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            checkCancelled();
            try {
                HttpResponse<T> response = HTTP.send(request, handler);
                int status = response.statusCode();
                if (status != 429 && status < 500) return response;
                last = new IOException("HTTP " + status);
            } catch (java.io.IOException ex) {
                last = ex;
            }
            if (attempt < MAX_ATTEMPTS) {
                try { Thread.sleep(600L * attempt * attempt); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw ie; }
            }
        }
        if (last != null) throw last;
        throw new IOException("AI request failed");
    }

    public static String complete(AiConfig cfg, String system, String user) throws Exception {
        return completeDetailed(cfg, system, List.of(AiMessage.user(user))).text();
    }

    public static String complete(AiConfig cfg, String system, List<AiMessage> messages) throws Exception {
        return completeDetailed(cfg, system, messages).text();
    }

    public static AiResult completeStreaming(AiConfig cfg, String system, List<AiMessage> messages,
                                             java.util.function.Consumer<String> onDelta) throws Exception {
        beginRequest();
        AiProvider provider = cfg.provider();
        if (onDelta == null) return completeDetailed(cfg, system, messages);
        String model = cfg.activeModel();
        String key = cfg.activeKey();
        String base = cfg.activeBaseUrl();
        if (provider.requiresKey() && (key == null || key.isBlank())) {
            throw new IllegalStateException(I18n.t("No API key configured. Open Settings and add one."));
        }
        if (base == null || base.isBlank()) throw new IllegalStateException(I18n.t("Base URL is required for this provider."));
        if (model == null || model.isBlank()) throw new IllegalStateException(I18n.t("Enter a model name for this provider."));
        HttpRequest request = switch (provider.kind()) {
            case OPENAI -> openAiRequest(provider, base, model, key, system, messages, true);
            case ANTHROPIC -> anthropicRequest(base, model, key, system, messages, true);
            case GEMINI -> geminiRequest(base, model, key, system, messages, true);
        };
        HttpResponse<java.io.InputStream> response = sendWithRetry(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException(errorMessage(body, response.statusCode()));
        }
        StringBuilder text = new StringBuilder();
        long prompt = 0, completion = 0, total = 0;
        java.io.InputStream streamBody = response.body();
        ACTIVE_STREAM.set(streamBody);
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(streamBody, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                checkCancelled();
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.equals("[DONE]")) break;
                if (data.isEmpty()) continue;
                try {
                    Object root = Json.parse(data);
                    if (provider.kind() == AiProvider.Kind.OPENAI) {
                        Object delta = Json.at(root, "choices", 0, "delta", "content");
                        appendDelta(text, delta, onDelta);
                        long p = number(Json.at(root, "usage", "prompt_tokens"));
                        long c = number(Json.at(root, "usage", "completion_tokens"));
                        long t = number(Json.at(root, "usage", "total_tokens"));
                        if (p > 0) prompt = p;
                        if (c > 0) completion = c;
                        if (t > 0) total = t;
                    } else if (provider.kind() == AiProvider.Kind.ANTHROPIC) {
                        appendDelta(text, Json.at(root, "delta", "text"), onDelta);
                        long p = number(Json.at(root, "message", "usage", "input_tokens"));
                        long c = number(Json.at(root, "usage", "output_tokens"));
                        if (p > 0) prompt = p;
                        if (c > 0) completion = c;
                    } else {
                        Object candidates = Json.at(root, "candidates");
                        if (candidates instanceof java.util.List<?> list && !list.isEmpty()) {
                            appendDelta(text, Json.at(list.get(0), "content", "parts", 0, "text"), onDelta);
                        }
                        long p = number(Json.at(root, "usageMetadata", "promptTokenCount"));
                        long c = number(Json.at(root, "usageMetadata", "candidatesTokenCount"));
                        long t = number(Json.at(root, "usageMetadata", "totalTokenCount"));
                        if (p > 0) prompt = p;
                        if (c > 0) completion = c;
                        if (t > 0) total = t;
                    }
                } catch (Exception ignored) { }
            }
        } finally {
            ACTIVE_STREAM.compareAndSet(streamBody, null);
        }
        if (text.isEmpty()) throw new IOException(I18n.t("Empty response from the provider."));
        if (completion <= 0) completion = Math.max(1, text.length() / 4);
        if (total <= 0) total = prompt + completion;
        return new AiResult(text.toString().trim(), prompt, completion, total);
    }

    private static void appendDelta(StringBuilder text, Object delta, java.util.function.Consumer<String> onDelta) {
        if (delta == null) return;
        String piece = String.valueOf(delta);
        if (!piece.isEmpty()) { text.append(piece); onDelta.accept(piece); }
    }

    public static AiResult completeDetailed(AiConfig cfg, String system, List<AiMessage> messages) throws Exception {
        beginRequest();
        AiProvider provider = cfg.provider();
        String model = cfg.activeModel();
        String key = cfg.activeKey();
        String base = cfg.activeBaseUrl();
        if (provider.requiresKey() && (key == null || key.isBlank())) {
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

        HttpResponse<String> response = sendWithRetry(request, HttpResponse.BodyHandlers.ofString());
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

    public static List<String> listModels(AiConfig cfg) throws Exception {
        AiProvider provider = cfg.provider();
        String base = cfg.activeBaseUrl();
        String key = cfg.activeKey();
        if (base == null || base.isBlank()) throw new IOException(I18n.t("Base URL is required for this provider."));
        String url = base.replaceAll("/+$", "");
        HttpRequest.Builder b;
        if (provider.kind() == AiProvider.Kind.GEMINI) {
            url += "/models?key=" + URLEncoder.encode(key == null ? "" : key, StandardCharsets.UTF_8);
            b = HttpRequest.newBuilder(URI.create(url));
        } else {
            url += "/models";
            b = HttpRequest.newBuilder(URI.create(url));
            if (key != null && !key.isBlank()) {
                if (provider.kind() == AiProvider.Kind.ANTHROPIC) b.header("x-api-key", key);
                else b.header("Authorization", "Bearer " + key);
            }
            if (provider.kind() == AiProvider.Kind.ANTHROPIC) b.header("anthropic-version", "2023-06-01");
        }
        b.timeout(Duration.ofSeconds(30)).header("User-Agent", "eLuajs/1.5.7")
                .header("Accept", "application/json");
        HttpResponse<String> response = sendWithRetry(b.GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IOException("Model list failed: HTTP " + response.statusCode());
        Object root = Json.parse(response.body());
        List<String> result = new java.util.ArrayList<>();
        Object list = Json.at(root, "data");
        if (list instanceof List<?> items) {
            for (Object item : items) {
                String id = item instanceof Map<?, ?> map ? String.valueOf(map.get("id")) : null;
                if (id != null && !id.isBlank() && !id.equals("null")) result.add(id);
            }
        }
        list = Json.at(root, "models");
        if (list instanceof List<?> items) {
            for (Object item : items) {
                String name = item instanceof Map<?, ?> map ? String.valueOf(map.get("name")) : null;
                if (name != null && !name.isBlank() && !name.equals("null")) {
                    if (name.startsWith("models/")) name = name.substring(7);
                    result.add(name);
                }
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
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
        return openAiRequest(provider, base, model, key, system, messages, false);
    }

    private static HttpRequest openAiRequest(AiProvider provider, String base, String model, String key,
                                             String system, List<AiMessage> messages, boolean stream) {
        String url = endpoint(base, "/chat/completions");
        String body = "{\"model\":" + Json.quote(model)
                + ",\"messages\":" + openAiMessages(system, messages) + ",\"temperature\":0.2"
                + (stream ? ",\"stream\":true" : "") + "}";
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("User-Agent", "eLuajs/1.5.7")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (key != null && !key.isBlank()) b.header("Authorization", "Bearer " + key);
        if (provider == AiProvider.OPENROUTER) {
            b.header("HTTP-Referer", "https://github.com/whyefkan/eLuajs");
            b.header("X-Title", "eLuajs");
        }
        return b.build();
    }

    private static HttpRequest anthropicRequest(String base, String model, String key, String system, List<AiMessage> messages) {
        return anthropicRequest(base, model, key, system, messages, false);
    }

    private static HttpRequest anthropicRequest(String base, String model, String key, String system,
                                                List<AiMessage> messages, boolean stream) {
        String url = endpoint(base, "/messages");
        String body = "{\"model\":" + Json.quote(model)
                + ",\"max_tokens\":4096,\"temperature\":0.2"
                + ",\"system\":[{\"type\":\"text\",\"text\":" + Json.quote(system)
                + ",\"cache_control\":{\"type\":\"ephemeral\"}}]"
                + (stream ? ",\"stream\":true" : "")
                + ",\"messages\":" + anthropicMessages(messages) + "}";
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .header("User-Agent", "eLuajs/1.5.7")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private static HttpRequest geminiRequest(String base, String model, String key, String system, List<AiMessage> messages) {
        return geminiRequest(base, model, key, system, messages, false);
    }

    private static HttpRequest geminiRequest(String base, String model, String key, String system,
                                             List<AiMessage> messages, boolean stream) {
        String m = model.startsWith("models/") ? model.substring("models/".length()) : model;
        String encodedModel = URLEncoder.encode(m, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
        String url = endpoint(base, "/models/" + encodedModel + (stream ? ":streamGenerateContent?alt=sse&key=" : ":generateContent?key=") + encodedKey);
        String body = "{\"system_instruction\":{\"parts\":[{\"text\":" + Json.quote(system) + "}]},"
                + "\"contents\":" + geminiContents(messages) + ","
                + "\"generationConfig\":{\"temperature\":0.2}}";
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("User-Agent", "eLuajs/1.5.7")
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
