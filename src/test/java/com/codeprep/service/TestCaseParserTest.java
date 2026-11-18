package com.codeprep.service;

import static org.junit.jupiter.api.Assertions.*;

import com.codeprep.model.TestCaseVisibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestCaseParserTest {
    @TempDir Path temp;
    private final TestCaseParser parser = new TestCaseParser(new ObjectMapper());

    @Test void parsesNestedValuesAndNull() throws Exception {
        Path file = temp.resolve("cases.json");
        Files.writeString(file, """
                {"testCases":[
                  {"visibility":"SAMPLE","input":{"x":[1,{"y":true}],"nullable":null},"expected":[1,2]},
                  {"visibility":"HIDDEN","input":[false,"text"],"expected":null}
                ]}
                """);
        var cases = parser.parse(file);
        assertEquals(2, cases.size());
        assertEquals(1, cases.get(0).index());
        assertEquals(2, cases.get(1).index());
        assertEquals(TestCaseVisibility.SAMPLE, cases.get(0).visibility());
        assertTrue(cases.get(0).input().path("x").get(1).path("y").asBoolean());
        assertTrue(cases.get(0).input().get("nullable").isNull());
        assertTrue(cases.get(1).expected().isNull());
        assertNull(cases.get(0).timeLimitMs());
    }

    @Test void parsesExplicitTimeLimitsIncludingBoundaries() throws Exception {
        Path file = temp.resolve("limits.json");
        Files.writeString(file, """
                {"testCases":[
                  {"visibility":"SAMPLE","input":null,"expected":null,"timeLimitMs":1},
                  {"visibility":"HIDDEN","input":null,"expected":null,"timeLimitMs":60000}
                ]}
                """);
        var cases = parser.parse(file);
        assertEquals(1, cases.get(0).timeLimitMs());
        assertEquals(60_000, cases.get(1).timeLimitMs());
        assertEquals(java.time.Duration.ofMillis(1), cases.get(0).effectiveTimeout(java.time.Duration.ofSeconds(5)));
    }

    @Test void rejectsInvalidTimeLimits() throws Exception {
        Path file = temp.resolve("limits.json");
        for (String invalid : java.util.List.of("null", "0", "-1", "60001", "1.5", "1.0", "\"100\"", "true", "[]", "99999999999999999999")) {
            Files.writeString(file, """
                    {"testCases":[{"visibility":"HIDDEN","input":null,"expected":null,"timeLimitMs":%s}]}
                    """.formatted(invalid));
            assertTrue(assertThrows(java.io.IOException.class, () -> parser.parse(file)).getMessage().contains("timeLimitMs"), invalid);
        }
    }

    @Test void rejectsMissingFieldsAndUnsupportedVisibility() throws Exception {
        Path file = temp.resolve("cases.json");
        Files.writeString(file, """
                {"testCases":[{"input":{},"expected":1}]}
                """);
        assertTrue(assertThrows(Exception.class, () -> parser.parse(file)).getMessage().contains("visibility"));
        Files.writeString(file, """
                {"testCases":[{"visibility":"SECRET","input":{},"expected":1}]}
                """);
        assertTrue(assertThrows(Exception.class, () -> parser.parse(file)).getMessage().contains("unsupported"));
        Files.writeString(file, """
                {"testCases":[{"visibility":"SAMPLE","input":{}}]}
                """);
        assertTrue(assertThrows(Exception.class, () -> parser.parse(file)).getMessage().contains("input and expected"));
        Files.writeString(file, "{broken");
        assertThrows(Exception.class, () -> parser.parse(file));
    }

    @Test void rejectsTrailingDocumentsAndAmbiguousDuplicateKeys() throws Exception {
        Path file = temp.resolve("cases.json");
        Files.writeString(file, "{\"testCases\":[]} {\"testCases\":[]}");
        assertThrows(java.io.IOException.class, () -> parser.parse(file));
        Files.writeString(file, "{\"testCases\":[],\"testCases\":[]}");
        assertThrows(java.io.IOException.class, () -> parser.parse(file));
    }

    @Test void retainsDecimalPrecisionForTestComparison() throws Exception {
        Path file = temp.resolve("cases.json");
        Files.writeString(file, """
                {"testCases":[{"visibility":"SAMPLE","input":0.12345678901234567890123456789,"expected":null}]}
                """);
        assertEquals(new java.math.BigDecimal("0.12345678901234567890123456789"),
                parser.parse(file).get(0).input().decimalValue());
    }
}
