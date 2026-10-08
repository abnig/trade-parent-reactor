package com.trading.upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Semaphore;
import com.trading.coin.CoinImportRuntime;
import com.trading.model.coin.CoinValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** File-only requests retain staging compatibility; explicit options run the isolated Coin job. */
@Service
public class ZerodhaTransactionUploadService {

    private final Path stagingDirectory;
    private final Path coinDirectory;
    private final CoinImportRuntime coin;
    private final int maximumBytes;
    private final Semaphore importSlot = new Semaphore(1);

    public ZerodhaTransactionUploadService(
            @Value("${app.upload.zerodha-transactions-directory}") Path stagingDirectory,
            @Value("${app.upload.coin-import-directory}") Path coinDirectory,
            @Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maximumSize,
            CoinImportRuntime coin) {
        this.stagingDirectory = stagingDirectory.toAbsolutePath().normalize();
        this.coinDirectory = coinDirectory.toAbsolutePath().normalize();
        this.maximumBytes = Math.toIntExact(maximumSize.toBytes());
        if (maximumBytes < 1 || maximumBytes > 100 * 1024 * 1024) throw new IllegalArgumentException("Upload limit must be between 1 byte and 100 MiB");
        this.coin = coin;
    }

    public ZerodhaTransactionUpload importFile(long userId, MultipartFile file, CoinUploadOptions options) {
        if (!importSlot.tryAcquire()) throw failure(HttpStatus.TOO_MANY_REQUESTS, "A Coin import is already running. Please retry shortly.");
        try {
            validateFile(userId, file);
            var policy = options.forOwner(userId);
            if (file.getSize() > maximumBytes) throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "The CSV exceeds the upload size limit.");
            byte[] bytes;
            try (var stream = file.getInputStream()) { bytes = stream.readNBytes(maximumBytes + 1); }
            if (bytes.length > maximumBytes) throw failure(HttpStatus.PAYLOAD_TOO_LARGE, "The CSV exceeds the upload size limit.");
            var result = coin.importBytes(bytes, policy, coinDirectory);
            return new ZerodhaTransactionUpload(UUID.randomUUID(), filename(file.getOriginalFilename()), bytes.length,
                    result.status(), result);
        } catch (CoinUploadException e) {
            throw e;
        } catch (CoinValidationException e) {
            boolean conflict = e.problems().stream().anyMatch(p -> p.code().contains("CONFLICT")
                    || p.code().contains("TRANSITION") || p.code().contains("RECONCILIATION"));
            throw new CoinUploadException(conflict ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY,
                    conflict ? "The Coin file conflicts with existing imports or orders." : "Coin CSV validation failed. Check the listed records and fields.", e.problems());
        } catch (org.springframework.batch.core.launch.JobExecutionAlreadyRunningException e) {
            throw failure(HttpStatus.CONFLICT, "This Coin import is already running.");
        } catch (IllegalArgumentException e) {
            if ("SCHEMA_PREFLIGHT_FAILED".equals(e.getMessage()))
                throw failure(HttpStatus.SERVICE_UNAVAILABLE, "Coin import database prerequisites are not ready.");
            if ("IMPORT_CONTRACT_CHANGED".equals(e.getMessage()))
                throw failure(HttpStatus.CONFLICT, "This file was previously imported with different options. Reuse its original options.");
            throw failure(HttpStatus.BAD_REQUEST, "Invalid Coin upload options, file, or owned account.");
        } catch (org.springframework.dao.DataAccessException | org.springframework.beans.BeansException e) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "Coin import database or runtime is unavailable. Check its configuration and schema.");
        } catch (Exception e) {
            throw failure(HttpStatus.INTERNAL_SERVER_ERROR, "Coin import failed. Some chunks may have completed; retry the same file and options after resolving the failure.");
        } finally {
            importSlot.release();
        }
    }

    private static CoinUploadException failure(HttpStatus status, String message) {
        return new CoinUploadException(status, message, List.of());
    }

    public ZerodhaTransactionUpload stage(long userId, MultipartFile file) throws IOException {
        validateFile(userId, file);
        String originalFilename = filename(file.getOriginalFilename());

        UUID uploadId = UUID.randomUUID();
        Path ownerDirectory = stagingDirectory.resolve(Long.toString(userId)).normalize();
        if (!ownerDirectory.startsWith(stagingDirectory)) {
            throw new IllegalArgumentException("Unable to stage the uploaded file.");
        }
        Files.createDirectories(ownerDirectory);
        file.transferTo(ownerDirectory.resolve(uploadId + ".csv"));
        return new ZerodhaTransactionUpload(uploadId, originalFilename, file.getSize(), "UPLOADED");
    }

    private static void validateFile(long userId, MultipartFile file) {
        if (userId <= 0) throw new IllegalArgumentException("A valid user is required to upload a file.");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Select a non-empty Zerodha CSV file.");

        String originalFilename = filename(file.getOriginalFilename());
        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("Upload a Zerodha transactions CSV file.");
        }

    }

    private static String filename(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Uploaded file must have a filename.");
        String normalized = value.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Uploaded file must have a filename.");
        }
        return name;
    }
}
