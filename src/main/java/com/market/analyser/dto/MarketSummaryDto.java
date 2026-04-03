package com.market.analyser.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Dashboard summary — counts per rating + top picks.
 */
@Data
@Builder
public class MarketSummaryDto {

    private int totalStocks;
    private int strongBuyCount;
    private int buyCount;
    private int neutralCount;
    private int sellCount;
    private int strongSellCount;

    private double bullishPct;   // % of stocks with buy/strong-buy
    private double bearishPct;

    private List<AnalysisResultDto> topBuys;      // top 5 by score
    private List<AnalysisResultDto> topSells;     // bottom 5 by score

    private Map<String, Integer> sectorSentiment; // sector → avg score
    private LocalDateTime generatedAt;
    private String provider;
}
