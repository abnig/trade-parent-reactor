package com.trading.coin.upload;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.trading.coin.service.CoinImportRuntime;

@Configuration
public class CoinUploadConfiguration {
	@Bean
	public CoinImportRuntime coinImportRuntime(DataSource source,
			@Value("${batch.coin.chunk-size:100}") int chunkSize) {
		return new CoinImportRuntime(source, chunkSize);
	}
}
