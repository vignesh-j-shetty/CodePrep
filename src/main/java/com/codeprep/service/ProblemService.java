package com.codeprep.service;

import com.codeprep.model.Problem;
import com.codeprep.repository.ProblemRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ProblemService {
    private final ProblemRepository repository;
    public ProblemService(ProblemRepository repository) { this.repository = repository; }
    public List<Problem> load(Path root) throws IOException { return repository.discover(root); }
    public List<Problem> loadPreservingExisting(Path root, List<Problem> current) throws IOException {
        Map<String, Problem> existing = current.stream().collect(Collectors.toMap(Problem::id, Function.identity()));
        return load(root).stream().map(problem -> existing.getOrDefault(problem.id(), problem)).toList();
    }
}
