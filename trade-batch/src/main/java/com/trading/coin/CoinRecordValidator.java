package com.trading.coin;

import java.math.*;
import java.nio.file.Path;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.function.Supplier;
import com.trading.model.coin.*;
import com.trading.model.coin.CoinValidationException.Problem;

/** Visits all recoverable records and fields. An invalid file is never partially imported. */
public final class CoinRecordValidator {
    public CoinImportFile validate(CoinCsvReader.Parsed parsed, Path source, CoinImportOptions options) {
        var header = parsed.records().getFirst();
        if (!header.problems().isEmpty() || header.values().size() != CoinRow.HEADERS.size()
                || !new HashSet<>(header.values()).equals(new HashSet<>(CoinRow.HEADERS)))
            throw new CoinValidationException(List.of(new Problem(0, "header", "INVALID_HEADER")));
        List<Problem> problems = new ArrayList<>();
        List<CoinRow> rows = new ArrayList<>();
        Map<String, Long> references = new HashMap<>();
        var dates = DateTimeFormatter.ofPattern(options.dateFormat(), Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
        var times = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
        for (var record : parsed.records().subList(1, parsed.records().size())) {
            long n = record.number();
            problems.addAll(record.problems());
            if (record.values().size() != 16) { problems.add(new Problem(n, "csv", "FIELD_COUNT")); continue; }
            Map<String, String> fields = new LinkedHashMap<>();
            for (int i = 0; i < 16; i++) fields.put(header.values().get(i), record.values().get(i));
            for (String required : List.of("client_id", "scheme_name", "exchange_order_id"))
                if (fields.get(required).isBlank()) problems.add(new Problem(n, required, "REQUIRED"));
            String id = fields.get("exchange_order_id");
            Long prior = references.putIfAbsent(id, n);
            if (prior != null) problems.add(new Problem(n, "exchange_order_id", "AMBIGUOUS_DUPLICATE_REFERENCE"));
            LocalDate date = convert(n, "trade_date", problems, () -> LocalDate.parse(fields.get("trade_date"), dates));
            if (date != null && (date.isBefore(options.periodStart()) || date.isAfter(options.periodEnd())))
                problems.add(new Problem(n, "trade_date", "OUTSIDE_PERIOD"));
            LocalTime time = fields.get("ordered_at").isEmpty() ? null
                    : convert(n, "ordered_at", problems, () -> LocalTime.parse(fields.get("ordered_at"), times));
            BigDecimal amount = decimal(n, "amount", fields, 18, 2, problems);
            BigDecimal units = decimal(n, "units", fields, 21, 6, problems);
            BigDecimal nav = decimal(n, "nav", fields, 21, 6, problems);
            if (fields.get("status").equals("COMPLETE") && options.postingPolicy() != CoinImportOptions.PostingPolicy.ORDER_ONLY) {
                if (!Set.of("BUY", "SELL").contains(fields.get("transaction_mode")))
                    problems.add(new Problem(n, "transaction_mode", "POSTING_DIRECTION"));
                if (amount == null || amount.signum() <= 0) problems.add(new Problem(n, "amount", "COMPLETE_REQUIRES_POSITIVE"));
                if (units == null || units.signum() <= 0) problems.add(new Problem(n, "units", "COMPLETE_REQUIRES_POSITIVE"));
                if (nav == null || nav.signum() <= 0) problems.add(new Problem(n, "nav", "COMPLETE_REQUIRES_POSITIVE"));
                if (options.postingPolicy() == CoinImportOptions.PostingPolicy.TRADE_DATE_ORDER_TIME && time == null)
                    problems.add(new Problem(n, "ordered_at", "POSTING_TIME_REQUIRED"));
            }
            rows.add(new CoinRow(n, record.lineStart(), record.lineEnd(), record.start(), record.end(),
                    record.raw(), fields, date, time, amount, units, nav));
        }
        if (rows.isEmpty()) problems.add(new Problem(0, "csv", "NO_DATA_RECORDS"));
        for (Long n : options.fundOverrides().keySet())
            if (n > parsed.records().size() - 1) problems.add(new Problem(n, "fundMapping", "UNKNOWN_RECORD"));
        if (!problems.isEmpty()) throw new CoinValidationException(problems);
        return new CoinImportFile(source, parsed.sha256(), parsed.bytes().length, header.raw(), rows, options);
    }
    private static BigDecimal decimal(long n, String field, Map<String, String> fields, int precision, int scale, List<Problem> errors) {
        String value = fields.get(field);
        if (value.isEmpty()) return null;
        return convert(n, field, errors, () -> {
            if (!value.matches("[+]?[0-9]+(?:\\.[0-9]+)?")) throw new IllegalArgumentException();
            BigDecimal result = new BigDecimal(value).setScale(scale, RoundingMode.UNNECESSARY);
            if (result.precision() > precision) throw new IllegalArgumentException();
            return result;
        });
    }
    private static <T> T convert(long n, String field, List<Problem> errors, Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException | DateTimeException | ArithmeticException e) {
            errors.add(new Problem(n, field, "INVALID_VALUE")); return null;
        }
    }
}
