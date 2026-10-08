package com.trading.coin;

import com.trading.repository.CoinImportRepository;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;

public final class CoinCompletionListener implements JobExecutionListener {
    private final CoinImportRepository repository;
    public CoinCompletionListener(CoinImportRepository repository) { this.repository = repository; }
    @Override public void afterJob(JobExecution execution) {
        if (execution.getStatus() != BatchStatus.COMPLETED)
            repository.failed(execution.getJobParameters().getLong("importFileId"));
    }
}
