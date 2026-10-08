package com.trading.repository;

import java.util.List;
import java.util.Map;
import com.trading.model.coin.*;

public interface CoinImportRepository {
    record Claim(long importId, boolean completed, Map<Long, Long> funds) {
        public Claim { funds = Map.copyOf(funds); }
    }
    /** Checks every record and all proposed DB effects. No writes when preview=true. */
    Claim prepare(CoinImportFile file, boolean preview);
    CoinImportOptions options(long importId, long ownerId, long accountId);
    void stage(long importId, CoinImportFile file, List<? extends CoinRow> rows);
    void project(long importId, CoinImportFile file, List<? extends CoinRow> rows);
    void complete(long importId, CoinImportFile file);
    void failed(long importId);
    CoinImportResult result(long importId);
}
