# Question Generation Prompt

Copy the prompt below into your coding agent. Replace the bracketed values before sending it.

---

Generate original Java interview-practice questions in the exact file format below. Save them in the new folder supplied by the user. Create the actual files, not just examples or instructions. No application source code or project knowledge is required.

## My requirements

- New standalone destination folder (absolute path): `[DESTINATION_FOLDER]`
- Number of questions: `[COUNT]`
- Topics: `[TOPICS, e.g. arrays, strings, binary search, trees]`
- Difficulty mix: `[e.g. 3 Easy, 4 Medium, 3 Hard]`
- Additional preferences: `[PREFERENCES, or none]`

If a required value is missing, ask for it before generating questions. Create the supplied destination folder if it does not exist; an existing empty folder is also acceptable. If it already contains files or folders, ask for a new empty destination rather than merging or overwriting anything.

Only generate question files. Do not build an application, importer, or execution framework.

## Required folder structure

Group questions by topic, using descriptive folder names:

```text
[DESTINATION_FOLDER]/
  Arrays/
    UniqueQuestionName/
      problem.md
      testcases.json
      solutions/
        java/
          Solution.java
          TestAdapter.java
```

Each question must be self-contained. Do not create progress files, settings files, compiled classes, or runtime dependencies inside the exported question folders.

## Problem statement

Use this format for `problem.md`:

```markdown
# Question Title

Difficulty: Medium

Topics: Array, HashMap

## Problem

An unambiguous problem description with the Java method signature.

## Examples

At least two examples with input, output, and explanation.

## Constraints

Explicit input sizes, value ranges, and assumptions.

## Performance

Expected time and space complexity, and the execution limit for performance cases.
```

Use only `Easy`, `Medium`, or `Hard` for difficulty. Define behavior for relevant edge cases, overflow, duplicates, empty inputs, and invalid inputs. Either exclude invalid inputs explicitly or specify their results. Every test must respect the stated constraints.

Write original statements and examples; do not copy proprietary problem text or test sets. Vary the reasoning required instead of producing superficial renamings of the same question.

## Java solution and adapter

- Target Java 17.
- Use the default package, with no `package` declaration.
- `Solution.java` must contain an editable `class Solution` with the documented method signature and a compiling starter body. Use a clear `UnsupportedOperationException("Implement this method")` placeholder rather than returning misleading dummy answers.
- `TestAdapter.java` must define `public class TestAdapter implements com.codeprep.api.TestAdapter`.
- Its method signature must be:

```java
@Override
public JsonNode execute(JsonNode input, ObjectMapper mapper) throws Exception
```

- Import `com.fasterxml.jackson.databind.JsonNode` and `com.fasterxml.jackson.databind.ObjectMapper`.
- Convert JSON input to Java values, call the corresponding `Solution` method, and return a JSON-compatible result.
- Keep problem-specific node/data classes inside the two supported Java files, with no conflicting top-level class names. Define any types used by the public solution signature so the starter compiles with the adapter.
- Use only the JDK, Jackson, and the adapter interface specified above. The interface is supplied by the consuming environment; do not generate or install it in the destination.
- Keep adapters lightweight and deterministic. Do not compute expected answers, implement the solution, read external files, access the network, or generate random data at runtime.
- If multiple outputs are valid, explicitly define canonical output in the statement and normalize the actual result in the adapter where appropriate. Arrays are compared positionally; do not assume arbitrary ordering or alternative valid answers will be accepted.
- Do not silently hide malformed outputs or solution errors. Normalization must preserve wrong-answer detection.

The adapter contract looks like this; adapt names and data types to each question:

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestAdapter implements com.codeprep.api.TestAdapter {
    @Override
    public JsonNode execute(JsonNode input, ObjectMapper mapper) throws Exception {
        int[] values = mapper.convertValue(input.get("values"), int[].class);
        int result = new Solution().solve(values);
        return mapper.valueToTree(result);
    }
}
```

## Test-case format

`testcases.json` must be strict JSON containing a `testCases` array:

```json
{
  "testCases": [
    {
      "visibility": "SAMPLE",
      "input": { "values": [4, 9] },
      "expected": 13
    },
    {
      "visibility": "HIDDEN",
      "timeLimitMs": 1500,
      "input": { "values": [20, 22] },
      "expected": 42
    }
  ]
}
```

This example demonstrates the schema only, not a sufficient performance test.

- Each case requires `visibility`, `input`, and `expected`.
- Visibility must be exactly `SAMPLE` or `HIDDEN`.
- Cases are positional: do not add names or IDs.
- Input and expected output can be any JSON value, including null.
- `timeLimitMs` is optional. When present, it must be a whole-number JSON integer from 1 to 60000. Do not use null, strings, or decimals.
- Do not include comments, duplicate JSON keys, trailing commas, generators, ranges, ellipses, or references to external fixtures. Store all input data and expected outputs as literal JSON values.

For each question, provide at least 3 SAMPLE cases and 8 HIDDEN cases. Samples must match the statement. Hidden tests must cover relevant boundary conditions, tricky correctness cases, and at least 2 performance cases.

## Performance tests that reject inefficient solutions

The questions must distinguish a correct efficient implementation from at least one plausible, correct-but-inefficient implementation.

- Choose large, adversarial inputs that force the inefficient approach to perform its worst-case work. Avoid inputs that permit immediate early return.
- Keep all stress inputs within the documented constraints. If larger constraints are necessary, update the statement before generating tests.
- Specify `timeLimitMs` explicitly on performance cases.
- Prefer generous execution budgets over fragile tiny deadlines. Where Java execution is available, measure efficient and inefficient reference implementations on the generated inputs and choose limits with substantial headroom for the efficient implementation.
- Increase input sizes or improve adversarial coverage if both implementations finish comfortably within the limit. Keep file sizes practical and stay within the 60000 ms maximum.
- Prefer compact expected outputs for very large inputs; keep serialized results below 1 MiB.
- Do not add intentional sleeps, artificial repeated calls, or implementation-specific tricks to make a solution time out. Test genuine algorithmic work.

## Validation before delivery

For every question:

1. Validate the folder structure, Markdown metadata, strict JSON schema, limit values, and consistency of statements, signatures, adapters, and tests.
2. Verify that the starter signature, adapter invocation, input structure, and expected output structure agree.
3. Independently verify expected outputs using a simple correctness oracle for small cases and a separately justified construction or independent algorithm for large cases.
4. Where execution tools are available, validate an efficient reference solution and compare it with a correct-but-inefficient implementation. Keep reference code and validation artifacts outside the destination.
5. Leave the starter in the exported `Solution.java`, not a completed solution. Remove only temporary artifacts you created; do not delete existing user files.

Do not require an existing application, install a framework, or generate extra project files to complete this task. If execution tools or supplied dependencies are unavailable, still generate all question files and clearly state which checks were not run. Never claim measured timeout behavior without actually measuring it.

## Final response

Give the absolute destination path and a concise table containing each question's relative folder, title, difficulty, topics, SAMPLE/HIDDEN counts, performance limits, and validation status. Highlight any unresolved limitations. Do not create additional summary or reference-solution files inside the exported question folders.
