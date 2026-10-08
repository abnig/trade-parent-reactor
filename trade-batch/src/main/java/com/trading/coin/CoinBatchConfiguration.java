package com.trading.coin;

import javax.sql.DataSource;
import java.io.IOException;
import com.trading.model.coin.CoinRow;
import com.trading.repository.CoinImportRepository;
import com.trading.repository.impl.JdbcCoinImportRepository;
import org.springframework.batch.core.configuration.annotation.*;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

// Register/import explicitly. Deliberately not a component: REST must not scan Batch into its root context.
@EnableBatchProcessing(transactionManagerRef = "coinTransactionManager")
@EnableJdbcJobRepository(dataSourceRef = "dataSource", transactionManagerRef = "coinTransactionManager")
public class CoinBatchConfiguration {
    @Bean public JdbcTransactionManager coinTransactionManager(DataSource source) { return new JdbcTransactionManager(source); }
    @Bean public SyncTaskExecutor taskExecutor() { return new SyncTaskExecutor(); }
    @Bean public CoinImportRepository coinRepository(DataSource source, @Qualifier("coinTransactionManager") JdbcTransactionManager manager) {
        return new JdbcCoinImportRepository(new JdbcTemplate(source), manager);
    }
    @Bean public CoinCsvReader coinCsvReader() { return new CoinCsvReader(CoinCsvReader.Limits.defaults()); }
    @Bean public CoinRecordValidator coinRecordValidator() { return new CoinRecordValidator(); }
    @Bean @JobScope public CoinJobInput coinJobInput(
            @Value("#{jobParameters['importFileId']}") Long id,
            @Value("#{jobParameters['ownerUserId']}") Long owner,
            @Value("#{jobParameters['brokerAccountId']}") Long account,
            @Value("#{jobParameters['inputFile']}") String path,
            @Value("#{jobParameters['fileSha256']}") String hash,
            CoinImportRepository repository, CoinCsvReader reader, CoinRecordValidator validator) throws IOException {
        return new CoinJobInput(id, owner, account, path, hash, repository, reader, validator);
    }
    @Bean @StepScope public CoinItemReader coinStageReader(CoinJobInput input) {
        return new CoinItemReader("coinStageReader", input.file().rows());
    }
    @Bean @StepScope public CoinItemReader coinProjectionReader(CoinJobInput input) {
        return new CoinItemReader("coinProjectionReader", input.file().rows());
    }
    @Bean public CoinStageWriter coinStageWriter(CoinImportRepository repository, CoinJobInput input) { return new CoinStageWriter(repository, input); }
    @Bean public CoinProjectionWriter coinProjectionWriter(CoinImportRepository repository, CoinJobInput input) { return new CoinProjectionWriter(repository, input); }
    @Bean public CoinCompletionListener coinCompletionListener(CoinImportRepository repository) { return new CoinCompletionListener(repository); }
    @Bean public Step coinStageRowsStep(JobRepository repository, @Qualifier("coinTransactionManager") JdbcTransactionManager manager,
            @Qualifier("coinStageReader") CoinItemReader reader, CoinStageWriter writer, @Value("${batch.coin.chunk-size:100}") int chunk) {
        return new StepBuilder("coinStageRowsStep", repository).<CoinRow, CoinRow>chunk(chunk)
                .reader(reader).writer(writer).transactionManager(manager).build();
    }
    @Bean public Step coinProjectOrdersStep(JobRepository repository, @Qualifier("coinTransactionManager") JdbcTransactionManager manager,
            @Qualifier("coinProjectionReader") CoinItemReader reader, CoinProjectionWriter writer, @Value("${batch.coin.chunk-size:100}") int chunk) {
        return new StepBuilder("coinProjectOrdersStep", repository).<CoinRow, CoinRow>chunk(chunk)
                .reader(reader).writer(writer).transactionManager(manager).build();
    }
    @Bean public Step coinFinalizeStep(JobRepository repository, @Qualifier("coinTransactionManager") JdbcTransactionManager manager,
            CoinImportRepository imports, CoinJobInput input) {
        return new StepBuilder("coinFinalizeStep", repository).tasklet((contribution, context) -> {
            imports.complete(input.importId(), input.file()); return RepeatStatus.FINISHED;
        }).transactionManager(manager).build();
    }
    @Bean public Job coinOrderHistoryImportJob(JobRepository repository, CoinCompletionListener listener,
            @Qualifier("coinStageRowsStep") Step stage, @Qualifier("coinProjectOrdersStep") Step project,
            @Qualifier("coinFinalizeStep") Step finish) {
        return new JobBuilder("coinOrderHistoryImportJob", repository).listener(listener).start(stage).next(project).next(finish).build();
    }
}
