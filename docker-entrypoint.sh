#!/bin/bash
set -e

# This script runs Java code in a secure Docker container
# It expects code to be provided via stdin or as an argument

# Save input code to file
cat > Solution.java

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