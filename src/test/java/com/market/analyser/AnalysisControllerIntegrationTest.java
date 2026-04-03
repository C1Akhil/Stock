package com.market.analyser;

import com.market.analyser.dto.AnalysisResultDto;
import com.market.analyser.dto.MarketSummaryDto;
import com.market.analyser.model.Rating;
import com.market.analyser.service.AnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalysisService analysisService;

    private AnalysisResultDto sampleDto() {
        return AnalysisResultDto.builder()
                .symbol("RELIANCE")
                .sector("Energy")
                .ltp(2950.0)
                .change(25.0)
                .changePct(0.85)
                .score(4)
                .rating(Rating.STRONG_BUY)
                .action("Buy CE aggressively")
                .analyzedAt(LocalDateTime.now())
                .dataSource("Yahoo Finance")
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/analysis/all returns 200 with list")
    void getAllReturns200() throws Exception {
        when(analysisService.analyseAll()).thenReturn(List.of(sampleDto()));

        mockMvc.perform(get("/api/v1/analysis/all").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].rating").value("STRONG_BUY"));
    }

    @Test
    @DisplayName("GET /api/v1/analysis/summary returns market summary")
    void getSummaryReturns200() throws Exception {
        MarketSummaryDto summary = MarketSummaryDto.builder()
                .totalStocks(50)
                .strongBuyCount(10)
                .buyCount(15)
                .neutralCount(12)
                .sellCount(8)
                .strongSellCount(5)
                .bullishPct(50.0)
                .bearishPct(26.0)
                .generatedAt(LocalDateTime.now())
                .provider("Yahoo Finance")
                .build();

        when(analysisService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analysis/summary").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStocks").value(50))
                .andExpect(jsonPath("$.provider").value("Yahoo Finance"));
    }

    @Test
    @DisplayName("POST /api/v1/analysis/refresh evicts cache and returns 200")
    void refreshReturns200() throws Exception {
        doNothing().when(analysisService).evictCache();

        mockMvc.perform(post("/api/v1/analysis/refresh"))
                .andExpect(status().isOk());

        verify(analysisService, times(1)).evictCache();
    }

    @Test
    @DisplayName("GET /api/v1/analysis/stock/INVALID returns 400")
    void invalidSymbolReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/stock/INVALID"))
                .andExpect(status().isBadRequest());
    }
}
