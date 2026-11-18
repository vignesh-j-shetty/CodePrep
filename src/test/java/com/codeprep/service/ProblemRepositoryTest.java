package com.codeprep.service;

import static org.junit.jupiter.api.Assertions.*;

import com.codeprep.repository.ProblemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProblemRepositoryTest {
    @TempDir Path temp;

    @Test void discoversValidProblemsAndSkipsMalformedFolders() throws Exception {
        Path valid = temp.resolve("Arrays/Pair");
        Files.createDirectories(valid);
        Files.writeString(valid.resolve("problem.md"), """
                # Pair Finder
                Difficulty: Medium
                Topics: Array, HashMap
                ## Problem
                Find a pair.
                """);
        Files.writeString(valid.resolve("testcases.json"), """
                {"testCases":[{"visibility":"SAMPLE","input":{"n":[2]},"expected":true}]}
                """);
        Path missingCases = temp.resolve("Invalid");
        Files.createDirectories(missingCases);
        Files.writeString(missingCases.resolve("problem.md"), "# Invalid");
        Path malformed = temp.resolve("Broken");
        Files.createDirectories(malformed);
        Files.writeString(malformed.resolve("problem.md"), "# Broken");
        Files.writeString(malformed.resolve("testcases.json"), "not-json");

        var repo = new ProblemRepository(new ProblemMarkdownParser(), new TestCaseParser(new ObjectMapper()));
        var found = repo.discover(temp);
        assertEquals(1, found.size());
        assertEquals("Pair Finder", found.get(0).title());
        assertEquals("MEDIUM", found.get(0).difficulty().name());
        assertEquals(java.util.List.of("Array", "HashMap"), found.get(0).topics());
        assertTrue(found.get(0).descriptionMarkdown().contains("Find a pair"));
    }
}
