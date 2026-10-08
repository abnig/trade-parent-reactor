package com.trading.model.coin;

public record CoinImportResult(long importId, String status, long records, long inserted,
        long updated, long unchanged, long posted) { }
