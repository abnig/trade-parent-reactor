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
import com.trading.batch.processor.TradeRecordItemProcessor;
import com.trading.batch.reader.TradeFileItemReader;
import com.trading.batch.reader.mapper.TradeFieldSetMapper;
import com.trading.batch.writer.TradeRecordItemWriter;
import com.trading.model.TradeRecord;
import com.trading.repository.impl.TradeRecordRepository;

@Configuration
public class BatchConfiguration {

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private TradeRecordItemProcessor itemProcessor;

	@Autowired
	private TradeRecordItemWriter itemWriter;

	@Autowired
	private JobExecutionListener jobExecutionListener;

	@Autowired
	private TradeRecordRepository repository;

	@Value("${batch.chunk-size:1000}")
	private int chunkSize;

	@Value("${batch.skip-limit:100}")
	private int skipLimit;

	@Value("${batch.csv.input-file-path}")
	private String inputFilePath;

	@Value("${batch.truncate-flag}")
	private String truncateFlag;

	@Value("${batch.recursive-flag}")
	private String recursiveFlag;

	@Bean
	@Scope("prototype")
	public Boolean truncateFlag() {
		return Boolean.valueOf(this.truncateFlag);
	}

	@Bean(name = "taskExecutor")
	public TaskExecutor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

		// Core pool size - minimum threads always kept alive
		executor.setCorePoolSize(5);
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
	public TaskExecutorJobOperator taskExecutorJobOperator(@Autowired TaskExecutor taskExecutor) {
		TaskExecutorJobOperator operator = new TaskExecutorJobOperator();
		operator.setTaskExecutor(taskExecutor);
		operator.setJobRepository(jobRepository);
		operator.setJobRegistry(new MapJobRegistry());
		return operator;
	}

	@Bean
	@StepScope
	public TradeFileItemReader tradeFileItemReader() {

		DelimitedLineTokenizer lineTokenizer = new DelimitedLineTokenizer();
		lineTokenizer.setDelimiter(",");
		lineTokenizer.setNames("symbol", "isin", "trade_date", "exchange", "segment", "series", "trade_type", "auction",
				"quantity", "price", "trade_id", "order_id", "order_execution_time");

		DefaultLineMapper<TradeRecord> lineMapper = new DefaultLineMapper<TradeRecord>();
		lineMapper.setLineTokenizer(lineTokenizer);
		lineMapper.setFieldSetMapper(new TradeFieldSetMapper());

		TradeFileItemReader tradeFileItemReader = new TradeFileItemReader(lineMapper);

		return tradeFileItemReader;
	}

	@SuppressWarnings("unused")
	@Bean
	public Tasklet truncateTradeTableTasklet() {
		return (contribution, chunkContext) -> {
			Map<String, Object> jobParams = chunkContext.getStepContext().getJobParameters();
			Boolean truncateFlag = (Boolean) jobParams.get("truncateFlag");
			if (!truncateFlag)
				return RepeatStatus.FINISHED;
			else
				this.repository.truncateTradesTable();
			return RepeatStatus.FINISHED; // signals step completion
		};
	}

	@Bean
	public Step deleteRecordsStep(Tasklet truncateTradeTableTasklet) {
		return new StepBuilder("deleteRecordsStep", jobRepository).tasklet(truncateTradeTableTasklet)
				.transactionManager(transactionManager).build();
	}

	@Bean
	public Step csvProcessingStep() {
		return new StepBuilder("csvProcessingStep", jobRepository).<TradeRecord, TradeRecord>chunk(chunkSize)
				.reader(tradeFileItemReader()).processor(itemProcessor).writer(itemWriter).faultTolerant()
				.skipLimit(skipLimit).transactionManager(transactionManager).skip(IllegalArgumentException.class)
				.skip(Exception.class).build();
	}

	@Bean
	public Job csvProcessingJob(Step deleteRecordsStep, Step csvProcessingStep) {
		return new JobBuilder("csvProcessingJob", jobRepository).listener(jobExecutionListener).start(deleteRecordsStep)
				.next(csvProcessingStep).build();
	}

}