package com.market.analyser.service;

import com.market.analyser.config.MarketProperties;
import com.market.analyser.model.Signal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pure Java implementation of MACD, RSI and Moving Average calculations.
 *
 * All methods accept a List<Double> of closing prices ordered oldest → newest.
 */
@Component
@RequiredArgsConstructor
public class IndicatorEngine {

    private final MarketProperties props;

    // ─── Moving Averages ────────────────────────────────────────────────────

    /**
     * Simple Moving Average over the last {@code period} values.
     */
    public double sma(List<Double> closes, int period) {
        if (closes.size() < period) return Double.NaN;
        int from = closes.size() - period;
        double sum = 0;
        for (int i = from; i < closes.size(); i++) sum += closes.get(i);
        return sum / period;
    }

    /**
     * Exponential Moving Average — full EMA over the whole series.
     * Returns NaN if not enough data.
     */
    public double ema(List<Double> closes, int period) {
        if (closes.size() < period) return Double.NaN;
        double k = 2.0 / (period + 1);
        double ema = closes.get(0);
        for (int i = 1; i < closes.size(); i++) {
            ema = closes.get(i) * k + ema * (1 - k);
        }
        return ema;
    }

    /** Full EMA series — one value per input close (padded with NaN for first period-1 bars). */
    private double[] emaSeries(List<Double> closes, int period) {
        double[] result = new double[closes.size()];
        double k = 2.0 / (period + 1);
        // seed with SMA of first `period` bars
        double sum = 0;
        for (int i = 0; i < period; i++) sum += closes.get(i);
        result[period - 1] = sum / period;
        for (int i = period; i < closes.size(); i++) {
            result[i] = closes.get(i) * k + result[i - 1] * (1 - k);
        }
        for (int i = 0; i < period - 1; i++) result[i] = Double.NaN;
        return result;
    }

    /**
     * MA signal: price above 20-period MA → BULLISH; below → BEARISH.
     */
    public MaResult calculateMA(List<Double> closes) {
        int shortP = props.getIndicators().getMaShort();
        int longP  = props.getIndicators().getMaLong();
        double shortMa = sma(closes, shortP);
        double longMa  = sma(closes, longP);
        double lastClose = closes.get(closes.size() - 1);

        Signal signal;
        if (Double.isNaN(shortMa)) {
            signal = Signal.NEUTRAL;
        } else if (lastClose > shortMa) {
            signal = Signal.BULLISH;
        } else {
            signal = Signal.BEARISH;
        }
        return new MaResult(signal, shortMa, longMa);
    }

    // ─── RSI ────────────────────────────────────────────────────────────────

    /**
     * Wilder's RSI.
     * BULLISH > 60, BEARISH < 40, else NEUTRAL.
     */
    public RsiResult calculateRSI(List<Double> closes) {
        int period = props.getIndicators().getRsiPeriod();
        if (closes.size() <= period) return new RsiResult(Signal.NEUTRAL, 50.0);

        double gainSum = 0, lossSum = 0;
        for (int i = 1; i <= period; i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            if (diff >= 0) gainSum += diff; else lossSum -= diff;
        }
        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;

        for (int i = period + 1; i < closes.size(); i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            double gain = diff >= 0 ? diff : 0;
            double loss = diff < 0 ? -diff : 0;
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        double rsi = avgLoss == 0 ? 100 : 100 - (100 / (1 + avgGain / avgLoss));
        Signal signal = rsi > 60 ? Signal.BULLISH : rsi < 40 ? Signal.BEARISH : Signal.NEUTRAL;
        return new RsiResult(signal, rsi);
    }

    // ─── MACD ────────────────────────────────────────────────────────────────

    /**
     * MACD(12,26,9).
     * BULLISH when MACD > Signal line (histogram > 0); BEARISH otherwise.
     */
    public MacdResult calculateMACD(List<Double> closes) {
        int fast   = props.getIndicators().getMacdFast();
        int slow   = props.getIndicators().getMacdSlow();
        int signal = props.getIndicators().getMacdSignal();

        int minRequired = slow + signal;
        if (closes.size() < minRequired) return new MacdResult(Signal.NEUTRAL, 0, 0, 0);

        double[] fastEma = emaSeries(closes, fast);
        double[] slowEma = emaSeries(closes, slow);

        // MACD line = fast EMA - slow EMA (valid from index slow-1 onwards)
        double[] macdLine = new double[closes.size()];
        for (int i = slow - 1; i < closes.size(); i++) {
            macdLine[i] = fastEma[i] - slowEma[i];
        }

        // Build list of valid MACD values to compute signal EMA
        int validFrom = slow - 1;
        java.util.List<Double> macdList = new java.util.ArrayList<>();
        for (int i = validFrom; i < closes.size(); i++) macdList.add(macdLine[i]);

        if (macdList.size() < signal) return new MacdResult(Signal.NEUTRAL, 0, 0, 0);

        double signalLine = ema(macdList, signal);
        double macdVal = macdList.get(macdList.size() - 1);
        double histogram = macdVal - signalLine;

        Signal sig = histogram > 0 ? Signal.BULLISH : histogram < 0 ? Signal.BEARISH : Signal.NEUTRAL;
        return new MacdResult(sig, macdVal, signalLine, histogram);
    }

    // ─── Result record types ────────────────────────────────────────────────

    public record MaResult(Signal signal, double shortMa, double longMa) {}

    public record RsiResult(Signal signal, double value) {}

    public record MacdResult(Signal signal, double macdLine, double signalLine, double histogram) {}
}
