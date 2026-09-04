package com.trading.batch.config;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import com.trading.batch.listener.JobExecutionListener;
import com.trading.batch.processor.LedgerRecordItemProcessor;
import com.trading.batch.reader.LedgerFileItemReader;
import com.trading.batch.reader.mapper.LedgerRecordFieldSetMapper;
import com.trading.batch.writer.LedgerRecordItemWriter;
import com.trading.model.LedgerRecord;
import com.trading.repository.impl.LedgerRecordRepository;

@Configuration
public class LedgerBalancesBatchConfiguration {

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private LedgerRecordItemProcessor ledgerRecordItemProcessor;

	@Autowired
	private LedgerRecordItemWriter ledgerRecordItemWriter;

	@Autowired
	private LedgerRecordRepository ledgerRecordRepository;
	
	@Value("${batch.ledger-chunk-size:1000}")
	private int ledgerChunkSize;

	@Value("${batch.ledger-skip-limit:100}")
	private int ledgerSkipLimit;

	@Value("${batch.csv.ledger-input-file-path}")
	private String ledgerInputFilePath;

	@Value("${batch.ledger-truncate-flag:FALSE}")
	private String ledgerTruncateFlag;

	@Value("${batch.ledger-recursive-flag:FALSE}")
	private String ledgerRecursiveFlag;

	@Bean
	@Scope("prototype")
	public Boolean ledgerTruncateFlag() {
		return Boolean.valueOf(this.ledgerTruncateFlag);
	}

	@Bean(name = "ledgerTaskExecutor")
	public TaskExecutor ledgerTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

		// Core pool size - minimum threads always kept alive
		executor.setCorePoolSize(8);
		// Max pool size - maximum number of threads
		executor.setMaxPoolSize(10);
		// Queue capacity - how many tasks can wait
		executor.setQueueCapacity(25);
		// Thread name prefix
		executor.setThreadNamePrefix("MyApp-");
		// Rejected policy (DISCARD_OLDEST, CALLER_RUNS, ABORT, KILL)
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		return executor;
	}

	@Bean
	public TaskExecutorJobOperator ledgerTaskExecutorJobOperator(@Autowired TaskExecutor ledgerTaskExecutor) {
		TaskExecutorJobOperator operator = new TaskExecutorJobOperator();
		operator.setTaskExecutor(ledgerTaskExecutor);
		operator.setJobRepository(jobRepository);
		operator.setJobRegistry(new MapJobRegistry());
		return operator;
	}

	@Bean
	@StepScope
	public LedgerFileItemReader ledgerFileItemReader() {

		DelimitedLineTokenizer lineTokenizer = new DelimitedLineTokenizer();
		lineTokenizer.setDelimiter(",");
		lineTokenizer.setNames("particulars","posting_date","cost_center", "voucher_type", "debit", "credit", "net_balance");

		DefaultLineMapper<LedgerRecord> lineMapper = new DefaultLineMapper<LedgerRecord>();
		lineMapper.setLineTokenizer(lineTokenizer);
		lineMapper.setFieldSetMapper(new LedgerRecordFieldSetMapper());

		LedgerFileItemReader ledgerFileItemReader = new LedgerFileItemReader(lineMapper);

		return ledgerFileItemReader;
	}

	@Bean
	public Tasklet truncateLedgerTableTasklet() {
		return (contribution, chunkContext) -> {
			Map<String, Object> jobParams = chunkContext.getStepContext().getJobParameters();
			Boolean truncateFlag = (Boolean) jobParams.get("truncateFlag");
			if (!truncateFlag) {
				contribution.setExitStatus(org.springframework.batch.core.ExitStatus.COMPLETED);	
				return RepeatStatus.FINISHED;
			}
			else {
				contribution.setExitStatus(org.springframework.batch.core.ExitStatus.COMPLETED);
				this.ledgerRecordRepository.truncateLedgerRecordTable();
			}
			return RepeatStatus.FINISHED; // signals step completion
		};
	}
	
	@Bean
	public JobExecutionListener ledgerJobExecutionListener() {
		return new JobExecutionListener();
	}

	@Bean
	public Step deleteLedgerRecordsStep(Tasklet truncateLedgerTableTasklet) {
		return new StepBuilder("deleteLedgerRecordsStep", jobRepository).tasklet(truncateLedgerTableTasklet)
				.transactionManager(transactionManager).build();
	}

	@Bean
	public Step ledgerCsvProcessingStep() {
		return new StepBuilder("ledgerCsvProcessingStep", jobRepository).<LedgerRecord, LedgerRecord>chunk(ledgerChunkSize)
				.reader(ledgerFileItemReader()).processor(ledgerRecordItemProcessor).writer(ledgerRecordItemWriter).faultTolerant()
				.skipLimit(ledgerSkipLimit).transactionManager(transactionManager).skip(IllegalArgumentException.class)
				.skip(Exception.class).build();
	}

	@Bean
	public Job ledgerCsvProcessingJob(Step deleteLedgerRecordsStep, Step ledgerCsvProcessingStep, JobExecutionListener ledgerJobExecutionListener) {
		return new JobBuilder("ledgerCsvProcessingJob", jobRepository).listener(ledgerJobExecutionListener).start(deleteLedgerRecordsStep)
				.next(ledgerCsvProcessingStep).build();
	}

}