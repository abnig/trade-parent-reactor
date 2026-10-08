package com.trading.upload;

import java.util.UUID;

/** Existing receipt fields plus the completed Coin result when import options were supplied. */
public record ZerodhaTransactionUpload(UUID uploadId, String originalFilename, long size, String status,
        com.trading.model.coin.CoinImportResult importResult) {
    public ZerodhaTransactionUpload(UUID uploadId, String originalFilename, long size, String status) {
        this(uploadId, originalFilename, size, status, null);
    }
}
