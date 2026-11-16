package com.trading.coin.batch.listener;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;

public final class CoinCompletionListener implements JobExecutionListener {

	@Override
	public void afterJob(JobExecution execution) {
		// TODO: Implement logic to handle job completion, such as logging and sending to REST layer
	}
}
