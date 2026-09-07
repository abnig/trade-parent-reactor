package com.trading.controller;

import java.util.List;
import com.trading.model.MutualFund;
import com.trading.model.MutualFundValue;
import com.trading.repository.AnalyticsRepository;
import com.trading.security.AccountPrincipal;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsRepository repository;

    public AnalyticsController(AnalyticsRepository repository) { this.repository = repository; }

    @GetMapping("/funds")
    public List<MutualFund> funds(@AuthenticationPrincipal AccountPrincipal user) {
        return repository.findFunds(user.getId());
    }

    @GetMapping("/funds/{fundId}/values")
    public List<MutualFundValue> values(@AuthenticationPrincipal AccountPrincipal user,
            @PathVariable @Positive(message = "Mutual fund ID must be positive") long fundId) {
        return repository.findValueHistory(user.getId(), fundId);
    }
}
