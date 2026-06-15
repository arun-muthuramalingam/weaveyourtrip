package com.weaveyourtrip.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP daily rate limit for AI generation. In-memory; resets at midnight via
 * a lazy check on each call. Sufficient for MVP single-instance deploys.
 *
 * <p>Replaces with Bucket4j + Redis or similar in v1.2 when horizontal scale
 * becomes a concern.
 */
@Service
@Slf4j
public class RateLimiter {

    private final int dailyLimit;
    private final Map<String, Usage> usage = new ConcurrentHashMap<>();

    public RateLimiter(
            @Value("${weaveyourtrip.generation-cap-per-ip-per-day:20}") int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    /**
     * Increments the usage counter for the given IP. Returns true if the
     * caller is under the limit (and the call should proceed); false otherwise.
     */
    public boolean tryAcquire(String ip) {
        if (ip == null || ip.isBlank()) ip = "unknown";

        LocalDate today = LocalDate.now();
        Usage u = usage.computeIfAbsent(ip, k -> new Usage(today, 0));

        synchronized (u) {
            if (!u.date.equals(today)) {
                u.date = today;
                u.count = 0;
            }
            if (u.count >= dailyLimit) {
                log.warn("Rate limit hit for ip={} count={} limit={}", ip, u.count, dailyLimit);
                return false;
            }
            u.count++;
            return true;
        }
    }

    public int remaining(String ip) {
        Usage u = usage.get(ip);
        if (u == null || !u.date.equals(LocalDate.now())) return dailyLimit;
        return Math.max(0, dailyLimit - u.count);
    }

    private static final class Usage {
        LocalDate date;
        int count;
        Usage(LocalDate date, int count) {
            this.date = date;
            this.count = count;
        }
    }
}
