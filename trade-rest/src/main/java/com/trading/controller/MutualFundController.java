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

import com.trading.model.MutualFund;
import com.trading.repository.MutualFundRepository;
import com.trading.validation.MutualFundReferenceValidator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@RestController
@Validated
@RequestMapping("/api/mutual-funds")
public class MutualFundController {

    private final MutualFundRepository repository;
    private final MutualFundReferenceValidator referenceValidator;

    public MutualFundController(MutualFundRepository repository,
                                MutualFundReferenceValidator referenceValidator) {
        this.repository = repository;
        this.referenceValidator = referenceValidator;
    }

    @GetMapping
    public ResponseEntity<List<MutualFund>> findAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MutualFund> findById(
            @PathVariable @Positive(message = "ID must be positive") Long id) {

        return ResponseEntity.ok(repository.findById(id));
    }

    @GetMapping("/broker-account/{brokerAccountId}")
    public ResponseEntity<List<MutualFund>> findByBrokerAccountId(
            @PathVariable @Positive(message = "Broker account ID must be positive") Long brokerAccountId) {

        return ResponseEntity.ok(
                repository.findByBrokerAccountId(brokerAccountId)
        );
    }

    @PostMapping
    public ResponseEntity<MutualFund> create(
            @Valid @RequestBody MutualFund mutualFund) {

        referenceValidator.requireBrokerAccount(mutualFund.getBrokerAccountId());

        MutualFund savedFund = repository.save(mutualFund);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(savedFund);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MutualFund> update(
            @PathVariable @Positive(message = "ID must be positive") Long id,
            @Valid @RequestBody MutualFund mutualFund) {

        mutualFund.setMutualFundId(id);
        referenceValidator.requireBrokerAccount(mutualFund.getBrokerAccountId());

        MutualFund updatedFund = repository.update(mutualFund);

        return ResponseEntity.ok(updatedFund);
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
