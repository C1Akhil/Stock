package com.market.analyser.model;

/**
 * Final options recommendation derived from combined indicator score.
 *
 * Score range: -6 (all bearish) to +6 (all bullish).
 */
public enum Rating {

    STRONG_BUY("Buy CE aggressively", "All 6 indicators bullish across both timeframes"),
    BUY("Buy CE", "Majority indicators bullish"),
    NEUTRAL("Wait / Straddle", "Mixed signals — no clear directional edge"),
    SELL("Buy PE", "Majority indicators bearish"),
    STRONG_SELL("Buy PE aggressively", "All 6 indicators bearish across both timeframes");

    private final String action;
    private final String description;

    Rating(String action, String description) {
        this.action = action;
        this.description = description;
    }

    public String getAction() { return action; }
    public String getDescription() { return description; }

    /**
     * Map combined score (-6 to +6) → Rating.
     */
    public static Rating fromScore(int score) {
        if (score >= 4)  return STRONG_BUY;
        if (score >= 2)  return BUY;
        if (score >= -1) return NEUTRAL;
        if (score >= -3) return SELL;
        return STRONG_SELL;
    }
}
