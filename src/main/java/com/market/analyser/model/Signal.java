package com.market.analyser.model;

/**
 * Direction of a single technical indicator.
 */
public enum Signal {
    BULLISH,
    BEARISH,
    NEUTRAL;

    /** Score contribution: +1, -1, 0 */
    public int score() {
        return switch (this) {
            case BULLISH -> 1;
            case BEARISH -> -1;
            case NEUTRAL -> 0;
        };
    }
}
