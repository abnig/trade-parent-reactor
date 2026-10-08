package com.trading.coin;

import java.io.IOException;
import java.nio.file.Path;
import com.trading.model.coin.*;
import com.trading.repository.CoinImportRepository;

/** Job-scoped reload validates managed bytes and the frozen policy in every new JVM. */
public class CoinJobInput {
    private final long importId;
    private final CoinImportFile file;
    public CoinJobInput(long importId, long owner, long account, String path, String hash,
            CoinImportRepository repository, CoinCsvReader reader, CoinRecordValidator validator) throws IOException {
        this.importId = importId;
        var parsed = reader.read(Path.of(path));
        if (!parsed.sha256().equals(hash)) throw new IllegalArgumentException("RESTART_INPUT_CHANGED");
        file = validator.validate(parsed, Path.of(path), repository.options(importId, owner, account));
    }
    public long importId() { return importId; }
    public CoinImportFile file() { return file; }
}
