package com.trading.coin;

import com.trading.model.coin.CoinRow;
import com.trading.repository.CoinImportRepository;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

public final class CoinProjectionWriter implements ItemWriter<CoinRow> {
    private final CoinImportRepository repository;
    private final CoinJobInput input;
    public CoinProjectionWriter(CoinImportRepository repository, CoinJobInput input) { this.repository = repository; this.input = input; }
    @Override public void write(Chunk<? extends CoinRow> chunk) {
        try { repository.project(input.importId(), input.file(), chunk.getItems()); }
        catch (RuntimeException e) { throw CoinBatchFailure.sanitized("PROJECTION", e); }
    }
}
