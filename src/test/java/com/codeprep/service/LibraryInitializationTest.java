package com.codeprep.service;

import static org.junit.jupiter.api.Assertions.*;

import com.codeprep.config.AppConfig;
import com.codeprep.model.Language;
import com.codeprep.repository.ProblemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryInitializationTest {
    @TempDir Path home;
    private String previousHome;

    @BeforeEach void useTemporaryHome() {
        previousHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
    }

    @AfterEach void restoreHome() {
        System.setProperty("user.home", previousHome);
    }

    @Test void firstRunInstallsOnlyTwoSumAndNeverReinstallsOrOverwrites() throws Exception {
        AppConfig config = AppConfig.initializeLibrary();
        assertEquals(home.resolve(".codeprep").resolve("problems"), config.problemRoot());
        var repository = new ProblemRepository(new ProblemMarkdownParser(), new TestCaseParser(new ObjectMapper()));
        var problems = repository.discover(config.problemRoot());
        assertEquals(1, problems.size());
        assertEquals("Two Sum", problems.get(0).title());
        Path statement = problems.get(0).directory().resolve("problem.md");
        Path solution = problems.get(0).directory().resolve("solutions").resolve("java").resolve("Solution.java");
        Files.writeString(solution, "my saved solution");
        Files.delete(statement);

        assertEquals(config, AppConfig.initializeLibrary());
        DemoProblemInstaller.install(config.problemRoot());
        assertFalse(Files.exists(statement), "Deleted default files must not be reinstalled.");
        assertEquals("my saved solution", Files.readString(solution));
        assertTrue(Files.isRegularFile(AppConfig.configFile()));
    }

    @Test void existingUserKeepsConfiguredRootPathsSettingsAndAllData() throws Exception {
        Path root = Files.createDirectories(home.resolve("old-library"));
        Path question = Files.createDirectories(root.resolve("Arrays").resolve("MyQuestion"));
        Files.writeString(question.resolve("problem.md"), "original statement");
        Files.writeString(question.resolve("Solution.java"), "original solution");
        Files.writeString(root.resolve("progress.json"), "{\"Arrays/MyQuestion\":\"original progress\"}");
        AppConfig original = new AppConfig(root, 23, Language.JAVA, "dark");
        original.save();
        String settings = Files.readString(AppConfig.configFile());
        assertEquals(original, AppConfig.initializeLibrary());
        assertEquals(settings, Files.readString(AppConfig.configFile()));
        assertEquals("original statement", Files.readString(question.resolve("problem.md")));
        assertEquals("original solution", Files.readString(question.resolve("Solution.java")));
        assertEquals("{\"Arrays/MyQuestion\":\"original progress\"}", Files.readString(root.resolve("progress.json")));
        assertFalse(Files.exists(home.resolve(".codeprep").resolve("problems")));
    }

    @Test void editorFontSizeIsPersistedClampedAndDefaultsForOlderSettings() throws Exception {
        Path root = Files.createDirectories(home.resolve("library"));
        new AppConfig(root, 5, Language.JAVA, "dark", 18).save();
        assertEquals(18, AppConfig.load().editorFontSize());
        assertEquals(32, new AppConfig(root, 5, Language.JAVA, "dark", 99).editorFontSize());
        assertEquals(10, new AppConfig(root, 5, Language.JAVA, "dark", 1).editorFontSize());
        Files.writeString(AppConfig.configFile(), "{\"problemRoot\":" + new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(root.toString()) + ",\"selectedLanguage\":\"JAVA\",\"theme\":\"dark\"}");
        assertEquals(13, AppConfig.load().editorFontSize());
    }

    @Test void missingConfiguredLibraryIsReportedAndNotRecreated() throws Exception {
        Path missing = home.resolve("missing");
        new AppConfig(missing, 5, Language.JAVA, "dark").save();
        String settings = Files.readString(AppConfig.configFile());
        IOException error = assertThrows(IOException.class, AppConfig::initializeLibrary);
        assertTrue(error.getMessage().contains(missing.toString()));
        assertFalse(Files.exists(missing));
        assertEquals(settings, Files.readString(AppConfig.configFile()));
        assertFalse(Files.exists(home.resolve(".codeprep").resolve("problems")));
    }

    @Test void corruptOrRootlessExistingSettingsAreNotTreatedAsFirstRun() throws Exception {
        Files.createDirectories(AppConfig.configFile().getParent());
        Files.writeString(AppConfig.configFile(), "{invalid");
        assertThrows(IOException.class, AppConfig::initializeLibrary);
        assertEquals("{invalid", Files.readString(AppConfig.configFile()));
        Files.writeString(AppConfig.configFile(), "{}");
        assertThrows(IOException.class, AppConfig::initializeLibrary);
        assertFalse(Files.exists(home.resolve(".codeprep").resolve("problems")));
    }

    @Test void preexistingDefaultFolderIsPreservedEvenWithoutSettings() throws Exception {
        Path root = Files.createDirectories(home.resolve(".codeprep").resolve("problems"));
        Files.writeString(root.resolve("progress.json"), "keep me");
        assertEquals(root, AppConfig.initializeLibrary().problemRoot());
        assertEquals("keep me", Files.readString(root.resolve("progress.json")));
        assertFalse(Files.exists(root.resolve("Arrays")));
    }
}
