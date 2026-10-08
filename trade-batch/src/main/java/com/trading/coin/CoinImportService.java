package com.trading.coin;

import com.trading.model.coin.*;
import com.trading.repository.CoinImportRepository;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;

/** Synchronous completion, stable business identity; submitting a job is never called success. */
public final class CoinImportService {
    private final CoinImportRepository repository;
    private final JobOperator operator;
    private final Job job;
    public CoinImportService(CoinImportRepository repository, JobOperator operator, Job job) {
        this.repository = repository; this.operator = operator; this.job = job;
    }
    public CoinImportResult execute(CoinImportFile file, boolean preview) throws Exception {
        var claim = repository.prepare(file, preview);
        if (preview) return new CoinImportResult(claim.importId(), "DRY_RUN", file.rows().size(), 0, 0, 0, 0);
        if (claim.completed()) return repository.result(claim.importId());
        var parameters = new JobParametersBuilder()
                .addLong("ownerUserId", file.options().ownerId()).addLong("brokerAccountId", file.options().accountId())
                .addString("sourceSystem", "ZERODHA_COIN").addString("fileSha256", file.sha256())
                .addLong("importFileId", claim.importId(), false).addString("inputFile", file.managedPath().toString(), false)
                .toJobParameters();
        var execution = operator.start(job, parameters);
        if (execution.getStatus() != BatchStatus.COMPLETED) throw new IllegalStateException("COIN_JOB_FAILED execution=" + execution.getId());
        return repository.result(claim.importId());
    }
}
