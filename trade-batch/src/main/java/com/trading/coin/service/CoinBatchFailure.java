package com.trading.coin.service;

/**
 * Batch persists/logs exception chains; PostgreSQL details may contain a
 * complete private row.
 */
public final class CoinBatchFailure {
	private CoinBatchFailure() {
	}

	public static IllegalStateException sanitized(String phase, RuntimeException failure) {
		String code = failure.getClass() == IllegalArgumentException.class && failure.getMessage() != null
				&& failure.getMessage().matches("[A-Z_]{1,80}") ? failure.getMessage() : "DATABASE_OR_INPUT_FAILURE";
		return new IllegalStateException("COIN_" + phase + "_FAILED: " + code);
	}
}
