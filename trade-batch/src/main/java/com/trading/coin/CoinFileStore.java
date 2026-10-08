package com.trading.coin;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import com.trading.model.coin.*;

/** Private immutable copies. Original input is never moved, rewritten or deleted. */
public final class CoinFileStore {
    private final Path root;
    public CoinFileStore(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public CoinImportFile store(CoinImportFile file, byte[] bytes) throws IOException {
        if (!CoinCsvReader.sha256(bytes).equals(file.sha256())) throw new IOException("SOURCE_HASH_CHANGED");
        Path directory = root.resolve(Long.toString(file.options().ownerId())).resolve(Long.toString(file.options().accountId()));
        privateDirectory(directory);
        Path target = directory.resolve(file.sha256() + ".csv");
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            Path temp = Files.createTempFile(directory, ".coin-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try {
                Files.write(temp, bytes);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temp); }
        }
        if (Files.isSymbolicLink(target) || !Arrays.equals(Files.readAllBytes(target), bytes))
            throw new IOException("MANAGED_FILE_CHANGED");
        return new CoinImportFile(target, file.sha256(), file.byteCount(), file.rawHeader(), file.rows(), file.options());
    }
    private void privateDirectory(Path directory) throws IOException {
        Path path = directory.getRoot();
        for (Path part : directory) {
            path = path.resolve(part);
            if (Files.isSymbolicLink(path)) throw new IOException("SYMLINK_WORK_DIRECTORY");
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                try { Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))); }
                catch (FileAlreadyExistsException ignored) { /* another same-owner importer */ }
            }
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("INVALID_WORK_DIRECTORY");
            if (path.startsWith(root)) {
                var mode = Files.getPosixFilePermissions(path);
                if (mode.stream().anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_")))
                    throw new IOException("WORK_DIRECTORY_MUST_BE_PRIVATE");
            }
        }
    }
}
