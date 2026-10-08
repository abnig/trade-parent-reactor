package com.trading.coin;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import com.trading.model.coin.CoinImportOptions;
import org.springframework.boot.DefaultApplicationArguments;

/** Reject unrelated/legacy batch flags before a Spring context or database is opened. */
public record CoinCommandLine(String mode, List<Path> inputs, Path workDirectory, CoinImportOptions options) {
    public static CoinCommandLine parse(String... args) {
        var arguments = new DefaultApplicationArguments(args);
        Set<String> allowed = Set.of("mode", "input-file", "work-directory", "owner-id", "account-id", "period-start", "period-end",
                "date-format", "posting-policy", "fund-map");
        if (!arguments.getNonOptionArgs().isEmpty()) throw new IllegalArgumentException("Unexpected positional/legacy arguments");
        for (String name : arguments.getOptionNames())
            if (!allowed.contains(name) && !name.startsWith("spring.") && !name.equals("batch.coin.chunk-size"))
                throw new IllegalArgumentException("Unsupported option: " + name);
        String mode = one(arguments, "mode");
        if (!Set.of("VALIDATE", "DRY_RUN", "IMPORT").contains(mode)) throw new IllegalArgumentException("Explicit VALIDATE/DRY_RUN/IMPORT mode required");
        var paths = arguments.getOptionValues("input-file");
        if (paths == null || paths.isEmpty() || paths.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("Input file required");
        Map<Long, Long> mappings = new TreeMap<>();
        if (arguments.containsOption("fund-map")) for (String item : one(arguments, "fund-map").split(",", -1)) {
            String[] pair = item.split(":", -1);
            if (pair.length != 2 || mappings.put(Long.valueOf(pair[0]), Long.valueOf(pair[1])) != null)
                throw new IllegalArgumentException("fund-map must contain unique record:fund pairs");
        }
        var options = new CoinImportOptions(Long.parseLong(one(arguments, "owner-id")), Long.parseLong(one(arguments, "account-id")),
                LocalDate.parse(one(arguments, "period-start")), LocalDate.parse(one(arguments, "period-end")), one(arguments, "date-format"),
                CoinImportOptions.PostingPolicy.valueOf(one(arguments, "posting-policy")), mappings);
        Path work = mode.equals("VALIDATE") ? null : Path.of(one(arguments, "work-directory"));
        return new CoinCommandLine(mode, paths.stream().map(Path::of).toList(), work, options);
    }
    private static String one(DefaultApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) throw new IllegalArgumentException("One --" + name + " is required");
        return values.getFirst();
    }
}
