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

import com.trading.model.MutualFundValue;
import com.trading.repository.MutualFundValueRepository;
import com.trading.validation.MutualFundReferenceValidator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@RestController
@Validated
@RequestMapping("/api/mutual-fund-values")
public class MutualFundValueController {

    private final MutualFundValueRepository repository;
    private final MutualFundReferenceValidator referenceValidator;

    public MutualFundValueController(MutualFundValueRepository repository,
                                     MutualFundReferenceValidator referenceValidator) {
        this.repository = repository;
        this.referenceValidator = referenceValidator;
    }

    @GetMapping
    public ResponseEntity<List<MutualFundValue>> findAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MutualFundValue> findById(
            @PathVariable @Positive(message = "ID must be positive") Long id) {

        return ResponseEntity.ok(repository.findById(id));
    }

    @GetMapping("/mutual-fund/{mutualFundId}")
    public ResponseEntity<List<MutualFundValue>> findByMutualFundId(
            @PathVariable @Positive(message = "Mutual fund ID must be positive") Long mutualFundId) {

        return ResponseEntity.ok(
                repository.findByMutualFundId(mutualFundId)
        );
    }

    @PostMapping
    public ResponseEntity<MutualFundValue> create(
            @Valid @RequestBody MutualFundValue value) {

        referenceValidator.requireMutualFund(value.getMutualFundId());

        MutualFundValue savedValue = repository.save(value);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(savedValue);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MutualFundValue> update(
            @PathVariable @Positive(message = "ID must be positive") Long id,
            @Valid @RequestBody MutualFundValue value) {

        value.setValId(id);
        referenceValidator.requireMutualFund(value.getMutualFundId());

        MutualFundValue updatedValue = repository.update(value);

        return ResponseEntity.ok(updatedValue);
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
