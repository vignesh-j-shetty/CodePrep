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

JDK 24+ prints JavaFX 21 warnings about restricted native methods (`System::load`, `WebPage`) and `sun.misc.Unsafe` memory access. They do not affect functionality. `mvn javafx:run` passes the needed flags automatically on JDK 24+ (profile `jdk24-javafx-runtime-flags`). When launching from an IDE on JDK 24+, add these VM options; omit them on JDK 17–22, where `--sun-misc-unsafe-memory-access` is not recognized:

```text
--enable-native-access=javafx.graphics,javafx.web --sun-misc-unsafe-memory-access=allow
```

If JavaFX is on the classpath instead of the module path, use `--enable-native-access=ALL-UNNAMED`. For jpackage images built with a JDK 24+ runtime, pass the same options through `--java-options`.
## Using the application

- Search by problem title or topic and filter by difficulty. Random Problem chooses an unsolved problem when one exists.
- The description pane renders Markdown. The Java editor edits and saves only `solutions/java/Solution.java`; `TestAdapter.java` is never loaded into the editor.
- **Run** executes SAMPLE tests only and never marks a problem solved. **Run All Tests** executes both SAMPLE and HIDDEN tests. A problem is marked solved only if every test in a Run All Tests execution passes.
- The Results tab shows generated labels and statuses, including **Time Limit Exceeded**. A timed-out case shows its execution limit and does not mark the problem solved. Hidden case details and console output are omitted; hidden failures show only a generic message (or the time-limit message), status, and duration. Execution continues with the remaining cases after a timeout.
- Use the native OS title bar to minimize, maximize, or close the window. Closing with unsaved editor changes asks for confirmation.
- Ctrl+S saves and Ctrl+Enter runs sample tests (use Command on macOS). Tab indents, Shift+Tab unindents, and Enter carries indentation forward. The editor displays the current line and column.
- Ctrl+Space opens completions and snippets. Java includes keyword/document-identifier completions and `sout`, `fori`, and `main` snippets. Configured brackets are paired automatically. Ctrl+/ toggles comments; Ctrl+Shift+F formats indentation (use Command for these last two shortcuts on macOS). These actions are also available in the editor context menu.
- Syntax diagnostics appear below the editor and are underlined in the code; click the diagnostic status to jump to the first issue. Java diagnostics use the JDK parser in a background thread, not compilation of the full problem. Unresolved adapter-defined types are intentionally not flagged here; Run remains the authoritative compilation check.
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

Only `problem.md`, `testcases.json`, and registered language source files under their configured `solutions/<directory>` are copied (currently `.java` files under `solutions/java`). Source progress, compiled files, and unrelated files are not imported. Symbolic links and source folders overlapping the library are rejected. Each question is staged and validated outside the library before moving into a unique destination without replacement. Changes to statement identity, tests, or title since preview require another scan. Scanning/copying and execution cannot run concurrently. Import completion reports imported/skipped/invalid/failed counts and per-folder details; existing selection, editor drafts, saved solutions, and progress are retained. If the existing library has invalid folders, repair the reported folders before importing so duplicate checking remains reliable.

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

## Adding a language

The editor uses native RichTextFX rather than Monaco to retain the offline JavaFX application without adding a Chromium runtime. `LanguageEditor` owns widget events, rendering, completion menus, and the diagnostic lifecycle; it contains no Java rules. `LanguageProviderRegistry` discovers independent modules through Java `ServiceLoader`. Only `JavaProvider` ships today.

1. Implement `com.codeprep.editor.LanguageProvider` in a public class with a public no-argument constructor. Use `new Language("PYTHON")`, `new Language("CPP")`, etc.; language IDs are open values, not an enum that needs editing. Supply the display name, solution directory/file, source extensions, starter code, highlighting, completions/snippets, bracket pairs, comments, indentation, and newline rules. Optional formatter and diagnostic providers can wrap external tools or a language server.
2. Register the fully qualified provider class in `src\main\resources\META-INF\services\com.codeprep.editor.LanguageProvider` (or the same service descriptor in a plugin JAR on the runtime classpath). Each language ID and solution directory must be unique. For direct construction/tests, use `registry.register(provider)` before creating the view.
3. Implement `LanguageExecutor` for that language's execution/compilation API and register it in `src\main\resources\META-INF\services\com.codeprep.executor.LanguageExecutor`. Until an executor is installed, editing/saving still work and Run actions are disabled with an explanation. Problem authors must supply whatever adapter/runner files that executor needs in the language's solution directory.

No editor-core, selector, importer, or language-enum changes are needed. The selector lists registered providers and switches all rules dynamically. Drafts and reset baselines are keyed by both problem ID and language; changing languages preserves drafts, clears undo history and diagnostics, and loads the correct solution file. Save/reset and execution paths come from provider metadata. Closing checks unsaved drafts across problems and languages. Existing `"JAVA"` settings/progress and `solutions/java/Solution.java` paths remain compatible.

Providers must be stateless/thread-safe. Highlighting, completion, indentation, and bracket checks run on the UI thread and must be fast/non-blocking; language-server integrations should return cached completions rather than performing synchronous I/O there. Highlight tokens must be sorted, non-overlapping UTF-16 ranges within the source; built-in CSS styles are `keyword`, `string`, `comment`, and `number`. Completion ranges replace source text, with the caret offset relative to the inserted text. `complete()` should include desired `snippets()` entries and indent multiline snippets appropriately; snippet caret offsets follow the same convention. Comments can specify a line prefix or block delimiters. Formatter implementations must preserve program semantics. Diagnostic ranges use UTF-16 offsets too; diagnostics run on a debounced background worker, and results from old documents/languages are discarded. Providers should propagate tooling failures so the editor can log and display them. Missing optional services are reported explicitly.

The Java formatter conservatively adjusts indentation rather than applying a comprehensive Java style guide. Java completion is lexical (keywords, common types, document identifiers, snippets), not type-aware. Its diagnostics are parser-only, not semantic analysis or linting. A future Java language-server-backed provider can replace these services without modifying the editor widget. The test-only Python-like provider exercises registration, different indentation/comments/brackets, runtime switching, import paths, and execution capability without claiming Python support.

## Architecture and limitations

The UI is JavaFX with RichTextFX editing, CommonMark rendering, and CSS styling. Jackson handles test-case and progress JSON. Problem discovery/parsing, progress, and execution are separate services/repositories. Java execution compiles in a unique temporary directory and starts a separate JVM process for each test case, with execution-phase deadlines, per-case limits or a configurable default, and cleanup. The process is **not a security sandbox**: submitted code can access the user's files and network with the permissions of the current account. Do not execute untrusted solutions on a machine containing data you need to protect. Stronger OS-level isolation is outside this version.

Hidden-case concealment is a UI/presentation guarantee, not cryptographic protection; users can inspect their local problem files. Automated tests cover discovery, parsing, compilation/execution, timeout handling primitives, hidden-result sanitization, provider services, and JavaFX language switching (interaction tests require a display). Native installer creation and visual interaction should still be manually checked on each target OS.
