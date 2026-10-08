package com.trading.model.coin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Lossless source evidence and separately validated order values. Never log this object. */
public record CoinRow(long number, long lineStart, long lineEnd, long byteStart, long byteEnd,
        byte[] rawRecord, Map<String, String> fields, LocalDate tradeDate, LocalTime orderedAt,
        BigDecimal amount, BigDecimal units, BigDecimal nav) {
    public static final List<String> HEADERS = List.of("client_id", "isin", "scheme_name", "plan",
            "transaction_mode", "settlement_id", "trade_date", "ordered_at", "folio_number",
            "amount", "units", "nav", "status", "exchange_order_id", "remarks", "tag");
    public CoinRow {
        rawRecord = rawRecord.clone();
        fields = Map.copyOf(fields);
    }
    @Override public byte[] rawRecord() { return rawRecord.clone(); }
    public String field(String name) { return fields.get(name); }
    @Override public String toString() { return "CoinRow[number=" + number + "]"; }
}
