package com.trading.coin;

import java.nio.file.Path;
import java.util.Map;
import javax.sql.DataSource;
import com.trading.model.coin.CoinImportOptions;
import com.trading.model.coin.CoinImportResult;
import com.trading.repository.CoinImportRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Embeds the same Coin job without component scanning, Boot auto-configuration or owning the caller's pool. */
public class CoinImportRuntime {
    private final DataSource source;
    private final int chunkSize;
    public CoinImportRuntime(DataSource source, int chunkSize) {
        if (chunkSize < 1) throw new IllegalArgumentException("Positive Coin chunk size required");
        this.source = source;
        this.chunkSize = chunkSize;
    }
    public CoinImportResult importBytes(byte[] bytes, CoinImportOptions options, Path workDirectory) throws Exception {
        var parsed = new CoinCsvReader(CoinCsvReader.Limits.defaults()).parse(bytes);
        var file = new CoinRecordValidator().validate(parsed, workDirectory.resolve("upload.csv"), options);
        try (var context = new AnnotationConfigApplicationContext()) {
            // A manually registered singleton is not destroyed when this private context closes.
            context.getBeanFactory().registerSingleton("dataSource", source);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("coin-runtime",
                    Map.of("batch.coin.chunk-size", chunkSize)));
            context.register(CoinBatchConfiguration.class);
            context.refresh();
            var repository = context.getBean(CoinImportRepository.class);
            repository.prepare(file, true);
            var managed = new CoinFileStore(workDirectory).store(file, bytes);
            return new CoinImportService(repository, context.getBean(JobOperator.class), context.getBean(Job.class))
                    .execute(managed, false);
        }
    }
}
