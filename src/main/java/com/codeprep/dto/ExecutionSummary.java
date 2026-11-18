package com.codeprep.dto;

import java.util.List;

public record ExecutionSummary(List<TestCaseDisplayResult> results, int passed, int failed,
                               int executed, long durationMillis, boolean allPassed,
                               String compilationErrors, String executionOutput) {
    public ExecutionSummary { results = List.copyOf(results); }
}
