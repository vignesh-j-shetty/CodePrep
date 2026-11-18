package com.codeprep.dto;

import com.codeprep.model.TestStatus;

public record TestCaseDisplayResult(String label, boolean hidden, TestStatus status,
                                    String input, String expected, String actual,
                                    long durationMillis, String message) {}
