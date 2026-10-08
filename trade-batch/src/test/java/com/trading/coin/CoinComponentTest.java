package com.trading.coin;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import com.trading.model.coin.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import static org.junit.jupiter.api.Assertions.*;

class CoinComponentTest {
    @TempDir Path directory;
    @org.junit.jupiter.api.BeforeEach void canonicalDirectory() throws Exception { directory = directory.toRealPath(); }
    static List<String> arguments(Path input) {
        return new ArrayList<>(List.of("--mode=VALIDATE", "--input-file=" + input, "--owner-id=1", "--account-id=1",
                "--period-start=2026-10-01", "--period-end=2026-10-31", "--date-format=dd/MM/uuuu", "--posting-policy=ORDER_ONLY"));
    }
    @Test void commandLineRequiresExplicitPolicyAndRejectsLegacyDestructiveFlags() {
        var args = arguments(directory.resolve("input.csv"));
        assertEquals("VALIDATE", CoinCommandLine.parse(args.toArray(String[]::new)).mode());
        for (String flag : List.of("--truncateFlag=true", "--inputDir=/tmp", "--mode=IMPORT", "--fund-map=1:1,1:2", "positional")) {
            var invalid = new ArrayList<>(args); invalid.add(flag);
            assertThrows(IllegalArgumentException.class, () -> CoinCommandLine.parse(invalid.toArray(String[]::new)));
        }
        args.removeIf(a -> a.startsWith("--posting-policy="));
        assertThrows(IllegalArgumentException.class, () -> CoinCommandLine.parse(args.toArray(String[]::new)));
    }
    @Test void validatesAllInputFilesWithoutDatabaseOrManagedFiles() throws Exception {
        var a = directory.resolve("a.csv"); var b = directory.resolve("b.csv");
        Files.write(a, CoinFixtures.csv(CoinFixtures.fields("00001", "PROCESSING")));
        Files.write(b, CoinFixtures.csv(CoinFixtures.fields("00002", "COMPLETE")));
        var args = arguments(a); args.add("--input-file=" + b);
        assertEquals(0, CoinOrderHistoryBatchApplication.run(args.toArray(String[]::new)));
        Files.writeString(a, "invalid"); Files.writeString(b, "also invalid");
        assertEquals(1, CoinOrderHistoryBatchApplication.run(args.toArray(String[]::new)));
        assertEquals(1, CoinOrderHistoryBatchApplication.run("--truncateFlag=true"));
    }
    @Test void fileStorePreservesInputAndReusesPrivateContentAddressedCopy() throws Exception {
        byte[] bytes = CoinFixtures.csv(CoinFixtures.fields("00001", "PROCESSING"));
        var original = directory.resolve("original.csv"); Files.write(original, bytes);
        var file = CoinFixtures.file(bytes, CoinFixtures.options());
        var store = new CoinFileStore(directory.resolve("managed"));
        var managed = store.store(file, bytes);
        assertArrayEquals(bytes, Files.readAllBytes(original)); assertArrayEquals(bytes, Files.readAllBytes(managed.managedPath()));
        assertEquals(managed.managedPath(), store.store(file, bytes).managedPath());
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(managed.managedPath()));
        assertThrows(java.io.IOException.class, () -> store.store(file, new byte[]{1}));
        Files.writeString(managed.managedPath(), "tampered");
        assertThrows(java.io.IOException.class, () -> store.store(file, bytes));
    }
    @Test void fileStoreRejectsSymlinkAndPublicWorkDirectory() throws Exception {
        byte[] bytes = CoinFixtures.csv(CoinFixtures.fields("00001", "PROCESSING"));
        var file = CoinFixtures.file(bytes, CoinFixtures.options());
        var link = directory.resolve("linked"); Files.createSymbolicLink(link, directory);
        assertThrows(java.io.IOException.class, () -> new CoinFileStore(link).store(file, bytes));
        var open = directory.resolve("open"); Files.createDirectory(open, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x")));
        assertThrows(java.io.IOException.class, () -> new CoinFileStore(open).store(file, bytes));
    }
    @Test void checkpointResumesAtNextRecordAndRejectsInvalidPositions() {
        var rows = CoinFixtures.file(CoinFixtures.csv(CoinFixtures.fields("1", "PROCESSING"), CoinFixtures.fields("2", "COMPLETE")), CoinFixtures.options()).rows();
        var context = new ExecutionContext(); var reader = new CoinItemReader("stage", rows); reader.open(context);
        assertEquals(1, reader.read().number()); reader.update(context);
        var restarted = new CoinItemReader("stage", rows); restarted.open(context);
        assertEquals(2, restarted.read().number()); assertNull(restarted.read());
        assertEquals(1, context.size()); assertEquals(1, context.getInt("stage.index"));
        context.putInt("stage.index", 3); assertThrows(IllegalArgumentException.class, () -> restarted.open(context));
        var projection = new CoinItemReader("projection", rows); projection.open(context); assertEquals(1, projection.read().number());
    }
    @Test void legacyApplicationConfigurationDoesNotDiscoverCoinBeans() {
        for (Class<?> application : List.of(com.trading.TradeBatchApplication.class, com.trading.LedgerBalancesBatchApplication.class)) {
            try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
                context.register(application);
                // Resolve the actual scan/import graph without instantiating or executing legacy runners.
                var processor = new org.springframework.context.annotation.ConfigurationClassPostProcessor();
                processor.setEnvironment(context.getEnvironment());
                processor.postProcessBeanDefinitionRegistry(context.getDefaultListableBeanFactory());
                assertTrue(context.containsBeanDefinition("batchConfiguration"));
                assertTrue(context.containsBeanDefinition("ledgerBalancesBatchConfiguration"));
                assertFalse(context.containsBeanDefinition("coinBatchConfiguration"));
                assertFalse(context.containsBeanDefinition("coinOrderHistoryBatchApplication"));
                assertFalse(context.containsBeanDefinition("coinOrderHistoryImportJob"));
            }
        }
    }
}
