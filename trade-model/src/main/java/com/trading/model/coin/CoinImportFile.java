package com.trading.model.coin;

import java.nio.file.Path;
import java.util.List;

public record CoinImportFile(Path managedPath, String sha256, long byteCount, byte[] rawHeader,
        List<CoinRow> rows, CoinImportOptions options) {
    public CoinImportFile {
        rawHeader = rawHeader.clone();
        rows = List.copyOf(rows);
    }
    @Override public byte[] rawHeader() { return rawHeader.clone(); }
    @Override public String toString() { return "CoinImportFile[records=" + rows.size() + "]"; }
}
