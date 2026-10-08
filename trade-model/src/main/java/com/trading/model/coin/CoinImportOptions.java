package com.trading.model.coin;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/** Explicit business policy, persisted unchanged for a file's entire restart lifetime. */
public record CoinImportOptions(long ownerId, long accountId, LocalDate periodStart, LocalDate periodEnd,
        String dateFormat, PostingPolicy postingPolicy, Map<Long, Long> fundOverrides) {
    public enum PostingPolicy { ORDER_ONLY, TRADE_DATE_MIDNIGHT, TRADE_DATE_ORDER_TIME }
    public CoinImportOptions {
        if (ownerId <= 0 || accountId <= 0) throw new IllegalArgumentException("Valid owner/account required");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        if (periodStart.isAfter(periodEnd) || periodStart.getYear() < 1 || periodEnd.getYear() > 9999)
            throw new IllegalArgumentException("Invalid period bounds");
        if (!ListFormats.SUPPORTED.contains(dateFormat)) throw new IllegalArgumentException("Explicit supported date format required");
        Objects.requireNonNull(postingPolicy, "postingPolicy");
        fundOverrides = Map.copyOf(fundOverrides);
        if (fundOverrides.entrySet().stream().anyMatch(e -> e.getKey() <= 0 || e.getValue() <= 0))
            throw new IllegalArgumentException("Invalid fund mapping");
    }
    private static final class ListFormats {
        static final java.util.Set<String> SUPPORTED = java.util.Set.of("dd/MM/uuuu", "MM/dd/uuuu", "uuuu-MM-dd");
    }
}
