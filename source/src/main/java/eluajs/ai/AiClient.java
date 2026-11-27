package eluajs.ai;

import eluajs.util.AppInfo;
import eluajs.util.I18n;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Minimal multi-provider AI client with no external dependencies. */
public final class AiClient {
    private static final AtomicReference<AutoCloseable> ACTIVE_STREAM = new AtomicReference<>();
    private static final AtomicBoolean CANCELLED = new AtomicBoolean();
    private static final int MAX_ATTEMPTS = 3;
    private static final int ANTHROPIC_MAX_TOKENS = 8192;

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

    private static String userAgent() {
        return "eLuajs/" + AppInfo.version();
    }

    private static <T> HttpResponse<T> sendWithRetry(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                                     boolean honorCancel) throws Exception {
        return sendWithRetry(request, handler, honorCancel, MAX_ATTEMPTS);
    }

    private static <T> HttpResponse<T> sendWithRetry(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                                     boolean honorCancel, int maxAttempts) throws Exception {
        Exception last = null;
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (honorCancel) checkCancelled();
            else if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Request interrupted");
            try {
                HttpResponse<T> response = HTTP.send(request, handler);
                int status = response.statusCode();
                if ((status != 429 && status < 500) || attempt == attempts) return response;
                if (response.body() instanceof AutoCloseable closeable) {
                    try { closeable.close(); } catch (Exception ignored) { }
                }
                last = new IOException("HTTP " + status);
            } catch (java.io.IOException ex) {
                last = ex;
            }
            if (attempt < attempts) {
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
        HttpResponse<java.io.InputStream> response = sendWithRetry(request, HttpResponse.BodyHandlers.ofInputStream(), true);
        if (response.statusCode() / 100 != 2) {
            String body;
            try (java.io.InputStream errStream = response.body()) {
                body = new String(errStream.readAllBytes(), StandardCharsets.UTF_8);
            }
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
                Object root;
                try {
                    root = Json.parse(data);
                } catch (Exception ignored) {
                    continue;
                }
                String streamErr = streamedError(root);
                if (streamErr != null) throw new IOException(streamErr);
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
                    if ("content_block_delta".equals(Json.stringAt(root, "type"))
                            || Json.at(root, "delta", "text") != null) {
                        appendDelta(text, Json.at(root, "delta", "text"), onDelta);
                    }
                    long p = number(Json.at(root, "message", "usage", "input_tokens"));
                    long c = number(Json.at(root, "usage", "output_tokens"));
                    if (p > 0) prompt = p;
                    if (c > 0) completion = c;
                } else {
                    Object parts = Json.at(root, "candidates", 0, "content", "parts");
                    if (parts instanceof List<?> partList) {
                        for (Object part : partList) {
                            if (part instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("thought"))) continue;
                            appendDelta(text, text(part), onDelta);
                        }
                    }
                    long p = number(Json.at(root, "usageMetadata", "promptTokenCount"));
                    long c = number(Json.at(root, "usageMetadata", "candidatesTokenCount"));
                    long t = number(Json.at(root, "usageMetadata", "totalTokenCount"));
                    if (p > 0) prompt = p;
                    if (c > 0) completion = c;
                    if (t > 0) total = t;
                }
            }
        } finally {
            ACTIVE_STREAM.compareAndSet(streamBody, null);
        }
        if (text.isEmpty()) throw new IOException(I18n.t("Empty response from the provider."));
        if (completion <= 0) completion = Math.max(1, text.length() / 4);
        if (total <= 0) total = prompt + completion;
        return new AiResult(text.toString().trim(), prompt, completion, total);
    }

    private static String streamedError(Object root) {
        if (!(root instanceof Map<?, ?> map)) return null;
        if ("error".equals(Json.stringAt(root, "type")) || map.containsKey("error")) {
            Object msg = Json.at(root, "error", "message");
            if (msg != null && !String.valueOf(msg).isBlank()) return String.valueOf(msg);
            Object err = map.get("error");
            if (err != null && !String.valueOf(err).isBlank()) return String.valueOf(err);
        }
        return null;
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

        HttpResponse<String> response = sendWithRetry(request, HttpResponse.BodyHandlers.ofString(), true);
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
        return listModels(cfg, Duration.ofSeconds(30), MAX_ATTEMPTS);
    }

    /** Fast, single-attempt model list fetch used by silent startup sync. */
    public static List<String> listModelsFast(AiConfig cfg) throws Exception {
        return listModels(cfg, Duration.ofSeconds(10), 1);
    }

    private static List<String> listModels(AiConfig cfg, Duration timeout, int maxAttempts) throws Exception {
        AiProvider provider = cfg.provider();
        String base = cfg.activeBaseUrl();
        String key = cfg.activeKey();
        if (base == null || base.isBlank()) throw new IOException(I18n.t("Base URL is required for this provider."));
        String normalizedBase = normalizeBaseUrl(base);
        Set<String> collected = new LinkedHashSet<>();

        if (provider.kind() == AiProvider.Kind.GEMINI) {
            String pageToken = null;
            for (int page = 0; page < 5; page++) {
                String url = normalizedBase + "/models?pageSize=1000"
                        + (pageToken != null && !pageToken.isBlank()
                        ? "&pageToken=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8) : "");
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                        .timeout(timeout)
                        .header("User-Agent", userAgent())
                        .header("Accept", "application/json");
                if (key != null && !key.isBlank()) b.header("x-goog-api-key", key);
                HttpResponse<String> response = sendWithRetry(b.GET().build(), HttpResponse.BodyHandlers.ofString(), false, maxAttempts);
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("Model list failed: " + errorMessage(response.body(), response.statusCode()));
                }
                Object root = Json.parse(response.body());
                collected.addAll(parseModelList(provider, root));
                pageToken = Json.stringAt(root, "nextPageToken");
                if (pageToken == null || pageToken.isBlank()) break;
            }
        } else {
            String url = normalizedBase + (provider.kind() == AiProvider.Kind.ANTHROPIC ? "/models?limit=100" : "/models");
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", userAgent())
                    .header("Accept", "application/json");
            if (key != null && !key.isBlank()) {
                if (provider.kind() == AiProvider.Kind.ANTHROPIC) b.header("x-api-key", key);
                else b.header("Authorization", "Bearer " + key);
            }
            if (provider.kind() == AiProvider.Kind.ANTHROPIC) b.header("anthropic-version", "2023-06-01");
            if (provider == AiProvider.OPENROUTER) {
                b.header("HTTP-Referer", "https://github.com/whyefkan/eLuajs");
                b.header("X-Title", "eLuajs");
            }
            HttpResponse<String> response = sendWithRetry(b.GET().build(), HttpResponse.BodyHandlers.ofString(), false, maxAttempts);
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Model list failed: " + errorMessage(response.body(), response.statusCode()));
            }
            Object root = Json.parse(response.body());
            collected.addAll(parseModelList(provider, root));
        }

        List<String> result = new ArrayList<>(collected);
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    static List<String> parseModelList(AiProvider provider, Object root) {
        Set<String> result = new LinkedHashSet<>();
        Object dataList = Json.at(root, "data");
        if (dataList instanceof List<?> items) {
            collectModelEntries(provider, items, result);
        }
        Object modelsList = Json.at(root, "models");
        if (modelsList instanceof List<?> items) {
            collectModelEntries(provider, items, result);
        }
        if (root instanceof List<?> items) {
            collectModelEntries(provider, items, result);
        }
        return new ArrayList<>(result);
    }

    private static void collectModelEntries(AiProvider provider, List<?> items, Set<String> out) {
        for (Object item : items) {
            String id = null;
            if (item instanceof String s) {
                id = s;
            } else if (item instanceof Map<?, ?> map) {
                if (provider != null && provider.kind() == AiProvider.Kind.GEMINI) {
                    Object methods = map.get("supportedGenerationMethods");
                    if (methods instanceof List<?> methodList && !methodList.isEmpty()
                            && !methodList.contains("generateContent")) {
                        continue;
                    }
                }
                Object rawId = map.get("id");
                Object rawName = map.get("name");
                if (rawId != null && !String.valueOf(rawId).isBlank()) id = String.valueOf(rawId);
                else if (rawName != null && !String.valueOf(rawName).isBlank()) id = String.valueOf(rawName);
            }
            if (id == null) continue;
            id = id.trim();
            if (id.isEmpty() || "null".equals(id)) continue;
            if (id.startsWith("models/")) id = id.substring("models/".length());
            if (provider == AiProvider.OPENAI && !isOpenAiChatModel(id)) continue;
            out.add(id);
        }
    }

    static boolean isOpenAiChatModel(String id) {
        String m = id.toLowerCase(Locale.ROOT);
        if (m.contains("embedding") || m.contains("whisper") || m.contains("moderation")
                || m.contains("dall-e") || m.contains("gpt-image") || m.contains("sora")
                || m.contains("transcribe") || m.contains("realtime") || m.contains("gpt-live")
                || m.startsWith("tts-") || m.contains("-tts") || m.startsWith("babbage")
                || m.startsWith("davinci") || m.contains("audio-preview")) {
            return false;
        }
        return true;
    }

    static boolean supportsCustomTemperature(AiProvider provider, String model) {
        if (model == null || model.isBlank()) return false;
        String m = model.trim().toLowerCase(Locale.ROOT);
        int slash = m.lastIndexOf('/');
        String bare = slash >= 0 ? m.substring(slash + 1) : m;

        if (provider == AiProvider.ANTHROPIC) {
            return bare.startsWith("claude-3");
        }
        if (provider == AiProvider.GEMINI) {
            return bare.startsWith("gemini-1.") || bare.startsWith("gemini-2.0");
        }
        if (bare.startsWith("gpt-6") || bare.startsWith("gpt-5")
                || bare.startsWith("o1") || bare.startsWith("o3") || bare.startsWith("o4")
                || bare.startsWith("deepseek-reasoner") || bare.equals("deepseek-pro")
                || bare.startsWith("claude-fable") || bare.startsWith("claude-opus-5")
                || bare.startsWith("claude-sonnet-5") || bare.startsWith("claude-haiku-5")
                || bare.startsWith("claude-opus-4-7") || bare.startsWith("claude-opus-4-8")) {
            return false;
        }
        return true;
    }

    static String normalizeBaseUrl(String base) {
        String b = base == null ? "" : base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        for (String suffix : new String[]{"/chat/completions", "/messages", "/responses", "/models"}) {
            if (b.endsWith(suffix)) {
                b = b.substring(0, b.length() - suffix.length());
                while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
            }
        }
        return b;
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
        StringBuilder body = new StringBuilder();
        body.append("{\"model\":").append(Json.quote(model))
                .append(",\"messages\":").append(openAiMessages(system, messages));
        if (supportsCustomTemperature(provider, model)) {
            body.append(",\"temperature\":0.2");
        }
        if (stream) body.append(",\"stream\":true");
        body.append('}');
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
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
        StringBuilder body = new StringBuilder();
        body.append("{\"model\":").append(Json.quote(model))
                .append(",\"max_tokens\":").append(ANTHROPIC_MAX_TOKENS);
        if (supportsCustomTemperature(AiProvider.ANTHROPIC, model)) {
            body.append(",\"temperature\":0.2");
        }
        if (system != null && !system.isBlank()) {
            body.append(",\"system\":[{\"type\":\"text\",\"text\":").append(Json.quote(system))
                    .append(",\"cache_control\":{\"type\":\"ephemeral\"}}]");
        }
        if (stream) body.append(",\"stream\":true");
        body.append(",\"messages\":").append(anthropicMessages(messages)).append('}');
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .header("User-Agent", userAgent())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    private static HttpRequest geminiRequest(String base, String model, String key, String system, List<AiMessage> messages) {
        return geminiRequest(base, model, key, system, messages, false);
    }

    private static HttpRequest geminiRequest(String base, String model, String key, String system,
                                             List<AiMessage> messages, boolean stream) {
        String m = model.startsWith("models/") ? model.substring("models/".length()) : model;
        String encodedModel = URLEncoder.encode(m, StandardCharsets.UTF_8).replace("+", "%20");
        String url = endpoint(base, "/models/" + encodedModel + (stream ? ":streamGenerateContent?alt=sse" : ":generateContent"));
        StringBuilder body = new StringBuilder("{");
        boolean needsComma = false;
        if (system != null && !system.isBlank()) {
            body.append("\"system_instruction\":{\"parts\":[{\"text\":").append(Json.quote(system)).append("}]}");
            needsComma = true;
        }
        if (needsComma) body.append(',');
        body.append("\"contents\":").append(geminiContents(messages));
        if (supportsCustomTemperature(AiProvider.GEMINI, m)) {
            body.append(",\"generationConfig\":{\"temperature\":0.2}");
        }
        body.append('}');
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (key != null && !key.isBlank()) b.header("x-goog-api-key", key);
        return b.build();
    }

    private static String openAiMessages(String system, List<AiMessage> messages) {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        if (system != null && !system.isBlank()) {
            b.append("{\"role\":\"system\",\"content\":").append(Json.quote(system)).append("}");
            first = false;
        }
        for (AiMessage m : messages) {
            if (m == null || m.content() == null || m.content().isBlank()) continue;
            if (!first) b.append(',');
            first = false;
            b.append("{\"role\":").append(Json.quote(m.role()))
                    .append(",\"content\":").append(Json.quote(m.content())).append("}");
        }
        if (first) b.append("{\"role\":\"user\",\"content\":\"(continue)\"}");
        return b.append("]").toString();
    }

    static List<AiMessage> mergeConsecutiveRoles(List<AiMessage> messages, String assistantRole, String userRole) {
        List<AiMessage> merged = new ArrayList<>();
        for (AiMessage m : messages) {
            if (m == null || m.content() == null || m.content().isBlank()) continue;
            String role = "assistant".equals(m.role()) ? assistantRole : userRole;
            if (merged.isEmpty() && role.equals(assistantRole)) {
                merged.add(new AiMessage(userRole, "(continue)"));
            }
            if (!merged.isEmpty() && merged.get(merged.size() - 1).role().equals(role)) {
                AiMessage prev = merged.get(merged.size() - 1);
                merged.set(merged.size() - 1, new AiMessage(role, prev.content() + "\n\n" + m.content()));
            } else {
                merged.add(new AiMessage(role, m.content()));
            }
        }
        if (merged.isEmpty()) merged.add(new AiMessage(userRole, "(continue)"));
        return merged;
    }

    static String anthropicMessages(List<AiMessage> messages) {
        List<AiMessage> merged = mergeConsecutiveRoles(messages, "assistant", "user");
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < merged.size(); i++) {
            AiMessage m = merged.get(i);
            if (i > 0) b.append(',');
            b.append("{\"role\":").append(Json.quote(m.role()))
                    .append(",\"content\":").append(Json.quote(m.content())).append("}");
        }
        return b.append("]").toString();
    }

    static String geminiContents(List<AiMessage> messages) {
        List<AiMessage> merged = mergeConsecutiveRoles(messages, "model", "user");
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < merged.size(); i++) {
            AiMessage m = merged.get(i);
            if (i > 0) b.append(',');
            b.append("{\"role\":").append(Json.quote(m.role()))
                    .append(",\"parts\":[{\"text\":").append(Json.quote(m.content())).append("}]}");
        }
        return b.append("]").toString();
    }

    static String endpoint(String base, String suffix) {
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
            if (Boolean.TRUE.equals(map.get("thought"))) return null;
            if ("thinking".equals(map.get("type"))) return null;
            Object t = map.get("text");
            return t == null ? null : text(t);
        }
        if (value instanceof List<?> list) return joinParts(list);
        return String.valueOf(value);
    }

    static String joinParts(Object value) {
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
