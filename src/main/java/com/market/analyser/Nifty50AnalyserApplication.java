package com.market.analyser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Nifty 50 Options Analyser
 *
 * Fetches live OHLCV data and computes MACD, RSI, MA indicators
 * across 1H and 3H timeframes to generate Buy/Sell ratings for options.
 *
 * Supported providers: Yahoo Finance, Alpha Vantage, Twelve Data, Upstox, Zerodha
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class Nifty50AnalyserApplication {

    public static void main(String[] args) {
        SpringApplication.run(Nifty50AnalyserApplication.class, args);
    }
}
