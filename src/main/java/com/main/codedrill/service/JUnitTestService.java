package com.main.codedrill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class JUnitTestService {

    private final CodeExecutionService codeExecutionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public JUnitTestService(CodeExecutionService codeExecutionService) {
        this.codeExecutionService = codeExecutionService;
    }

    /**
     * Runs JUnit tests against student code using secure Docker execution
     *
     * @param studentCode The student's Java code
     * @param junitTests  The JUnit tests to run against the student code
     * @return A map containing test results
     */
    public Map<String, Object> runTests(String studentCode, String junitTests) {
        try {
            // Use the secure Docker execution service
            String jsonResult = codeExecutionService.executeJUnitTests(studentCode, junitTests);

            // Parse the JSON result back to a Map
            return objectMapper.readValue(jsonResult, Map.class);

        } catch (Exception e) {
            // Return error result in the expected format
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error running tests: " + e.getMessage());
            result.put("testsSucceeded", 0);
            result.put("testsFailed", 0);
            result.put("testsSkipped", 0);
            result.put("totalTests", 0);
            result.put("allTestsPassed", false);
            result.put("exception", e.toString());
            return result;
        }
    }

    /**
     * Convenience method for backward compatibility
     */
    @Deprecated
    public Map<String, Object> runTestsLocally(String studentCode, String junitTests) {
        // Redirect to secure Docker execution
        return runTests(studentCode, junitTests);
    }
}