# CodePrep

CodePrep is an offline JavaFX desktop application for local, LeetCode-style interview practice. It requires JDK 17 or newer and Maven 3.8+. The supported solution language in this release is Java.

## Build, test, and launch

```text
mvn clean test
mvn clean package
mvn javafx:run
```

On a new installation, CodePrep creates `~/.codeprep/problems`, installs only the bundled **Two Sum** question, and opens it selected. Initialization is persisted in `~/.codeprep/settings.json`; later launches never reinstall deleted defaults or overwrite edits. Existing installations retain their configured library, question paths, saved solutions, and progress. A missing configured library or unreadable settings file is reported rather than silently reset: restore the library at its original path or repair the settings file. Progress remains in `progress.json` at the library root. Maven downloads dependencies during the first build; the application itself does not require network access.

The application uses the active JDK for both compilation and launching solutions. If Maven is using a JRE or an unexpected JDK, set `JAVA_HOME` to the JDK 17+ installation and ensure its `bin` directory is on `PATH`.

## Using the application

- Search by problem title or topic and filter by difficulty. Random Problem chooses an unsolved problem when one exists.
- The description pane renders Markdown. The Java editor edits and saves only `solutions/java/Solution.java`; `TestAdapter.java` is never loaded into the editor.
- **Run** executes SAMPLE tests only and never marks a problem solved. **Run All Tests** executes both SAMPLE and HIDDEN tests. A problem is marked solved only if every test in a Run All Tests execution passes.
- The Results tab shows generated labels and statuses, including **Time Limit Exceeded**. A timed-out case shows its execution limit and does not mark the problem solved. Hidden case details and console output are omitted; hidden failures show only a generic message (or the time-limit message), status, and duration. Execution continues with the remaining cases after a timeout.
- Use the native OS title bar to minimize, maximize, or close the window. Closing with unsaved editor changes asks for confirmation.
- Ctrl+S saves and Ctrl+Enter runs sample tests (use Command on macOS). Tab indents, Shift+Tab unindents, and Enter carries indentation forward. The editor displays the current line and column.
- Settings display the library path read-only and retain the default per-test execution timeout (1–60 seconds) and theme. Explicit question-specific limits override this default. Use **Import Questions** to add questions; it never switches the library.

## Importing questions

Choose **Import Questions** in the toolbar and select either one question folder or a parent containing multiple question folders. Scanning is recursive and runs in the background. The preview lists every candidate with a reason:

| Status | Default action |
|---|---|
| New | Import to a new, uniquely named folder |
| Already exists | Skip: same normalized statement and equivalent tests |
| Needs review | Skip: same statement with different tests, or same normalized title with a different statement |
| Invalid | Skip and display the validation error |

Select **Reviewed: import separately** on individual conflicts to import additional copies; existing questions are never overwritten or updated. Statement identity uses SHA-256 after parsing out title/difficulty/topics and normalizing line endings and trailing whitespace. Indentation, case, and punctuation remain meaningful. Solution edits and paths do not affect identity. Test comparison canonicalizes JSON object keys but preserves array order, values, SAMPLE/HIDDEN visibility, and explicit time limits. Changing a time limit requires review even when the input and expected output are unchanged. Duplicates within the selected batch are also detected.

Only `problem.md`, `testcases.json`, and Java source files under `solutions/java` are copied. Source progress, compiled files, and unrelated files are not imported. Symbolic links and source folders overlapping the library are rejected. Each question is staged and validated outside the library before moving into a unique destination without replacement. Changes to statement identity, tests, or title since preview require another scan. Scanning/copying and execution cannot run concurrently. Import completion reports imported/skipped/invalid/failed counts and per-folder details; existing selection, editor drafts, saved solutions, and progress are retained. If the existing library has invalid folders, repair the reported folders before importing so duplicate checking remains reliable.

## Problem directory format

Problems are discovered recursively. A problem folder is valid when it contains both `problem.md` and `testcases.json`; its solution folder is optional.

```text
MyProblems/
  Arrays/
    TwoSum/
      problem.md
      testcases.json
      solutions/
        java/
          Solution.java
          TestAdapter.java
```

`problem.md` begins with a title heading and may include `Difficulty: Easy|Medium|Hard` and a comma-separated `Topics:` line. The remaining statement must not be empty and is rendered with CommonMark.

Each case must have `visibility` (`SAMPLE` or `HIDDEN`), `input`, and `expected`; cases are positional and must not have names or IDs:

