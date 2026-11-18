package com.codeprep.service;

import static org.junit.jupiter.api.Assertions.*;

import com.codeprep.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestCaseServiceTest {
    @Test void runSelectsSamplesAndRunAllSelectsEveryVisibility() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<TestCase> cases = List.of(
                new TestCase(1, TestCaseVisibility.HIDDEN, mapper.readTree("{\"n\":2}"), mapper.readTree("2")),
                new TestCase(2, TestCaseVisibility.SAMPLE, mapper.readTree("{\"n\":1}"), mapper.readTree("1")));
        Problem problem = new Problem("p", "Problem", Difficulty.EASY, List.of(), "", Path.of("."), cases);
        TestCaseService service = new TestCaseService();
        assertEquals(List.of(cases.get(1)), service.forRun(problem, false));
        assertEquals(2, service.forRun(problem, false).get(0).index());
        assertEquals(cases, service.forRun(problem, true));
    }
}
