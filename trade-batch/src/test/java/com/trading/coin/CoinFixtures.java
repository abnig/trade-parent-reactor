package com.trading.coin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import com.trading.model.coin.*;

final class CoinFixtures {
    static final String HEADER = String.join(",", CoinRow.HEADERS);
    static Map<String, String> fields(String id, String status) {
        Map<String, String> f = new LinkedHashMap<>();
        String[] values = {"CLIENT000", "INFTEST00001", "Test Fund", "", "BUY", "0001", "01/10/2026", "12:00 AM",
                "000123/01", "100.00", "10.000000", "10.000000", status, id, "", "{\"label\":[\"example\"]}"};
        for (int i = 0; i < 16; i++) f.put(CoinRow.HEADERS.get(i), values[i]);
        if (status.equals("PROCESSING")) { f.put("units", "0"); f.put("nav", "0"); f.put("settlement_id", ""); }
        return f;
    }
    static String line(Map<String, String> fields) { return CoinRow.HEADERS.stream().map(fields::get).reduce((a,b) -> a + "," + b).orElseThrow(); }
    @SafeVarargs static byte[] csv(Map<String, String>... rows) {
        return (HEADER + "\r\n" + Arrays.stream(rows).map(CoinFixtures::line).reduce((a,b) -> a + "\r\n" + b).orElse("") + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
    static CoinImportOptions options() { return options(CoinImportOptions.PostingPolicy.ORDER_ONLY); }
    static CoinImportOptions options(CoinImportOptions.PostingPolicy policy) {
        return new CoinImportOptions(1, 1, LocalDate.of(2026,10,1), LocalDate.of(2026,10,31), "dd/MM/uuuu", policy, Map.of());
    }
    static CoinImportFile file(byte[] bytes, CoinImportOptions options) {
        var parsed = new CoinCsvReader(CoinCsvReader.Limits.defaults()).parse(bytes);
        return new CoinRecordValidator().validate(parsed, Path.of("/synthetic/input.csv"), options);
    }
}
