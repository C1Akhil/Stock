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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Alpha Vantage intraday provider.
 *
 * Free tier: 25 API calls / day.
 * Register at https://www.alphavantage.co/support/#api-key
 *
 * Set market.alpha-vantage.api-key in application.yml.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlphaVantageProvider implements MarketDataProvider {

    private final WebClient webClient;
    private final MarketProperties props;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String providerName() { return "Alpha Vantage"; }

    @Override
    public List<Candle> fetchCandles(String symbol, String timeframe, int limit) {
        try {
            // Alpha Vantage uses "60min" for 1H; no native 3H → aggregate
            String interval = "60min";
            // Remove ".NS" suffix — AV uses BSE/NSE suffixes differently
            String avSymbol = symbol.replace(".NS", ".BSE");

            String url = props.getAlphaVantage().getBaseUrl()
                    + "?function=TIME_SERIES_INTRADAY"
                    + "&symbol=" + avSymbol
                    + "&interval=" + interval
                    + "&outputsize=full"
                    + "&apikey=" + props.getAlphaVantage().getApiKey();

            log.debug("Alpha Vantage request for {}", symbol);

            String json = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            List<Candle> candles = parseResponse(json, symbol);
            if ("3H".equals(timeframe)) candles = aggregate3H(candles, symbol);
            if (candles.size() > limit) candles = candles.subList(candles.size() - limit, candles.size());
            return candles;

        } catch (Exception e) {
            log.error("Alpha Vantage fetch failed for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Candle> parseResponse(String json, String symbol) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode series = root.path("Time Series (60min)");
        if (series.isMissingNode()) {
            log.warn("Alpha Vantage: no data node. Possibly rate-limited. Response: {}",
                    json.substring(0, Math.min(200, json.length())));
            return Collections.emptyList();
        }

        // AV returns newest-first; reverse to oldest-first
        List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
        series.fields().forEachRemaining(entries::add);
        entries.sort(Map.Entry.comparingByKey());

        List<Candle> candles = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : entries) {
            JsonNode bar = entry.getValue();
            candles.add(Candle.builder()
                    .symbol(symbol)
                    .timeframe("1H")
                    .timestamp(LocalDateTime.parse(entry.getKey(), FMT))
                    .open(bar.path("1. open").asDouble())
                    .high(bar.path("2. high").asDouble())
                    .low(bar.path("3. low").asDouble())
                    .close(bar.path("4. close").asDouble())
                    .volume(bar.path("5. volume").asLong())
                    .build());
        }
        return candles;
    }

    private List<Candle> aggregate3H(List<Candle> oneHour, String symbol) {
        List<Candle> result = new ArrayList<>();
        for (int i = 0; i + 2 < oneHour.size(); i += 3) {
            Candle a = oneHour.get(i), b = oneHour.get(i + 1), c = oneHour.get(i + 2);
            result.add(Candle.builder()
                    .symbol(symbol).timeframe("3H").timestamp(a.getTimestamp())
                    .open(a.getOpen())
                    .high(Math.max(a.getHigh(), Math.max(b.getHigh(), c.getHigh())))
                    .low(Math.min(a.getLow(), Math.min(b.getLow(), c.getLow())))
                    .close(c.getClose())
                    .volume(a.getVolume() + b.getVolume() + c.getVolume())
                    .build());
        }
        return result;
    }
}
