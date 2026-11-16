package com.trading.upload;

import java.nio.file.Path;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

/**
 * File-only requests retain staging compatibility; explicit options run the
 * isolated Coin job.
 */
@Service
public class ZerodhaTransactionUploadService {

	private final Path stagingDirectory;
	private final Path coinDirectory;
	private final int maximumBytes;
	private final Semaphore importSlot = new Semaphore(1);

	public ZerodhaTransactionUploadService(@Value("${app.upload.zerodha-transactions-directory}") Path stagingDirectory,
			@Value("${app.upload.coin-import-directory}") Path coinDirectory,
			@Value("${spring.servlet.multipart.max-file-size:1000MB}") DataSize maximumSize) {
		this.stagingDirectory = stagingDirectory.toAbsolutePath().normalize();
		this.coinDirectory = coinDirectory.toAbsolutePath().normalize();
		this.maximumBytes = Math.toIntExact(maximumSize.toBytes());
		if (maximumBytes < 1 || maximumBytes > 100 * 1024 * 1024)
			throw new IllegalArgumentException("Upload limit must be between 1 byte and 100 MiB");
	}

}
