package com.codeprep.repository;

import com.codeprep.model.Problem;
import com.codeprep.service.ProblemMarkdownParser;
import com.codeprep.service.TestCaseParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProblemRepository {
    private static final Logger log = LoggerFactory.getLogger(ProblemRepository.class);
    private final ProblemMarkdownParser markdownParser;
    private final TestCaseParser testCaseParser;
    public ProblemRepository(ProblemMarkdownParser markdownParser, TestCaseParser testCaseParser) {
        this.markdownParser = markdownParser; this.testCaseParser = testCaseParser;
    }

    public List<Problem> discover(Path root) throws IOException {
        if (!Files.isDirectory(root)) throw new IOException("Problem library is missing: " + root);
        List<Problem> problems = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path dir : paths.filter(Files::isDirectory).toList()) {
                Path markdown = dir.resolve("problem.md"), cases = dir.resolve("testcases.json");
                if (!Files.isRegularFile(markdown) || !Files.isRegularFile(cases)) continue;
                try {
                    problems.add(validate(root, dir));
                } catch (IOException | RuntimeException ex) {
                    log.warn("Skipping malformed problem folder {}: {}", dir, ex.getMessage());
                }
            }
        }
        problems.sort(Comparator
                .comparing((Problem p) -> p.id().contains("/") ? p.id().substring(0, p.id().indexOf('/')) : "",
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Problem::title, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(problems);
    }

    public Problem validate(Path root, Path directory) throws IOException {
        Path markdown = directory.resolve("problem.md"), cases = directory.resolve("testcases.json");
        if (!Files.isRegularFile(markdown) || !Files.isRegularFile(cases)) {
            throw new IOException("Both problem.md and testcases.json are required.");
        }
        var parsed = markdownParser.parse(Files.readString(markdown));
        if (parsed.markdown().isBlank()) throw new IOException("The problem statement is empty.");
        String id = root.toAbsolutePath().normalize().relativize(directory.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        return new Problem(id, parsed.title(), parsed.difficulty(), parsed.topics(),
                parsed.markdown(), directory, testCaseParser.parse(cases));
    }
}
