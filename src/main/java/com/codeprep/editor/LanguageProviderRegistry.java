package com.codeprep.editor;

import com.codeprep.model.Language;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public final class LanguageProviderRegistry {
    private final Map<Language, LanguageProvider> providers = new LinkedHashMap<>();

    public LanguageProviderRegistry(Collection<LanguageProvider> registered) {
        registered.forEach(this::register);
    }

    public static LanguageProviderRegistry discover() {
        return new LanguageProviderRegistry(ServiceLoader.load(LanguageProvider.class).stream()
                .map(ServiceLoader.Provider::get).toList());
    }

    public void register(LanguageProvider provider) {
        if (!provider.solutionDirectory().matches("[a-z][a-z0-9_-]*")
                || !provider.solutionFileName().matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")
                || provider.sourceExtensions().isEmpty()
                || provider.sourceExtensions().stream().anyMatch(ext -> !ext.matches("\\.[A-Za-z0-9]+"))
                || provider.sourceExtensions().stream().noneMatch(provider.solutionFileName()::endsWith)
                || provider.indentation().isEmpty() || !provider.indentation().matches("[ \\t]+")) {
            throw new IllegalArgumentException("Invalid language configuration: " + provider.language());
        }
        if (providers.containsKey(provider.language())
                || providers.values().stream().anyMatch(p -> p.solutionDirectory().equals(provider.solutionDirectory()))) {
            throw new IllegalArgumentException("Duplicate language or solution directory: " + provider.language());
        }
        providers.put(provider.language(), provider);
    }

    public LanguageProvider require(Language language) {
        LanguageProvider provider = providers.get(language);
        if (provider == null) throw new IllegalArgumentException("No language provider registered for " + language);
        return provider;
    }

    public List<LanguageProvider> providers() { return List.copyOf(providers.values()); }
}
