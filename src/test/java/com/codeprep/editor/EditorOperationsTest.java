package com.codeprep.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EditorOperationsTest {
    @Test void togglesConfiguredLineCommentsAndPreservesIndentation() {
        var style = new LanguageProvider.CommentStyle("#", null, null);
        String source = "  one\n  two\nthree";
        var edit = EditorOperations.toggleComment(source, 0, 12, style);
        assertEquals("  # one\n  # two", edit.replacement());
        assertEquals(0, edit.start());
        assertEquals(11, edit.end());
        var reverse = EditorOperations.toggleComment(edit.replacement(), edit.caret(), edit.anchor(), style);
        assertEquals("  one\n  two", reverse.replacement());
        assertTrue(reverse.anchor() > reverse.caret());
    }

    @Test void supportsBlockOnlyCommentsAndReportsMissingConfiguration() {
        var style = new LanguageProvider.CommentStyle(null, "<!--", "-->");
        var edit = EditorOperations.toggleComment("text", 0, 4, style);
        assertEquals("<!--text-->", edit.replacement());
        assertEquals("text", EditorOperations.toggleComment(edit.replacement(), 0, edit.caret(), style).replacement());
        assertThrows(UnsupportedOperationException.class, () -> EditorOperations.toggleComment("x", 0, 1,
                new LanguageProvider.CommentStyle(null, null, null)));
    }
}
