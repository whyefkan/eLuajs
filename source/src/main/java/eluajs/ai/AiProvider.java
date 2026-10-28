package eluajs.ai;

import java.util.List;
import java.util.Locale;

public enum AiProvider {
    OPENAI("OpenAI", Kind.OPENAI, "https://api.openai.com/v1", "gpt-6.1-sol",
            "gpt-6-astra", "gpt-6.1-sol", "gpt-6.1-terra", "gpt-6-luna",
            "gpt-5.6-cyber", "gpt-5", "gpt-5-mini", "gpt-5-nano", "gpt-4.1", "gpt-4.1-mini", "o4-mini"),
    ANTHROPIC("Claude (Anthropic)", Kind.ANTHROPIC, "https://api.anthropic.com/v1", "claude-opus-5-5",
            "claude-fable-5-1", "claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5",
            "claude-fable-5", "claude-opus-5", "claude-opus-4-8", "claude-opus-4-7",
            "claude-opus-4-6", "claude-opus-4-5", "claude-sonnet-5", "claude-sonnet-4-6",
            "claude-haiku-4-5", "claude-sonnet-4-5", "claude-opus-4-1", "claude-3-5-sonnet-latest"),
    GEMINI("Google Gemini", Kind.GEMINI, "https://generativelanguage.googleapis.com/v1beta", "gemini-3.8-flash",
            "gemini-4-argon", "gemini-3.8-flash", "gemini-3.8-live",
            "gemini-3.8-live-extended-thinking", "gemini-3.6-flash", "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite", "gemini-3.1-pro-preview", "gemini-3-pro-preview",
            "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash"),
    GROQ("Groq", Kind.OPENAI, "https://api.groq.com/openai/v1", "openai/gpt-oss-120b",
            "openai/gpt-oss-120b", "openai/gpt-oss-20b", "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant", "qwen/qwen3-32b", "deepseek-r1-distill-llama-70b",
            "moonshotai/kimi-k2-instruct"),
    DEEPSEEK("DeepSeek", Kind.OPENAI, "https://api.deepseek.com/v1", "deepseek-v4-flash",
            "deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner"),
    OPENROUTER("OpenRouter", Kind.OPENAI, "https://openrouter.ai/api/v1", "openai/gpt-6.1-sol",
            "openai/gpt-6-astra", "openai/gpt-6.1-sol", "openai/gpt-6.1-terra", "openai/gpt-6-luna",
            "anthropic/claude-fable-5.1", "anthropic/claude-opus-5.5", "anthropic/claude-sonnet-5.5",
            "anthropic/claude-haiku-5.5", "google/gemini-4-argon", "google/gemini-3.8-flash",
            "openai/gpt-5", "openai/gpt-5-mini", "anthropic/claude-sonnet-4.5",
            "google/gemini-2.5-pro", "deepseek/deepseek-v4-pro", "deepseek/deepseek-v4-flash",
            "x-ai/grok-4", "meta-llama/llama-3.3-70b-instruct"),
    RIVER("River AI", Kind.OPENAI, "", "deepseek-v4-pro",
            "deepseek-v4-pro", "deepseek-v4-flash", "astra-sonnet-5.5"),
    OLLAMA("Ollama (local)", Kind.OPENAI, "http://localhost:11434/v1", "llama3.2",
            "llama3.2", "qwen2.5-coder:7b", "deepseek-coder-v2", "mistral", "codellama"),
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
    public boolean requiresKey() { return this != OLLAMA; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static AiProvider fromId(String id) {
        if (id == null) return OPENAI;
        for (AiProvider p : values()) if (p.name().equalsIgnoreCase(id) || p.id().equalsIgnoreCase(id)) return p;
        return OPENAI;
    }

    @Override public String toString() { return displayName; }
}
