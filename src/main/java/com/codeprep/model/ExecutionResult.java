package com.codeprep.model;

import java.util.List;

public record ExecutionResult(List<TestCaseResult> testResults, String compilationErrors,
                              String executionOutput, long durationMillis, boolean compilationSucceeded) {
    public ExecutionResult { testResults = List.copyOf(testResults); }
}
