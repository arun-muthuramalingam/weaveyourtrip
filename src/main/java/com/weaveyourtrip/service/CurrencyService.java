package com.weaveyourtrip.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.weaveyourtrip.model.Passport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * FX rates via <a href="https://www.frankfurter.app/">frankfurter.app</a> —
 * free, no API key, ECB rates updated daily.
 *
 * <p>Caches the result for 24h via Caffeine. One HTTP call per day per
 * base currency. Frankfurter returns rates relative to a base currency
 * (default EUR); the {@code rates} map contains the conversion factor for
 * each target.
 *
 * <p>Falls back gracefully when the network call fails — returns 1.0
 * conversion factor so the app stays usable even with broken FX upstream.
 */
@Service
@Slf4j
public class CurrencyService {

    /** Maps each passport to its primary local currency code. */
    private static final Map<Passport, String> CURRENCY_FOR_PASSPORT = Map.of(
            Passport.IN,    "INR",
            Passport.US,    "USD",
            Passport.GB,    "GBP",
            Passport.EU,    "EUR",
            Passport.OTHER, "EUR"
    );

    /** Maps each currency code to a display symbol. */
    private static final Map<String, String> SYMBOL = Map.of(
            "INR", "₹",
            "USD", "$",
            "GBP", "£",
            "EUR", "€"
    );

    private final RestClient client;
    private final Cache<String, Map<String, Double>> ratesCache;

    public CurrencyService(@Value("${weaveyourtrip.fx-rates-url}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
        this.ratesCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(24))
                .maximumSize(20)
                .build();
    }

    /** ISO currency code for the traveller's passport. Defaults to EUR. */
    public String currencyFor(Passport passport) {
        return CURRENCY_FOR_PASSPORT.getOrDefault(passport, "EUR");
    }

    /** Display symbol, e.g. {@code ₹}, {@code $}. */
    public String symbol(String currency) {
        return SYMBOL.getOrDefault(currency, currency + " ");
    }

    /**
     * Convert an amount from one currency to another. Returns the input
     * unchanged when {@code from} equals {@code to} or when the FX lookup
     * fails (graceful degradation).
     */
    public double convert(double amount, String from, String to) {
        if (from == null || to == null || from.equalsIgnoreCase(to)) return amount;

        try {
            Map<String, Double> rates = fetchRates(from.toUpperCase(Locale.ROOT));
            Double rate = rates.get(to.toUpperCase(Locale.ROOT));
            if (rate == null) {
                log.warn("No FX rate found for {} → {}", from, to);
                return amount;
            }
            return amount * rate;
        } catch (Exception e) {
            log.warn("FX conversion failed for {} → {}: {}", from, to, e.getMessage());
            return amount;
        }
    }

    /**
     * Convenience for templates — format an amount as "₹ 1,80,000" or
     * "€ 2,000" with locale-appropriate grouping (Indian for INR, Western
     * for others).
     */
    public String format(double amount, String currency) {
        long rounded = Math.round(amount);
        String formatted = "INR".equalsIgnoreCase(currency)
                ? formatIndianGrouping(rounded)
                : String.format(Locale.ENGLISH, "%,d", rounded);
        return symbol(currency) + formatted;
    }

    private Map<String, Double> fetchRates(String base) {
        return ratesCache.get(base, key -> {
            log.info("Fetching FX rates for base={}", key);
            FrankfurterResponse resp = client.get()
                    .uri(uri -> uri.queryParam("base", key).build())
                    .retrieve()
                    .body(FrankfurterResponse.class);
            return resp != null && resp.rates != null ? resp.rates : Map.of();
        });
    }

    /** Indian number grouping (1,80,000 not 180,000). */
    static String formatIndianGrouping(long n) {
        if (n < 1000) return String.valueOf(n);
        String s = String.valueOf(n);
        int len = s.length();
        StringBuilder sb = new StringBuilder();
        sb.append(s, 0, len - 3);                      // last three digits group
        for (int i = sb.length() - 2; i > 0; i -= 2) { // then pairs going left
            sb.insert(i, ',');
        }
        sb.append(',').append(s, len - 3, len);
        return sb.toString();
    }

    /** Minimal mapping of the frankfurter.app JSON response. */
    record FrankfurterResponse(double amount, String base, String date, Map<String, Double> rates) {
    }
}
