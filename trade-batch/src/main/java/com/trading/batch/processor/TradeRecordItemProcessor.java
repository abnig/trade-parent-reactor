package com.trading.batch.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.trading.model.TradeRecord;

@Component
public class TradeRecordItemProcessor implements org.springframework.batch.infrastructure.item.ItemProcessor<TradeRecord, TradeRecord> {

    private static final Logger logger = LoggerFactory.getLogger(TradeRecordItemProcessor.class);

    @Override
    public TradeRecord process(TradeRecord in) throws Exception {
    	logger.debug("processed trade id: " + in.toString());
        return in;
    }


}