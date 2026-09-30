package com.trading.upload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Stages source files only; it deliberately does not parse or persist transactions. */
@Service
public class ZerodhaTransactionUploadService {

    private final Path stagingDirectory;

    public ZerodhaTransactionUploadService(
            @Value("${app.upload.zerodha-transactions-directory}") Path stagingDirectory) {
        this.stagingDirectory = stagingDirectory.toAbsolutePath().normalize();
    }

    public ZerodhaTransactionUpload stage(long userId, MultipartFile file) throws IOException {
        if (userId <= 0) throw new IllegalArgumentException("A valid user is required to upload a file.");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Select a non-empty Zerodha CSV file.");

        String originalFilename = filename(file.getOriginalFilename());
        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("Upload a Zerodha transactions CSV file.");
        }

        UUID uploadId = UUID.randomUUID();
        Path ownerDirectory = stagingDirectory.resolve(Long.toString(userId)).normalize();
        if (!ownerDirectory.startsWith(stagingDirectory)) {
            throw new IllegalArgumentException("Unable to stage the uploaded file.");
        }
        Files.createDirectories(ownerDirectory);
        file.transferTo(ownerDirectory.resolve(uploadId + ".csv"));
        return new ZerodhaTransactionUpload(uploadId, originalFilename, file.getSize(), "UPLOADED");
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
