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

import com.trading.dto.PagedResponse;
import com.trading.dto.MutualFundValueDto;
import com.trading.model.MutualFundValue;
import com.trading.repository.MutualFundValueRepository;
import com.trading.repository.PageRequest;
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
    public ResponseEntity<PagedResponse<MutualFundValueDto>> findAll(
            @RequestParam(defaultValue = "0") long page,
            @RequestParam(defaultValue = "20") long size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return ResponseEntity.ok(PagedResponse.of(repository.findAll(pageRequest).stream().map(MutualFundValueDto::from).toList(), pageRequest, repository.count()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MutualFundValueDto> findById(
            @PathVariable @Positive(message = "ID must be positive") Long id) {

        return ResponseEntity.ok(MutualFundValueDto.from(repository.findById(id)));
    }

    @GetMapping("/mutual-fund/{mutualFundId}")
    public ResponseEntity<PagedResponse<MutualFundValueDto>> findByMutualFundId(
            @PathVariable @Positive(message = "Mutual fund ID must be positive") Long mutualFundId,
            @RequestParam(defaultValue = "0") long page,
            @RequestParam(defaultValue = "20") long size) {
        PageRequest pageRequest = PageRequest.of(page, size);

        return ResponseEntity.ok(PagedResponse.of(repository.findByMutualFundId(mutualFundId, pageRequest).stream().map(MutualFundValueDto::from).toList(),
                pageRequest, repository.countByMutualFundId(mutualFundId)));
    }

    @PostMapping
    public ResponseEntity<MutualFundValueDto> create(
            @Valid @RequestBody MutualFundValueDto request) {

        MutualFundValue value = request.toModel();

        referenceValidator.requireMutualFund(value.getMutualFundId());

        MutualFundValue savedValue = repository.save(value);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MutualFundValueDto.from(savedValue));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MutualFundValueDto> update(
            @PathVariable @Positive(message = "ID must be positive") Long id,
            @Valid @RequestBody MutualFundValueDto request) {

        MutualFundValue value = request.toModel();

        value.setValId(id);
        referenceValidator.requireMutualFund(value.getMutualFundId());

        MutualFundValue updatedValue = repository.update(value);

        return ResponseEntity.ok(MutualFundValueDto.from(updatedValue));
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
