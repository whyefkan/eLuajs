package eluajs.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Rough public-list-price estimates. The UI labels these values as approximate. */
public final class AiPricing {
    private record Rate(String prefix, double inputPerMillion, double outputPerMillion) { }
    private static final List<Rate> RATES = new ArrayList<>();

    static {
        rate("gpt-5-mini", 0.25, 2.00);
        rate("gpt-5-nano", 0.05, 0.40);
        rate("gpt-5", 1.25, 10.00);
        rate("gpt-4.1-mini", 0.40, 1.60);
        rate("gpt-4.1", 2.00, 8.00);
        rate("o4-mini", 1.10, 4.40);
        rate("claude-sonnet-4", 3.00, 15.00);
        rate("claude-opus-4", 15.00, 75.00);
        rate("claude-haiku-4", 1.00, 5.00);
        rate("claude-3-5-sonnet", 3.00, 15.00);
        rate("gemini-2.5-pro", 1.25, 10.00);
        rate("gemini-2.5-flash", 0.30, 2.50);
        rate("gemini-2.0-flash", 0.10, 0.40);
        rate("gemini-1.5-pro", 1.25, 5.00);
        rate("gemini-1.5-flash", 0.075, 0.30);
        rate("openai/gpt-oss-120b", 0.15, 0.75);
        rate("openai/gpt-oss-20b", 0.10, 0.50);
        rate("llama-3.3-70b", 0.59, 0.79);
        rate("llama-3.1-8b", 0.05, 0.08);
        rate("deepseek-v4-pro", 0.55, 2.19);
        rate("deepseek-v4-flash", 0.27, 1.10);
        rate("deepseek-chat", 0.27, 1.10);
        rate("deepseek-reasoner", 0.55, 2.19);
        rate("openai/gpt-5-mini", 0.25, 2.00);
        rate("openai/gpt-5", 1.25, 10.00);
        rate("anthropic/claude-sonnet-4.5", 3.00, 15.00);
        rate("google/gemini-2.5-pro", 1.25, 10.00);
        rate("deepseek/deepseek-v4-pro", 0.55, 2.19);
        rate("deepseek/deepseek-v4-flash", 0.27, 1.10);
        RATES.sort(Comparator.comparingInt((Rate r) -> r.prefix().length()).reversed());
    }

    private AiPricing() {}

    private static void rate(String prefix, double in, double out) {
        RATES.add(new Rate(prefix.toLowerCase(Locale.ROOT), in, out));
    }

    public static double estimate(AiProvider provider, String model, long promptTokens, long completionTokens) {
        if (model == null) return 0;
        String m = model.toLowerCase(Locale.ROOT);
        for (Rate rate : RATES) {
            if (m.startsWith(rate.prefix()) || m.contains(rate.prefix())) {
                return promptTokens / 1_000_000.0 * rate.inputPerMillion()
                        + completionTokens / 1_000_000.0 * rate.outputPerMillion();
            }
        }
        return 0;
    }

    public static String formatCost(double cost) {
        if (cost <= 0) return "—";
        if (cost < 0.0001) return "<$0.0001";
        return String.format(Locale.US, "$%.4f", cost);
    }

    public static String formatTokens(long tokens) {
        if (tokens >= 1_000_000) return String.format(Locale.US, "%.1fM", tokens / 1_000_000.0);
        if (tokens >= 1000) return String.format(Locale.US, "%.1fk", tokens / 1000.0);
        return String.valueOf(tokens);
    }
}
