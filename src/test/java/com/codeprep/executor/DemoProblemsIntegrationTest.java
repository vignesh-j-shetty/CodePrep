package com.codeprep.executor;

import static org.junit.jupiter.api.Assertions.*;

import com.codeprep.repository.ProblemRepository;
import com.codeprep.service.ProblemMarkdownParser;
import com.codeprep.service.TestCaseParser;
import com.codeprep.model.ExecutionRequest;
import com.codeprep.model.Language;
import com.codeprep.model.TestStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DemoProblemsIntegrationTest {
    @Test void bothBundledProblemsPassEverySampleAndHiddenCase() throws Exception {
        Path root = Path.of(getClass().getResource("/demo-problems").toURI());
        var repository = new ProblemRepository(new ProblemMarkdownParser(), new TestCaseParser(new ObjectMapper()));
        var problems = repository.discover(root);
        assertEquals(2, problems.size());
        for (var problem : problems) {
            Path javaDir = problem.directory().resolve("solutions/java");
            String source = Files.readString(javaDir.resolve("Solution.java"));
            var result = new JavaExecutor().execute(new ExecutionRequest(problem, source, Language.JAVA,
                    problem.testCases(), Duration.ofSeconds(5), javaDir));
            assertTrue(result.compilationSucceeded(), problem.title() + ": " + result.compilationErrors());
            assertEquals(problem.testCases().size(), result.testResults().size());
            assertTrue(result.testResults().stream().allMatch(test -> test.status() == TestStatus.PASSED),
                    problem.title() + " did not pass all tests: " + result.testResults());
            for (int i = 0; i < problem.testCases().size(); i++) {
                Integer limit = problem.testCases().get(i).timeLimitMs();
                if (limit != null) {
                    long duration = result.testResults().get(i).durationMillis();
                    assertTrue(duration < limit / 3, "Efficient solution needs timing headroom: " + duration + " ms, limit " + limit);
                }
            }
        }
    }

    @Test void twoSumRejectsCorrectQuadraticImplementationOnHiddenStressCase() throws Exception {
        Path root = Path.of(getClass().getResource("/demo-problems").toURI());
        var repository = new ProblemRepository(new ProblemMarkdownParser(), new TestCaseParser(new ObjectMapper()));
        var problem = repository.discover(root).stream().filter(p -> p.title().equals("Two Sum")).findFirst().orElseThrow();
        var input = problem.testCases().get(problem.testCases().size() - 1).input();
        assertEquals(300_000, input.path("nums").size());
        for (int i = 0; i < 299_998; i++) assertEquals(0, input.path("nums").get(i).intValue());
        assertEquals(1, input.path("nums").get(299_998).intValue());
        assertEquals(2, input.path("nums").get(299_999).intValue());
        assertEquals(3, input.path("target").intValue());
        String source = """
                class Solution {
                    public int[] twoSum(int[] nums, int target) {
                        for (int i = 0; i < nums.length; i++)
                            for (int j = i + 1; j < nums.length; j++)
                                if (nums[i] + nums[j] == target) return new int[] {i, j};
                        return new int[0];
                    }
                }
                """;
        var result = new JavaExecutor().execute(new ExecutionRequest(problem, source, Language.JAVA,
                problem.testCases(), Duration.ofSeconds(60), problem.directory().resolve("solutions").resolve("java")));
        assertTrue(result.compilationSucceeded(), result.compilationErrors());
        assertEquals(problem.testCases().size(), result.testResults().size());
        assertTrue(result.testResults().stream().filter(t -> t.index() <= 4).allMatch(t -> t.status() == TestStatus.PASSED));
        var stress = result.testResults().get(result.testResults().size() - 1);
        assertEquals(TestStatus.TIMED_OUT, stress.status());
        assertNull(stress.input());
        assertNull(stress.expected());
        assertNull(stress.actual());
        assertEquals("Time Limit Exceeded (limit: 1500 ms).", stress.message());
    }
}
