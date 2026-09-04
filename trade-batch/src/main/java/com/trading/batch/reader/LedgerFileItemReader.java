package com.trading.batch.reader;

import java.io.File;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.core.io.FileSystemResource;

import com.trading.model.LedgerRecord;

public class LedgerFileItemReader extends FlatFileItemReader<LedgerRecord> implements StepExecutionListener {

	private static final Logger logger = LoggerFactory.getLogger(LedgerFileItemReader.class);

	@SuppressWarnings("unused")
	private LineMapper<LedgerRecord> ledgerLineMapper;

	public LedgerFileItemReader(LineMapper<LedgerRecord> ledgerLineMapper) {
		super(ledgerLineMapper);
	}

	@Override
	public void beforeStep(StepExecution stepExecution) {
		JobParameters jobParameters = stepExecution.getJobParameters();

		String inputFile = jobParameters.getString("inputFile");
		if (inputFile == null || inputFile.isEmpty()) {
			throw new IllegalArgumentException("Missing required job parameter: inputFile");
		}

		File file = new File(inputFile);
		if (!file.exists()) {
			throw new IllegalStateException("Input file does not exist: " + inputFile);
		}
		logger.info("Processing file: {}", inputFile);

		configureReader(inputFile);
	}

	@Override
	public @Nullable ExitStatus afterStep(StepExecution stepExecution) {
		logger.info("=== AFTER STEP ===");
		if (stepExecution.getReadCount() > 0) {
			logger.info("Successfully processed {} records", stepExecution.getReadCount());
		}
		return null;
	}

	// Private method to configure the reader
	private void configureReader(String inputFile) {
		try {
			setResource(new FileSystemResource(inputFile));
			logger.info("Reader configured successfully");
		} catch (Exception e) {
			logger.error("Error configuring reader: {}", e.getMessage(), e);
			throw new RuntimeException("Failed to configure item reader", e);
		}
	}
}
