package com.trading.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

import com.trading.model.MutualFundTxn;
import com.trading.model.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Date-only transaction date at the HTTP boundary. */
public record MutualFundTxnDto(
        Long mutualFundTxnId,
        @NotNull(message = "Mutual fund ID is required")
        @Positive(message = "Mutual fund ID must be positive") Long mutualFundId,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.0", message = "Amount must not be negative") BigDecimal amount,
        @NotNull(message = "Units are required")
        @DecimalMin(value = "0.0", message = "Units must not be negative") BigDecimal units,
        @NotNull(message = "Average price is required")
        @DecimalMin(value = "0.0", message = "Average price must not be negative") BigDecimal avgPrice,
        LocalDateTime createDate,
        LocalDateTime updateDate,
        @NotNull(message = "Transaction date is required") String txnDate,
        @NotNull(message = "Transaction type is required") TransactionType transactionType) {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.ENGLISH)
                    .withResolverStyle(ResolverStyle.STRICT);

    public MutualFundTxnDto {
        if (txnDate != null) {
            txnDate = parseDate(txnDate).format(DATE_FORMAT);
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

    public MutualFundTxn toModel() {
        MutualFundTxn txn = new MutualFundTxn();
        txn.setMutualFundTxnId(mutualFundTxnId);
        txn.setMutualFundId(mutualFundId);
        txn.setAmount(amount);
        txn.setUnits(units);
        txn.setAvgPrice(avgPrice);
        txn.setCreateDate(createDate);
        txn.setUpdateDate(updateDate);
        txn.setTxnDate(txnDate == null ? null : parseDate(txnDate).atStartOfDay());
        txn.setTransactionType(transactionType);
        return txn;
    }

    public static MutualFundTxnDto from(MutualFundTxn txn) {
        return new MutualFundTxnDto(txn.getMutualFundTxnId(), txn.getMutualFundId(),
                txn.getAmount(), txn.getUnits(), txn.getAvgPrice(), txn.getCreateDate(),
                txn.getUpdateDate(), txn.getTxnDate() == null ? null
                        : txn.getTxnDate().format(DATE_FORMAT), txn.getTransactionType());
    }
}
