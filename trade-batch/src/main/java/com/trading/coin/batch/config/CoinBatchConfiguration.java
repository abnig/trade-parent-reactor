package com.trading.coin.batch.config;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.support.JdbcTransactionManager;

import com.trading.coin.batch.model.inbound.CoinRecord;
import com.trading.coin.batch.reader.CoinRecordFileItemReader;
import com.trading.coin.batch.reader.mapper.CoinRecordFieldSetMapper;

// Register/import explicitly. Deliberately not a component: REST must not scan Batch into its root context.
@EnableBatchProcessing(transactionManagerRef = "coinTransactionManager")
@EnableJdbcJobRepository(dataSourceRef = "dataSource", transactionManagerRef = "coinTransactionManager")
@Configuration
public class CoinBatchConfiguration {
	@Bean
	public JdbcTransactionManager coinTransactionManager(DataSource source) {
		return new JdbcTransactionManager(source);
	}

	@Bean
	public SyncTaskExecutor coinTaskExecutor() {
		return new SyncTaskExecutor();
	}
	
	
	@Bean
	@StepScope
	public CoinRecordFileItemReader ledgerFileItemReader() {

		DelimitedLineTokenizer lineTokenizer = new DelimitedLineTokenizer();
		lineTokenizer.setDelimiter(",");
		lineTokenizer.setNames("clientId", "isin", "schemeName", "plan", "transactionMode", "settlementId", "tradeDate",
				"orderedAt", "folioNumber", "amount", "units", "nav", "status", "exchangeOrderId", "remarks",
				"tag");

		DefaultLineMapper<CoinRecord> lineMapper = new DefaultLineMapper<CoinRecord>();
		lineMapper.setLineTokenizer(lineTokenizer);
		lineMapper.setFieldSetMapper(new CoinRecordFieldSetMapper());

		CoinRecordFileItemReader coinRecordFileItemReader = new CoinRecordFileItemReader(lineMapper);

		return coinRecordFileItemReader;
	}


}
