package com.codeprep.executor;

import com.codeprep.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Compiles and runs a problem adapter and solution in an isolated temporary process (not a security sandbox). */
public final class JavaExecutor implements LanguageExecutor {
    private static final Logger log = LoggerFactory.getLogger(JavaExecutor.class);
    private static final String RESULT_PREFIX = "CODEPREP_RESULT:";
    private static final String ERROR_PREFIX = "CODEPREP_ERROR:";
    private static final Duration PROCESS_OVERHEAD_ALLOWANCE = Duration.ofSeconds(15);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override public Language getLanguage() { return Language.JAVA; }

    @Override public ExecutionResult execute(ExecutionRequest request) {
        long start = System.nanoTime();
        Path temp = null;
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) return compilationFailure("A full JDK is required; no Java compiler was found.");
            Path adapter = request.solutionDirectory().resolve("TestAdapter.java");
            if (!Files.isRegularFile(adapter)) return compilationFailure("TestAdapter.java is missing from the problem's solutions/java directory.");
            temp = Files.createTempDirectory("codeprep-run-");
            Path solution = temp.resolve("Solution.java");
            Files.writeString(solution, request.sourceCode(), StandardCharsets.UTF_8);
            Path runner = temp.resolve("CodePrepGeneratedRunner.java");
            Files.writeString(runner, generatedRunner(), StandardCharsets.UTF_8);
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                var units = manager.getJavaFileObjects(solution.toFile(), adapter.toFile(), runner.toFile());
                List<String> options = List.of("-classpath", runtimeClassPath(), "-d", temp.toString(), "-encoding", "UTF-8");
                boolean compiled = Boolean.TRUE.equals(compiler.getTask(null, manager, diagnostics, options, null, units).call());
                if (!compiled) {
                    String errors = diagnostics.getDiagnostics().stream()
                            .map(d -> d.getKind() + " line " + d.getLineNumber() + ": " + d.getMessage(null))
                            .reduce((a, b) -> a + System.lineSeparator() + b).orElse("Compilation failed.");
                    return new ExecutionResult(List.of(), errors, "", elapsed(start), false);
                }
            }
            List<TestCaseResult> results = new ArrayList<>();
            StringBuilder output = new StringBuilder();
            for (int i = 0; i < request.testCases().size(); i++) {
                TestCase test = request.testCases().get(i);
                Duration timeout = test.effectiveTimeout(request.timeout());
                CaseRun run = runOne(temp, i + 1, test.input(), timeout);
                TestStatus status;
                JsonNode actual = null;
                String message = "";
                if (run.timedOut) {
                    status = TestStatus.TIMED_OUT;
                    message = "Time Limit Exceeded (limit: " + timeout.toMillis() + " ms).";
                } else if (run.error != null) {
                    status = TestStatus.ERROR;
                    message = test.visibility() == TestCaseVisibility.SAMPLE ? run.error : "Hidden test case failed. Input and expected output are not available.";
                } else {
                    actual = run.actual;
                    status = jsonEquals(actual, test.expected()) ? TestStatus.PASSED : TestStatus.FAILED;
                    if (status == TestStatus.FAILED)
                        message = test.visibility() == TestCaseVisibility.SAMPLE ? "Output did not match expected value." : "Hidden test case failed. Input and expected output are not available.";
                }
                if (test.visibility() == TestCaseVisibility.SAMPLE) {
                    if (!run.userOutput.isBlank()) output.append(run.userOutput).append(System.lineSeparator());
                    results.add(new TestCaseResult(test.index() > 0 ? test.index() : i + 1, test.visibility(), status, test.input(), test.expected(), actual,
                            run.durationMillis, message));
                } else {
                    results.add(new TestCaseResult(test.index() > 0 ? test.index() : i + 1, test.visibility(), status, null, null, null,
                            run.durationMillis, status == TestStatus.PASSED ? "" : status == TestStatus.TIMED_OUT
                                    ? message : "Hidden test case failed. Input and expected output are not available."));
                }
            }
            return new ExecutionResult(results, "", output.toString().strip(), elapsed(start), true);
        } catch (IOException | RuntimeException ex) {
            log.error("Java execution could not be completed: {}", ex.getMessage());
            return new ExecutionResult(List.of(), "", "Execution could not be completed: " + ex.getMessage(), elapsed(start), false);
        } finally {
            if (temp != null) deleteTree(temp);
        }
    }

    private CaseRun runOne(Path temp, int index, JsonNode input, Duration timeout) throws IOException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        if (!Files.isExecutable(javaExecutable)) throw new IOException("Java runtime was not found at the active JDK.");
        String classpath = temp + java.io.File.pathSeparator + runtimeClassPath();
        Path workingDirectory = Files.createDirectories(temp.resolve("case-" + index));
        byte[] payload = mapper.writeValueAsBytes(mapper.createObjectNode().set("input", input));
        Process process = new ProcessBuilder(javaExecutable.toString(), "-cp", classpath, "CodePrepGeneratedRunner")
                .directory(workingDirectory.toFile()).start();
        CompletableFuture<String> captured = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        CompletableFuture<String> errors = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
        try {
            CompletableFuture<Void> sent = CompletableFuture.runAsync(() -> {
                try (var stdin = process.getOutputStream()) {
                    stdin.write(payload);
                } catch (IOException ex) {
                    throw new CompletionException(ex);
                }
            });
            if (waitForCase(process, workingDirectory, timeout)) {
                return new CaseRun(null, null, true, "", timeout.toMillis());
            }
            sent.get(2, TimeUnit.SECONDS);
            String text = captured.get(2, TimeUnit.SECONDS);
            String stderr = errors.get(2, TimeUnit.SECONDS);
            Path durationFile = workingDirectory.resolve("duration-nanos");
            if (!Files.isRegularFile(durationFile)) {
                return new CaseRun(null, "Program terminated before completing execution. " + (text + "\n" + stderr).strip(),
                        false, "", 0);
            }
            long nanos = Long.parseLong(Files.readString(durationFile));
            if (nanos < 0) throw new IOException("The execution process returned an invalid duration.");
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(nanos);
            if (nanos > timeout.toNanos()) return new CaseRun(null, null, true, "", durationMillis);
            for (String line : text.split("\\R")) {
                if (line.startsWith(RESULT_PREFIX)) return new CaseRun(mapper.readTree(line.substring(RESULT_PREFIX.length())), null, false,
                        text.replace(line, "").strip(), durationMillis);
                if (line.startsWith(ERROR_PREFIX)) return new CaseRun(null, line.substring(ERROR_PREFIX.length()), false,
                        text.replace(line, "").strip(), durationMillis);
            }
            return new CaseRun(null, "Program terminated without returning a result. " + (text + "\n" + stderr).strip(),
                    false, "", durationMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Execution was interrupted.", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IOException("Could not communicate with the execution process.", ex);
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) {
                process.destroyForcibly();
                try {
                    if (!process.waitFor(2, TimeUnit.SECONDS)) throw new IOException("The execution process could not be terminated.");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while terminating the execution process.", ex);
                }
            }
        }
    }

    private boolean waitForCase(Process process, Path directory, Duration timeout) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + PROCESS_OVERHEAD_ALLOWANCE.toNanos();
        boolean started = false, completed = false;
        // Phase markers keep JVM startup and result serialization outside the solution's budget.
        while (!process.waitFor(10, TimeUnit.MILLISECONDS)) {
            if (!started && Files.exists(directory.resolve("execution-started"))) {
                started = true;
                deadline = System.nanoTime() + timeout.toNanos();
            }
            if (started && !completed && Files.exists(directory.resolve("execution-completed"))) {
                completed = true;
                deadline = System.nanoTime() + PROCESS_OVERHEAD_ALLOWANCE.toNanos();
            }
            if (System.nanoTime() >= deadline) {
                if (started && !completed) return true;
                throw new IOException(started ? "Execution result communication exceeded 15 seconds."
                        : "Execution process startup or input loading exceeded 15 seconds.");
            }
        }
        return false;
    }

    private boolean jsonEquals(JsonNode a, JsonNode b) {
        if (a == null || b == null) return a == b;
        if (a.isNumber() && b.isNumber()) return a.decimalValue().compareTo(b.decimalValue()) == 0;
        if (a.isArray() && b.isArray()) {
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) if (!jsonEquals(a.get(i), b.get(i))) return false;
            return true;
        }
        if (a.isObject() && b.isObject()) {
            if (a.size() != b.size()) return false;
            var fields = a.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!b.has(field.getKey()) || !jsonEquals(field.getValue(), b.get(field.getKey()))) return false;
            }
            return true;
        }
        return a.equals(b);
    }

    private ExecutionResult compilationFailure(String message) { return new ExecutionResult(List.of(), message, "", 0, false); }
    private static long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }
    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase().contains("win"); }
    private static String runtimeClassPath() {
        String classPath = System.getProperty("java.class.path", "");
        String modulePath = System.getProperty("jdk.module.path", "");
        if (classPath.isBlank()) return modulePath;
        if (modulePath.isBlank()) return classPath;
        return classPath + java.io.File.pathSeparator + modulePath;
    }
    private static String readAll(InputStream input) {
        try (input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            int retained = 0;
            while ((count = input.read(buffer)) != -1) {
                int keep = Math.min(count, 1_048_576 - retained);
                if (keep > 0) {
                    out.write(buffer, 0, keep);
                    retained += keep;
                }
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException ex) { throw new CompletionException(ex); }
    }
    private static String generatedRunner() {
        return """
                import com.fasterxml.jackson.databind.JsonNode;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import java.nio.file.Files;
                import java.nio.file.Path;
                public class CodePrepGeneratedRunner {
                    public static void main(String[] args) throws Exception {
                        ObjectMapper mapper = new ObjectMapper();
                        try {
                            JsonNode root = mapper.readTree(System.in);
                            JsonNode result;
                            Files.createFile(Path.of("execution-started"));
                            long began = System.nanoTime();
                            try {
                                result = new TestAdapter().execute(root.get("input"), mapper);
                            } finally {
                                long nanos = System.nanoTime() - began;
                                Files.writeString(Path.of("duration-nanos"), Long.toString(nanos));
                                Files.createFile(Path.of("execution-completed"));
                            }
                            System.out.println("CODEPREP_RESULT:" + mapper.writeValueAsString(result));
                        } catch (Throwable error) {
                            System.out.println("CODEPREP_ERROR:" + error.getClass().getSimpleName() + ": " +
                                    (error.getMessage() == null ? "execution failed" : error.getMessage()));
                        }
                    }
                }
                """;
    }
    private static void deleteTree(Path path) {
        try (var paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        } catch (IOException ex) { log.warn("Could not clean temporary execution directory."); }
    }
    private record CaseRun(JsonNode actual, String error, boolean timedOut, String userOutput, long durationMillis) {}

}
