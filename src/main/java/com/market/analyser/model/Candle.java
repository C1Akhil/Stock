package com.market.analyser.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OHLCV candle — one entry per symbol + timeframe + timestamp.
 */
@Entity
@Table(name = "candles",
       indexes = {
           @Index(name = "idx_candle_sym_tf", columnList = "symbol,timeframe"),
           @Index(name = "idx_candle_ts", columnList = "timestamp")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    /** "1H" or "3H" */
    @Column(nullable = false, length = 5)
    private String timeframe;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
}
