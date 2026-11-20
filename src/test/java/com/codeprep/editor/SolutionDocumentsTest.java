package com.codeprep.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SolutionDocumentsTest {
    @Test void richTextFxLineEndingNormalizationDoesNotMarkUneditedFilesDirty() {
        assertTrue(SolutionDocuments.sameContent("class Solution {\r\n}\r\n", "class Solution {\n}\n"));
        assertTrue(SolutionDocuments.sameContent("one\rtwo", "one\ntwo"));
        assertFalse(SolutionDocuments.sameContent("one\n", "two\n"));
        assertFalse(SolutionDocuments.sameContent("one\n", "one"));
        assertFalse(SolutionDocuments.sameContent("one ", "one"));
    }
}
