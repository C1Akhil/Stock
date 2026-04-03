package com.market.analyser;

import com.market.analyser.config.MarketProperties;
import com.market.analyser.model.Signal;
import com.market.analyser.service.IndicatorEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class IndicatorEngineTest {

    private IndicatorEngine engine;

    @BeforeEach
    void setUp() {
        MarketProperties props = new MarketProperties();
        MarketProperties.Indicators ind = new MarketProperties.Indicators();
        ind.setRsiPeriod(14);
        ind.setMacdFast(12);
        ind.setMacdSlow(26);
        ind.setMacdSignal(9);
        ind.setMaShort(20);
        ind.setMaLong(50);
        props.setIndicators(ind);
        engine = new IndicatorEngine(props);
    }

    // ── SMA ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SMA of [1,2,3,4,5] with period 5 should be 3.0")
    void sma_basic() {
        List<Double> prices = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        assertThat(engine.sma(prices, 5)).isEqualTo(3.0);
    }

    @Test
    @DisplayName("SMA returns NaN when not enough data")
    void sma_insufficientData() {
        assertThat(engine.sma(List.of(1.0, 2.0), 5)).isNaN();
    }

    // ── RSI ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RSI on monotonically rising series should be near 100 (BULLISH)")
    void rsi_risingPrices() {
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 30; i++) prices.add(100.0 + i);
        IndicatorEngine.RsiResult result = engine.calculateRSI(prices);
        assertThat(result.signal()).isEqualTo(Signal.BULLISH);
        assertThat(result.value()).isGreaterThan(60.0);
    }

    @Test
    @DisplayName("RSI on monotonically falling series should be near 0 (BEARISH)")
    void rsi_fallingPrices() {
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 30; i++) prices.add(200.0 - i);
        IndicatorEngine.RsiResult result = engine.calculateRSI(prices);
        assertThat(result.signal()).isEqualTo(Signal.BEARISH);
        assertThat(result.value()).isLessThan(40.0);
    }

    @Test
    @DisplayName("RSI returns NEUTRAL when data is insufficient")
    void rsi_insufficientData() {
        IndicatorEngine.RsiResult result = engine.calculateRSI(List.of(100.0, 101.0));
        assertThat(result.signal()).isEqualTo(Signal.NEUTRAL);
    }

    // ── MACD ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MACD on strongly rising prices should show positive histogram (BULLISH)")
    void macd_risingPrices() {
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 60; i++) prices.add(100.0 + i * 2);
        IndicatorEngine.MacdResult result = engine.calculateMACD(prices);
        assertThat(result.signal()).isEqualTo(Signal.BULLISH);
        assertThat(result.histogram()).isGreaterThan(0);
    }

    @Test
    @DisplayName("MACD returns NEUTRAL when data is insufficient")
    void macd_insufficientData() {
        List<Double> prices = List.of(100.0, 101.0, 102.0);
        IndicatorEngine.MacdResult result = engine.calculateMACD(prices);
        assertThat(result.signal()).isEqualTo(Signal.NEUTRAL);
    }

    // ── MA ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Price above 20-period SMA should be BULLISH")
    void ma_priceAboveSma() {
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 20; i++) prices.add(90.0);
        prices.add(120.0); // last price well above SMA of 90
        IndicatorEngine.MaResult result = engine.calculateMA(prices);
        assertThat(result.signal()).isEqualTo(Signal.BULLISH);
    }

    @Test
    @DisplayName("Price below 20-period SMA should be BEARISH")
    void ma_priceBelowSma() {
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 20; i++) prices.add(110.0);
        prices.add(80.0); // last price well below SMA of 110
        IndicatorEngine.MaResult result = engine.calculateMA(prices);
        assertThat(result.signal()).isEqualTo(Signal.BEARISH);
    }

    // ── Score / Rating ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Signal scores sum correctly")
    void signalScores() {
        assertThat(Signal.BULLISH.score()).isEqualTo(1);
        assertThat(Signal.BEARISH.score()).isEqualTo(-1);
        assertThat(Signal.NEUTRAL.score()).isEqualTo(0);
    }
}
