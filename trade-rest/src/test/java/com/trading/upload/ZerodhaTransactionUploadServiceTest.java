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
        var service = new ZerodhaTransactionUploadService(stagingDirectory);
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
        var service = new ZerodhaTransactionUploadService(stagingDirectory);

        assertThrows(IllegalArgumentException.class,
                () -> service.stage(7L, new MockMultipartFile("file", "orders.csv", "text/csv", new byte[0])));
        assertThrows(IllegalArgumentException.class,
                () -> service.stage(7L, new MockMultipartFile("file", "orders.xlsx", "application/octet-stream", new byte[] {1})));
    }
}
