package com.codeprep.editor;

/** Text-only indentation operations, independent of the editor widget. */
public final class EditorIndentation {
    private static final String INDENT = "    ";

    private EditorIndentation() {}

    public record Edit(int start, int end, String replacement, int anchor, int caret) {}

    public static Edit tab(String text, int anchor, int caret, boolean unindent) {
        int selectionStart = Math.min(anchor, caret);
        int selectionEnd = Math.max(anchor, caret);
        if (selectionStart == selectionEnd && !unindent) {
            return new Edit(caret, caret, INDENT, caret + INDENT.length(), caret + INDENT.length());
        }

        int start = text.lastIndexOf('\n', selectionStart - 1) + 1;
        int lastSelected = selectionEnd > selectionStart ? selectionEnd - 1 : selectionEnd;
        int nextNewline = text.indexOf('\n', lastSelected);
        int end = nextNewline < 0 ? text.length() : nextNewline;
        String[] lines = text.substring(start, end).split("\n", -1);
        StringBuilder replacement = new StringBuilder();
        int newAnchor = anchor;
        int newCaret = caret;
        int position = start;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int removed = 0;
            if (unindent) {
                if (line.startsWith("\t")) removed = 1;
                else while (removed < line.length() && removed < INDENT.length() && line.charAt(removed) == ' ') removed++;
                replacement.append(line.substring(removed));
            } else {
                replacement.append(INDENT).append(line);
            }
            int delta = unindent ? -removed : INDENT.length();
            if (anchor >= position) newAnchor += unindent ? -Math.min(removed, anchor - position) : delta;
            if (caret >= position) newCaret += unindent ? -Math.min(removed, caret - position) : delta;
            position += line.length() + 1;
            if (i < lines.length - 1) replacement.append('\n');
        }
        return new Edit(start, end, replacement.toString(), newAnchor, newCaret);
    }

    public static String newline(String text, int selectionStart) {
        int lineStart = text.lastIndexOf('\n', selectionStart - 1) + 1;
        String beforeCaret = text.substring(lineStart, selectionStart);
        int indentation = 0;
        while (indentation < beforeCaret.length()
                && (beforeCaret.charAt(indentation) == ' ' || beforeCaret.charAt(indentation) == '\t')) indentation++;
        String prefix = beforeCaret.substring(0, indentation);
        return "\n" + prefix + (beforeCaret.stripTrailing().endsWith("{") ? INDENT : "");
    }
}
