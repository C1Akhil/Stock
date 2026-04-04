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

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final MarketDataProviderFactory providerFactory;
    private final IndicatorEngine indicatorEngine;

    private static final int CANDLE_LIMIT = 100;

    @Cacheable("analysisResults")
    public List<AnalysisResultDto> analyseAll() {
        log.info("Running full Nifty50 analysis...");
        MarketDataProvider provider = providerFactory.getProvider();

        List<AnalysisResultDto> results = new ArrayList<>();
        int failCount = 0;

        for (Map.Entry<String, String> entry : Nifty50Registry.getStocks().entrySet()) {
            String symbol = entry.getKey();
            String sector = entry.getValue();
            try {
                AnalysisResultDto dto = analyseSymbol(symbol, sector, provider);
                if (dto.getLtp() == 0) failCount++;
                results.add(dto);
            } catch (Exception e) {
                log.error("Analysis failed for {}: {}", symbol, e.getMessage());
                failCount++;
            }
        }

        // If >80% failed, Yahoo Finance is blocked on this server — use simulated data
        if (failCount > Nifty50Registry.getStocks().size() * 0.8) {
            log.warn("Live data failed for {}/{} stocks — switching to simulated data",
                    failCount, Nifty50Registry.getStocks().size());
            return generateSimulatedData();
        }

        results.sort(Comparator.comparingInt(AnalysisResultDto::getScore).reversed());
        log.info("Analysis complete — {} stocks processed ({} failed)", results.size(), failCount);
        return results;
    }

    public AnalysisResultDto analyseSingle(String symbol) {
        String sector = Nifty50Registry.getSector(symbol);
        return analyseSymbol(symbol, sector, providerFactory.getProvider());
    }

    @Cacheable("analysisResults")
    public MarketSummaryDto getSummary() {
        List<AnalysisResultDto> all = analyseAll();

        long sb = all.stream().filter(r -> r.getRating() == Rating.STRONG_BUY).count();
        long b  = all.stream().filter(r -> r.getRating() == Rating.BUY).count();
        long n  = all.stream().filter(r -> r.getRating() == Rating.NEUTRAL).count();
        long s  = all.stream().filter(r -> r.getRating() == Rating.SELL).count();
        long ss = all.stream().filter(r -> r.getRating() == Rating.STRONG_SELL).count();

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

    @CacheEvict(value = "analysisResults", allEntries = true)
    public void evictCache() {
        log.info("Analysis cache evicted");
    }

    // ── Live symbol analysis ─────────────────────────────────────────────────

    private AnalysisResultDto analyseSymbol(String symbol, String sector, MarketDataProvider provider) {
        List<Candle> candles1h = provider.fetchCandles(symbol, "1H", CANDLE_LIMIT);
        List<Candle> candles3h = provider.fetchCandles(symbol, "3H", CANDLE_LIMIT);

        List<Double> closes1h = closes(candles1h);
        List<Double> closes3h = closes(candles3h);

        IndicatorEngine.MacdResult macd1h = indicatorEngine.calculateMACD(closes1h);
        IndicatorEngine.MacdResult macd3h = indicatorEngine.calculateMACD(closes3h);
        IndicatorEngine.RsiResult  rsi1h  = indicatorEngine.calculateRSI(closes1h);
        IndicatorEngine.RsiResult  rsi3h  = indicatorEngine.calculateRSI(closes3h);
        IndicatorEngine.MaResult   ma1h   = indicatorEngine.calculateMA(closes1h);
        IndicatorEngine.MaResult   ma3h   = indicatorEngine.calculateMA(closes3h);

        int score = macd1h.signal().score() + macd3h.signal().score()
                  + rsi1h.signal().score()  + rsi3h.signal().score()
                  + ma1h.signal().score()   + ma3h.signal().score();

        Rating rating = Rating.fromScore(score);

        double ltp       = closes1h.isEmpty() ? 0 : closes1h.get(closes1h.size() - 1);
        double prev      = closes1h.size() > 1 ? closes1h.get(closes1h.size() - 2) : ltp;
        double change    = ltp - prev;
        double changePct = prev == 0 ? 0 : (change / prev) * 100;

        return AnalysisResultDto.builder()
                .symbol(Nifty50Registry.displayName(symbol))
                .sector(sector)
                .ltp(round2(ltp))
                .change(round2(change))
                .changePct(round2(changePct))
                .macd1h(macd1h.signal())
                .macd1hValue(round4(macd1h.macdLine()))
                .macd1hSignal(round4(macd1h.signalLine()))
                .macd1hHistogram(round4(macd1h.histogram()))
                .rsi1h(rsi1h.signal())
                .rsi1hValue(round2(rsi1h.value()))
                .ma1h(ma1h.signal())
                .ma1hShort(round2(ma1h.shortMa()))
                .ma1hLong(round2(ma1h.longMa()))
                .macd3h(macd3h.signal())
                .macd3hValue(round4(macd3h.macdLine()))
                .macd3hSignal(round4(macd3h.signalLine()))
                .macd3hHistogram(round4(macd3h.histogram()))
                .rsi3h(rsi3h.signal())
                .rsi3hValue(round2(rsi3h.value()))
                .ma3h(ma3h.signal())
                .ma3hShort(round2(ma3h.shortMa()))
                .ma3hLong(round2(ma3h.longMa()))
                .score(score)
                .rating(rating)
                .action(rating.getAction())
                .analyzedAt(LocalDateTime.now())
                .dataSource(provider.providerName())
                .build();
    }

    // ── Simulated fallback ────────────────────────────────────────────────────

    private static final double[][] BASE_PRICES = {
        {2950},{3820},{1680},{1540},{1230},{2380},{1780},{1120},{780},{3450},
        {6800},{1680},{445},{2650},{12200},{480},{1580},{355},{310},{3350},
        {10800},{2320},{1680},{6200},{1480},{5800},{1640},{920},{160},{620},
        {430},{265},{290},{165},{2450},{1320},{1580},{4800},{4200},{9200},
        {1120},{5200},{7200},{680},{1480},{1020},{2800},{980},{3200},{295}
    };

    private List<AnalysisResultDto> generateSimulatedData() {
        log.info("Generating simulated market data for all 50 stocks");
        List<AnalysisResultDto> results = new ArrayList<>();
        // Seed changes every 5 minutes so data refreshes periodically
        long seed = System.currentTimeMillis() / 300_000L;
        int idx = 0;

        for (Map.Entry<String, String> entry : Nifty50Registry.getStocks().entrySet()) {
            String sym    = entry.getKey();
            String sector = entry.getValue();
            double base   = idx < BASE_PRICES.length ? BASE_PRICES[idx][0] : 1000 + idx * 80;

            double r1 = pr(seed, idx, 1);
            double r2 = pr(seed, idx, 2);
            double r3 = pr(seed, idx, 3);
            double r4 = pr(seed, idx, 4);
            double r5 = pr(seed, idx, 5);
            double r6 = pr(seed, idx, 6);

            double ltp       = round2(base * (0.97 + r1 * 0.06));
            double prev      = round2(base * (0.97 + pr(seed - 1, idx, 1) * 0.06));
            double change    = round2(ltp - prev);
            double changePct = round2(prev == 0 ? 0 : change / prev * 100);

            Signal macd1h = sig(r1); Signal macd3h = sig(r2);
            Signal rsi1h  = sig(r3); Signal rsi3h  = sig(r4);
            Signal ma1h   = sig(r5); Signal ma3h   = sig(r6);

            double rsi1hVal = round2(20 + r3 * 70);
            double rsi3hVal = round2(20 + r4 * 70);
            double hist1h   = round4((r1 - 0.5) * 20);
            double hist3h   = round4((r2 - 0.5) * 25);

            int score = macd1h.score() + macd3h.score() + rsi1h.score()
                      + rsi3h.score()  + ma1h.score()   + ma3h.score();
            Rating rating = Rating.fromScore(score);

            results.add(AnalysisResultDto.builder()
                    .symbol(Nifty50Registry.displayName(sym))
                    .sector(sector)
                    .ltp(ltp).change(change).changePct(changePct)
                    .macd1h(macd1h)
                    .macd1hValue(round4((r1 - 0.5) * 20))
                    .macd1hSignal(round4((r1 - 0.48) * 18))
                    .macd1hHistogram(hist1h)
                    .rsi1h(rsi1h).rsi1hValue(rsi1hVal)
                    .ma1h(ma1h)
                    .ma1hShort(round2(ltp * (0.98 + r5 * 0.04)))
                    .ma1hLong(round2(ltp * 0.96))
                    .macd3h(macd3h)
                    .macd3hValue(round4((r2 - 0.5) * 25))
                    .macd3hSignal(round4((r2 - 0.48) * 22))
                    .macd3hHistogram(hist3h)
                    .rsi3h(rsi3h).rsi3hValue(rsi3hVal)
                    .ma3h(ma3h)
                    .ma3hShort(round2(ltp * (0.97 + r6 * 0.05)))
                    .ma3hLong(round2(ltp * 0.94))
                    .score(score).rating(rating).action(rating.getAction())
                    .analyzedAt(LocalDateTime.now())
                    .dataSource("Simulated (Yahoo Finance unavailable on cloud)")
                    .build());
            idx++;
        }

        results.sort(Comparator.comparingInt(AnalysisResultDto::getScore).reversed());
        return results;
    }

    /** Deterministic pseudo-random in [0,1) */
    private double pr(long seed, int idx, int offset) {
        long v = ((seed * 2654435761L) ^ (idx * 40503L) ^ (offset * 12345L)) & 0x7FFFFFFFL;
        return (v % 10000) / 10000.0;
    }

    private Signal sig(double r) {
        return r > 0.58 ? Signal.BULLISH : r < 0.42 ? Signal.BEARISH : Signal.NEUTRAL;
    }

    private List<Double> closes(List<Candle> candles) {
        return candles.stream().map(Candle::getClose).collect(Collectors.toList());
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
