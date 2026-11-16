package com.trading.coin.batch.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

import com.trading.coin.batch.model.outbound.MutualFundTxnOutbound;

public class CoinRecordMutualFundTxnWriter implements ItemWriter<MutualFundTxnOutbound> {

	@Override
	public void write(Chunk<? extends MutualFundTxnOutbound> chunk) throws Exception {
		for (MutualFundTxnOutbound item : chunk.getItems()) {
			// Write each item to the destination (e.g., database, file, etc.)
			System.out.println("Writing MutualFundTxnOutbound: " + item);
		}

	}

}
