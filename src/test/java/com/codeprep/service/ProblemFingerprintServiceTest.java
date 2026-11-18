package com.codeprep.service;

import static org.junit.jupiter.api.Assertions.*;

import com.codeprep.model.Problem;
import com.codeprep.model.TestCase;
import com.codeprep.model.TestCaseVisibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProblemFingerprintServiceTest {
    private final ProblemFingerprintService fingerprints = new ProblemFingerprintService();
    private final ProblemMarkdownParser parser = new ProblemMarkdownParser();
    private final ObjectMapper mapper = new ObjectMapper();

    private Problem problem(String markdown) {
        var parsed = parser.parse(markdown);
        return new Problem("id", parsed.title(), parsed.difficulty(), parsed.topics(), parsed.markdown(), Path.of("question"), List.of());
    }

    @Test void normalizesLineEndingsTrailingWhitespaceAndExcludesMetadata() {
        Problem first = problem("# Original\r\nDifficulty: Easy\r\nTopics: Arrays\r\n\r\nFind X.  \r\n    code();\t\r\n");
        Problem second = problem("# Renamed\nDifficulty: Hard\nTopics: Strings\n\nFind X.\n    code();\n");
        assertEquals(fingerprints.statement(first), fingerprints.statement(second));
        assertEquals(64, fingerprints.statement(first).length());
        assertNotEquals(fingerprints.title(first), fingerprints.title(second));
        assertEquals(fingerprints.statement(second),
                fingerprints.statement(problem("# Another\rFind X.\u2003\r    code();\f\r")));
    }

    @Test void preservesIndentationCaseAndPunctuationIncludingFirstStatementLine() {
        for (String changed : List.of("find X.", "Find X!", "    Find X.")) {
            assertNotEquals(fingerprints.statement(problem("# Title\nFind X.")),
                    fingerprints.statement(problem("# Title\n" + changed)));
        }
        assertNotEquals(fingerprints.statement(problem("# T\n    code();")),
                fingerprints.statement(problem("# T\ncode();")));
    }

    @Test void neverUsesAnEmptyStatementAsAKey() {
        assertThrows(IllegalArgumentException.class, () -> fingerprints.statement(problem("# Only a title\nDifficulty: Easy\n")));
        assertThrows(IllegalArgumentException.class, () -> fingerprints.statement(problem("  \r\n\t")));
    }

    @Test void normalizesTitlesWithoutChangingStatementIdentity() {
        assertEquals(fingerprints.title(problem("#  TWO   Sum \nOne")),
                fingerprints.title(problem("# two sum\nTwo")));
    }

    @Test void canonicalizesNestedObjectKeysButPreservesArraysValuesAndVisibility() throws Exception {
        var first = withTests("{\"b\":[1,{\"z\":2,\"a\":3}],\"a\":null}", TestCaseVisibility.SAMPLE);
        var reordered = withTests("{\"a\":null,\"b\":[1,{\"a\":3,\"z\":2}]}", TestCaseVisibility.SAMPLE);
        assertEquals(fingerprints.tests(first), fingerprints.tests(reordered));
        assertNotEquals(fingerprints.tests(first), fingerprints.tests(withTests(
                "{\"a\":null,\"b\":[{\"a\":3,\"z\":2},1]}", TestCaseVisibility.SAMPLE)));
        assertNotEquals(fingerprints.tests(first), fingerprints.tests(withTests(
                "{\"b\":[1,{\"z\":2,\"a\":\"3\"}],\"a\":null}", TestCaseVisibility.SAMPLE)));
        assertNotEquals(fingerprints.tests(first), fingerprints.tests(withTests(
                "{\"b\":[1,{\"z\":2,\"a\":3}],\"a\":null}", TestCaseVisibility.HIDDEN)));
    }

    private Problem withTests(String input, TestCaseVisibility visibility) throws Exception {
        return new Problem("id", "Title", com.codeprep.model.Difficulty.EASY, List.of(), "Statement", Path.of("other"),
                List.of(new TestCase(visibility, mapper.readTree(input), mapper.readTree("true"))));
    }

    @Test void preservesTestCaseOrderAndExpectedValues() throws Exception {
        var first = withTests("1", TestCaseVisibility.SAMPLE);
        var second = withTests("2", TestCaseVisibility.HIDDEN);
        var ordered = new Problem("id", "Title", first.difficulty(), List.of(), "Statement", Path.of("question"),
                List.of(first.testCases().get(0), second.testCases().get(0)));
        var reversed = new Problem("id", "Title", first.difficulty(), List.of(), "Statement", Path.of("question"),
                List.of(second.testCases().get(0), first.testCases().get(0)));
        assertNotEquals(fingerprints.tests(ordered), fingerprints.tests(reversed));
        var changedExpected = new Problem("id", "Title", first.difficulty(), List.of(), "Statement", Path.of("question"),
                List.of(new TestCase(TestCaseVisibility.SAMPLE, mapper.readTree("1"), mapper.readTree("false"))));
        assertNotEquals(fingerprints.tests(first), fingerprints.tests(changedExpected));
    }

    @Test void includesExplicitTimeLimitsInTestIdentity() throws Exception {
        Problem original = withTests("1", TestCaseVisibility.HIDDEN);
        TestCase test = original.testCases().get(0);
        var limited = new Problem("id", "Title", original.difficulty(), List.of(), "Statement", Path.of("question"),
                List.of(new TestCase(1, test.visibility(), test.input(), test.expected(), 1500)));
        var changed = new Problem("id", "Title", original.difficulty(), List.of(), "Statement", Path.of("question"),
                List.of(new TestCase(1, test.visibility(), test.input(), test.expected(), 2000)));
        assertNotEquals(fingerprints.tests(original), fingerprints.tests(limited));
        assertNotEquals(fingerprints.tests(limited), fingerprints.tests(changed));
    }
}
