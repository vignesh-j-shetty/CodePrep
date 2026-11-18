package com.codeprep.model;

public enum TestStatus {
    PASSED, FAILED, ERROR, TIMED_OUT;

    public String displayName() {
        return this == TIMED_OUT ? "Time Limit Exceeded" : name();
    }
}
