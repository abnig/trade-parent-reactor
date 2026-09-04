package com.trading.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LedgerBalancesBatchJobService {

	private static final Logger logger = LoggerFactory.getLogger(LedgerBalancesBatchJobService.class);

	@Autowired
	@Qualifier("ledgerTaskExecutorJobOperator")
	private TaskExecutorJobOperator ledgerTaskExecutorJobOperator;

	@Autowired
	@Qualifier("ledgerCsvProcessingJob")
	private Job ledgerCsvProcessingJob;
	
	@Value("${batch.csv.ledger-input-file-path}")
	private String ledgerInputFilePath;

	@Value("${batch.ledger-truncate-flag}")
	private String ledgerTruncateFlag;

	@Value("${batch.ledger-recursive-flag}")
	private String ledgerRecursiveFlag;

	
	public void runCsvProcessingJob() {
		try {
			List<Path> listTradeFiles  = listTradeFiles();
			for (Path path : listTradeFiles) {
				JobParameters jobParameters = jobParameters(Boolean.valueOf(ledgerTruncateFlag), path);
				ledgerTaskExecutorJobOperator.start(ledgerCsvProcessingJob, jobParameters);
			}

		} catch (Exception e) {
			logger.error("Error running CSV processing job", e);
			throw new RuntimeException("Failed to run CSV processing job", e);
		}
	}

	
	
	public JobParameters jobParameters(Boolean truncateFlag, Path path) {
		JobParametersBuilder jobPramBuilder = new JobParametersBuilder();
		jobPramBuilder.addJobParameter("truncateFlag", truncateFlag, Boolean.class);
		jobPramBuilder.addString("inputFile", path.toAbsolutePath().toString());
		jobPramBuilder.addLong("timestamp", System.currentTimeMillis());
		JobParameters jobParameters = jobPramBuilder.toJobParameters();
		return jobParameters;
	}
	

	public List<Path> listTradeFiles() {
	    Path dir = Path.of(ledgerInputFilePath);
	    
	    if (!Files.isDirectory(dir)) {
	        throw new IllegalArgumentException("Not a directory: " + ledgerInputFilePath);
	    }
	    
	    try {
	        if (Boolean.valueOf(ledgerRecursiveFlag)) {
	            return Files.walk(dir)
	                    .filter(Files::isRegularFile)
	                    .filter(path -> path.toString().endsWith(".csv"))
	                    .toList();
	        } else {
	            return Files.list(dir)
	                    .filter(Files::isRegularFile)
	                    .filter(path -> path.toString().endsWith(".csv"))
	                    .collect(Collectors.toList());
	        }
	    } catch (Exception e) {
	        throw new RuntimeException("Error listing files", e);
	    }

	}

}