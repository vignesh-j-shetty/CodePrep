package com.codeprep.service;

import com.codeprep.model.TestCase;
import com.codeprep.model.TestCaseVisibility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class TestCaseParser {
    private final ObjectMapper mapper;

    public TestCaseParser(ObjectMapper mapper) { this.mapper = mapper; }

    public List<TestCase> parse(Path file) throws IOException {
        JsonNode root = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .readTree(Files.readString(file));
        if (root == null || !root.isObject() || !root.path("testCases").isArray()) {
            throw new IOException("Expected an object containing a testCases array.");
        }
        List<TestCase> result = new ArrayList<>();
        int index = 0;
        for (JsonNode node : root.path("testCases")) {
            index++;
            if (!node.isObject()) throw new IOException("Test Case " + index + " must be an object.");
            JsonNode visibility = node.get("visibility");
            if (visibility == null || !visibility.isTextual()) {
                throw new IOException("Test Case " + index + " is missing a valid visibility.");
            }
            TestCaseVisibility parsed;
            try { parsed = TestCaseVisibility.valueOf(visibility.asText()); }
            catch (IllegalArgumentException ex) {
                throw new IOException("Test Case " + index + " has unsupported visibility.", ex);
            }
            JsonNode input = node.get("input");
            JsonNode expected = node.get("expected");
            if (input == null || expected == null) throw new IOException("Test Case " + index + " must include input and expected values.");
            JsonNode limit = node.get("timeLimitMs");
            Integer timeLimitMs = null;
            if (limit != null) {
                if (!limit.isIntegralNumber() || !limit.canConvertToInt() || limit.intValue() < 1 || limit.intValue() > 60_000) {
                    throw new IOException("Test Case " + index + ": timeLimitMs must be a whole number between 1 and 60000.");
                }
                timeLimitMs = limit.intValue();
            }
            result.add(new TestCase(index, parsed, input, expected, timeLimitMs));
        }
        return List.copyOf(result);
    }
}
