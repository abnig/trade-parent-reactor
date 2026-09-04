package com.trading.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.trading.exception.GlobalExceptionHandler;
import com.trading.model.MutualFund;
import com.trading.repository.MutualFundRepository;
import com.trading.validation.MutualFundReferenceValidator;

import org.springframework.context.annotation.Import;

@WebMvcTest(MutualFundController.class)
@Import(GlobalExceptionHandler.class)
class MutualFundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MutualFundRepository repository;

    @MockitoBean
    private MutualFundReferenceValidator referenceValidator;

    @Test
    void createsValidMutualFundWithExistingResponseShape() throws Exception {
        MutualFund saved = new MutualFund(10L, 5L, "Index Fund", null, null);
        when(repository.save(any(MutualFund.class))).thenReturn(saved);

        mockMvc.perform(post("/api/mutual-funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"brokerAccountId": 5, "mutualFundName": "Index Fund"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mutualFundId", is(10)))
                .andExpect(jsonPath("$.brokerAccountId", is(5)))
                .andExpect(jsonPath("$.mutualFundName", is("Index Fund")));
    }

    @Test
    void rejectsBlankMutualFundName() throws Exception {
        mockMvc.perform(post("/api/mutual-funds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"brokerAccountId": 5, "mutualFundName": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors.mutualFundName",
                        is("Mutual fund name is required")));
    }

    @Test
    void returnsNotFoundForUnknownMutualFund() throws Exception {
        when(repository.findById(99L)).thenThrow(new EmptyResultDataAccessException(1));

        mockMvc.perform(get("/api/mutual-funds/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.path", is("/api/mutual-funds/99")));
    }

    @Test
    void hidesUnexpectedRepositoryFailures() throws Exception {
        when(repository.findAll()).thenThrow(new RuntimeException("database detail"));

        mockMvc.perform(get("/api/mutual-funds"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", is("An unexpected error occurred.")));
    }
}
