package com.trading.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.trading.repository.PageRequest;
import com.trading.validation.MutualFundReferenceValidator;

import org.springframework.context.annotation.Import;

@WebMvcTest(MutualFundController.class)
@Import(GlobalExceptionHandler.class)
class MutualFundControllerTest {

    @Test
    void acceptsFundMetadataOnCreateAndUpdateAndReturnsItOnEveryRead() throws Exception {
        String body = """
                {"brokerAccountId":5,"mutualFundName":"Index Fund",
                 "isin":"INF123456789","plan":"Direct Growth","folioNumber":"00001234/05"}
                """;
        when(repository.save(any())).thenAnswer(invocation -> {
            MutualFund fund = invocation.getArgument(0);
            fund.setMutualFundId(10L);
            return fund;
        });
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        for (var request : java.util.List.of(post("/api/mutual-funds"), put("/api/mutual-funds/10"))) {
            mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.mutualFundId", is(10)))
                    .andExpect(jsonPath("$.isin", is("INF123456789")))
                    .andExpect(jsonPath("$.plan", is("Direct Growth")))
                    .andExpect(jsonPath("$.folioNumber", is("00001234/05")));
        }
        var fund = new MutualFund(10L, 5L, "Index Fund", null, null);
        fund.setIsin("INF123456789");
        fund.setPlan("Direct Growth");
        fund.setFolioNumber("00001234/05");
        when(repository.findById(10L)).thenReturn(fund);
        when(repository.findAll(any())).thenReturn(java.util.List.of(fund));
        when(repository.findByBrokerAccountId(org.mockito.ArgumentMatchers.eq(5L), any()))
                .thenReturn(java.util.List.of(fund));
        for (String path : java.util.List.of("/api/mutual-funds/10", "/api/mutual-funds", "/api/mutual-funds/broker-account/5")) {
            String prefix = path.endsWith("/10") ? "$" : "$.content[0]";
            mockMvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(jsonPath(prefix + ".isin", is("INF123456789")))
                    .andExpect(jsonPath(prefix + ".plan", is("Direct Growth")))
                    .andExpect(jsonPath(prefix + ".folioNumber", is("00001234/05")));
        }
    }

    @Test
    void acceptsNASourceMetadataWithoutRestrictionsAndExplicitClearing() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        for (String isin : java.util.List.of("N/A", "short", "longer-than-twelve-characters", "")) {
            for (var request : java.util.List.of(post("/api/mutual-funds"), put("/api/mutual-funds/10"))) {
                mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"brokerAccountId\":5,\"mutualFundName\":\"Fund\",\"isin\":\"" + isin + "\"}"))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(jsonPath("$.isin", is(isin)));
            }
        }
        for (String field : java.util.List.of("plan", "folioNumber")) {
            for (var request : java.util.List.of(post("/api/mutual-funds"), put("/api/mutual-funds/10"))) {
                mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                                .content("{\"brokerAccountId\":5,\"mutualFundName\":\"Fund\",\"" + field + "\":\"N/A\"}"))
                        .andExpect(status().is2xxSuccessful())
                        .andExpect(jsonPath("$." + field, is("N/A")));
            }
        }
    }

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
        when(repository.findAll(new PageRequest(0, 20))).thenThrow(new RuntimeException("database detail"));

        mockMvc.perform(get("/api/mutual-funds"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", is("An unexpected error occurred.")));
    }
}
