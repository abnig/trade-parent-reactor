package com.trading.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;

import com.trading.TradeRestApplication;

class RestWorkspaceStartupTest {
    @TempDir Path uploadDirectory;

    private URLClassLoader workspaceClassLoader() throws Exception {
        // m2e resolves the coin-library classifier to the whole workspace project.
        Path restClasses = Path.of(TradeRestApplication.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path batchClasses = restClasses.resolve("../../../trade-batch/target/classes").normalize();
        assertThat(Files.isRegularFile(batchClasses.resolve("com/trading/kite/TradeBatchApplication.class")))
                .as("Build with -pl trade-rest -am so the full batch workspace output exists")
                .isTrue();
        return new URLClassLoader(new java.net.URL[]{batchClasses.toUri().toURL()},
                TradeRestApplication.class.getClassLoader());
    }

    @Test
    void workspaceScanExcludesStandaloneLaunchersAndBatchInfrastructure() throws Exception {
        try (var loader = workspaceClassLoader(); var context = new AnnotationConfigApplicationContext()) {
            context.setClassLoader(loader);
            context.register(TradeRestApplication.class);
            var processor = new ConfigurationClassPostProcessor();
            processor.setEnvironment(context.getEnvironment());
            processor.setResourceLoader(context);
            processor.setBeanClassLoader(loader);
            // Parse the real configuration graph without instantiating any runner or datasource.
            processor.postProcessBeanDefinitionRegistry(context.getDefaultListableBeanFactory());

            assertThat(Arrays.asList(context.getBeanDefinitionNames()))
                    .contains("tradeRestApplication", "registrationService", "securityConfiguration",
                            "ownedPortfolioConfiguration", "ownedBrokers", "ownedFunds",
                            "ownedTransactions", "ownedValues", "zerodhaTransactionUploadService")
                    .doesNotContain("tradeBatchApplication", "ledgerBalancesBatchApplication",
                            "coinOrderHistoryBatchApplication", "batchConfiguration",
                            "ledgerBalancesBatchConfiguration", "batchJobService",
                            "ledgerBalancesBatchJobService", "csvProcessingJob", "ledgerCsvProcessingJob",
                            "coinBatchConfiguration", "coinOrderHistoryImportJob", "coinTransactionManager",
                            "coinTaskExecutor", "ledgerFileItemReader", "jobRepository", "jobOperator");
        }
    }
}
