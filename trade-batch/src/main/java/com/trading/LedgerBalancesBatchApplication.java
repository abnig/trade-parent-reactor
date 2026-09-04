package com.trading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.trading.service.LedgerBalancesBatchJobService;

@SpringBootApplication
@EnableBatchProcessing
public class LedgerBalancesBatchApplication implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(LedgerBalancesBatchApplication.class);

	@Autowired
	@Qualifier("ledgerBalancesBatchJobService")
	private LedgerBalancesBatchJobService ledgerBalancesBatchJobService;

	public static void main(String[] args) {
		SpringApplication.run(LedgerBalancesBatchApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {

		try {
			ledgerBalancesBatchJobService.runCsvProcessingJob();
		} catch (Exception e) {
			logger.error("Batch processing failed", e);
			System.exit(1);
		}
	}
}