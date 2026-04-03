package com.market.analyser.service;

import com.market.analyser.dto.AnalysisResultDto;
import com.market.analyser.dto.MarketSummaryDto;
import com.market.analyser.model.Candle;
import com.market.analyser.model.Nifty50Registry;
import com.market.analyser.model.Rating;
import com.market.analyser.model.Signal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates live market data fetching, indicator computation,
 * and rating generation for all Nifty 50 stocks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final MarketDataProviderFactory providerFactory;
    private final IndicatorEngine indicatorEngine;

    private static final int CANDLE_LIMIT = 100; // enough for MACD(26)+Signal(9)

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Analyse all 50 stocks. Results cached for 5 minutes.
     */
    @Cacheable("analysisResults")
    public List<AnalysisResultDto> analyseAll() {
        log.info("Running full Nifty50 analysis...");
        MarketDataProvider provider = providerFactory.getProvider();

        List<AnalysisResultDto> results = new ArrayList<>();
        for (Map.Entry<String, String> entry : Nifty50Registry.getStocks().entrySet()) {
            String symbol = entry.getKey();
            String sector = entry.getValue();
            try {
                AnalysisResultDto dto = analyseSymbol(symbol, sector, provider);
                results.add(dto);
            } catch (Exception e) {
                log.error("Analysis failed for {}: {}", symbol, e.getMessage());
            }
        }

        results.sort(Comparator.comparingInt(AnalysisResultDto::getScore).reversed());
        log.info("Analysis complete — {} stocks processed", results.size());
        return results;
    }

    /**
     * Analyse a single symbol by name (e.g. "RELIANCE.NS").
     */
    public AnalysisResultDto analyseSingle(String symbol) {
        String sector = Nifty50Registry.getSector(symbol);
        return analyseSymbol(symbol, sector, providerFactory.getProvider());
    }

    /**
     * Market-wide summary with counts, top picks and sector sentiment.
     */
    @Cacheable("analysisResults")
    public MarketSummaryDto getSummary() {
        List<AnalysisResultDto> all = analyseAll();

        long sb = all.stream().filter(r -> r.getRating() == Rating.STRONG_BUY).count();
        long b  = all.stream().filter(r -> r.getRating() == Rating.BUY).count();
        long n  = all.stream().filter(r -> r.getRating() == Rating.NEUTRAL).count();
        long s  = all.stream().filter(r -> r.getRating() == Rating.SELL).count();
        long ss = all.stream().filter(r -> r.getRating() == Rating.STRONG_SELL).count();

        // Sector average scores
        Map<String, Integer> sectorSentiment = all.stream()
                .collect(Collectors.groupingBy(
                        AnalysisResultDto::getSector,
                        Collectors.collectingAndThen(
                                Collectors.averagingInt(AnalysisResultDto::getScore),
                                avg -> (int) Math.round(avg))));

        return MarketSummaryDto.builder()
                .totalStocks(all.size())
                .strongBuyCount((int) sb)
                .buyCount((int) b)
                .neutralCount((int) n)
                .sellCount((int) s)
                .strongSellCount((int) ss)
                .bullishPct(all.isEmpty() ? 0 : (sb + b) * 100.0 / all.size())
                .bearishPct(all.isEmpty() ? 0 : (s + ss) * 100.0 / all.size())
                .topBuys(all.stream().limit(5).collect(Collectors.toList()))
                .topSells(all.stream()
                        .sorted(Comparator.comparingInt(AnalysisResultDto::getScore))
                        .limit(5).collect(Collectors.toList()))
                .sectorSentiment(sectorSentiment)
                .generatedAt(LocalDateTime.now())
                .provider(providerFactory.getProvider().providerName())
                .build();
    }

    /** Evict cache so next call fetches fresh data. */
    @CacheEvict(value = "analysisResults", allEntries = true)
    public void evictCache() {
        log.info("Analysis cache evicted");
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private AnalysisResultDto analyseSymbol(String symbol, String sector, MarketDataProvider provider) {
        List<Candle> candles1h = provider.fetchCandles(symbol, "1H", CANDLE_LIMIT);
        List<Candle> candles3h = provider.fetchCandles(symbol, "3H", CANDLE_LIMIT);

        List<Double> closes1h = closes(candles1h);
        List<Double> closes3h = closes(candles3h);

        // Compute indicators
        IndicatorEngine.MacdResult macd1h = indicatorEngine.calculateMACD(closes1h);
        IndicatorEngine.MacdResult macd3h = indicatorEngine.calculateMACD(closes3h);
        IndicatorEngine.RsiResult  rsi1h  = indicatorEngine.calculateRSI(closes1h);
        IndicatorEngine.RsiResult  rsi3h  = indicatorEngine.calculateRSI(closes3h);
        IndicatorEngine.MaResult   ma1h   = indicatorEngine.calculateMA(closes1h);
        IndicatorEngine.MaResult   ma3h   = indicatorEngine.calculateMA(closes3h);

        // Composite score
        int score = macd1h.signal().score() + macd3h.signal().score()
                  + rsi1h.signal().score()  + rsi3h.signal().score()
                  + ma1h.signal().score()   + ma3h.signal().score();

        Rating rating = Rating.fromScore(score);

        // Last price info
        double ltp    = closes1h.isEmpty() ? 0 : closes1h.get(closes1h.size() - 1);
        double prev   = closes1h.size() > 1 ? closes1h.get(closes1h.size() - 2) : ltp;
        double change = ltp - prev;
        double changePct = prev == 0 ? 0 : (change / prev) * 100;

        return AnalysisResultDto.builder()
                .symbol(Nifty50Registry.displayName(symbol))
                .sector(sector)
                .ltp(round2(ltp))
                .change(round2(change))
                .changePct(round2(changePct))
                // 1H
                .macd1h(macd1h.signal())
                .macd1hValue(round4(macd1h.macdLine()))
                .macd1hSignal(round4(macd1h.signalLine()))
                .macd1hHistogram(round4(macd1h.histogram()))
                .rsi1h(rsi1h.signal())
                .rsi1hValue(round2(rsi1h.value()))
                .ma1h(ma1h.signal())
                .ma1hShort(round2(ma1h.shortMa()))
                .ma1hLong(round2(ma1h.longMa()))
                // 3H
                .macd3h(macd3h.signal())
                .macd3hValue(round4(macd3h.macdLine()))
                .macd3hSignal(round4(macd3h.signalLine()))
                .macd3hHistogram(round4(macd3h.histogram()))
                .rsi3h(rsi3h.signal())
                .rsi3hValue(round2(rsi3h.value()))
                .ma3h(ma3h.signal())
                .ma3hShort(round2(ma3h.shortMa()))
                .ma3hLong(round2(ma3h.longMa()))
                // composite
                .score(score)
                .rating(rating)
                .action(rating.getAction())
                .analyzedAt(LocalDateTime.now())
                .dataSource(provider.providerName())
                .build();
    }

    private List<Double> closes(List<Candle> candles) {
        return candles.stream().map(Candle::getClose).collect(Collectors.toList());
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
