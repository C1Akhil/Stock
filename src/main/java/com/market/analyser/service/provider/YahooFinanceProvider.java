package com.market.analyser.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.market.analyser.config.MarketProperties;
import com.market.analyser.model.Candle;
import com.market.analyser.service.MarketDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fetches OHLCV data from Yahoo Finance (no API key required).
 *
 * Endpoint pattern:
 *   GET https://query1.finance.yahoo.com/v8/finance/chart/{symbol}
 *        ?interval=1h&range=7d          (1H candles)
 *        ?interval=1h&range=21d  (proxy for 3H — we aggregate)
 *
 * Note: Yahoo does not expose a native "3h" interval.
 *       We fetch 1h candles and aggregate every 3 bars into a 3H candle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinanceProvider implements MarketDataProvider {

    private final WebClient webClient;
    private final MarketProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public String providerName() {
        return "Yahoo Finance";
    }

    @Override
    public List<Candle> fetchCandles(String symbol, String timeframe, int limit) {
        try {
            String range    = "1H".equals(timeframe) ? "7d" : "21d";
            String interval = "1h"; // always fetch 1h; aggregate for 3H

            String url = props.getYahooFinance().getBaseUrl()
                    + "/" + symbol
                    + "?interval=" + interval
                    + "&range=" + range
                    + "&includePrePost=false";

            log.debug("Yahoo Finance request: {}", url);

            String json = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<Candle> candles = parseYahooResponse(json, symbol, "1H");
            if ("3H".equals(timeframe)) {
                candles = aggregate3H(candles, symbol);
            }

            // Keep only the most recent `limit` candles
            if (candles.size() > limit) {
                candles = candles.subList(candles.size() - limit, candles.size());
            }
            return candles;

        } catch (Exception e) {
            log.error("Yahoo Finance fetch failed for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Candle> parseYahooResponse(String json, String symbol, String timeframe) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode result = root.path("chart").path("result").get(0);
        if (result == null || result.isMissingNode()) return Collections.emptyList();

        JsonNode timestamps = result.path("timestamp");
        JsonNode quote      = result.path("indicators").path("quote").get(0);

        JsonNode opens   = quote.path("open");
        JsonNode highs   = quote.path("high");
        JsonNode lows    = quote.path("low");
        JsonNode closes  = quote.path("close");
        JsonNode volumes = quote.path("volume");

        List<Candle> candles = new ArrayList<>();
        ZoneId ist = ZoneId.of("Asia/Kolkata");

        for (int i = 0; i < timestamps.size(); i++) {
            if (closes.get(i).isNull()) continue; // skip gaps

            candles.add(Candle.builder()
                    .symbol(symbol)
                    .timeframe(timeframe)
                    .timestamp(LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(timestamps.get(i).asLong()), ist))
                    .open(opens.get(i).asDouble())
                    .high(highs.get(i).asDouble())
                    .low(lows.get(i).asDouble())
                    .close(closes.get(i).asDouble())
                    .volume(volumes.get(i).asLong())
                    .build());
        }
        return candles;
    }

    /**
     * Combine every 3 consecutive 1H candles into a single 3H candle.
     */
    private List<Candle> aggregate3H(List<Candle> oneHour, String symbol) {
        List<Candle> result = new ArrayList<>();
        for (int i = 0; i + 2 < oneHour.size(); i += 3) {
            Candle a = oneHour.get(i);
            Candle b = oneHour.get(i + 1);
            Candle c = oneHour.get(i + 2);
            result.add(Candle.builder()
                    .symbol(symbol)
                    .timeframe("3H")
                    .timestamp(a.getTimestamp()) // open of first bar
                    .open(a.getOpen())
                    .high(Math.max(a.getHigh(), Math.max(b.getHigh(), c.getHigh())))
                    .low(Math.min(a.getLow(), Math.min(b.getLow(), c.getLow())))
                    .close(c.getClose())         // close of last bar
                    .volume(a.getVolume() + b.getVolume() + c.getVolume())
                    .build());
        }
        return result;
    }
}
