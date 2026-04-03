package com.market.analyser.scheduler;

import com.market.analyser.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically evicts the analysis cache so the next request
 * triggers a fresh fetch from the market data provider.
 *
 * Interval is driven by market.refresh-interval-seconds (default 300s = 5 min).
 * The fixed-delay here mirrors that property; adjust in application.yml.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketRefreshScheduler {

    private final AnalysisService analysisService;

    /**
     * Runs every 5 minutes during market hours (IST 09:15 – 15:30 Mon–Fri).
     * Cron: every 5 min between 9:15 and 15:30, Mon–Fri.
     */
    @Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void refreshDuringMarketHours() {
        log.info("[Scheduler] Market hours — evicting cache for fresh data");
        analysisService.evictCache();
    }

    /**
     * Outside market hours: evict once per hour so data stays reasonably fresh.
     */
    @Scheduled(cron = "0 0 * * * MON-FRI", zone = "Asia/Kolkata")
    public void refreshOffHours() {
        log.debug("[Scheduler] Off-hours cache eviction");
        analysisService.evictCache();
    }
}
