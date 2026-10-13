package com.trading.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;

import com.trading.TradeRestApplication;
import com.trading.coin.service.CoinImportRuntime;

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
                    .contains("tradeRestApplication", "coinUploadConfiguration", "coinImportRuntime",
                            "registrationService", "securityConfiguration", "ownedPortfolioConfiguration")
                    .doesNotContain("tradeBatchApplication", "ledgerBalancesBatchApplication",
                            "coinOrderHistoryBatchApplication", "batchConfiguration",
                            "ledgerBalancesBatchConfiguration", "batchJobService",
                            "ledgerBalancesBatchJobService", "csvProcessingJob", "ledgerCsvProcessingJob",
                            "coinOrderHistoryImportJob", "jobRepository", "jobOperator");
        }
    }

    @Test
    void workspaceWebContextStartsWithCoinBridgeAndNoRootBatchJobs() throws Exception {
        try (var loader = workspaceClassLoader()) {
            new WebApplicationContextRunner()
                    .withClassLoader(loader)
                    .withInitializer(context -> context.getBeanFactory()
                            .setConversionService(new ApplicationConversionService()))
                    .withUserConfiguration(TradeRestApplication.class)
                    .withPropertyValues(
                            "spring.datasource.url=jdbc:h2:mem:rest_workspace_startup;DB_CLOSE_DELAY=-1",
                            "spring.datasource.driver-class-name=org.h2.Driver",
                            "spring.datasource.username=sa", "spring.datasource.password=",
                            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                            "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false",
                            "app.upload.zerodha-transactions-directory=" + uploadDirectory.resolve("legacy"),
                            "app.upload.coin-import-directory=" + uploadDirectory.resolve("coin"))
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(CoinImportRuntime.class)
                                .doesNotHaveBean(Job.class).doesNotHaveBean(JobRepository.class)
                                .doesNotHaveBean(CommandLineRunner.class);
                        assertThat(context).hasBean("securityFilterChain")
                                .hasBean("zerodhaTransactionUploadService");
                    });
        }
    }
}
