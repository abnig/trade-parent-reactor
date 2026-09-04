package com.trading.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.trading.model.MutualFundTxn;
import com.trading.repository.MutualFundTxnRepository;
import com.trading.validation.MutualFundReferenceValidator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

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
    public ResponseEntity<List<MutualFundTxn>> findAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MutualFundTxn> findById(
            @PathVariable @Positive(message = "ID must be positive") Long id) {

        return ResponseEntity.ok(repository.findById(id));
    }

    @GetMapping("/mutual-fund/{mutualFundId}")
    public ResponseEntity<List<MutualFundTxn>> findByMutualFundId(
            @PathVariable @Positive(message = "Mutual fund ID must be positive") Long mutualFundId) {

        return ResponseEntity.ok(
                repository.findByMutualFundId(mutualFundId)
        );
    }

    @PostMapping
    public ResponseEntity<MutualFundTxn> create(
            @Valid @RequestBody MutualFundTxn txn) {

        referenceValidator.requireMutualFund(txn.getMutualFundId());

        MutualFundTxn savedTxn = repository.save(txn);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(savedTxn);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MutualFundTxn> update(
            @PathVariable @Positive(message = "ID must be positive") Long id,
            @Valid @RequestBody MutualFundTxn txn) {

        txn.setMutualFundTxnId(id);
        referenceValidator.requireMutualFund(txn.getMutualFundId());

        MutualFundTxn updatedTxn = repository.update(txn);

        return ResponseEntity.ok(updatedTxn);
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
