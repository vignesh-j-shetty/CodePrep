package com.codeprep.executor;

import com.codeprep.model.ExecutionRequest;
import com.codeprep.model.ExecutionResult;
import com.codeprep.model.Language;

public interface LanguageExecutor {
    Language getLanguage();
    ExecutionResult execute(ExecutionRequest request);
}
