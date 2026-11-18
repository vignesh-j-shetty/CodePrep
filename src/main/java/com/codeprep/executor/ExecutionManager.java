package com.codeprep.executor;

import com.codeprep.model.ExecutionRequest;
import com.codeprep.model.ExecutionResult;
import com.codeprep.model.Language;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ExecutionManager {
    private final Map<Language, LanguageExecutor> executors = new EnumMap<>(Language.class);
    public ExecutionManager(List<LanguageExecutor> registered) {
        for (LanguageExecutor executor : registered) executors.put(executor.getLanguage(), executor);
    }
    public ExecutionResult execute(ExecutionRequest request) {
        LanguageExecutor executor = executors.get(request.language());
        if (executor == null) throw new IllegalArgumentException("No executor registered for " + request.language());
        return executor.execute(request);
    }
}
