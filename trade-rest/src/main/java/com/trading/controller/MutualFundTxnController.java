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

import com.trading.model.MutualFundTxn;
import com.trading.repository.MutualFundTxnRepository;

@RestController
@RequestMapping("/api/mutual-fund-txns")
public class MutualFundTxnController {

    private final MutualFundTxnRepository repository;

    public MutualFundTxnController(MutualFundTxnRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<MutualFundTxn>> findAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MutualFundTxn> findById(
            @PathVariable Long id) {

        return ResponseEntity.ok(repository.findById(id));
    }

    @GetMapping("/mutual-fund/{mutualFundId}")
    public ResponseEntity<List<MutualFundTxn>> findByMutualFundId(
            @PathVariable Long mutualFundId) {

        return ResponseEntity.ok(
                repository.findByMutualFundId(mutualFundId)
        );
    }

    @PostMapping
    public ResponseEntity<MutualFundTxn> create(
            @RequestBody MutualFundTxn txn) {

        MutualFundTxn savedTxn = repository.save(txn);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(savedTxn);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MutualFundTxn> update(
            @PathVariable Long id,
            @RequestBody MutualFundTxn txn) {

        txn.setMutualFundTxnId(id);

        MutualFundTxn updatedTxn = repository.update(txn);

        return ResponseEntity.ok(updatedTxn);
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
