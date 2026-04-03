package com.market.analyser.dto;

import com.market.analyser.model.Rating;
import com.market.analyser.model.Signal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Full analysis result for one symbol.
 */
@Data
@Builder
public class AnalysisResultDto {

    private String symbol;
    private String sector;
    private double ltp;           // last traded price
    private double change;        // absolute change
    private double changePct;     // % change

    // 1H indicators
    private Signal macd1h;
    private double macd1hValue;
    private double macd1hSignal;
    private double macd1hHistogram;

    private Signal rsi1h;
    private double rsi1hValue;

    private Signal ma1h;
    private double ma1hShort;     // 20-period
    private double ma1hLong;      // 50-period

    // 3H indicators
    private Signal macd3h;
    private double macd3hValue;
    private double macd3hSignal;
    private double macd3hHistogram;

    private Signal rsi3h;
    private double rsi3hValue;

    private Signal ma3h;
    private double ma3hShort;
    private double ma3hLong;

    // Composite
    private int score;            // -6 to +6
    private Rating rating;
    private String action;
    private LocalDateTime analyzedAt;
    private String dataSource;
}
