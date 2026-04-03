package com.market.analyser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "market")
public class MarketProperties {

    private String provider = "YAHOO_FINANCE";
    private long refreshIntervalSeconds = 300;
    private Indicators indicators = new Indicators();
    private AlphaVantage alphaVantage = new AlphaVantage();
    private TwelveData twelveData = new TwelveData();
    private YahooFinance yahooFinance = new YahooFinance();
    private Upstox upstox = new Upstox();
    private Zerodha zerodha = new Zerodha();

    @Data
    public static class Indicators {
        private int rsiPeriod = 14;
        private int macdFast = 12;
        private int macdSlow = 26;
        private int macdSignal = 9;
        private int maShort = 20;
        private int maLong = 50;
    }

    @Data
    public static class AlphaVantage {
        private String apiKey;
        private String baseUrl = "https://www.alphavantage.co/query";
    }

    @Data
    public static class TwelveData {
        private String apiKey;
        private String baseUrl = "https://api.twelvedata.com";
    }

    @Data
    public static class YahooFinance {
        private String baseUrl = "https://query1.finance.yahoo.com/v8/finance/chart";
    }

    @Data
    public static class Upstox {
        private String apiKey;
        private String accessToken;
        private String baseUrl = "https://api.upstox.com/v2";
    }

    @Data
    public static class Zerodha {
        private String apiKey;
        private String accessToken;
        private String baseUrl = "https://api.kite.trade";
    }
}
