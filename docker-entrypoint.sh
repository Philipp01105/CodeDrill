#!/bin/bash
set -e

# Function to execute regular Java code
execute_regular_code() {
    local java_code="$1"

    # Save input code to file
    echo "$java_code" > Solution.java

    # Compile the Java code
    if ! javac Solution.java 2> compile_error.txt; then
        echo "Compilation Error"
        cat compile_error.txt
        exit 1
    fi

    # Run with timeout and memory constraints
    if ! timeout ${TIMEOUT}s java -Xmx${MEMORY_LIMIT} Solution 2> runtime_error.txt; then
        EXIT_CODE=$?
        if [ $EXIT_CODE -eq 124 ]; then
            echo "Execution timeout - your code took too long to run"
        else
            echo "Runtime Error"
            cat runtime_error.txt
        fi
        exit $EXIT_CODE
    fi

    # Exit with the same code as the Java program
    exit $?
}

# Function to execute JUnit tests
execute_junit_tests() {
    local input="$1"

    # Parse the input to extract student code and test code
    student_code=$(echo "$input" | sed -n '/===STUDENT_CODE===/,/===TEST_CODE===/p' | sed '1d;$d')
    test_code=$(echo "$input" | sed -n '/===TEST_CODE===/,/===END===/p' | sed '1d;$d')

    # Extract class names
    student_class=$(echo "$student_code" | grep -o 'public class [A-Za-z0-9_]*' | head -1 | cut -d' ' -f3)
    test_class=$(echo "$test_code" | grep -o 'public class [A-Za-z0-9_]*' | head -1 | cut -d' ' -f3)

    if [ -z "$student_class" ] || [ -z "$test_class" ]; then
        echo '{"success":false,"message":"Could not extract class names","testsSucceeded":0,"testsFailed":0,"testsSkipped":0,"totalTests":0,"allTestsPassed":false}'
        exit 0
    fi

    # Write the Java files
    echo "$student_code" > "${student_class}.java"
    echo "$test_code" > "${test_class}.java"

    # Compile student code first
    if ! javac -cp ".:$JUNIT_CLASSPATH" "${student_class}.java" 2> student_compile_error.txt; then
        error_msg=$(cat student_compile_error.txt | tr '"' "'" | tr '\n' ' ')
        echo "{\"success\":false,\"message\":\"Student code compilation failed: $error_msg\",\"testsSucceeded\":0,\"testsFailed\":0,\"testsSkipped\":0,\"totalTests\":0,\"allTestsPassed\":false}"
        exit 0
    fi

    # Compile test code
    if ! javac -cp ".:$JUNIT_CLASSPATH" "${test_class}.java" 2> test_compile_error.txt; then
        error_msg=$(cat test_compile_error.txt | tr '"' "'" | tr '\n' ' ')
        echo "{\"success\":false,\"message\":\"Test code compilation failed: $error_msg\",\"testsSucceeded\":0,\"testsFailed\":0,\"testsSkipped\":0,\"totalTests\":0,\"allTestsPassed\":false}"
        exit 0
    fi

    # Create a simple test runner
    cat > TestRunner.java << 'EOF'
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.io.StringWriter;
import java.io.PrintWriter;

public class TestRunner {
    public static void main(String[] args) {
        try {
            String testClassName = args[0];
            Class<?> testClass = Class.forName(testClassName);

            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(testClass))
                .build();

            Launcher launcher = LauncherFactory.create();
            SummaryGeneratingListener listener = new SummaryGeneratingListener();

            launcher.registerTestExecutionListeners(listener);
            launcher.execute(request);

            TestExecutionSummary summary = listener.getSummary();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("testsSucceeded", (int)summary.getTestsSucceededCount());
            result.put("testsFailed", (int)summary.getTestsFailedCount());
            result.put("testsSkipped", (int)summary.getTestsSkippedCount());
            result.put("totalTests", (int)summary.getTestsFoundCount());
            result.put("allTestsPassed", summary.getTestsFailedCount() == 0 && summary.getTestsSucceededCount() > 0);

            if (summary.getTestsFailedCount() > 0) {
                List<Map<String, String>> failures = new ArrayList<>();
                summary.getFailures().forEach(failure -> {
                    Map<String, String> failureInfo = new HashMap<>();
                    failureInfo.put("testName", failure.getTestIdentifier().getDisplayName());

                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    failure.getException().printStackTrace(pw);
                    String stackTrace = sw.toString();

                    failureInfo.put("exception", failure.getException().getMessage());
                    failureInfo.put("stackTrace", stackTrace);
                    failures.add(failureInfo);
                });
                result.put("failures", failures);
            }

            ObjectMapper mapper = new ObjectMapper();
            System.out.println(mapper.writeValueAsString(result));

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stackTrace = sw.toString().replace("\"", "\\\"").replace("\n", "\\n");

            System.out.println("{\"success\":false,\"message\":\"Test execution failed: " +
                             e.getMessage().replace("\"", "\\\"") +
                             "\",\"testsSucceeded\":0,\"testsFailed\":0,\"testsSkipped\":0,\"totalTests\":0,\"allTestsPassed\":false,\"exception\":\"" +
                             stackTrace + "\"}");
        }
    }
}
EOF

    # Compile the test runner
    if ! javac -cp ".:$JUNIT_CLASSPATH" TestRunner.java 2> runner_compile_error.txt; then
        error_msg=$(cat runner_compile_error.txt | tr '"' "'" | tr '\n' ' ')
        echo "{\"success\":false,\"message\":\"Test runner compilation failed: $error_msg\",\"testsSucceeded\":0,\"testsFailed\":0,\"testsSkipped\":0,\"totalTests\":0,\"allTestsPassed\":false}"
        exit 0
    fi

    # Run the tests with timeout and memory limits
    if ! timeout 30s java -Xmx${MEMORY_LIMIT} -cp ".:$JUNIT_CLASSPATH" TestRunner "$test_class" 2> test_runtime_error.txt; then
        EXIT_CODE=$?
        if [ $EXIT_CODE -eq 124 ]; then
            echo '{"success":false,"message":"Test execution timeout - tests took too long to run","testsSucceeded":0,"testsFailed":0,"testsSkipped":0,"totalTests":0,"allTestsPassed":false}'
        else
            error_msg=$(cat test_runtime_error.txt | tr '"' "'" | tr '\n' ' ')
            echo "{\"success\":false,\"message\":\"Test execution failed: $error_msg\",\"testsSucceeded\":0,\"testsFailed\":0,\"testsSkipped\":0,\"totalTests\":0,\"allTestsPassed\":false}"
        fi
        exit 0
    fi
}

# Read all input
input=$(cat)

# Check if this is a JUnit test execution by looking for special markers
if echo "$input" | grep -q "===STUDENT_CODE==="; then
    # JUnit test mode
    execute_junit_tests "$input"
else
    # Regular code execution mode
    execute_regular_code "$input"
fi