package com.trading.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;

import com.trading.exception.InvalidReferenceException;
import com.trading.repository.MutualFundBrokerAccountRepository;
import com.trading.repository.MutualFundRepository;

@ExtendWith(MockitoExtension.class)
class MutualFundReferenceValidatorTest {

    @Mock
    private MutualFundBrokerAccountRepository brokerAccountRepository;

    @Mock
    private MutualFundRepository mutualFundRepository;

    @InjectMocks
    private MutualFundReferenceValidator validator;

    @Test
    void rejectsUnknownBrokerAccount() {
        doThrow(new EmptyResultDataAccessException(1)).when(brokerAccountRepository).findById(7L);

        InvalidReferenceException exception = assertThrows(InvalidReferenceException.class,
                () -> validator.requireBrokerAccount(7L));

        org.junit.jupiter.api.Assertions.assertEquals("Broker account 7 does not exist.", exception.getMessage());
    }

    @Test
    void rejectsUnknownMutualFund() {
        doThrow(new EmptyResultDataAccessException(1)).when(mutualFundRepository).findById(8L);

        InvalidReferenceException exception = assertThrows(InvalidReferenceException.class,
                () -> validator.requireMutualFund(8L));

        org.junit.jupiter.api.Assertions.assertEquals("Mutual fund 8 does not exist.", exception.getMessage());
    }

    @Test
    void acceptsExistingMutualFund() {
        assertDoesNotThrow(() -> validator.requireMutualFund(3L));
        verify(mutualFundRepository).findById(3L);
    }
}
