package com.market.analyser.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static registry of all 50 Nifty50 constituents.
 *
 * Yahoo Finance uses the ".NS" suffix for NSE-listed equities.
 * Upstox / Zerodha use exchange-specific instrument tokens —
 * update the symbol map below to match your broker's format.
 */
public final class Nifty50Registry {

    private Nifty50Registry() {}

    /** symbol → sector */
    private static final Map<String, String> STOCKS = new LinkedHashMap<>();

    static {
        // Banking & Finance
        STOCKS.put("HDFCBANK.NS",   "Banking");
        STOCKS.put("ICICIBANK.NS",  "Banking");
        STOCKS.put("KOTAKBANK.NS",  "Banking");
        STOCKS.put("AXISBANK.NS",   "Banking");
        STOCKS.put("SBIN.NS",       "Banking");
        STOCKS.put("INDUSINDBK.NS", "Banking");
        STOCKS.put("BAJFINANCE.NS", "Finance");
        STOCKS.put("BAJAJFINSV.NS", "Finance");
        STOCKS.put("SHRIRAMFIN.NS", "Finance");
        STOCKS.put("HDFCLIFE.NS",   "Insurance");
        STOCKS.put("SBILIFE.NS",    "Insurance");

        // IT
        STOCKS.put("TCS.NS",      "IT");
        STOCKS.put("INFY.NS",     "IT");
        STOCKS.put("WIPRO.NS",    "IT");
        STOCKS.put("HCLTECH.NS",  "IT");
        STOCKS.put("TECHM.NS",    "IT");

        // Energy & Oil
        STOCKS.put("RELIANCE.NS", "Energy");
        STOCKS.put("NTPC.NS",     "Energy");
        STOCKS.put("POWERGRID.NS","Energy");
        STOCKS.put("ONGC.NS",     "Energy");
        STOCKS.put("BPCL.NS",     "Energy");
        STOCKS.put("IOC.NS",      "Energy");

        // FMCG
        STOCKS.put("HINDUNILVR.NS","FMCG");
        STOCKS.put("ITC.NS",       "FMCG");
        STOCKS.put("NESTLEIND.NS", "FMCG");
        STOCKS.put("TATACONSUM.NS","FMCG");
        STOCKS.put("BRITANNIA.NS", "FMCG");

        // Auto
        STOCKS.put("MARUTI.NS",    "Auto");
        STOCKS.put("TATAMOTORS.NS","Auto");
        STOCKS.put("M%26M.NS",     "Auto");
        STOCKS.put("EICHERMOT.NS", "Auto");
        STOCKS.put("HEROMOTOCO.NS","Auto");
        STOCKS.put("BAJAJ-AUTO.NS","Auto");

        // Metals
        STOCKS.put("JSWSTEEL.NS",  "Metals");
        STOCKS.put("TATASTEEL.NS", "Metals");
        STOCKS.put("HINDALCO.NS",  "Metals");

        // Pharma
        STOCKS.put("SUNPHARMA.NS", "Pharma");
        STOCKS.put("DRREDDY.NS",   "Pharma");
        STOCKS.put("CIPLA.NS",     "Pharma");
        STOCKS.put("DIVISLAB.NS",  "Pharma");
        STOCKS.put("APOLLOHOSP.NS","Healthcare");

        // Conglomerate / Others
        STOCKS.put("LT.NS",        "Infra");
        STOCKS.put("ADANIPORTS.NS","Infra");
        STOCKS.put("ASIANPAINT.NS","Paints");
        STOCKS.put("TITAN.NS",     "Consumer");
        STOCKS.put("ULTRACEMCO.NS","Cement");
        STOCKS.put("GRASIM.NS",    "Textiles");
        STOCKS.put("BHARTIARTL.NS","Telecom");
        STOCKS.put("COALINDIA.NS", "Mining");
        STOCKS.put("BEL.NS",       "Defence");
    }

    public static Map<String, String> getStocks() {
        return STOCKS;
    }

    public static String getSector(String symbol) {
        return STOCKS.getOrDefault(symbol, "Unknown");
    }

    /** Strip exchange suffix for display: "RELIANCE.NS" → "RELIANCE" */
    public static String displayName(String symbol) {
        return symbol.replace(".NS", "").replace("%26", "&");
    }
}
