package com.trading.coin;

import java.util.*;
import com.trading.model.coin.*;
import com.trading.repository.CoinImportRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
@Import(CoinBatchConfiguration.class)
public class CoinOrderHistoryBatchApplication {
    public static void main(String[] args) { System.exit(run(args)); }
    public static int run(String... args) {
        try {
            var command = CoinCommandLine.parse(args);
            var reader = new CoinCsvReader(CoinCsvReader.Limits.defaults());
            var validator = new CoinRecordValidator();
            List<CoinImportFile> validated = new ArrayList<>();
            List<byte[]> bytes = new ArrayList<>();
            boolean invalid = false;
            // Validate every supplied file before any database/context is opened.
            for (int i = 0; i < command.inputs().size(); i++) {
                try {
                    var parsed = reader.read(command.inputs().get(i));
                    validated.add(validator.validate(parsed, command.inputs().get(i), command.options()));
                    bytes.add(parsed.bytes());
                } catch (CoinValidationException e) {
                    invalid = true;
                    for (var issue : e.problems()) System.err.printf("file=%d record=%d field=%s code=%s%n", i + 1, issue.record(), issue.field(), issue.code());
                } catch (java.io.IOException e) {
                    invalid = true;
                    System.err.printf("file=%d record=0 field=csv code=INPUT_UNREADABLE%n", i + 1);
                }
            }
            if (invalid) return 1;
            if (command.mode().equals("VALIDATE")) {
                System.out.println("VALIDATED files=" + validated.size() + " records=" + validated.stream().mapToLong(f -> f.rows().size()).sum());
                return 0;
            }
            SpringApplication application = new SpringApplication(CoinOrderHistoryBatchApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setDefaultProperties(Map.of("spring.config.name", "coin"));
            try (var context = application.run(args)) {
                var repository = context.getBean(CoinImportRepository.class);
                var service = new CoinImportService(repository, context.getBean(JobOperator.class), context.getBean(Job.class));
                // Validate DB effects for every input before reserving any file or starting a job.
                boolean rejected = false;
                for (int i = 0; i < validated.size(); i++) {
                    try { repository.prepare(validated.get(i), true); }
                    catch (CoinValidationException e) {
                        rejected = true;
                        for (var issue : e.problems()) System.err.printf("file=%d record=%d field=%s code=%s%n", i + 1, issue.record(), issue.field(), issue.code());
                    }
                }
                if (rejected) return 1;
                if (command.mode().equals("DRY_RUN")) {
                    for (var file : validated) System.out.println(service.execute(file, true));
                } else {
                    var store = new CoinFileStore(command.workDirectory());
                    for (int i = 0; i < validated.size(); i++) System.out.println(service.execute(store.store(validated.get(i), bytes.get(i)), false));
                }
            }
            return 0;
        } catch (CoinValidationException e) {
            e.problems().forEach(p -> System.err.println("record=" + p.record() + " field=" + p.field() + " code=" + p.code()));
            return 1;
        } catch (Exception e) {
            // JDBC exceptions can include values; never print their messages/stack traces to the CLI.
            System.err.println("Coin import failed (" + e.getClass().getSimpleName() + "). Review validation codes and job status.");
            return 1;
        }
    }
}
