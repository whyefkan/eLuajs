package eluajs.ai;

import static eluajs.util.I18n.t;

/** Turns provider/network failures into short, actionable messages. */
public final class AiErrors {
    private AiErrors() {}

    public static String friendly(Throwable error) {
        if (error == null) return t("Unknown AI error.");
        String raw = error.getMessage() == null ? error.toString() : error.getMessage();
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("missing api key") || lower.contains("no api key") || lower.contains("401") || lower.contains("unauthorized")) {
            return t("AI authentication failed. Check the API key in Settings.");
        }
        if (lower.contains("402") || lower.contains("quota") || lower.contains("insufficient") || lower.contains("billing")) {
            return t("AI quota or billing limit reached. Check your provider account.");
        }
        if (lower.contains("429") || lower.contains("rate limit") || lower.contains("too many requests")) {
            return t("AI rate limit reached. Wait a moment and retry.");
        }
        if (lower.contains("context length") || lower.contains("maximum context") || lower.contains("too many tokens")) {
            return t("The request is too large for this model. Use a smaller context or a larger model.");
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return t("The AI request timed out. Check your connection and retry.");
        }
        if (lower.contains("unknown host") || lower.contains("connect") || lower.contains("network")) {
            return t("Could not reach the AI provider. Check your internet connection.");
        }
        if (lower.contains("empty response")) return t("The provider returned an empty response. Retry or choose another model.");
        return raw.length() > 300 ? raw.substring(0, 297) + "…" : raw;
    }
}
