package com.codeprep.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DemoProblemInstaller {
    private static final List<String> FILES = List.of(
            "Arrays/TwoSum/problem.md", "Arrays/TwoSum/testcases.json",
            "Arrays/TwoSum/solutions/java/Solution.java", "Arrays/TwoSum/solutions/java/TestAdapter.java");
    private DemoProblemInstaller() {}
    public static void install(Path destination) throws IOException {
        if (Files.exists(destination)) return;
        Files.createDirectories(destination.getParent());
        Path staging = Files.createTempDirectory(destination.getParent(), ".codeprep-default-");
        try {
            for (String relative : FILES) {
                Path target = staging.resolve(relative);
                Files.createDirectories(target.getParent());
                try (InputStream input = DemoProblemInstaller.class.getResourceAsStream("/demo-problems/" + relative)) {
                    if (input == null) throw new IOException("Bundled demo problem resource is missing: " + relative);
                    Files.copy(input, target);
                }
            }
            Files.move(staging, destination);
        } finally {
            if (Files.exists(staging)) {
                try (var paths = Files.walk(staging)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
        }
    }
}
