package eluajs.ai;

import java.util.List;
import java.util.Locale;

public enum AiProvider {
    OPENAI("OpenAI", Kind.OPENAI, "https://api.openai.com/v1", "gpt-5-mini",
            "gpt-5", "gpt-5-mini", "gpt-5-nano", "gpt-4.1", "gpt-4.1-mini", "o4-mini"),
    ANTHROPIC("Claude (Anthropic)", Kind.ANTHROPIC, "https://api.anthropic.com/v1", "claude-sonnet-4-5",
            "claude-sonnet-4-5", "claude-opus-4-1", "claude-haiku-4-5", "claude-3-5-sonnet-latest"),
    GEMINI("Google Gemini", Kind.GEMINI, "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash",
            "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash"),
    GROQ("Groq", Kind.OPENAI, "https://api.groq.com/openai/v1", "openai/gpt-oss-120b",
            "openai/gpt-oss-120b", "openai/gpt-oss-20b", "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant", "qwen/qwen3-32b", "deepseek-r1-distill-llama-70b",
            "moonshotai/kimi-k2-instruct"),
    DEEPSEEK("DeepSeek", Kind.OPENAI, "https://api.deepseek.com/v1", "deepseek-v4-flash",
            "deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner"),
    OPENROUTER("OpenRouter", Kind.OPENAI, "https://openrouter.ai/api/v1", "openai/gpt-5-mini",
            "openai/gpt-5", "openai/gpt-5-mini", "anthropic/claude-sonnet-4.5",
            "google/gemini-2.5-pro", "deepseek/deepseek-v4-pro", "deepseek/deepseek-v4-flash",
            "x-ai/grok-4", "meta-llama/llama-3.3-70b-instruct"),
    RIVER("River AI", Kind.OPENAI, "", "deepseek-v4-pro",
            "deepseek-v4-pro", "deepseek-v4-flash", "astra-sonnet-5.5"),
    CUSTOM("Custom (OpenAI compatible)", Kind.OPENAI, "", "gpt-4o-mini", "gpt-4o-mini");

    public enum Kind { OPENAI, ANTHROPIC, GEMINI }

    private final String displayName;
    private final Kind kind;
    private final String defaultBaseUrl;
    private final String defaultModel;
    private final List<String> models;

    AiProvider(String displayName, Kind kind, String defaultBaseUrl, String defaultModel, String... models) {
        this.displayName = displayName;
        this.kind = kind;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.models = List.of(models);
    }

    public String displayName() { return displayName; }
    public Kind kind() { return kind; }
    public String defaultBaseUrl() { return defaultBaseUrl; }
    public String defaultModel() { return defaultModel; }
    public List<String> models() { return models; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static AiProvider fromId(String id) {
        if (id == null) return OPENAI;
        for (AiProvider p : values()) if (p.name().equalsIgnoreCase(id) || p.id().equalsIgnoreCase(id)) return p;
        return OPENAI;
    }

    @Override public String toString() { return displayName; }
}
