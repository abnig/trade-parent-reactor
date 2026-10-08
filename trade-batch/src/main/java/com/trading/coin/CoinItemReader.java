package com.trading.coin;

import java.util.List;
import com.trading.model.coin.CoinRow;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.support.AbstractItemStreamItemReader;

/** Checkpoint is an ordinal, never private cells. Both steps revalidate the entire immutable file. */
public class CoinItemReader extends AbstractItemStreamItemReader<CoinRow> {
    private final List<CoinRow> rows;
    private int index;
    public CoinItemReader(String name, List<CoinRow> rows) { setName(name); this.rows = List.copyOf(rows); }
    @Override public void open(ExecutionContext context) {
        index = context.getInt(getExecutionContextKey("index"), 0);
        if (index < 0 || index > rows.size()) throw new IllegalArgumentException("INVALID_CHECKPOINT");
    }
    @Override public CoinRow read() { return index < rows.size() ? rows.get(index++) : null; }
    @Override public void update(ExecutionContext context) { context.putInt(getExecutionContextKey("index"), index); }
}
