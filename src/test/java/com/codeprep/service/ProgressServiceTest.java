package com.codeprep.service;

import static org.junit.jupiter.api.Assertions.*;

import com.codeprep.model.Language;
import com.codeprep.repository.ProgressRepository;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgressServiceTest {
    @TempDir Path temp;

    @Test void sampleAttemptsNeverSolveButPassingAllTestsPersistsSolvedProgress() throws Exception {
        Path file = temp.resolve("progress.json");
        ProgressService progress = new ProgressService(new ProgressRepository(file));
        progress.recordAttempt("arrays/two-sum", Language.JAVA, 25, true, false);
        assertFalse(progress.isSolved("arrays/two-sum"));
        progress.recordAttempt("arrays/two-sum", Language.JAVA, 30, true, true);
        assertTrue(progress.isSolved("arrays/two-sum"));
        ProgressService reloaded = new ProgressService(new ProgressRepository(file));
        assertTrue(reloaded.isSolved("arrays/two-sum"));
        assertEquals(2, reloaded.get("arrays/two-sum").attempts());
    }
}
