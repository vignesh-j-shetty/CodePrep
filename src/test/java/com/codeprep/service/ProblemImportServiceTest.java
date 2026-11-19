package com.codeprep.service;

import static com.codeprep.service.ProblemImportService.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.codeprep.repository.ProblemRepository;
import com.codeprep.editor.LanguageProviderRegistry;
import com.codeprep.editor.TestLanguageProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProblemImportServiceTest {
    @TempDir Path temp;
    private final ProblemRepository repository = new ProblemRepository(new ProblemMarkdownParser(), new TestCaseParser(new ObjectMapper()));
    private final ProblemImportService importer = new ProblemImportService(repository, new ProblemFingerprintService());
    private Path library;
    private Path source;
    private static final String CASES = """
            {"testCases":[{"visibility":"SAMPLE","input":{"a":1,"b":[2,3]},"expected":4}]}
            """;

    @BeforeEach void createRoots() throws Exception {
        library = Files.createDirectory(temp.resolve("library"));
        source = Files.createDirectory(temp.resolve("source"));
    }

    private Path question(Path parent, String folder, String title, String statement, String tests) throws Exception {
        Path dir = Files.createDirectories(parent.resolve(folder));
        Files.writeString(dir.resolve("problem.md"), "# " + title + "\nDifficulty: Easy\nTopics: Arrays\n\n" + statement);
        Files.writeString(dir.resolve("testcases.json"), tests);
        return dir;
    }

    @Test void importsSingleQuestionAndOnlySupportedFilesWithoutProgress() throws Exception {
        Path incoming = question(source, "New", "A Question", "Do something.", CASES);
        Path java = Files.createDirectories(incoming.resolve("solutions").resolve("java"));
        Files.writeString(java.resolve("Solution.java"), "saved code");
        Files.writeString(java.resolve("TestAdapter.java"), "adapter code");
        Files.writeString(java.resolve("Other.java"), "helper code");
        Files.writeString(java.resolve("Solution.class"), "not supported");
        Files.writeString(incoming.resolve("progress.json"), "never import me");
        Files.writeString(incoming.resolve("notes.txt"), "not supported");
        var preview = importer.scan(incoming, library);
        assertEquals(NEW, preview.candidates().get(0).status());
        var result = importer.importQuestions(preview, Set.of());
        assertEquals(1, result.imported());
        assertEquals(0, result.failed());
        Path imported = library.resolve("New");
        assertEquals("saved code", Files.readString(imported.resolve("solutions").resolve("java").resolve("Solution.java")));
        assertEquals(Set.of("problem.md", "testcases.json", "solutions/java/Solution.java",
                "solutions/java/TestAdapter.java", "solutions/java/Other.java"), snapshot(imported).keySet());
        assertEquals(1, repository.discover(library).size());
        assertNoStagingFolders();
    }

    @Test void importsSourcesForANewlyRegisteredLanguageWithoutImporterChanges() throws Exception {
        Path incoming = question(source, "New", "A Question", "Do something.", CASES);
        Path sources = Files.createDirectories(incoming.resolve("solutions").resolve("python"));
        Files.writeString(sources.resolve("solution.py"), "def solve(): pass");
        Files.writeString(sources.resolve("adapter.py"), "adapter");
        Files.writeString(sources.resolve("compiled.pyc"), "not source");
        var extensibleImporter = new ProblemImportService(repository, new ProblemFingerprintService(),
                new LanguageProviderRegistry(List.of(new TestLanguageProvider())));
        var result = extensibleImporter.importQuestions(extensibleImporter.scan(incoming, library), Set.of());
        assertEquals(1, result.imported());
        assertEquals(Set.of("problem.md", "testcases.json", "solutions/python/solution.py", "solutions/python/adapter.py"),
                snapshot(library.resolve("New")).keySet());
    }

    @Test void skipsEquivalentStatementAndTestsDespiteMetadataPathsAndSolutionChanges() throws Exception {
        Path original = question(library, "Existing", "Old title", "Find X.\n    code();", CASES);
        Path incoming = question(source, "DifferentPath", "New title", "Find X.  \r\n    code();\t\r\n",
                CASES.replace("\"a\":1,\"b\":[2,3]", "\"b\":[2,3],\"a\":1"));
        Files.writeString(incoming.resolve("problem.md"), Files.readString(incoming.resolve("problem.md"))
                .replace("Difficulty: Easy", "Difficulty: Hard").replace("Topics: Arrays", "Topics: Trees"));
        Files.createDirectories(original.resolve("solutions").resolve("java"));
        Files.writeString(original.resolve("solutions").resolve("java").resolve("Solution.java"), "edited solution");
        Map<String, String> before = snapshot(library);
        var preview = importer.scan(source, library);
        assertEquals(ALREADY_EXISTS, preview.candidates().get(0).status());
        var result = importer.importQuestions(preview, Set.of(incoming));
        assertEquals(0, result.imported());
        assertEquals(1, result.skipped());
        assertEquals(before, snapshot(library));
    }

    @Test void changedTestsNeedReviewAndCanOnlyImportAsSeparateQuestion() throws Exception {
        question(library, "Original", "Title", "Statement", CASES);
        Path incoming = question(source, "Changed", "Renamed", "Statement", CASES.replace("\"expected\":4", "\"expected\":5"));
        Map<String, String> before = snapshot(library);
        var preview = importer.scan(source, library);
        assertEquals(NEEDS_REVIEW, preview.candidates().get(0).status());
        assertTrue(preview.candidates().get(0).reason().contains("different tests"));
        assertEquals(1, importer.importQuestions(preview, Set.of()).skipped());
        assertEquals(before, snapshot(library));
        var result = importer.importQuestions(preview, Set.of(incoming));
        assertEquals(1, result.imported());
        assertEquals(2, repository.discover(library).size());
        before.forEach((path, content) -> assertEquals(content, read(library.resolve(path))));
    }

    @Test void sameNormalizedTitleDifferentStatementNeedsReview() throws Exception {
        question(library, "Original", "Two Sum", "First statement.", CASES);
        Path incoming = question(source, "Another", " TWO   SUM ", "Different statement.", CASES);
        var preview = importer.scan(source, library);
        assertEquals(NEEDS_REVIEW, preview.candidates().get(0).status());
        assertTrue(preview.candidates().get(0).reason().contains("different statement"));
        assertEquals(1, importer.importQuestions(preview, Set.of()).skipped());
        assertEquals(1, importer.importQuestions(preview, Set.of(incoming)).imported());
    }

    @Test void changedTimeLimitNeedsReviewAndSurvivesImport() throws Exception {
        question(library, "Original", "Title", "Statement", CASES);
        Path incoming = question(source, "Limited", "Title", "Statement",
                CASES.replace("\"expected\":4", "\"expected\":4,\"timeLimitMs\":1500"));
        var preview = importer.scan(source, library);
        assertEquals(NEEDS_REVIEW, preview.candidates().get(0).status());
        assertEquals(1, importer.importQuestions(preview, Set.of(incoming)).imported());
        var imported = repository.discover(library).stream()
                .filter(p -> p.testCases().get(0).timeLimitMs() != null).findFirst().orElseThrow();
        assertEquals(1500, imported.testCases().get(0).timeLimitMs());
    }

    @Test void detectsDuplicatesAndConflictsWithinRecursiveIncomingBatch() throws Exception {
        question(source, "A", "Same", "Same statement", CASES);
        question(source, "B", "Other title", "Same statement", CASES);
        question(source, "nested/C", "Changed tests", "Same statement", CASES.replace("\"SAMPLE\"", "\"HIDDEN\""));
        question(source, "nested/D", "SAME", "Other statement", CASES);
        var preview = importer.scan(source, library);
        assertEquals(List.of(NEW, ALREADY_EXISTS, NEEDS_REVIEW, NEEDS_REVIEW),
                preview.candidates().stream().map(ProblemImportService.Candidate::status).toList());
        var result = importer.importQuestions(preview, Set.of());
        assertEquals(1, result.imported());
        assertEquals(3, result.skipped());
        assertEquals(1, repository.discover(library).size());
    }

    @Test void reportsInvalidInputAndContinuesWithValidQuestions() throws Exception {
        Path missing = Files.createDirectories(source.resolve("Missing"));
        Files.writeString(missing.resolve("problem.md"), "# Missing\nStatement");
        question(source, "Broken", "Broken", "Statement", "{bad json");
        question(source, "Empty", "Empty", " \t\n", CASES);
        question(source, "Visibility", "Visibility", "Statement", CASES.replace("SAMPLE", "SECRET"));
        question(source, "Valid", "Valid", "Valid statement", CASES);
        var preview = importer.scan(source, library);
        assertEquals(4, preview.candidates().stream().filter(c -> c.status() == INVALID).count());
        assertTrue(preview.candidates().stream().filter(c -> c.status() == INVALID).allMatch(c -> !c.reason().isBlank()));
        var result = importer.importQuestions(preview, Set.of());
        assertEquals(4, result.invalid());
        assertEquals(1, result.imported());
        assertEquals(0, result.failed());
    }

    @Test void reportsEmptyOrMissingSourcesAndOverlappingPaths() throws Exception {
        assertEquals(INVALID, importer.scan(source, library).candidates().get(0).status());
        assertThrows(IOException.class, () -> importer.scan(temp.resolve("missing"), library));
        assertThrows(IOException.class, () -> importer.scan(library, library));
        assertThrows(IOException.class, () -> importer.scan(temp, library));
        Path child = Files.createDirectory(library.resolve("child"));
        assertThrows(IOException.class, () -> importer.scan(child, library));
        assertTrue(snapshot(library).isEmpty());
    }

    @Test void usesUniqueDestinationsWithoutReplacingFileOrFolderCollisions() throws Exception {
        question(source, "Question", "New", "New statement", CASES);
        Files.createDirectory(library.resolve("Question"));
        Files.writeString(library.resolve("Question-2"), "keep this file");
        var result = importer.importQuestions(importer.scan(source, library), Set.of());
        assertEquals(1, result.imported());
        assertTrue(Files.isRegularFile(library.resolve("Question-3").resolve("problem.md")));
        assertTrue(Files.isDirectory(library.resolve("Question")));
        assertEquals("keep this file", Files.readString(library.resolve("Question-2")));
    }

    @Test void normalizesDotPathsBeforeChoosingDestinationsAndStaging() throws Exception {
        Path incoming = question(source, "Question", "Title", "Statement", CASES);
        var preview = importer.scan(incoming.resolve("."), library.resolve("."));
        assertEquals(incoming.toAbsolutePath().normalize(), preview.source());
        assertEquals(library.toAbsolutePath().normalize(), preview.library());
        assertEquals(1, importer.importQuestions(preview, Set.of()).imported());
        assertTrue(Files.isRegularFile(library.resolve("Question").resolve("problem.md")));
        assertNoStagingFolders();
    }

    @Test void failuresLeaveNoPartialQuestionsAndDoNotPreventOtherImports() throws Exception {
        Path failing = question(source, "A", "Failing", "First statement", CASES);
        question(source, "B", "Working", "Second statement", CASES);
        var preview = importer.scan(source, library);
        Files.delete(failing.resolve("testcases.json"));
        var result = importer.importQuestions(preview, Set.of());
        assertEquals(1, result.failed());
        assertEquals(1, result.imported());
        assertFalse(Files.exists(library.resolve("A")));
        assertEquals(1, repository.discover(library).size());
        assertTrue(result.details().stream().anyMatch(detail -> detail.contains("Failed") && detail.contains("scan again")));
        assertNoStagingFolders();
    }

    @Test void changedSourceOrNewLibraryDuplicateIsRevalidatedBeforeCommit() throws Exception {
        Path incoming = question(source, "A", "New", "Statement", CASES);
        var preview = importer.scan(source, library);
        Files.writeString(incoming.resolve("testcases.json"), CASES.replace("\"expected\":4", "\"expected\":7"));
        assertEquals(1, importer.importQuestions(preview, Set.of()).failed());
        assertTrue(snapshot(library).isEmpty());
        Files.writeString(incoming.resolve("testcases.json"), CASES);
        question(library, "Appeared", "Other title", "Statement", CASES);
        assertEquals(1, importer.importQuestions(preview, Set.of()).skipped());
        assertEquals(1, repository.discover(library).size());
        assertNoStagingFolders();
    }

    @Test void newConflictsAfterPreviewRequireAnotherReview() throws Exception {
        question(source, "A", "Title", "Statement", CASES);
        var preview = importer.scan(source, library);
        question(library, "Appeared", "Title", "Other statement", CASES);
        assertEquals(1, importer.importQuestions(preview, Set.of()).skipped());
        assertEquals(1, repository.discover(library).size());
    }

    @Test void importsPreserveAllExistingIdsFilesSolutionsAndProgress() throws Exception {
        Path original = question(library, "Arrays/Existing", "Existing", "Existing statement", CASES);
        Path java = Files.createDirectories(original.resolve("solutions").resolve("java"));
        Files.writeString(java.resolve("Solution.java"), "my edited saved solution");
        Files.writeString(library.resolve("progress.json"), "{\"Arrays/Existing\":{\"solved\":true}}");
        Map<String, String> before = snapshot(library);
        var service = new ProblemService(repository);
        var current = service.load(library);
        var selected = current.get(0);
        String id = selected.id();
        question(source, "New", "New", "New statement", CASES);
        assertEquals(1, importer.importQuestions(importer.scan(source, library), Set.of()).imported());
        before.forEach((path, content) -> assertEquals(content, read(library.resolve(path))));
        assertEquals(original, repository.discover(library).stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow().directory());
        assertEquals("Arrays/Existing", id);
        var refreshed = service.loadPreservingExisting(library, current);
        assertEquals(2, refreshed.size());
        assertSame(selected, refreshed.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(),
                "Refresh must retain existing objects so selection and editor drafts stay untouched.");
    }

    @Test void refusesMissingOrInvalidExistingLibrariesWithoutChangingThem() throws Exception {
        question(source, "New", "New", "Statement", CASES);
        Files.delete(library);
        assertThrows(IOException.class, () -> importer.scan(source, library));
        assertFalse(Files.exists(library));
        Files.createDirectory(library);
        Path broken = Files.createDirectory(library.resolve("Broken"));
        Files.writeString(broken.resolve("problem.md"), "broken");
        var before = snapshot(library);
        assertTrue(assertThrows(IOException.class, () -> importer.scan(source, library)).getMessage().contains("Repair"));
        assertEquals(before, snapshot(library));
    }

    @Test void rejectsSymbolicLinksInSourcesAndTheirAncestors() throws Exception {
        Path incoming = question(source, "Question", "Title", "Statement", CASES);
        Path link = source.resolve("linked");
        try {
            Files.createSymbolicLink(link, incoming);
        } catch (IOException | UnsupportedOperationException | SecurityException ex) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links unavailable: " + ex.getMessage());
        }
        assertThrows(IOException.class, () -> importer.scan(link, library));
        assertThrows(IOException.class, () -> importer.scan(link.resolve("solutions"), library));
        var preview = importer.scan(source, library);
        assertTrue(preview.candidates().stream().anyMatch(c -> c.status() == INVALID && c.reason().contains("Symbolic")));
        Files.delete(link);
        Files.createSymbolicLink(incoming.resolve("progress.json"), temp.resolve("private.json"));
        assertEquals(INVALID, importer.scan(incoming, library).candidates().get(0).status());
        assertTrue(snapshot(library).isEmpty());
    }

    private Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> result = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                result.put(root.relativize(file).toString().replace('\\', '/'), Files.readString(file));
            }
        }
        return result;
    }

    private String read(Path file) {
        try { return Files.readString(file); }
        catch (IOException ex) { throw new AssertionError(ex); }
    }

    private void assertNoStagingFolders() throws IOException {
        try (var paths = Files.list(temp)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith(".codeprep-import-")));
        }
    }
}
