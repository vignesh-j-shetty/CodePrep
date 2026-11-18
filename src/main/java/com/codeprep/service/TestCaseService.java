package com.codeprep.service;

import com.codeprep.model.Problem;
import com.codeprep.model.TestCase;
import com.codeprep.model.TestCaseVisibility;
import java.util.List;

public final class TestCaseService {
    public List<TestCase> forRun(Problem problem, boolean includeHidden) {
        return problem.testCases().stream()
                .filter(testCase -> includeHidden || testCase.visibility() == TestCaseVisibility.SAMPLE)
                .toList();
    }
}
