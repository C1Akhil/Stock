package com.market.analyser.service;

import com.market.analyser.config.MarketProperties;
import com.market.analyser.service.provider.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Selects the correct {@link MarketDataProvider} based on
 * the {@code market.provider} configuration property.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataProviderFactory {

    private final MarketProperties props;
    private final YahooFinanceProvider yahooFinanceProvider;
    private final AlphaVantageProvider alphaVantageProvider;
    private final TwelveDataProvider twelveDataProvider;
    private final UpstoxProvider upstoxProvider;

    public MarketDataProvider getProvider() {
        String p = props.getProvider().toUpperCase();
        log.info("Using market data provider: {}", p);
        return switch (p) {
            case "ALPHA_VANTAGE"  -> alphaVantageProvider;
            case "TWELVE_DATA"    -> twelveDataProvider;
            case "UPSTOX"         -> upstoxProvider;
            case "YAHOO_FINANCE"  -> yahooFinanceProvider;
            default -> {
                log.warn("Unknown provider '{}', falling back to Yahoo Finance", p);
                yield yahooFinanceProvider;
            }
        };
    }
}
