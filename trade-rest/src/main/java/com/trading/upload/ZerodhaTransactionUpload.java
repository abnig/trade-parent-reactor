package com.trading.upload;

import java.util.UUID;

import com.trading.model.coin.CoinImportResult;

/**
 * Existing receipt fields plus the completed Coin result when import options
 * were supplied.
 */
public record ZerodhaTransactionUpload(UUID uploadId, String originalFilename, long size, String status, CoinImportResult importResult) {
	public ZerodhaTransactionUpload(UUID uploadId, String originalFilename, long size, String status) {
		this(uploadId, originalFilename, size, status, null);
	}
}
