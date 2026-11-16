package com.trading.coin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoinComponentTest {
    @TempDir Path directory;
    @org.junit.jupiter.api.BeforeEach void canonicalDirectory() throws Exception { directory = directory.toRealPath(); }
    static List<String> arguments(Path input) {
        return new ArrayList<>(List.of("--mode=VALIDATE", "--input-file=" + input, "--owner-id=1", "--account-id=1",
                "--period-start=2026-10-01", "--period-end=2026-10-31", "--date-format=dd/MM/uuuu", "--posting-policy=ORDER_ONLY"));
    }


  
    @Test void legacyApplicationConfigurationDoesNotDiscoverCoinBeans() {
        for (Class<?> application : List.of(com.trading.kite.TradeBatchApplication.class, com.trading.kite.LedgerBalancesBatchApplication.class)) {
            try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
                context.register(application);
                // Resolve the actual scan/import graph without instantiating or executing legacy runners.
                var processor = new org.springframework.context.annotation.ConfigurationClassPostProcessor();
                processor.setEnvironment(context.getEnvironment());
                processor.postProcessBeanDefinitionRegistry(context.getDefaultListableBeanFactory());
                assertTrue(context.containsBeanDefinition("batchConfiguration"));
                assertTrue(context.containsBeanDefinition("ledgerBalancesBatchConfiguration"));
                assertFalse(context.containsBeanDefinition("coinBatchConfiguration"));
                assertFalse(context.containsBeanDefinition("coinOrderHistoryBatchApplication"));
                assertFalse(context.containsBeanDefinition("coinOrderHistoryImportJob"));
            }
        }
    }
}
