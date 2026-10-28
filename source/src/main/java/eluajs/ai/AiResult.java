package eluajs.ai;

public record AiResult(String text, long promptTokens, long completionTokens, long totalTokens) {
    public long total() { return totalTokens > 0 ? totalTokens : promptTokens + completionTokens; }
}
