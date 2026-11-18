package com.codeprep.model;

import java.time.Instant;

public record ProblemProgress(boolean solved, int attempts, Instant lastAttempted, Instant lastSuccessful,
                              Language lastLanguage, long timeSpentMillis) {}
