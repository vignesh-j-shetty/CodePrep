package com.codeprep.model;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record ExecutionRequest(Problem problem, String sourceCode, Language language,
                               List<TestCase> testCases, Duration timeout, Path solutionDirectory) {
    public ExecutionRequest { testCases = List.copyOf(testCases); }
}
