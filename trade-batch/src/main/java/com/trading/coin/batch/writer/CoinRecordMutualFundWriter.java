package com.trading.coin.batch.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

import com.trading.coin.batch.model.outbound.MutualFundOutbound;

public class CoinRecordMutualFundWriter implements ItemWriter<MutualFundOutbound> {

	@Override
	public void write(Chunk<? extends MutualFundOutbound> chunk) throws Exception {
		// Implement the logic to write MutualFundOutbound items to the desired destination
		for (MutualFundOutbound item : chunk.getItems()) {
			// Example: Write to a database, file, or external service
			System.out.println("Writing MutualFundOutbound item: " + item);
		}
	}

}
