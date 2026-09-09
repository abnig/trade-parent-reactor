package com.trading.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.trading.model.MutualFundValue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.trading.exception.GlobalExceptionHandler;
import com.trading.exception.InvalidReferenceException;
import com.trading.repository.MutualFundValueRepository;
import com.trading.validation.MutualFundReferenceValidator;

@WebMvcTest(MutualFundValueController.class)
@Import(GlobalExceptionHandler.class)
class MutualFundValueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MutualFundValueRepository repository;

    @MockitoBean
    private MutualFundReferenceValidator referenceValidator;

    @Test
    void rejectsMissingRequiredValueFields() throws Exception {
        mockMvc.perform(post("/api/mutual-fund-values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.mutualFundId", is("Mutual fund ID is required")))
                .andExpect(jsonPath("$.fieldErrors.totalValue", is("Total value is required")))
                .andExpect(jsonPath("$.fieldErrors.valueAsOfDate", is("Value as-of date is required")));
    }

    @Test
    void rejectsInvalidMutualFundReference() throws Exception {
        doThrow(new InvalidReferenceException("Mutual fund 99 does not exist."))
                .when(referenceValidator).requireMutualFund(99L);

        mockMvc.perform(post("/api/mutual-fund-values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mutualFundId": 99, "totalValue": 500.00,
                                 "valueAsOfDate": "2026-01-15T10:30:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Mutual fund 99 does not exist.")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"15-Jan-2026", "2026-01-15", "2026-01-15T10:30:00", "2026-01-15T23:30:00Z"})
    void acceptsDatesAndLegacyTimestamps(String date) throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mockMvc.perform(post("/api/mutual-fund-values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mutualFundId":1,"totalValue":500,"valueAsOfDate":"%s"}
                                """.formatted(date)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valueAsOfDate", is("15-Jan-2026")));
        verify(repository).save(argThat(value ->
                value.getValueAsOfDate().equals(LocalDateTime.of(2026, 1, 15, 0, 0))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"31-Feb-2026", "29-Feb-2025", "15-XYZ-2026", "", "invalid"})
    void rejectsInvalidDates(String date) throws Exception {
        mockMvc.perform(post("/api/mutual-fund-values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mutualFundId":1,"totalValue":500,"valueAsOfDate":"%s"}
                                """.formatted(date)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void formatsDatesAcrossReadEndpoints() throws Exception {
        var value = new MutualFundValue(7L, 1L, BigDecimal.TEN, LocalDateTime.of(2026, 9, 9, 23, 30));
        when(repository.findById(7L)).thenReturn(value);
        when(repository.findAll(any())).thenReturn(List.of(value));
        when(repository.findByMutualFundId(org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(List.of(value));
        mockMvc.perform(get("/api/mutual-fund-values/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valueAsOfDate", is("09-Sep-2026")));
        for (String path : List.of("/api/mutual-fund-values", "/api/mutual-fund-values/mutual-fund/1")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].valueAsOfDate", is("09-Sep-2026")));
        }
    }

    @Test
    void updatesUsingDateOnlyFormat() throws Exception {
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mockMvc.perform(put("/api/mutual-fund-values/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mutualFundId":1,"totalValue":500,"valueAsOfDate":"29-Feb-2024"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valId", is(7)))
                .andExpect(jsonPath("$.valueAsOfDate", is("29-Feb-2024")));
    }
}
