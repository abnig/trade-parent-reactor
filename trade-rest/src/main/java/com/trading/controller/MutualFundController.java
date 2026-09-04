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

import com.trading.model.MutualFund;
import com.trading.repository.MutualFundRepository;

@RestController
@RequestMapping("/api/mutual-funds")
public class MutualFundController {

    private final MutualFundRepository repository;

    public MutualFundController(MutualFundRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<MutualFund>> findAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MutualFund> findById(
            @PathVariable Long id) {

        return ResponseEntity.ok(repository.findById(id));
    }

    @GetMapping("/broker-account/{brokerAccountId}")
    public ResponseEntity<List<MutualFund>> findByBrokerAccountId(
            @PathVariable Long brokerAccountId) {

        return ResponseEntity.ok(
                repository.findByBrokerAccountId(brokerAccountId)
        );
    }

    @PostMapping
    public ResponseEntity<MutualFund> create(
            @RequestBody MutualFund mutualFund) {

        MutualFund savedFund = repository.save(mutualFund);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(savedFund);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MutualFund> update(
            @PathVariable Long id,
            @RequestBody MutualFund mutualFund) {

        mutualFund.setMutualFundId(id);

        MutualFund updatedFund = repository.update(mutualFund);

        return ResponseEntity.ok(updatedFund);
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