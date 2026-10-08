package com.trading.coin;

import com.trading.model.coin.CoinRow;
import com.trading.repository.CoinImportRepository;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

public final class CoinStageWriter implements ItemWriter<CoinRow> {
    private final CoinImportRepository repository;
    private final CoinJobInput input;
    public CoinStageWriter(CoinImportRepository repository, CoinJobInput input) { this.repository = repository; this.input = input; }
    @Override public void write(Chunk<? extends CoinRow> chunk) {
        try { repository.stage(input.importId(), input.file(), chunk.getItems()); }
        catch (RuntimeException e) { throw CoinBatchFailure.sanitized("STAGING", e); }
    }
}
