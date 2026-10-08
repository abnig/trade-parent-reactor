package com.trading.upload;

import javax.sql.DataSource;
import com.trading.coin.CoinImportRuntime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoinUploadConfiguration {
    @Bean public CoinImportRuntime coinImportRuntime(DataSource source,
            @Value("${batch.coin.chunk-size:100}") int chunkSize) {
        return new CoinImportRuntime(source, chunkSize);
    }
}
