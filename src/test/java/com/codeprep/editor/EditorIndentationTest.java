package com.codeprep.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EditorIndentationTest {
    @Test void tabInsertsAtTheCaret() {
        var edit = EditorIndentation.tab("abc", 1, 1, false);
        assertEquals(new EditorIndentation.Edit(1, 1, "    ", 5, 5), edit);
    }

    @Test void indentAndUnindentSelectedLinesPreservesSelectionDirection() {
        String text = "one\ntwo\nthree";
        var indented = EditorIndentation.tab(text, 7, 0, false);
        assertEquals(new EditorIndentation.Edit(0, 7, "    one\n    two", 15, 4), indented);
        String updated = indented.replacement() + text.substring(indented.end());
        var unindented = EditorIndentation.tab(updated, indented.anchor(), indented.caret(), true);
        assertEquals(new EditorIndentation.Edit(0, 15, "one\ntwo", 7, 0), unindented);
    }

    @Test void selectionEndingAtNextLineStartDoesNotIndentThatLine() {
        var edit = EditorIndentation.tab("one\ntwo", 0, 4, false);
        assertEquals(new EditorIndentation.Edit(0, 3, "    one", 4, 8), edit);
    }

    @Test void unindentOnlyRemovesExistingWhitespace() {
        var edit = EditorIndentation.tab("  hello", 3, 3, true);
        assertEquals(new EditorIndentation.Edit(0, 7, "hello", 1, 1), edit);
        assertEquals(new EditorIndentation.Edit(0, 4, "name", 2, 2),
                EditorIndentation.tab("name", 2, 2, true));
    }

    @Test void tabUsesTheSelectedLanguagesIndentation() {
        assertEquals(new EditorIndentation.Edit(1, 1, "  ", 3, 3),
                EditorIndentation.tab("abc", 1, 1, false, "  "));
        assertEquals(new EditorIndentation.Edit(0, 7, "hello", 1, 1),
                EditorIndentation.tab("  hello", 3, 3, true, "  "));
    }
}
