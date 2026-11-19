package com.codeprep.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Objects;

/** An open language identifier, serialized compatibly with the original enum. */
public record Language(String name) {
    public static final Language JAVA = new Language("JAVA");

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public Language {
        Objects.requireNonNull(name, "name");
        name = name.toUpperCase(Locale.ROOT);
        if (!name.matches("[A-Z][A-Z0-9_]*")) throw new IllegalArgumentException("Invalid language ID: " + name);
    }

    public static Language valueOf(String name) { return new Language(name); }

    @Override @JsonValue public String name() { return name; }
    @Override public String toString() { return name; }
}
