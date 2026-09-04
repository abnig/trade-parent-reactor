package com.trading.batch.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.trading.model.LedgerRecord;
import com.trading.repository.impl.LedgerRecordRepository;

@Component
public class LedgerRecordItemWriter implements ItemWriter<LedgerRecord> {

	private static final Logger logger = LoggerFactory.getLogger(LedgerRecordItemWriter.class);

	@Autowired
	private LedgerRecordRepository ledgerRecordRepository;

	@Override
	public void write(Chunk<? extends LedgerRecord> chunk) throws Exception {
		logger.info("Writing batch of {} trade records to database", chunk.size());

		try {
			ledgerRecordRepository.saveAll(chunk.getItems());
			logger.info("Successfully saved {} trade records", chunk.size());
		} catch (Exception e) {
			logger.error("Error saving batch of trade records", e);
			throw e;
		}
	}
}