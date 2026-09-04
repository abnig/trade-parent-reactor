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

import com.trading.model.MutualFundBrokerAccount;
import com.trading.repository.MutualFundBrokerAccountRepository;

@RestController
@RequestMapping("/api/broker-accounts")
public class MutualFundBrokerAccountController {

    private final MutualFundBrokerAccountRepository mutualFundBrokerRepository;

    public MutualFundBrokerAccountController(MutualFundBrokerAccountRepository mutualFundBrokerRepository) {
        this.mutualFundBrokerRepository = mutualFundBrokerRepository;
    }

    @GetMapping
    public ResponseEntity<List<MutualFundBrokerAccount>> findAll() {
        return ResponseEntity.ok(mutualFundBrokerRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MutualFundBrokerAccount> findById(
            @PathVariable Long id) {

        return ResponseEntity.ok(mutualFundBrokerRepository.findById(id));
    }

    @PostMapping
    public ResponseEntity<MutualFundBrokerAccount> create(
            @RequestBody MutualFundBrokerAccount account) {

    	int savedAccount = mutualFundBrokerRepository.save(account);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(mutualFundBrokerRepository.findById((long) savedAccount));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MutualFundBrokerAccount> update(
            @PathVariable Long id,
            @RequestBody MutualFundBrokerAccount account) {

        account.setId(id);

        int updatedAccount = mutualFundBrokerRepository.update(account);

        return ResponseEntity.ok(mutualFundBrokerRepository.findById((long) updatedAccount));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id) {

        if (mutualFundBrokerRepository.deleteById(id) == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}