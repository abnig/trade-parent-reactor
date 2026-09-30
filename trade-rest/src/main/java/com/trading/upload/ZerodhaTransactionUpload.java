package com.trading.upload;

import java.util.UUID;

/** Metadata for a CSV file staged for a future Zerodha transaction import. */
public record ZerodhaTransactionUpload(UUID uploadId, String originalFilename, long size, String status) {
}
