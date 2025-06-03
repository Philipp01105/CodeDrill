package com.main.codedrill.service;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@Service
public class JUnitTestService {

    /**
     * Compiles and runs JUnit tests against student code
     *
     * @param studentCode The student's Java code
     * @param junitTests  The JUnit tests to run against the student code
     * @return A map containing test results
     */
    public Map<String, Object> runTests(String studentCode, String junitTests) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Create temporary directory for compilation
            Path tempDir = Files.createTempDirectory("junit-test");

            // Write student code to file
            String className = extractClassName(studentCode);
            if (className == null) {
                result.put("success", false);
                result.put("message", "Could not determine class name from student code");
                return result;
            }

            Path studentFile = tempDir.resolve(className + ".java");
            Files.writeString(studentFile, studentCode);

            // Write test code to file
            String testClassName = extractClassName(junitTests);
            if (testClassName == null) {
                result.put("success", false);
                result.put("message", "Could not determine class name from test code");
                return result;
            }

            Path testFile = tempDir.resolve(testClassName + ".java");
            Files.writeString(testFile, junitTests);

            // Compile files
            boolean compilationSuccess = compileFiles(tempDir, studentFile, testFile);
            if (!compilationSuccess) {
                result.put("success", false);
                result.put("message", "Compilation failed");
                return result;
            }

            // Run tests
            TestExecutionSummary summary = runJUnitTests(tempDir, testClassName);

            // Process results
            result.put("success", true);
            result.put("testsSucceeded", summary.getTestsSucceededCount());
            result.put("testsFailed", summary.getTestsFailedCount());
            result.put("testsSkipped", summary.getTestsSkippedCount());
            result.put("totalTests", summary.getTestsFoundCount());
            result.put("allTestsPassed", summary.getTestsFailedCount() == 0 && summary.getTestsSucceededCount() > 0);

            // Add failure details if any
            if (summary.getTestsFailedCount() > 0) {
                List<Map<String, String>> failures = new ArrayList<>();
                summary.getFailures().forEach(failure -> {
                    Map<String, String> failureInfo = new HashMap<>();
                    failureInfo.put("testName", failure.getTestIdentifier().getDisplayName());

                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    failure.getException().printStackTrace(pw);
                    failureInfo.put("exception", sw.toString());

                    failures.add(failureInfo);
                });
                result.put("failures", failures);
            }

            // Clean up
            deleteDirectory(tempDir.toFile());

            return result;
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error running tests: " + e.getMessage());
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            result.put("exception", sw.toString());
            return result;
        }
    }

    /**
     * Extract class name from Java code
     */
    private String extractClassName(String code) {
        // Simple regex to find class name
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("public\\s+class\\s+(\\w+)");
        java.util.regex.Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Compile Java files
     */
    private boolean compileFiles(Path directory, Path... files) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();

        command.add("javac");

        // Add classpath with JUnit
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));

        // Add files to compile
        for (Path file : files) {
            // Ensure paths are normalized for the current OS
            command.add(file.normalize().toString());
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(directory.toFile());

        // Redirect error stream to output stream to capture compilation errors
        pb.redirectErrorStream(true);

        Process process = pb.start();

        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return false;
        }

        return process.exitValue() == 0;
    }

    /**
     * Run JUnit tests
     */
    private TestExecutionSummary runJUnitTests(Path directory, String testClassName) throws Exception {
        // Create class loader for the compiled classes
        URL url = directory.toUri().toURL();

        URLClassLoader classLoader = new URLClassLoader(new URL[]{url}, getClass().getClassLoader());
        // Load the test class
        Class<?> testClass = classLoader.loadClass(testClassName);


        // Create launcher and run tests
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(testClass))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();

        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        return listener.getSummary();
    }

    /**
     * Recursively delete a directory
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
