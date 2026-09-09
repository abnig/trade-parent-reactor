package com.trading.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

import com.trading.model.MutualFundValue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Date-only HTTP representation; persistence and MCP retain their existing types. */
public record MutualFundValueDto(
        Long valId,
        @NotNull(message = "Mutual fund ID is required")
        @Positive(message = "Mutual fund ID must be positive") Long mutualFundId,
        @NotNull(message = "Total value is required")
        @DecimalMin(value = "0.0", message = "Total value must not be negative") BigDecimal totalValue,
        @NotNull(message = "Value as-of date is required") String valueAsOfDate) {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.ENGLISH)
                    .withResolverStyle(ResolverStyle.STRICT);

    public MutualFundValueDto {
        if (valueAsOfDate != null) {
            valueAsOfDate = parseDate(valueAsOfDate).format(DATE_FORMAT);
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            // Continue accepting ISO dates and timestamps from existing clients.
            return LocalDate.from(DateTimeFormatter.ISO_DATE_TIME.parse(
                    value.length() == 10 ? value + "T00:00:00" : value));
        }
    }

    public MutualFundValue toModel() {
        LocalDateTime date = valueAsOfDate == null ? null
                : parseDate(valueAsOfDate).atStartOfDay();
        return new MutualFundValue(valId, mutualFundId, totalValue, date);
    }

    public static MutualFundValueDto from(MutualFundValue value) {
        return new MutualFundValueDto(value.getValId(), value.getMutualFundId(),
                value.getTotalValue(), value.getValueAsOfDate() == null ? null
                        : value.getValueAsOfDate().format(DATE_FORMAT));
    }
}
