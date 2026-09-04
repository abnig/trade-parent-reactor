package com.trading.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.trading.repository.PageRequest;

class PagedResponseTest {

    @Test
    void calculatesMetadataForPartialLastPage() {
        PagedResponse<String> response = PagedResponse.of(List.of("item"), new PageRequest(1, 20), 21);

        assertEquals(2, response.getTotalPages());
        assertFalse(response.isFirst());
        assertTrue(response.isLast());
    }

    @Test
    void treatsZeroRecordsAsFirstAndLast() {
        PagedResponse<String> response = PagedResponse.of(List.of(), new PageRequest(0, 20), 0);

        assertEquals(0, response.getTotalPages());
        assertTrue(response.isFirst());
        assertTrue(response.isLast());
    }

    @Test
    void rejectsInvalidAndOverflowingPaginationInputs() {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> PageRequest.of(-1, 20));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> PageRequest.of(0, 101));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> PageRequest.of(Long.MAX_VALUE, 100));
    }
}
