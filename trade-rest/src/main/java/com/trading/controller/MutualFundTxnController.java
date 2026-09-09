package com.trading.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.trading.model.result.FundInvestmentSummary;
import com.trading.dto.PagedResponse;
import com.trading.dto.MutualFundTxnDto;
import com.trading.model.MutualFundTxn;
import com.trading.model.result.TransactionSummary;
import com.trading.repository.MutualFundTxnRepository;
import com.trading.repository.PageRequest;
import com.trading.validation.MutualFundReferenceValidator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@RestController
@Validated
@RequestMapping("/api/mutual-fund-txns")
public class MutualFundTxnController {

    private final MutualFundTxnRepository repository;
    private final MutualFundReferenceValidator referenceValidator;

    public MutualFundTxnController(MutualFundTxnRepository repository,
                                   MutualFundReferenceValidator referenceValidator) {
        this.repository = repository;
        this.referenceValidator = referenceValidator;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<MutualFundTxnDto>> findAll(
            @RequestParam(defaultValue = "0") long page,
            @RequestParam(defaultValue = "20") long size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return ResponseEntity.ok(PagedResponse.of(repository.findAll(pageRequest).stream()
                .map(MutualFundTxnDto::from).toList(), pageRequest, repository.count()));
    }

    @GetMapping("/summary/by-fund")
    public ResponseEntity<List<FundInvestmentSummary>> getFundInvestments() {
        return ResponseEntity.ok(repository.getFundInvestments());
    }

    @GetMapping("/summary")
    public ResponseEntity<TransactionSummary> getSummary(
            @RequestParam(required = false) @PositiveOrZero(message = "Mutual fund ID must be zero or positive") Long mutualFundId) {
        TransactionSummary summary = mutualFundId == null || mutualFundId == 0
                ? repository.getSummary()
                : repository.getSummary(mutualFundId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MutualFundTxnDto> findById(
            @PathVariable @Positive(message = "ID must be positive") Long id) {

        return ResponseEntity.ok(MutualFundTxnDto.from(repository.findById(id)));
    }

    @GetMapping("/mutual-fund/{mutualFundId}")
    public ResponseEntity<PagedResponse<MutualFundTxnDto>> findByMutualFundId(
            @PathVariable @Positive(message = "Mutual fund ID must be positive") Long mutualFundId,
            @RequestParam(defaultValue = "0") long page,
            @RequestParam(defaultValue = "20") long size) {
        PageRequest pageRequest = PageRequest.of(page, size);

        return ResponseEntity.ok(PagedResponse.of(repository.findByMutualFundId(mutualFundId, pageRequest).stream()
                .map(MutualFundTxnDto::from).toList(),
                pageRequest, repository.countByMutualFundId(mutualFundId)));
    }

    @PostMapping
    public ResponseEntity<MutualFundTxnDto> create(
            @Valid @RequestBody MutualFundTxnDto request) {

        MutualFundTxn txn = request.toModel();

        referenceValidator.requireMutualFund(txn.getMutualFundId());

        MutualFundTxn savedTxn = repository.save(txn);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MutualFundTxnDto.from(savedTxn));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MutualFundTxnDto> update(
            @PathVariable @Positive(message = "ID must be positive") Long id,
            @Valid @RequestBody MutualFundTxnDto request) {

        MutualFundTxn txn = request.toModel();

        txn.setMutualFundTxnId(id);
        referenceValidator.requireMutualFund(txn.getMutualFundId());

        MutualFundTxn updatedTxn = repository.update(txn);

        return ResponseEntity.ok(MutualFundTxnDto.from(updatedTxn));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive(message = "ID must be positive") Long id) {

        if (!repository.deleteById(id)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}
