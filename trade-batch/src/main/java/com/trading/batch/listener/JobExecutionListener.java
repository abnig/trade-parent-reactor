package com.trading.batch.listener;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;

public class JobExecutionListener implements org.springframework.batch.core.listener.JobExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(JobExecutionListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("STARTING CSV BATCH PROCESSING JOB");
        logger.info("Job Name: {}", jobExecution.getJobInstance().getJobName());
        logger.info("Job Parameters: {}", jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime();
        Duration duration = Duration.between(startTime, endTime);

        logger.info("=".repeat(50));
        logger.info("BATCH PROCESSING COMPLETED");
        logger.info("=".repeat(50));
        logger.info("Job Status: {}", jobExecution.getStatus());
        logger.info("Start Time: {}", startTime);
        logger.info("End Time: {}", endTime);
        logger.info("Duration: {} seconds", duration.toSeconds());
        
        jobExecution.getStepExecutions().forEach(stepExecution -> {
            logger.info("Step: {} - Read: {}, Written: {}, Skipped: {}", 
                    stepExecution.getStepName(),
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getSkipCount());
        });
        
        if (!jobExecution.getAllFailureExceptions().isEmpty()) {
            logger.error("Job completed with errors:");
            jobExecution.getAllFailureExceptions().forEach(throwable -> 
                logger.error("Error: {}", throwable.getMessage()));
        }
        
        logger.info("=".repeat(50));
    }
}