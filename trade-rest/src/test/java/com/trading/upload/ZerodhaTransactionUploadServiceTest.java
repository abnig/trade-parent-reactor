package com.trading.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ZerodhaTransactionUploadServiceTest {

    @TempDir
    Path stagingDirectory;

    @Test
    void stagesCsvWithAnOpaqueNameInsideTheUploadingUsersDirectory() throws Exception {
        var service = service();
        var upload = service.stage(7L, new MockMultipartFile("file", "C:\\exports\\orders.csv", "text/csv",
                "date,amount\n2026-09-23,100\n".getBytes()));

        assertEquals("orders.csv", upload.originalFilename());
        assertEquals("UPLOADED", upload.status());
        assertEquals(27, upload.size());
        Path stored = stagingDirectory.resolve("7").resolve(upload.uploadId() + ".csv");
        assertTrue(Files.exists(stored));
        assertEquals("date,amount\n2026-09-23,100\n", Files.readString(stored));
        assertFalse(Files.exists(stagingDirectory.resolve("orders.csv")));
    }

    @Test
    void rejectsEmptyAndNonCsvUploads() {
        var service = service();

        assertThrows(IllegalArgumentException.class,
                () -> service.stage(7L, new MockMultipartFile("file", "orders.csv", "text/csv", new byte[0])));
        assertThrows(IllegalArgumentException.class,
                () -> service.stage(7L, new MockMultipartFile("file", "orders.xlsx", "application/octet-stream", new byte[] {1})));
    }

    private ZerodhaTransactionUploadService service() {
        return new ZerodhaTransactionUploadService(stagingDirectory, stagingDirectory.resolve("coin"),
                org.springframework.util.unit.DataSize.ofMegabytes(10), null);
    }

    private CoinUploadOptions options() {
        return new CoinUploadOptions(1L, java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 31),
                "dd/MM/uuuu", com.trading.model.coin.CoinImportOptions.PostingPolicy.ORDER_ONLY, java.util.Map.of());
    }

    @Test void forwardsSessionOwnerAndPreservesFileOnlyStagingIndependently() {
        var runtime = new com.trading.coin.CoinImportRuntime(null, 1) {
            @Override public com.trading.model.coin.CoinImportResult importBytes(byte[] bytes, com.trading.model.coin.CoinImportOptions policy, Path work) {
                assertEquals(7, policy.ownerId()); assertEquals(1, policy.accountId()); assertEquals("synthetic", new String(bytes));
                return new com.trading.model.coin.CoinImportResult(9, "COMPLETED", 1, 1, 0, 0, 0);
            }
        };
        var service = new ZerodhaTransactionUploadService(stagingDirectory, stagingDirectory.resolve("managed"), org.springframework.util.unit.DataSize.ofMegabytes(10), runtime);
        var result = service.importFile(7, new MockMultipartFile("file", "data.csv", "text/csv", "synthetic".getBytes()), options());
        assertEquals("COMPLETED", result.status()); assertEquals(9, result.importResult().importId());
        assertFalse(Files.exists(stagingDirectory.resolve("7")));
    }

    @Test void boundedConcurrentImportsReturn429AndTheSlotIsReleased() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var runtime = new com.trading.coin.CoinImportRuntime(null, 1) {
            @Override public com.trading.model.coin.CoinImportResult importBytes(byte[] bytes, com.trading.model.coin.CoinImportOptions policy, Path work) throws Exception {
                entered.countDown();
                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Test worker timed out");
                return new com.trading.model.coin.CoinImportResult(1, "COMPLETED", 1, 1, 0, 0, 0);
            }
        };
        var service = new ZerodhaTransactionUploadService(stagingDirectory, stagingDirectory.resolve("managed"), org.springframework.util.unit.DataSize.ofMegabytes(10), runtime);
        var file = new MockMultipartFile("file", "data.csv", "text/csv", "synthetic".getBytes());
        try (var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> service.importFile(7, file, options()));
            try {
                assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        assertThrows(CoinUploadException.class, () -> service.importFile(7, file, options())).status());
            } finally { release.countDown(); }
            assertEquals("COMPLETED", first.get(5, java.util.concurrent.TimeUnit.SECONDS).status());
        }
        assertEquals("COMPLETED", service.importFile(7, file, options()).status());
    }

    @Test void stripsPrivateExceptionDetailsAndReleasesTheSlotAfterFailure() {
        var runtime = new com.trading.coin.CoinImportRuntime(null, 1) {
            @Override public com.trading.model.coin.CoinImportResult importBytes(byte[] bytes, com.trading.model.coin.CoinImportOptions policy, Path work) {
                throw new org.springframework.dao.DataAccessResourceFailureException("private database detail");
            }
        };
        var service = new ZerodhaTransactionUploadService(stagingDirectory, stagingDirectory.resolve("managed"), org.springframework.util.unit.DataSize.ofMegabytes(10), runtime);
        var file = new MockMultipartFile("file", "data.csv", "text/csv", "synthetic".getBytes());
        for (int attempt = 0; attempt < 2; attempt++) {
            var error = assertThrows(CoinUploadException.class, () -> service.importFile(7, file, options()));
            assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, error.status());
            assertEquals(null, error.getCause()); assertFalse(error.getMessage().contains("private database detail"));
        }
    }
}
