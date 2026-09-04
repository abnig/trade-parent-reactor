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

import com.trading.model.MutualFundValue;
import com.trading.repository.MutualFundValueRepository;

@RestController
@RequestMapping("/api/mutual-fund-values")
public class MutualFundValueController {

    private final MutualFundValueRepository repository;

    public MutualFundValueController(MutualFundValueRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<MutualFundValue>> findAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MutualFundValue> findById(
            @PathVariable Long id) {

        return ResponseEntity.ok(repository.findById(id));
    }

    @GetMapping("/mutual-fund/{mutualFundId}")
    public ResponseEntity<List<MutualFundValue>> findByMutualFundId(
            @PathVariable Long mutualFundId) {

        return ResponseEntity.ok(
                repository.findByMutualFundId(mutualFundId)
        );
    }

    @PostMapping
    public ResponseEntity<MutualFundValue> create(
            @RequestBody MutualFundValue value) {

        MutualFundValue savedValue = repository.save(value);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(savedValue);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MutualFundValue> update(
            @PathVariable Long id,
            @RequestBody MutualFundValue value) {

        value.setValId(id);

        MutualFundValue updatedValue = repository.update(value);

        return ResponseEntity.ok(updatedValue);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id) {

        if (!repository.deleteById(id)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}