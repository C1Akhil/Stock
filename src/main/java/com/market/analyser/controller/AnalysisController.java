package com.market.analyser.controller;

import com.market.analyser.dto.AnalysisResultDto;
import com.market.analyser.dto.MarketSummaryDto;
import com.market.analyser.model.Nifty50Registry;
import com.market.analyser.model.Rating;
import com.market.analyser.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/v1/analysis")
@RequiredArgsConstructor
@Tag(name = "Nifty50 Analysis", description = "Live market analysis with MACD, RSI and MA indicators")
public class AnalysisController {

    private final AnalysisService analysisService;

    // ─── All stocks ──────────────────────────────────────────────────────────

    @GetMapping("/all")
    @Operation(summary = "Analyse all 50 Nifty stocks",
               description = "Returns MACD, RSI and MA signals for 1H and 3H timeframes with buy/sell rating")
    public ResponseEntity<List<AnalysisResultDto>> analyseAll() {
        return ResponseEntity.ok(analysisService.analyseAll());
    }

    // ─── Single stock ────────────────────────────────────────────────────────

    @GetMapping("/stock/{symbol}")
    @Operation(summary = "Analyse a single stock",
               description = "Pass the NSE symbol with .NS suffix, e.g. RELIANCE.NS")
    public ResponseEntity<AnalysisResultDto> analyseStock(
            @Parameter(description = "NSE symbol e.g. RELIANCE.NS")
            @PathVariable String symbol) {

        String upper = symbol.toUpperCase();
        if (!Nifty50Registry.getStocks().containsKey(upper)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(analysisService.analyseSingle(upper));
    }

    // ─── Filter by rating ────────────────────────────────────────────────────

    @GetMapping("/rating/{rating}")
    @Operation(summary = "Filter stocks by rating",
               description = "Valid values: STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL")
    public ResponseEntity<List<AnalysisResultDto>> byRating(
            @Parameter(description = "Rating value") @PathVariable String rating) {
        try {
            Rating r = Rating.valueOf(rating.toUpperCase());
            List<AnalysisResultDto> filtered = analysisService.analyseAll()
                    .stream()
                    .filter(dto -> dto.getRating() == r)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(filtered);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ─── Filter by sector ────────────────────────────────────────────────────

    @GetMapping("/sector/{sector}")
    @Operation(summary = "Filter stocks by sector",
               description = "e.g. Banking, IT, Pharma, Auto, Energy, FMCG, Metals")
    public ResponseEntity<List<AnalysisResultDto>> bySector(
            @PathVariable String sector) {
        List<AnalysisResultDto> filtered = analysisService.analyseAll()
                .stream()
                .filter(dto -> dto.getSector().equalsIgnoreCase(sector))
                .collect(Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    // ─── Top buys / top sells ────────────────────────────────────────────────

    @GetMapping("/top-buys")
    @Operation(summary = "Top N stocks by bullish composite score")
    public ResponseEntity<List<AnalysisResultDto>> topBuys(
            @RequestParam(defaultValue = "10") int limit) {
        List<AnalysisResultDto> all = analysisService.analyseAll();
        return ResponseEntity.ok(all.stream().limit(limit).collect(Collectors.toList()));
    }

    @GetMapping("/top-sells")
    @Operation(summary = "Top N stocks by bearish composite score")
    public ResponseEntity<List<AnalysisResultDto>> topSells(
            @RequestParam(defaultValue = "10") int limit) {
        List<AnalysisResultDto> all = analysisService.analyseAll();
        List<AnalysisResultDto> sorted = all.stream()
                .sorted((a, b) -> Integer.compare(a.getScore(), b.getScore()))
                .limit(limit)
                .collect(Collectors.toList());
        return ResponseEntity.ok(sorted);
    }

    // ─── Summary ─────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @Operation(summary = "Market-wide summary",
               description = "Rating counts, bullish/bearish %, top picks and sector sentiment")
    public ResponseEntity<MarketSummaryDto> summary() {
        return ResponseEntity.ok(analysisService.getSummary());
    }

    // ─── Symbol list ──────────────────────────────────────────────────────────

    @GetMapping("/symbols")
    @Operation(summary = "List all Nifty50 symbols and their sectors")
    public ResponseEntity<Map<String, String>> symbols() {
        return ResponseEntity.ok(Nifty50Registry.getStocks());
    }

    // ─── Manual cache refresh ────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Force cache eviction",
               description = "Invalidates cached results; next GET will fetch live data")
    public ResponseEntity<String> refresh() {
        analysisService.evictCache();
        return ResponseEntity.ok("Cache evicted. Next request will fetch live market data.");
    }
}
