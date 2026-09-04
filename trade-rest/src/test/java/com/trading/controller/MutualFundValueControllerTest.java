package com.trading.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doThrow;
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
}
