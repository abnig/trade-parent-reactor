package com.trading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.trading.service.BatchJobService;

@SpringBootApplication
@EnableBatchProcessing
public class TradeBatchApplication implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(TradeBatchApplication.class);

	@Autowired
	private BatchJobService batchJobService;

	public static void main(String[] args) {
		SpringApplication.run(TradeBatchApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {

		try {
			batchJobService.runCsvProcessingJob();
		} catch (Exception e) {
			logger.error("Batch processing failed", e);
			System.exit(1);
		}
	}
}