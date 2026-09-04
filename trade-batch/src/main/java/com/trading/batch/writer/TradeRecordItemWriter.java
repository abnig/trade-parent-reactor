package com.trading.batch.writer;

import com.trading.model.TradeRecord;
import com.trading.repository.impl.TradeRecordRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TradeRecordItemWriter implements ItemWriter<TradeRecord> {

    private static final Logger logger = LoggerFactory.getLogger(TradeRecordItemWriter.class);

    @Autowired
    private TradeRecordRepository repository;

    @Override
    public void write(Chunk<? extends TradeRecord> chunk) throws Exception {
        logger.info("Writing batch of {} trade records to database", chunk.size());
        
        try {
            repository.saveAll(chunk.getItems());
            logger.info("Successfully saved {} trade records", chunk.size());
        } catch (Exception e) {
            logger.error("Error saving batch of trade records", e);
            throw e;
        }
    }
}