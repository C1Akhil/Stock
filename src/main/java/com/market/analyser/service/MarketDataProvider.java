package com.market.analyser.service;

import com.market.analyser.model.Candle;

import java.util.List;

/**
 * Strategy interface — each data provider implements this.
 * Returns up to {@code limit} candles, ordered oldest → newest.
 */
public interface MarketDataProvider {

    /**
     * Fetch OHLCV candles for the given symbol and timeframe.
     *
     * @param symbol    e.g. "RELIANCE.NS"
     * @param timeframe "1H" or "3H"
     * @param limit     number of candles needed (typically 60–100)
     * @return list of candles, oldest first
     */
    List<Candle> fetchCandles(String symbol, String timeframe, int limit);

    /** Human-readable name shown in API responses. */
    String providerName();
}
