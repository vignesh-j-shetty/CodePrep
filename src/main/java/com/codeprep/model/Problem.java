package com.codeprep.model;

import java.nio.file.Path;
import java.util.List;

public record Problem(String id, String title, Difficulty difficulty, List<String> topics,
                      String descriptionMarkdown, Path directory, List<TestCase> testCases) {
    public Problem {
        topics = List.copyOf(topics);
        testCases = List.copyOf(testCases);
    }
}
