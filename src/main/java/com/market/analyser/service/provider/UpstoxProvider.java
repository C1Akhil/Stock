package com.market.analyser.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.market.analyser.config.MarketProperties;
import com.market.analyser.model.Candle;
import com.market.analyser.service.MarketDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Upstox v2 Historical Candle API.
 *
 * Requires a valid Upstox access token (refresh daily via OAuth).
 * Docs: https://upstox.com/developer/api-documentation/
 *
 * Instrument key format: "NSE_EQ|INE002A01018"  (symbol's ISIN-based key)
 * See Upstox instruments CSV for the full mapping.
 *
 * Set market.upstox.access-token and market.upstox.api-key in application.yml.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpstoxProvider implements MarketDataProvider {

    private final WebClient webClient;
    private final MarketProperties props;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Override
    public String providerName() { return "Upstox"; }

    @Override
    public List<Candle> fetchCandles(String symbol, String timeframe, int limit) {
        try {
            // Upstox instrument key — must be URL-encoded pipe: NSE_EQ%7CINE002A01018
            // Here we use a simplified mapping; replace with real ISIN-based keys.
            String instrKey = toUpstoxKey(symbol);
            String interval = "1H".equals(timeframe) ? "1hour" : "3hour";

            // Date range: last 30 days for sufficient candle count
            String toDate   = java.time.LocalDate.now().toString();
            String fromDate = java.time.LocalDate.now().minusDays(30).toString();

            String url = props.getUpstox().getBaseUrl()
                    + "/historical-candle/" + instrKey
                    + "/" + interval
                    + "/" + toDate
                    + "/" + fromDate;

            log.debug("Upstox request for {} {}", symbol, timeframe);

            String json = webClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getUpstox().getAccessToken())
                    .header("Api-Version", "2.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<Candle> candles = parseResponse(json, symbol, timeframe);
            if (candles.size() > limit) candles = candles.subList(candles.size() - limit, candles.size());
            return candles;

        } catch (Exception e) {
            log.error("Upstox fetch failed for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Candle> parseResponse(String json, String symbol, String timeframe) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode candles = root.path("data").path("candles");

        List<Candle> result = new ArrayList<>();
        // Upstox returns: [timestamp, open, high, low, close, volume, oi]
        for (JsonNode bar : candles) {
            result.add(Candle.builder()
                    .symbol(symbol)
                    .timeframe(timeframe)
                    .timestamp(LocalDateTime.parse(bar.get(0).asText(), FMT))
                    .open(bar.get(1).asDouble())
                    .high(bar.get(2).asDouble())
                    .low(bar.get(3).asDouble())
                    .close(bar.get(4).asDouble())
                    .volume(bar.get(5).asLong())
                    .build());
        }
        // Upstox returns newest-first — reverse
        java.util.Collections.reverse(result);
        return result;
    }

    /**
     * Simplified symbol → Upstox instrument key mapping.
     * Replace with a proper lookup from the Upstox instruments CSV.
     *
     * Download instruments: https://assets.upstox.com/market-quote/instruments/exchange/NSE.csv.gz
     */
    private String toUpstoxKey(String symbol) {
        // Remove .NS suffix and URL-encode the pipe character
        String base = symbol.replace(".NS", "");
        return "NSE_EQ%7C" + base; // placeholder — use real ISIN keys in production
    }
}
