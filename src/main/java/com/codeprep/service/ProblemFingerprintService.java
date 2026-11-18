package com.codeprep.service;

import com.codeprep.model.Problem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class ProblemFingerprintService {
    public String statement(Problem problem) {
        String normalized = problem.descriptionMarkdown().lines().map(String::stripTrailing)
                .collect(Collectors.joining("\n"))
                .replaceFirst("\\A\\n+", "").replaceFirst("\\n+\\z", "");
        if (normalized.isBlank()) throw new IllegalArgumentException("The problem statement is empty.");
        return sha256(normalized);
    }

    public String title(Problem problem) {
        return problem.title().strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public String tests(Problem problem) {
        var cases = JsonNodeFactory.instance.arrayNode();
        for (var test : problem.testCases()) {
            var value = cases.addObject();
            value.put("visibility", test.visibility().name());
            if (test.timeLimitMs() != null) value.put("timeLimitMs", test.timeLimitMs());
            value.set("input", canonical(test.input()));
            value.set("expected", canonical(test.expected()));
        }
        return sha256(cases.toString());
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            var result = JsonNodeFactory.instance.objectNode();
            var names = new TreeSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            for (String name : names) result.set(name, canonical(node.get(name)));
            return result;
        }
        if (node.isArray()) {
            var result = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        return node;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}
