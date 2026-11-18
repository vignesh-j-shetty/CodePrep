package com.codeprep.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Problem-specific bridge between language-independent JSON inputs and a Solution method.
 * Implement this interface in solutions/java/TestAdapter.java; the adapter source is never edited by the user.
 */
public interface TestAdapter {
    JsonNode execute(JsonNode input, ObjectMapper mapper) throws Exception;
}