```json
{
  "testCases": [
    { "visibility": "SAMPLE", "input": { "values": [4, 9] }, "expected": 13 },
    { "visibility": "HIDDEN", "input": { "values": [20, 22] }, "expected": 42, "timeLimitMs": 1500 }
  ]
}
```

Inputs and outputs can be any JSON values: objects, arrays, strings, numbers, booleans, or null. Import previews expose missing files, empty statements, and malformed test cases. Invalid existing folders are skipped during normal library discovery and described in the application log.

`timeLimitMs` is optional and must be an integer from 1 to 60000; null, strings, fractions, and out-of-range values are invalid. When omitted, the case uses the Settings default. An explicit limit always takes precedence, whether shorter or longer than the default. Existing files without limits require no migration.

Execution time is wall-clock time inside the child JVM around adapter construction and `TestAdapter.execute()`, including input conversion, solution execution, and output conversion performed by the adapter. Compilation, JVM startup, JSON input loading, and final result serialization/communication are excluded. Startup/input loading and final communication each have a separate 15-second watchdog allowance; failures in these phases are execution errors rather than algorithm timeouts. Infinite loops are forcibly terminated. Timings are local-machine measurements, not static complexity analysis; question authors should use worst-case inputs and choose limits with substantial headroom for efficient solutions.

The bundled Two Sum question includes a 300,000-element hidden case with its only valid pair at the end and a 1500 ms limit. Its stated input-size bound has been raised accordingly so quadratic implementations can be rejected without relying on fragile sub-millisecond limits. Samples remain small. Updated bundled files apply to new installations only: existing questions and solutions are never overwritten. Existing users can import `src\main\resources\demo-problems\Arrays\TwoSum` as a separate reviewed question to obtain the performance case.

## Java adapter contract

An adapter is required for execution and lives at `solutions/java/TestAdapter.java`. It must be in the default package, define a public class named `TestAdapter`, and implement `com.codeprep.api.TestAdapter`:

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestAdapter implements com.codeprep.api.TestAdapter {
    @Override
    public JsonNode execute(JsonNode input, ObjectMapper mapper) throws Exception {
        int[] values = mapper.convertValue(input.get("values"), int[].class);
        int result = new Solution().sum(values);
        return mapper.valueToTree(result);
    }
}
```

The adapter receives one JSON `input` per test and returns the JSON-compatible result. It is compiled alongside the user's `Solution.java` and a generated runner. The adapter can define problem-specific data structures (for example, converting arrays to linked-list nodes). Keep `Solution` separate from the adapter. Complete adapters for Two Sum and Reverse Linked List are included in the bundled resources; only Two Sum is installed automatically. The execution layer uses a `LanguageExecutor` registry so future language executors can be registered independently.

## Packaging

`mvn clean package` creates the application JAR and runtime dependencies in `target/distribution` and `target/distribution/lib`. Run `jpackage --help` on the target machine and use its installed JDK. For an installer, Windows packaging also requires WiX Toolset; macOS packaging must run on macOS and may require signing/notarization for broad distribution. For example, from the project root:

```text
jpackage --type exe --name CodePrep --input target/distribution --main-jar codeprep-1.0.0.jar --main-class com.codeprep.app.CodePrepApplication --module-path target/distribution/lib --add-modules javafx.controls,javafx.web
```

On macOS, use `--type dmg` instead of `--type exe`. Build each native package on its target operating system (Windows packages on Windows, macOS packages on macOS); jpackage does not cross-compile native launchers. Confirm JavaFX modules and native libraries are resolved for the target JDK/platform before distributing an installer.

## Architecture and limitations

The UI is JavaFX with RichTextFX editing, CommonMark rendering, and CSS styling. Jackson handles test-case and progress JSON. Problem discovery/parsing, progress, and execution are separate services/repositories. Java execution compiles in a unique temporary directory and starts a separate JVM process for each test case, with execution-phase deadlines, per-case limits or a configurable default, and cleanup. The process is **not a security sandbox**: submitted code can access the user's files and network with the permissions of the current account. Do not execute untrusted solutions on a machine containing data you need to protect. Stronger OS-level isolation is outside this version.

Hidden-case concealment is a UI/presentation guarantee, not cryptographic protection; users can inspect their local problem files. The editor provides syntax highlighting and basic indentation, not full IDE completion or diagnostics. Automated tests cover discovery, parsing, compilation/execution, timeout handling primitives, and hidden-result sanitization; native installer creation and visual interaction should be manually checked on each target OS.
