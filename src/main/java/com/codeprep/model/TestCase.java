package com.codeprep.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;

public record TestCase(int index, TestCaseVisibility visibility, JsonNode input, JsonNode expected, Integer timeLimitMs) {
    public TestCase {
        if (timeLimitMs != null && (timeLimitMs < 1 || timeLimitMs > 60_000)) {
            throw new IllegalArgumentException("timeLimitMs must be a whole number between 1 and 60000.");
        }
    }

    public TestCase(int index, TestCaseVisibility visibility, JsonNode input, JsonNode expected) {
        this(index, visibility, input, expected, null);
    }

    public TestCase(TestCaseVisibility visibility, JsonNode input, JsonNode expected) {
        this(0, visibility, input, expected);
    }

    public Duration effectiveTimeout(Duration defaultTimeout) {
        return timeLimitMs == null ? defaultTimeout : Duration.ofMillis(timeLimitMs);
    }
}
