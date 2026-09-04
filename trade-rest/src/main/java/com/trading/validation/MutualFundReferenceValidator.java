package com.trading.validation;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

import com.trading.exception.InvalidReferenceException;
import com.trading.repository.MutualFundBrokerAccountRepository;
import com.trading.repository.MutualFundRepository;

@Component
public class MutualFundReferenceValidator {

    private final MutualFundBrokerAccountRepository brokerAccountRepository;
    private final MutualFundRepository mutualFundRepository;

    public MutualFundReferenceValidator(
            MutualFundBrokerAccountRepository brokerAccountRepository,
            MutualFundRepository mutualFundRepository) {
        this.brokerAccountRepository = brokerAccountRepository;
        this.mutualFundRepository = mutualFundRepository;
    }

    public void requireBrokerAccount(Long brokerAccountId) {
        try {
            brokerAccountRepository.findById(brokerAccountId);
        } catch (EmptyResultDataAccessException exception) {
            throw new InvalidReferenceException(
                    "Broker account " + brokerAccountId + " does not exist.");
        }
    }

    public void requireMutualFund(Long mutualFundId) {
        try {
            mutualFundRepository.findById(mutualFundId);
        } catch (EmptyResultDataAccessException exception) {
            throw new InvalidReferenceException(
                    "Mutual fund " + mutualFundId + " does not exist.");
        }
    }
}
