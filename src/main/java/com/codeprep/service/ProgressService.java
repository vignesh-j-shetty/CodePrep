package com.codeprep.service;

import com.codeprep.model.Language;
import com.codeprep.model.ProblemProgress;
import com.codeprep.repository.ProgressRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProgressService {
    private final ProgressRepository repository;
    private final Map<String, ProblemProgress> progress;
    public ProgressService(ProgressRepository repository) throws IOException {
        this.repository = repository;
        this.progress = new LinkedHashMap<>(repository.load());
    }
    public synchronized ProblemProgress get(String id) { return progress.get(id); }
    public synchronized boolean isSolved(String id) {
        ProblemProgress p = progress.get(id); return p != null && p.solved();
    }
    public synchronized long solvedCount() { return progress.values().stream().filter(ProblemProgress::solved).count(); }
    public synchronized void recordAttempt(String id, Language language, long elapsed, boolean allPassed, boolean allTestsRun) throws IOException {
        ProblemProgress old = progress.get(id);
        boolean solved = (old != null && old.solved()) || (allTestsRun && allPassed);
        progress.put(id, new ProblemProgress(solved, (old == null ? 0 : old.attempts()) + 1, Instant.now(),
                allTestsRun && allPassed ? Instant.now() : old == null ? null : old.lastSuccessful(),
                language, (old == null ? 0 : old.timeSpentMillis()) + elapsed));
        repository.save(progress);
    }
}
