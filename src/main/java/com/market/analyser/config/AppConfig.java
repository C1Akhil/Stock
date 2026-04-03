package com.market.analyser.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class AppConfig {

    /* ── WebClient ─────────────────────────────────────────────────── */

    @Bean
    public WebClient webClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(5 * 1024 * 1024)) // 5 MB
                .build();

        return WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Nifty50Analyser/1.0)")
                .exchangeStrategies(strategies)
                .build();
    }

    /* ── Cache ─────────────────────────────────────────────────────── */

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "marketData",       // raw OHLCV candles
                "analysisResults",  // computed signals
                "stockList"         // Nifty50 symbol list
        );
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .recordStats()
        );
        return manager;
    }

    /* ── Swagger / OpenAPI ─────────────────────────────────────────── */

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nifty 50 Options Analyser API")
                        .description("Live market data with MACD, RSI & MA signals across 1H and 3H timeframes")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Market Analyser")
                                .email("admin@marketanalyser.com")));
    }
}
