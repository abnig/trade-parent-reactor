package com.trading.batch.processor;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.trading.model.LedgerRecord;

@Component
@StepScope
public class LedgerRecordItemProcessor implements ItemProcessor<LedgerRecord, LedgerRecord>, StepExecutionListener {

    private JobParameters jobParameters;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.jobParameters = stepExecution.getJobParameters();
    }

	private static final Logger logger = LoggerFactory.getLogger(LedgerRecordItemProcessor.class);

    @Override
    public LedgerRecord process(LedgerRecord in) throws Exception {
    	in.setFileName(jobParameters.getString("inputFile"));
    	in.setCreateDateTime(LocalDateTime.now());
    	logger.debug("processed trade id: " + in.toString());
        return in;
    }


}