package com.trading.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.DateTimeException;
import com.trading.model.MutualFund;
import com.trading.model.MutualFundValue;
import com.trading.repository.AnalyticsRepository;
import com.trading.security.AccountPrincipal;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

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

    @GetMapping("/portfolio")
    public ResponseEntity<?> portfolio(@AuthenticationPrincipal AccountPrincipal user,
            @RequestParam Map<String, String> parameters) {
        if (!Set.of("fromDate", "toDate").containsAll(parameters.keySet())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only fromDate and toDate are accepted."));
        }
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = parseDate(parameters.get("fromDate"));
            toDate = parseDate(parameters.get("toDate"));
        } catch (DateTimeException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", "Dates must be valid YYYY-MM-DD dates with years from 0001 to 9999."));
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().body(Map.of("message", "From date must be on or before the to date."));
        }
        return ResponseEntity.ok(repository.findPortfolio(user.getId(), fromDate, toDate));
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isEmpty()) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeException("Invalid date format");
        LocalDate date = LocalDate.parse(value);
        if (date.getYear() < 1) throw new DateTimeException("Invalid year");
        return date;
    }
}
