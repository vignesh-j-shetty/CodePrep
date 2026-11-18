package com.codeprep.executor;

import static org.junit.jupiter.api.Assertions.*;

import com.codeprep.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaExecutorTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void runsSampleAndHiddenCasesWithoutReturningHiddenValues() throws Exception {
        Path adapterDir = Files.createDirectories(temp.resolve("solutions/java"));
        copyResource("/demo-problems/Arrays/TwoSum/solutions/java/TestAdapter.java", adapterDir.resolve("TestAdapter.java"));
        String source = resource("/demo-problems/Arrays/TwoSum/solutions/java/Solution.java");
        Problem problem = new Problem("Arrays/TwoSum", "Two Sum", Difficulty.EASY, List.of("Array"), "", temp,
                List.of());
        List<TestCase> cases = List.of(
                new TestCase(TestCaseVisibility.SAMPLE, mapper.readTree("{\"nums\":[2,7],\"target\":9}"), mapper.readTree("[0,1]")),
                new TestCase(TestCaseVisibility.HIDDEN, mapper.readTree("{\"nums\":[1,2],\"target\":3}"), mapper.readTree("[0,0]")));
        var result = new JavaExecutor().execute(new ExecutionRequest(problem, source, Language.JAVA, cases,
                Duration.ofSeconds(4), adapterDir));
        assertTrue(result.compilationSucceeded(), result.compilationErrors());
        assertEquals(TestStatus.PASSED, result.testResults().get(0).status());
        assertEquals(TestStatus.FAILED, result.testResults().get(1).status());
        assertNull(result.testResults().get(1).input());
        assertNull(result.testResults().get(1).expected());
        assertNull(result.testResults().get(1).actual());
        assertEquals("Hidden test case failed. Input and expected output are not available.",
                result.testResults().get(1).message());
        assertFalse(result.executionOutput().contains("\"target\":3"));
    }

    @Test void reportsCompilationErrors() throws Exception {
        Path adapterDir = Files.createDirectories(temp.resolve("bad/solutions/java"));
        copyResource("/demo-problems/Arrays/TwoSum/solutions/java/TestAdapter.java", adapterDir.resolve("TestAdapter.java"));
        Problem problem = new Problem("bad", "Bad", Difficulty.EASY, List.of(), "", temp, List.of());
        var result = new JavaExecutor().execute(new ExecutionRequest(problem, "class Solution { broken }",
                Language.JAVA, List.of(), Duration.ofSeconds(2), adapterDir));
        assertFalse(result.compilationSucceeded());
        assertTrue(result.compilationErrors().contains("ERROR"));
    }

    @Test void timesOutOneCaseAndContinuesWithTheNext() throws Exception {
        Path adapterDir = Files.createDirectories(temp.resolve("timeout/solutions/java"));
        Files.writeString(adapterDir.resolve("TestAdapter.java"), """
                import com.fasterxml.jackson.databind.JsonNode;
                import com.fasterxml.jackson.databind.ObjectMapper;
                public class TestAdapter implements com.codeprep.api.TestAdapter {
                    public JsonNode execute(JsonNode input, ObjectMapper mapper) throws Exception {
                        if (input.path("wait").asBoolean()) Thread.sleep(4000);
                        return mapper.getNodeFactory().numberNode(7);
                    }
                }
                """);
        Problem problem = new Problem("timeout", "Timeout", Difficulty.EASY, List.of(), "", temp, List.of());
        List<TestCase> cases = List.of(
                new TestCase(TestCaseVisibility.SAMPLE, mapper.readTree("{\"wait\":true}"), mapper.readTree("7")),
                new TestCase(TestCaseVisibility.SAMPLE, mapper.readTree("{\"wait\":false}"), mapper.readTree("7")));
        var result = new JavaExecutor().execute(new ExecutionRequest(problem, "class Solution {}", Language.JAVA,
                cases, Duration.ofSeconds(1), adapterDir));
        assertTrue(result.compilationSucceeded(), result.compilationErrors());
        assertEquals(TestStatus.TIMED_OUT, result.testResults().get(0).status());
        assertEquals(TestStatus.PASSED, result.testResults().get(1).status());
    }

    @Test void explicitLimitStopsAnInfiniteLoopConcealsHiddenDataAndContinues() throws Exception {
        Path adapterDir = Files.createDirectories(temp.resolve("explicit/solutions/java"));
        Files.writeString(adapterDir.resolve("TestAdapter.java"), """
                import com.fasterxml.jackson.databind.JsonNode;
                import com.fasterxml.jackson.databind.ObjectMapper;
                public class TestAdapter implements com.codeprep.api.TestAdapter {
                    public JsonNode execute(JsonNode input, ObjectMapper mapper) {
                        System.out.println("private output");
                        while (input.path("loop").asBoolean()) { }
                        return mapper.nullNode();
                    }
                }
                """);
        Problem problem = new Problem("explicit", "Explicit", Difficulty.EASY, List.of(), "", temp, List.of());
        List<TestCase> cases = List.of(
                new TestCase(1, TestCaseVisibility.HIDDEN, mapper.readTree("{\"loop\":true}"), mapper.nullNode(), 100),
                new TestCase(2, TestCaseVisibility.SAMPLE, mapper.readTree("{\"loop\":false}"), mapper.nullNode(), 100));
        var result = new JavaExecutor().execute(new ExecutionRequest(problem, "class Solution {}", Language.JAVA,
                cases, Duration.ofSeconds(60), adapterDir));
        assertTrue(result.compilationSucceeded(), result.executionOutput());
        var timedOut = result.testResults().get(0);
        assertEquals(TestStatus.TIMED_OUT, timedOut.status());
        assertEquals("Time Limit Exceeded (limit: 100 ms).", timedOut.message());
        assertEquals("Time Limit Exceeded", timedOut.status().displayName());
        assertNull(timedOut.input());
        assertNull(timedOut.expected());
        assertNull(timedOut.actual());
        assertEquals(TestStatus.PASSED, result.testResults().get(1).status());
        assertTrue(result.testResults().get(1).durationMillis() < 100);
        assertEquals("private output", result.executionOutput(), "Only the sample's output may be exposed.");
    }

    @Test void explicitLimitsCanBeLongerThanTheSettingsDefaultAndExcludeStartup() throws Exception {
        Path adapterDir = Files.createDirectories(temp.resolve("longer/solutions/java"));
        Files.writeString(adapterDir.resolve("TestAdapter.java"), """
                import com.fasterxml.jackson.databind.JsonNode;
                import com.fasterxml.jackson.databind.ObjectMapper;
                public class TestAdapter implements com.codeprep.api.TestAdapter {
                    public JsonNode execute(JsonNode input, ObjectMapper mapper) throws Exception {
                        Thread.sleep(150);
                        return mapper.nullNode();
                    }
                }
                """);
        Problem problem = new Problem("longer", "Longer", Difficulty.EASY, List.of(), "", temp, List.of());
        var test = new TestCase(1, TestCaseVisibility.SAMPLE, mapper.nullNode(), mapper.nullNode(), 1000);
        var result = new JavaExecutor().execute(new ExecutionRequest(problem, "class Solution {}", Language.JAVA,
                List.of(test), Duration.ofMillis(1), adapterDir));
        assertTrue(result.compilationSucceeded(), result.executionOutput());
        assertEquals(TestStatus.PASSED, result.testResults().get(0).status());
        assertTrue(result.testResults().get(0).durationMillis() >= 150);
        assertTrue(result.testResults().get(0).durationMillis() < 1000);
    }

    @Test void finalResultSerializationDoesNotConsumeTheExecutionBudget() throws Exception {
        Path adapterDir = Files.createDirectories(temp.resolve("serialization/solutions/java"));
        Files.writeString(adapterDir.resolve("TestAdapter.java"), """
                import com.fasterxml.jackson.databind.JsonNode;
                import com.fasterxml.jackson.databind.ObjectMapper;
                public class TestAdapter implements com.codeprep.api.TestAdapter {
                    public static class SlowResult {
                        public String getValue() throws Exception {
                            Thread.sleep(300);
                            return "ok";
                        }
                    }
                    public JsonNode execute(JsonNode input, ObjectMapper mapper) {
                        return mapper.getNodeFactory().pojoNode(new SlowResult());
                    }
                }
                """);
        Problem problem = new Problem("serialization", "Serialization", Difficulty.EASY, List.of(), "", temp, List.of());
        var test = new TestCase(1, TestCaseVisibility.SAMPLE, mapper.nullNode(), mapper.readTree("{\"value\":\"ok\"}"), 100);
        var result = new JavaExecutor().execute(new ExecutionRequest(problem, "class Solution {}", Language.JAVA,
                List.of(test), Duration.ofSeconds(5), adapterDir));
        assertTrue(result.compilationSucceeded(), result.executionOutput());
        assertEquals(TestStatus.PASSED, result.testResults().get(0).status());
        assertTrue(result.testResults().get(0).durationMillis() < 100);
    }

    @Test void abruptExitAndRuntimeErrorsAreNotTreatedAsCompletedSolutions() throws Exception {
        Path adapterDir = Files.createDirectories(temp.resolve("exit/solutions/java"));
        Files.writeString(adapterDir.resolve("TestAdapter.java"), """
                import com.fasterxml.jackson.databind.JsonNode;
                import com.fasterxml.jackson.databind.ObjectMapper;
                public class TestAdapter implements com.codeprep.api.TestAdapter {
                    public JsonNode execute(JsonNode input, ObjectMapper mapper) {
                        if (input.asText().equals("exit")) {
                            System.out.println("CODEPREP_RESULT:null");
                            System.exit(0);
                        }
                        if (input.asText().equals("error")) throw new IllegalStateException("sample error");
                        return mapper.nullNode();
                    }
                }
                """);
        Problem problem = new Problem("exit", "Exit", Difficulty.EASY, List.of(), "", temp, List.of());
        var cases = List.of(
                new TestCase(TestCaseVisibility.SAMPLE, mapper.getNodeFactory().textNode("exit"), mapper.nullNode()),
                new TestCase(TestCaseVisibility.SAMPLE, mapper.getNodeFactory().textNode("error"), mapper.nullNode()),
                new TestCase(TestCaseVisibility.SAMPLE, mapper.getNodeFactory().textNode("ok"), mapper.nullNode()));
        var result = new JavaExecutor().execute(new ExecutionRequest(problem, "class Solution {}", Language.JAVA,
                cases, Duration.ofSeconds(5), adapterDir));
        assertTrue(result.compilationSucceeded(), result.executionOutput());
        assertEquals(TestStatus.ERROR, result.testResults().get(0).status());
        assertTrue(result.testResults().get(0).message().contains("terminated before completing execution"));
        assertEquals(TestStatus.ERROR, result.testResults().get(1).status());
        assertTrue(result.testResults().get(1).message().contains("IllegalStateException: sample error"));
        assertEquals(TestStatus.PASSED, result.testResults().get(2).status());
    }

    private void copyResource(String name, Path target) throws Exception {
        try (var input = getClass().getResourceAsStream(name)) {
            assertNotNull(input);
            Files.copy(input, target);
        }
    }
    private String resource(String name) throws Exception {
        try (var input = getClass().getResourceAsStream(name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
