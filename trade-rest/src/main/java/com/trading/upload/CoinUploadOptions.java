package com.trading.upload;

import java.time.LocalDate;
import java.util.Map;
import com.trading.model.coin.CoinImportOptions;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** No owner/path/job-name properties: identity comes exclusively from the authenticated session. */
public record CoinUploadOptions(@NotNull @Positive Long brokerAccountId,
        @NotNull LocalDate periodStart, @NotNull LocalDate periodEnd,
        @NotBlank String dateFormat, @NotNull CoinImportOptions.PostingPolicy postingPolicy,
        Map<@Positive Long, @NotNull @Positive Long> fundOverrides) {
    public CoinImportOptions forOwner(long owner) {
        return new CoinImportOptions(owner, brokerAccountId, periodStart, periodEnd, dateFormat, postingPolicy,
                fundOverrides == null ? Map.of() : fundOverrides);
    }
}
