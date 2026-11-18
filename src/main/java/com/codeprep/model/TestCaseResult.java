package com.codeprep.model;

import com.fasterxml.jackson.databind.JsonNode;

public record TestCaseResult(int index, TestCaseVisibility visibility, TestStatus status,
                             JsonNode input, JsonNode expected, JsonNode actual,
                             long durationMillis, String message) {}
