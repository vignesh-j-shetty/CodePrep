package com.codeprep.editor;

import static org.junit.jupiter.api.Assertions.*;

import com.codeprep.editor.languages.JavaProvider;
import com.codeprep.executor.ExecutionManager;
import com.codeprep.executor.LanguageExecutor;
import com.codeprep.model.ExecutionRequest;
import com.codeprep.model.ExecutionResult;
import com.codeprep.model.Language;
import com.codeprep.model.ProblemProgress;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LanguageProviderRegistryTest {
    @Test void discoversOnlyShippedProvidersAndExecutors() {
        var registry = LanguageProviderRegistry.discover();
        assertEquals(List.of(Language.JAVA), registry.providers().stream().map(LanguageProvider::language).toList());
        assertInstanceOf(JavaProvider.class, registry.require(Language.JAVA));
        assertTrue(ExecutionManager.discover().supports(Language.JAVA));
        assertFalse(ExecutionManager.discover().supports(new Language("PYTHON")));
    }

    @Test void addsANewLanguageWithoutChangingCoreOrLanguageIdentity() {
        var registry = new LanguageProviderRegistry(List.of(new JavaProvider()));
        var python = new TestLanguageProvider();
        registry.register(python);
        assertSame(python, registry.require(new Language("python")));
        assertEquals(Path.of("question", "solutions", "python", "solution.py"),
                SolutionDocuments.file(Path.of("question"), python));
        assertThrows(IllegalArgumentException.class, () -> registry.require(new Language("RUST")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(python));
    }

    @Test void rejectsUnsafePathsAndDuplicateSolutionDirectories() {
        var registry = new LanguageProviderRegistry(List.of(new JavaProvider()));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new TestLanguageProvider() {
            @Override public String solutionDirectory() { return ".."; }
        }));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new TestLanguageProvider() {
            @Override public String solutionFileName() { return "..\\other.py"; }
        }));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new TestLanguageProvider() {
            @Override public String solutionDirectory() { return "java"; }
        }));
    }

    @Test void documentStateIsIsolatedByProblemAndLanguage() {
        var documents = new SolutionDocuments();
        var java = new SolutionDocuments.Key("one", Language.JAVA);
        var python = new SolutionDocuments.Key("one", new Language("python"));
        var other = new SolutionDocuments.Key("two", Language.JAVA);
        documents.drafts().put(java, "java draft");
        documents.drafts().put(python, "python draft");
        documents.initial().put(java, "saved java");
        assertEquals("java draft", documents.drafts().get(java));
        assertEquals("python draft", documents.drafts().get(python));
        assertNull(documents.drafts().get(other));
        documents.drafts().remove(python);
        assertEquals("java draft", documents.drafts().get(java));
        assertEquals("saved java", documents.initial().get(java));
    }

    @Test void preservesEnumJsonCompatibilityForSettingsAndProgress() throws Exception {
        var mapper = new ObjectMapper();
        assertEquals("\"JAVA\"", mapper.writeValueAsString(Language.JAVA));
        assertEquals(Language.JAVA, mapper.readValue("\"JAVA\"", Language.class));
        var progress = mapper.readValue("""
                {"solved":true,"attempts":1,"lastAttempted":null,"lastSuccessful":null,
                 "lastLanguage":"JAVA","timeSpentMillis":1}
                """, ProblemProgress.class);
        assertEquals(Language.JAVA, progress.lastLanguage());
        var custom = new Language("csharp");
        assertEquals(custom, mapper.readValue(mapper.writeValueAsString(custom), Language.class));
        assertThrows(IllegalArgumentException.class, () -> new Language("../java"));
    }

    @Test void executorRegistrationIsIndependentAndRejectsDuplicates() {
        Language language = new Language("PYTHON");
        LanguageExecutor executor = new LanguageExecutor() {
            @Override public Language getLanguage() { return language; }
            @Override public ExecutionResult execute(ExecutionRequest request) { throw new UnsupportedOperationException(); }
        };
        assertTrue(new ExecutionManager(List.of(executor)).supports(language));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionManager(List.of(executor, executor)));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionManager(List.of()).execute(
                new ExecutionRequest(null, "", language, List.of(), java.time.Duration.ofSeconds(1), Path.of("."))));
    }
}
