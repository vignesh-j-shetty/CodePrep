package com.codeprep.executor;

import com.codeprep.model.ExecutionRequest;
import com.codeprep.model.ExecutionResult;
import com.codeprep.model.Language;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public final class ExecutionManager {
    private final Map<Language, LanguageExecutor> executors = new HashMap<>();
    public ExecutionManager(List<LanguageExecutor> registered) {
        for (LanguageExecutor executor : registered) {
            if (executors.putIfAbsent(executor.getLanguage(), executor) != null)
                throw new IllegalArgumentException("Duplicate executor: " + executor.getLanguage());
        }
    }
    public static ExecutionManager discover() {
        return new ExecutionManager(ServiceLoader.load(LanguageExecutor.class).stream()
                .map(ServiceLoader.Provider::get).toList());
    }
    public boolean supports(Language language) { return executors.containsKey(language); }
    public ExecutionResult execute(ExecutionRequest request) {
        LanguageExecutor executor = executors.get(request.language());
        if (executor == null) throw new IllegalArgumentException("No executor registered for " + request.language());
        return executor.execute(request);
    }
}
