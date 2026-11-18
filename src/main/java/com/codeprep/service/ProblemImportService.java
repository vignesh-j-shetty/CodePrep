package com.codeprep.service;

import com.codeprep.model.Problem;
import com.codeprep.repository.ProblemRepository;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class ProblemImportService {
    public enum Status {
        NEW("New"), ALREADY_EXISTS("Already exists"), NEEDS_REVIEW("Needs review"), INVALID("Invalid");
        private final String label;
        Status(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record Candidate(Path source, Problem problem, Status status, String reason) {}
    public record Preview(Path source, Path library, List<Candidate> candidates) {
        public Preview { candidates = List.copyOf(candidates); }
    }
    public record Result(int imported, int skipped, int invalid, int failed, List<String> details) {
        public Result { details = List.copyOf(details); }
    }

    private final ProblemRepository repository;
    private final ProblemFingerprintService fingerprints;

    public ProblemImportService(ProblemRepository repository, ProblemFingerprintService fingerprints) {
        this.repository = repository;
        this.fingerprints = fingerprints;
    }

    public Preview scan(Path source, Path library) throws IOException {
        checkPaths(source, library);
        source = source.toAbsolutePath().normalize();
        library = library.toAbsolutePath().normalize();
        List<Problem> known = existing(library);
        List<Candidate> candidates = new ArrayList<>();
        for (Candidate candidate : collect(source)) {
            if (candidate.status() == Status.INVALID) {
                candidates.add(candidate);
            } else {
                Candidate classified = classify(candidate.source(), candidate.problem(), known);
                candidates.add(classified);
                known.add(candidate.problem());
            }
        }
        if (candidates.isEmpty()) {
            candidates.add(new Candidate(source, null, Status.INVALID,
                    "No question folders found. Select a folder containing problem.md and testcases.json."));
        }
        return new Preview(source, library, candidates);
    }

    public Result importQuestions(Preview preview, Set<Path> reviewedConflicts) throws IOException {
        checkPaths(preview.source(), preview.library());
        List<Problem> known = existing(preview.library());
        List<String> details = new ArrayList<>();
        int imported = 0, skipped = 0, invalid = 0, failed = 0;
        for (Candidate candidate : preview.candidates()) {
            if (candidate.status() == Status.INVALID) {
                invalid++;
                details.add(candidate.source() + ": Invalid - " + candidate.reason());
                continue;
            }
            boolean approved = candidate.status() == Status.NEEDS_REVIEW
                    && reviewedConflicts.contains(candidate.source());
            if (candidate.status() == Status.ALREADY_EXISTS
                    || (candidate.status() == Status.NEEDS_REVIEW && !approved)) {
                skipped++;
                details.add(candidate.source() + ": Skipped - " + candidate.reason());
                continue;
            }
            Path staging = null;
            try {
                checkPaths(candidate.source(), preview.library());
                staging = Files.createTempDirectory(preview.library().toRealPath().getParent(), ".codeprep-import-");
                Path stagedQuestion = staging.resolve("question");
                copyQuestion(candidate.source(), stagedQuestion);
                Problem staged = repository.validate(staging, stagedQuestion);
                if (!sameContent(staged, candidate.problem())
                        || !staged.title().equals(candidate.problem().title())) {
                    throw new IOException("Question changed since preview. Scan it again before importing.");
                }
                Candidate current = classify(candidate.source(), staged, known);
                if (current.status() == Status.ALREADY_EXISTS
                        || (current.status() == Status.NEEDS_REVIEW && !approved)) {
                    skipped++;
                    details.add(candidate.source() + ": Skipped - " + current.reason() + " Scan again to review changes.");
                    continue;
                }
                Path destination = moveUnique(stagedQuestion, preview.library(), candidate.source().getFileName().toString());
                imported++;
                known.add(candidate.problem());
                details.add(candidate.source() + ": Imported to " + destination);
            } catch (IOException | RuntimeException ex) {
                failed++;
                details.add(candidate.source() + ": Failed - " + ex.getMessage() + " Check access and scan again.");
            } finally {
                if (staging != null) {
                    try {
                        deleteStaging(staging);
                    } catch (IOException ex) {
                        details.add("Could not remove staging folder " + staging + ": " + ex.getMessage()
                                + ". Remove this temporary folder manually.");
                    }
                }
            }
        }
        return new Result(imported, skipped, invalid, failed, details);
    }

    private Candidate classify(Path source, Problem incoming, List<Problem> known) {
        String statement = fingerprints.statement(incoming);
        boolean changedTests = false, titleConflict = false;
        Path conflict = null;
        for (Problem other : known) {
            if (statement.equals(fingerprints.statement(other))) {
                if (fingerprints.tests(incoming).equals(fingerprints.tests(other))) {
                    return new Candidate(source, incoming, Status.ALREADY_EXISTS,
                            "Same statement and equivalent tests as " + other.directory() + " (library or earlier batch item).");
                }
                changedTests = true;
                conflict = other.directory();
            } else if (fingerprints.title(incoming).equals(fingerprints.title(other))) {
                titleConflict = true;
                conflict = other.directory();
            }
        }
        if (changedTests || titleConflict) {
            return new Candidate(source, incoming, Status.NEEDS_REVIEW,
                    (changedTests ? "Same statement, different tests" : "Same normalized title, different statement")
                            + " compared with " + conflict + ". Skipped unless explicitly imported separately.");
        }
        return new Candidate(source, incoming, Status.NEW, "No duplicate found; import into a new folder.");
    }

    private boolean sameContent(Problem first, Problem second) {
        return fingerprints.statement(first).equals(fingerprints.statement(second))
                && fingerprints.tests(first).equals(fingerprints.tests(second));
    }

    private List<Problem> existing(Path library) throws IOException {
        List<Problem> result = new ArrayList<>();
        for (Candidate candidate : collect(library)) {
            if (candidate.status() == Status.INVALID) {
                throw new IOException("Cannot safely check duplicates in the existing library: "
                        + candidate.source() + ": " + candidate.reason() + " Repair this folder and retry.");
            }
            result.add(candidate.problem());
        }
        return result;
    }

    private List<Candidate> collect(Path root) throws IOException {
        List<Candidate> result = new ArrayList<>();
        List<Path> folders = new ArrayList<>();
        List<Candidate> issues = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (Files.exists(dir.resolve("problem.md"), LinkOption.NOFOLLOW_LINKS)
                        || Files.exists(dir.resolve("testcases.json"), LinkOption.NOFOLLOW_LINKS)) {
                    folders.add(dir);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isSymbolicLink()) issues.add(new Candidate(file, null, Status.INVALID,
                        "Symbolic links are not supported. Select a real question folder."));
                else if (!attrs.isRegularFile()) issues.add(new Candidate(file, null, Status.INVALID,
                        "Only regular files are supported."));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException ex) {
                issues.add(new Candidate(file, null, Status.INVALID, "Cannot read folder: " + ex.getMessage()));
                return FileVisitResult.CONTINUE;
            }
        });
        for (Path dir : folders) {
            try {
                rejectLinks(dir);
                result.add(new Candidate(dir, repository.validate(root, dir), Status.NEW, ""));
            } catch (IOException | RuntimeException ex) {
                result.add(new Candidate(dir, null, Status.INVALID, ex.getMessage()));
            }
        }
        result.addAll(issues.stream().filter(issue -> folders.stream()
                .noneMatch(folder -> issue.source().startsWith(folder))).toList());
        result.sort(Comparator.comparing(candidate -> candidate.source().toString()));
        return result;
    }

    private void checkPaths(Path source, Path library) throws IOException {
        rejectLinkAncestors(source);
        rejectLinkAncestors(library);
        if (!Files.isDirectory(source)) throw new IOException("Import source is not an existing directory: " + source);
        if (!Files.isDirectory(library)) throw new IOException("Existing library is missing: " + library + ". Restore it before importing.");
        Path realSource = source.toRealPath(), realLibrary = library.toRealPath();
        if (realSource.startsWith(realLibrary) || realLibrary.startsWith(realSource)) {
            throw new IOException("Import source and library must not overlap. Select a separate source folder.");
        }
        if (realLibrary.getParent() == null) throw new IOException("A filesystem root cannot be used as an import library.");
    }

    private void rejectLinkAncestors(Path path) throws IOException {
        for (Path current = path.toAbsolutePath(); current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IOException("Symbolic links are not supported: " + current);
        }
    }

    private void rejectLinks(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) throw new IOException("Symbolic links are not supported: " + file);
                if (!attrs.isRegularFile()) throw new IOException("Only regular files are supported: " + file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void copyQuestion(Path source, Path destination) throws IOException {
        rejectLinks(source);
        Files.createDirectory(destination);
        Files.copy(source.resolve("problem.md"), destination.resolve("problem.md"), LinkOption.NOFOLLOW_LINKS);
        Files.copy(source.resolve("testcases.json"), destination.resolve("testcases.json"), LinkOption.NOFOLLOW_LINKS);
        Path java = source.resolve("solutions").resolve("java");
        if (Files.isDirectory(java)) {
            Files.walkFileTree(java, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isSymbolicLink()) throw new IOException("Symbolic links are not supported: " + file);
                    if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
                        Path target = destination.resolve("solutions").resolve("java").resolve(java.relativize(file));
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target, LinkOption.NOFOLLOW_LINKS);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        rejectLinks(destination);
    }

    private Path moveUnique(Path staged, Path library, String name) throws IOException {
        for (int suffix = 1; ; suffix++) {
            Path destination = library.resolve(suffix == 1 ? name : name + "-" + suffix);
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) continue;
            try {
                return Files.move(staged, destination);
            } catch (FileAlreadyExistsException ex) {
                // Another writer claimed this name; never replace it.
            }
        }
    }

    private void deleteStaging(Path staging) throws IOException {
        try (var paths = Files.walk(staging)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
