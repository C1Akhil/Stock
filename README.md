# Nifty 50 Options Analyser — Spring Boot

A production-ready Spring Boot service that fetches **live market data** for all 50 Nifty stocks and computes **MACD, RSI and Moving Average** signals across **1H and 3H timeframes** to generate buy/sell ratings for options trading.

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+

### 1. Clone and configure

```bash
git clone <repo-url>
cd nifty50-analyser
```

Edit `src/main/resources/application.yml`:

```yaml
market:
  provider: YAHOO_FINANCE   # no API key needed — best for quick start
```

### 2. Run

```bash
mvn spring-boot:run
```

Service starts on **http://localhost:8080/api**

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/analysis/all` | Full analysis of all 50 stocks |
| GET | `/api/v1/analysis/stock/{symbol}` | Single stock (e.g. `RELIANCE.NS`) |
| GET | `/api/v1/analysis/rating/{rating}` | Filter by rating (STRONG_BUY, BUY, etc.) |
| GET | `/api/v1/analysis/sector/{sector}` | Filter by sector (Banking, IT, etc.) |
| GET | `/api/v1/analysis/top-buys?limit=10` | Top bullish stocks |
| GET | `/api/v1/analysis/top-sells?limit=10` | Top bearish stocks |
| GET | `/api/v1/analysis/summary` | Market-wide dashboard summary |
| GET | `/api/v1/analysis/symbols` | List all symbols + sectors |
| POST | `/api/v1/analysis/refresh` | Force cache eviction |

**Swagger UI:** http://localhost:8080/api/swagger-ui.html  
**H2 Console:** http://localhost:8080/api/h2-console

---

## Indicator Logic

### Scoring (6 signals × {+1, 0, −1})

| # | Indicator | Timeframe | Bullish | Bearish |
|---|-----------|-----------|---------|---------|
| 1 | MACD | 1H | Histogram > 0 | Histogram < 0 |
| 2 | MACD | 3H | Histogram > 0 | Histogram < 0 |
| 3 | RSI | 1H | RSI > 60 | RSI < 40 |
| 4 | RSI | 3H | RSI > 60 | RSI < 40 |
| 5 | MA (20-period) | 1H | Price > MA20 | Price < MA20 |
| 6 | MA (20-period) | 3H | Price > MA20 | Price < MA20 |

### Rating thresholds

| Score | Rating | Options action |
|-------|--------|----------------|
| +4 to +6 | STRONG_BUY | Buy CE aggressively |
| +2 to +3 | BUY | Buy CE |
| −1 to +1 | NEUTRAL | Wait / Straddle |
| −3 to −2 | SELL | Buy PE |
| −6 to −4 | STRONG_SELL | Buy PE aggressively |

---

## Data Providers

### Yahoo Finance (default — no key needed)

```yaml
market:
  provider: YAHOO_FINANCE
```

Fetches 1H candles; aggregates 3×1H → 3H internally.

---

### Twelve Data (recommended — native 3H support)

Free: 800 calls/day. Register at https://twelvedata.com/

```yaml
market:
  provider: TWELVE_DATA
  twelve-data:
    api-key: YOUR_KEY_HERE
```

---

### Alpha Vantage

Free: 25 calls/day. Register at https://www.alphavantage.co/

```yaml
market:
  provider: ALPHA_VANTAGE
  alpha-vantage:
    api-key: YOUR_KEY_HERE
```

---

### Upstox (Indian broker — best for production)

Requires OAuth access token. Docs: https://upstox.com/developer/api-documentation/

```yaml
market:
  provider: UPSTOX
  upstox:
    api-key: YOUR_API_KEY
    access-token: YOUR_ACCESS_TOKEN
```

> **Important:** Replace the `toUpstoxKey()` mapping in `UpstoxProvider.java` with real ISIN-based instrument keys from the Upstox instruments CSV.

---

### Zerodha Kite

Requires daily session token. Docs: https://kite.trade/docs/connect/v3/

```yaml
market:
  provider: ZERODHA
  zerodha:
    api-key: YOUR_API_KEY
    access-token: YOUR_ACCESS_TOKEN
```

---

## Configuration Reference

```yaml
market:
  provider: YAHOO_FINANCE           # Provider selection
  refresh-interval-seconds: 300     # Cache TTL (5 min)
  indicators:
    rsi-period: 14                  # Wilder's RSI period
    macd-fast: 12                   # MACD fast EMA
    macd-slow: 26                   # MACD slow EMA
    macd-signal: 9                  # MACD signal EMA
    ma-short: 20                    # Short MA period
    ma-long: 50                     # Long MA period
```

---

## Project Structure

```
src/main/java/com/market/analyser/
├── Nifty50AnalyserApplication.java   # Entry point
├── config/
│   ├── AppConfig.java               # WebClient, Cache, Swagger beans
│   └── MarketProperties.java        # Typed config properties
├── controller/
│   ├── AnalysisController.java      # REST endpoints
│   └── GlobalExceptionHandler.java  # Error handling
├── dto/
│   ├── AnalysisResultDto.java       # Per-stock result
│   └── MarketSummaryDto.java        # Dashboard summary
├── model/
│   ├── Candle.java                  # OHLCV entity
│   ├── Nifty50Registry.java         # 50 symbols + sectors
│   ├── Rating.java                  # STRONG_BUY … STRONG_SELL
│   └── Signal.java                  # BULLISH / BEARISH / NEUTRAL
├── scheduler/
│   └── MarketRefreshScheduler.java  # Auto-refresh during market hours
└── service/
    ├── AnalysisService.java         # Core orchestration + caching
    ├── IndicatorEngine.java         # MACD, RSI, MA calculations
    ├── MarketDataProvider.java      # Strategy interface
    ├── MarketDataProviderFactory.java
    └── provider/
        ├── YahooFinanceProvider.java
        ├── AlphaVantageProvider.java
        ├── TwelveDataProvider.java
        └── UpstoxProvider.java
```

---

## Running Tests

```bash
mvn test
```

---

## Sample Response

```json
{
  "symbol": "RELIANCE",
  "sector": "Energy",
  "ltp": 2950.25,
  "change": 18.75,
  "changePct": 0.64,
  "macd1h": "BULLISH",
  "macd1hHistogram": 3.2145,
  "rsi1h": "BULLISH",
  "rsi1hValue": 64.3,
  "ma1h": "BULLISH",
  "ma1hShort": 2910.5,
  "macd3h": "BULLISH",
  "rsi3h": "NEUTRAL",
  "rsi3hValue": 55.8,
  "ma3h": "BULLISH",
  "score": 5,
  "rating": "STRONG_BUY",
  "action": "Buy CE aggressively",
  "analyzedAt": "2026-04-03T10:30:00",
  "dataSource": "Yahoo Finance"
}
```

---

## Disclaimer

This tool is for **educational and research purposes only**. Options trading carries significant financial risk. Always consult a SEBI-registered financial advisor before making investment decisions.
