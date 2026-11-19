package com.codeprep.editor;

import com.codeprep.model.Language;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Keeps document state separate for every problem/language pair. */
public final class SolutionDocuments {
    public record Key(String problemId, Language language) {}
    private final Map<Key, String> drafts = new HashMap<>();
    private final Map<Key, String> initial = new HashMap<>();

    public Map<Key, String> drafts() { return drafts; }
    public Map<Key, String> initial() { return initial; }
    public static Path directory(Path problem, LanguageProvider provider) {
        return problem.resolve("solutions").resolve(provider.solutionDirectory());
    }
    public static Path file(Path problem, LanguageProvider provider) {
        return directory(problem, provider).resolve(provider.solutionFileName());
    }
}
