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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Twelve Data provider.
 *
 * Free tier: 800 API calls / day, 8 calls / minute.
 * Supports native "1h" and "3h" intervals — ideal for this use case.
 * Register at https://twelvedata.com/
 *
 * Set market.twelve-data.api-key in application.yml.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TwelveDataProvider implements MarketDataProvider {

    private final WebClient webClient;
    private final MarketProperties props;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String providerName() { return "Twelve Data"; }

    @Override
    public List<Candle> fetchCandles(String symbol, String timeframe, int limit) {
        try {
            // Twelve Data supports native 1h and 3h intervals
            String interval = "1H".equals(timeframe) ? "1h" : "3h";
            // Strip .NS suffix; append exchange
            String tdSymbol = symbol.replace(".NS", "") + ":NSE";

            String url = props.getTwelveData().getBaseUrl() + "/time_series"
                    + "?symbol=" + tdSymbol
                    + "&interval=" + interval
                    + "&outputsize=" + limit
                    + "&order=ASC"
                    + "&apikey=" + props.getTwelveData().getApiKey();

            log.debug("Twelve Data request for {} {}", symbol, timeframe);

            String json = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(json, symbol, timeframe);

        } catch (Exception e) {
            log.error("Twelve Data fetch failed for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Candle> parseResponse(String json, String symbol, String timeframe) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        if (root.has("code") && root.path("code").asInt() != 200) {
            log.warn("Twelve Data error for {}: {}", symbol, root.path("message").asText());
            return Collections.emptyList();
        }

        JsonNode values = root.path("values");
        List<Candle> candles = new ArrayList<>();

        for (JsonNode bar : values) {
            candles.add(Candle.builder()
                    .symbol(symbol)
                    .timeframe(timeframe)
                    .timestamp(LocalDateTime.parse(bar.path("datetime").asText(), FMT))
                    .open(bar.path("open").asDouble())
                    .high(bar.path("high").asDouble())
                    .low(bar.path("low").asDouble())
                    .close(bar.path("close").asDouble())
                    .volume(bar.path("volume").asLong(0))
                    .build());
        }
        return candles;
    }
}
