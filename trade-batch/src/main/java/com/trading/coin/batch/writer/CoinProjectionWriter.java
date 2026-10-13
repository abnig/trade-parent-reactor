package com.trading.coin.batch.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

import com.trading.coin.service.CoinBatchFailure;
import com.trading.coin.service.CoinJobInput;
import com.trading.model.coin.CoinRow;
import com.trading.repository.CoinImportRepository;

public final class CoinProjectionWriter implements ItemWriter<CoinRow> {
    private final CoinImportRepository repository;
    private final CoinJobInput input;
    public CoinProjectionWriter(CoinImportRepository repository, CoinJobInput input) { this.repository = repository; this.input = input; }
    @Override public void write(Chunk<? extends CoinRow> chunk) {
        try { repository.project(input.importId(), input.file(), chunk.getItems()); }
        catch (RuntimeException e) { throw CoinBatchFailure.sanitized("PROJECTION", e); }
    }
}
