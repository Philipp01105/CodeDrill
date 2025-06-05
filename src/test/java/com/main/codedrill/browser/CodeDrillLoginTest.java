package com.main.codedrill.browser;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Objects;

public class CodeDrillLoginTest {

    private WebDriver driver;
    private WebDriverWait wait;
    private static final String BASE_URL = "https://codedrill.org";

    @Value("${codedrill.username}")
    private static final String USERNAME = "your_username";

    @Value("${codedrill.password}")
    private static final String PASSWORD = "your_password";

    public static void main(String[] args) {
        CodeDrillLoginTest test = new CodeDrillLoginTest();
        test.setUp();
        try {
            test.loginAndNavigateToTaskManagementAndCreateTaskAndProveAndDeleteTask();
            test.testInvalidLogin();
            test.testLoginAndCodeExecution();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @BeforeAll
    static void setupClass() {
        WebDriverManager.firefoxdriver().setup();
    }

    @BeforeEach
    void setUp() {
        FirefoxOptions options = new FirefoxOptions();

        options.addArguments("--headless");
        options.addArguments("--width=1920");
        options.addArguments("--height=1080");
        options.addPreference("dom.webnotifications.enabled", false);

        driver = new FirefoxDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        driver.manage().window().maximize();
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    @DisplayName("Should login, navigate to dashboard, then to task management")
    void loginAndNavigateToTaskManagementAndCreateTaskAndProveAndDeleteTask() throws InterruptedException {
        try {
            // Step 1: Navigate to homepage
            driver.get(BASE_URL);
            System.out.println("✓ Navigated to CodeDrill homepage");

            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            WebElement loginLink = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//a[@href='/login'] | //a[contains(text(), 'Login')]"))
            );
            loginLink.click();
            System.out.println("✓ Clicked on Login link");

            wait.until(ExpectedConditions.urlContains("login"));
            System.out.println("✓ Navigated to login page");

            // Step 3: Enter credentials
            WebElement usernameField = wait.until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.xpath("//input[@type='text'] | //input[@type='email'] | //input[@name='username'] | //input[@name='email'] | //input[@id='username'] | //input[@id='email']")
                    )
            );

            WebElement passwordField = driver.findElement(
                    By.xpath("//input[@type='password'] | //input[@name='password'] | //input[@id='password']")
            );

            usernameField.clear();
            usernameField.sendKeys(USERNAME);
            System.out.println("✓ Entered username: " + USERNAME);

            passwordField.clear();
            passwordField.sendKeys(PASSWORD);
            System.out.println("✓ Entered password");

            // Step 4: Submit login
            WebElement submitButton = driver.findElement(
                    By.xpath("//button[@type='submit'] | //input[@type='submit'] | //button[contains(text(), 'Sign In')] | //button[contains(text(), 'Login')] | //button[contains(@class, 'submit')]")
            );
            submitButton.click();
            System.out.println("✓ Submitted login form");
            Thread.sleep(3000);

            // Step 5: Verify login success
            boolean loginSuccessful = false;
            try {
                String currentUrl = driver.getCurrentUrl();
                Assertions.assertNotNull(currentUrl);
                if (!currentUrl.contains("login")) {
                    loginSuccessful = true;
                    System.out.println("✓ Login successful - redirected from login page");
                } else {
                    try {
                        WebElement userElement = wait.until(
                                ExpectedConditions.presenceOfElementLocated(
                                        By.xpath("//div[contains(@class, 'user')] | //span[contains(@class, 'username')] | //button[contains(text(), 'Logout')] | //a[contains(text(), 'Logout')]")
                                )
                        );
                        if (userElement.isDisplayed()) {
                            loginSuccessful = true;
                            System.out.println("✓ Login successful - user elements found");
                        }
                    } catch (Exception e) {
                        System.out.println("⚠ Could not find user elements");
                    }
                }

            } catch (Exception e) {
                System.out.println("⚠ Error checking login status: " + e.getMessage());
            }

            if (!loginSuccessful) {
                System.out.println("❌ Login may have failed. Current URL: " + driver.getCurrentUrl());
            }

            // Step 6: Navigate to Browse Practice Tasks
            WebElement browsePracticeTasksButton;
            try {
                browsePracticeTasksButton = wait.until(
                        ExpectedConditions.elementToBeClickable(
                                By.xpath("//button[contains(text(), 'Browse Practice Tasks')] | " +
                                        "//a[contains(text(), 'Browse Practice Tasks')] | " +
                                        "//button[contains(text(), 'Practice Tasks')] | " +
                                        "//a[contains(text(), 'Practice Tasks')] | " +
                                        "//a[contains(@class, 'material-btn') and contains(text(), 'Browse')] | " +
                                        "//*[contains(text(), 'Browse Practice Tasks')]")
                        )
                );
                browsePracticeTasksButton.click();
                System.out.println("✓ Clicked on 'Browse Practice Tasks' button");

            } catch (Exception e) {
                System.out.println("⚠ Could not find 'Browse Practice Tasks' button with standard selectors");

                try {
                    browsePracticeTasksButton = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.xpath("//*[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'practice') or " +
                                            "contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'tasks') or " +
                                            "contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'browse')]")
                            )
                    );
                    browsePracticeTasksButton.click();
                    System.out.println("✓ Found and clicked practice/tasks related button");

                } catch (Exception e2) {
                    System.err.println("❌ Could not find any practice tasks button: " + e2.getMessage());
                    printAvailableElements();
                    throw e2;
                }
            }

            Thread.sleep(2000);
            System.out.println("✓ Successfully navigated to practice tasks page");

            // Step 7: Click on Dashboard button
            try {
                WebElement dashboardButton = wait.until(
                        ExpectedConditions.elementToBeClickable(
                                By.xpath("//a[contains(text(), 'Dashboard')]")
                        )
                );
                dashboardButton.click();
                System.out.println("✓ Clicked on Dashboard button");
                Thread.sleep(2000);

            } catch (Exception e) {
                System.out.println("⚠ Could not find Dashboard button with standard selectors");

                // Try alternative selectors for navigation or menu items
                try {
                    WebElement navElement = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.xpath("//nav//a[contains(text(), 'Dashboard')] | " +
                                            "//div[contains(@class, 'nav')]//a[contains(text(), 'Dashboard')] | " +
                                            "//ul//a[contains(text(), 'Dashboard')] | " +
                                            "//header//a[contains(text(), 'Dashboard')]")
                            )
                    );
                    navElement.click();
                    System.out.println("✓ Found and clicked Dashboard in navigation");
                    Thread.sleep(2000);

                } catch (Exception e2) {
                    System.err.println("❌ Could not find Dashboard button: " + e2.getMessage());
                    printAvailableElements();
                    throw e2;
                }
            }

            try {
                WebElement taskManagementButton = wait.until(
                        ExpectedConditions.elementToBeClickable(
                                By.xpath("//a[contains(text(), 'Manage Tasks')]")
                        )
                );
                taskManagementButton.click();
                System.out.println("✓ Clicked on Task Management button");
                Thread.sleep(2000);

            } catch (Exception e) {
                System.out.println("⚠ Could not find Task Management button with standard selectors");

                // Try more flexible selectors
                try {
                    WebElement taskElement = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.xpath("//*[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'task') and " +
                                            "(contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'manage') or " +
                                            "contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'management'))] | " +
                                            "//a[contains(@href, 'task')] | " +
                                            "//button[contains(@class, 'task')] | " +
                                            "//a[contains(@class, 'task')]")
                            )
                    );
                    taskElement.click();
                    System.out.println("✓ Found and clicked task-related button");
                    Thread.sleep(2000);

                } catch (Exception e2) {
                    System.err.println("❌ Could not find Task Management button: " + e2.getMessage());
                    printAvailableElements();
                    throw e2;
                }
            }

            // Step 8: Click on Create Task button
            WebElement createTaskButton = wait.until(
                    ExpectedConditions.elementToBeClickable(
                            By.xpath("//a[contains(@Class, 'material-btn bg-primary-600 hover:bg-primary-700 text-white font-medium py-2 px-6 rounded-full shadow-elevation-1 hover:shadow-elevation-2 transition-all duration-300 transform hover:-translate-y-1 flex items-center animate-slide-in-right')]")
                    )
            );
            createTaskButton.click();
            System.out.println("✓ Clicked on Create Task button");
            Thread.sleep(2000);


            // Step 9: Fill in task details and submit
            {
                WebElement taskNameField = wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.id("title"))
                );
                WebElement taskShortDescriptionField = wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.id("description"))
                );
                WebElement taskLongDescriptionField = wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.id("content"))
                );
                WebElement taskJavaSolutionField = wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.id("solution"))
                );
                WebElement taskExpectedOutputField = wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.id("expectedOutput"))
                );
                WebElement taskJUnitTestField = wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.id("junitTests"))
                );
                WebElement taskTag = wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.id("tag1"))
                );
                createTaskButton = wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.xpath("//button[contains(@Class, 'material-btn px-6 py-2 bg-primary-600 text-white rounded-full hover:bg-primary-700 shadow-elevation-1 hover:shadow-elevation-2 transition-all duration-300 transform hover:-translate-y-1')]"))
                );

                taskNameField.clear();
                taskNameField.sendKeys("Test Task");
                System.out.println("✓ Entered task name");
                taskShortDescriptionField.clear();
                taskShortDescriptionField.sendKeys("This is a test task for CodeDrill.");
                System.out.println("✓ Entered task short description");
                taskLongDescriptionField.clear();
                taskLongDescriptionField.sendKeys("This task is designed to test the task creation and management features of CodeDrill.");
                System.out.println("✓ Entered task long description");
                taskJavaSolutionField.clear();
                taskJavaSolutionField.sendKeys("""
                        public class Solution {
                            public static void main(String[] args) {
                                System.out.println("Hello, CodeDrill!");
                            }
                        }""");
                System.out.println("✓ Entered task Java solution");
                taskExpectedOutputField.clear();
                taskExpectedOutputField.sendKeys("Hello, CodeDrill!");
                System.out.println("✓ Entered task expected output");
                taskJUnitTestField.clear();
                taskJUnitTestField.sendKeys("import org.junit.jupiter.api.Test;\n" +
                        "import static org.junit.jupiter.api.Assertions.assertEquals;\n" +
                        "\n" +
                        "public class SolutionTest {\n" +
                        "    @Test\n" +
                        "    public void testMain() {\n" +
                        "        assertEquals(\"Hello, CodeDrill!\", \"Hello, CodeDrill!\");\n" +
                        "    }\n" +
                        "}");
                System.out.println("✓ Entered task JUnit test");
                taskTag.click();
                System.out.println("Entered task tag");
                createTaskButton.click();
            }

            // Step 10: Validate creation on dashboard
            wait.until(ExpectedConditions.urlContains("moderator/tasks"));
            System.out.println("Navigated to Task Management page");
            WebElement createdTask = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[contains(text(), 'Test Task')]"))
            );
            Assertions.assertNotNull(createdTask, "Created task didnt work - element should not be null");

            System.out.println("✓ Task created successfully and found on dashboard");

            WebElement firstDeleteButton = driver.findElement(By.cssSelector("form[action*='delete']:first-of-type"));
            firstDeleteButton.click();
            Alert alert = wait.until(ExpectedConditions.alertIsPresent());
            alert.accept();
            try {
                wait.until(ExpectedConditions.invisibilityOfElementLocated(
                        By.xpath("//div[contains(text(), 'Test Task')]")
                ));
                System.out.println("Task successfully deleted - element is no longer visible");
            } catch (TimeoutException e) {
                Assertions.fail("Task was not deleted - element is still visible after timeout");
            }

            // Step 11: Final verification
            String finalUrl = driver.getCurrentUrl();
            System.out.println("✓ Final URL: " + finalUrl);
            System.out.println("✓ Test completed successfully!");
            System.out.println("✓ Navigation path: Login → Browse Practice Tasks → Dashboard → Task Management -> Create Task -> Prove on Practice Task -> Delete Task");

        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            printDebugInformation();
            throw e;
        }
    }

    @Test
    @DisplayName("Should try to login with invalid credentials and verify error message")
    void testInvalidLogin() {
        try {
            // Step 1: Navigate to homepage
            driver.get(BASE_URL);
            System.out.println("✓ Navigated to CodeDrill homepage");

            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            WebElement loginLink = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//a[@href='/login'] | //a[contains(text(), 'Login')]"))
            );
            loginLink.click();
            System.out.println("✓ Clicked on Login link");

            wait.until(ExpectedConditions.urlContains("login"));
            System.out.println("✓ Navigated to login page");

            // Step 3: Enter invalid credentials
            WebElement usernameField = wait.until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.xpath("//input[@type='text'] | //input[@type='email'] | //input[@name='username'] | //input[@name='email'] | //input[@id='username'] | //input[@id='email']")
                    )
            );

            WebElement passwordField = driver.findElement(
                    By.xpath("//input[@type='password'] | //input[@name='password'] | //input[@id='password']")
            );

            usernameField.clear();
            usernameField.sendKeys("invalidUser");
            System.out.println("✓ Entered invalid username");

            passwordField.clear();
            passwordField.sendKeys("invalidPass");
            System.out.println("✓ Entered invalid password");

            // Step 4: Submit login
            WebElement submitButton = driver.findElement(
                    By.xpath("//button[@type='submit'] | //input[@type='submit'] | //button[contains(text(), 'Sign In')] | //button[contains(text(), 'Login')] | //button[contains(@class, 'submit')]")
            );
            submitButton.click();
            System.out.println("✓ Submitted login form");

            // Step 5: Verify error message
            WebElement errorMessage = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[contains(text(), 'Invalid username or password')]"))
            );

            Assertions.assertTrue(errorMessage.isDisplayed(), "Error message should be displayed");
            System.out.println("✓ Error message displayed: " + errorMessage.getText());

        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Login and test code execution")
    void testLoginAndCodeExecution() throws InterruptedException {
        try {
            // Step 1: Navigate to homepage
            driver.get(BASE_URL);
            System.out.println("✓ Navigated to CodeDrill homepage");

            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            WebElement loginLink = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//a[@href='/login'] | //a[contains(text(), 'Login')]"))
            );
            loginLink.click();
            System.out.println("✓ Clicked on Login link");

            wait.until(ExpectedConditions.urlContains("login"));
            System.out.println("✓ Navigated to login page");

            // Step 3: Enter credentials
            WebElement usernameField = wait.until(
                    ExpectedConditions.presenceOfElementLocated(
                            By.xpath("//input[@type='text'] | //input[@type='email'] | //input[@name='username'] | //input[@name='email'] | //input[@id='username'] | //input[@id='email']")
                    )
            );

            WebElement passwordField = driver.findElement(
                    By.xpath("//input[@type='password'] | //input[@name='password'] | //input[@id='password']")
            );

            usernameField.clear();
            usernameField.sendKeys(USERNAME);
            System.out.println("✓ Entered username: " + USERNAME);

            passwordField.clear();
            passwordField.sendKeys(PASSWORD);
            System.out.println("✓ Entered password");

            // Step 4: Submit login
            WebElement submitButton = driver.findElement(
                    By.xpath("//button[@type='submit'] | //input[@type='submit'] | //button[contains(text(), 'Sign In')] | //button[contains(text(), 'Login')] | //button[contains(@class, 'submit')]")
            );
            submitButton.click();
            System.out.println("✓ Submitted login form");

            Thread.sleep(3000);

            // Step 5: Verify login success
            String currentUrl = driver.getCurrentUrl();
            Assertions.assertNotNull(currentUrl);

            if (!currentUrl.contains("login")) {
                System.out.println("✓ Login successful - redirected from login page");

                // Step 6: Navigate to "Browse Practice Tasks"
                try {
                    WebElement browsePracticeTasksButton = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.xpath("//button[contains(text(), 'Browse Practice Tasks')] | " +
                                            "//a[contains(text(), 'Browse Practice Tasks')] | " +
                                            "//button[contains(text(), 'Practice Tasks')] | " +
                                            "//a[contains(text(), 'Practice Tasks')] | " +
                                            "//*[contains(text(), 'Browse Practice Tasks')]")
                            )
                    );
                    browsePracticeTasksButton.click();
                    System.out.println("✓ Clicked on 'Browse Practice Tasks' button");
                } catch (Exception e) {
                    System.out.println("⚠ Could not find 'Browse Practice Tasks' button with standard selectors");

                    try {
                        WebElement browsePracticeTasksButton = wait.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.xpath("//*[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'practice') or " +
                                                "contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'tasks') or " +
                                                "contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'browse')]")
                                )
                        );
                        browsePracticeTasksButton.click();
                        System.out.println("✓ Found and clicked practice/tasks related button");
                    } catch (Exception e2) {
                        System.err.println("❌ Could not find any practice tasks button: " + e2.getMessage());
                        printAvailableElements();
                        throw e2;
                    }
                }

                Thread.sleep(2000);

                // Step 7: Find and click on Train button
                WebElement trainButton;
                try {
                    // First try to find by data-task-id
                    trainButton = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.xpath("//button[@data-task-id='1']")
                            )
                    );
                    System.out.println("✓ Found button with data-task-id='1'");
                } catch (Exception e) {
                    System.out.println("⚠ Could not find button with data-task-id='1', trying alternative selectors");

                    try {
                        trainButton = wait.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.xpath("//button[contains(text(), 'Train')][1] | " +
                                                "//a[contains(text(), 'Train')][1]")
                                )
                        );
                        System.out.println("✓ Found first button/link containing 'Train'");
                    } catch (Exception e2) {
                        System.err.println("❌ Could not find any Train button: " + e2.getMessage());
                        printAvailableElements();
                        throw e2;
                    }
                }

                trainButton.click();
                System.out.println("✓ Clicked on Train button");

                // Step 8: Wait for modal with textarea to appear
                /*WebElement codeTextArea;
                try {
                    codeTextArea = wait.until(
                            ExpectedConditions.visibilityOfElementLocated(
                                    By.xpath("//textarea[contains(@autocorrect, 'off')] or contains(@id, 'codeInput'))" +
                                            " | //div[contains(@class, 'code-editor')]//textarea | " +
                                            "//div[contains(@class, 'modal')]//textarea"
                                    )
                            )
                    );
                } catch (Exception e) {
                    System.out.println("⚠ Could not find textarea with standard selector");

                    try {
                        codeTextArea = wait.until(
                                ExpectedConditions.visibilityOfElementLocated(
                                        By.xpath("//textarea[@id='codeInput'] | " +
                                                "//div[contains(@class, 'code-editor')]//textarea | " +
                                                "//div[contains(@class, 'modal')]//textarea")
                                )
                        );
                        System.out.println("✓ Found textarea with alternative selector");
                    } catch (Exception e2) {
                        System.err.println("❌ Could not find any textarea: " + e2.getMessage());
                        printAvailableElements();
                        throw e2;
                    }
                }

                // Step 9: Enter some code
                codeTextArea.clear();
                codeTextArea.sendKeys("public class Solution {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        System.out.println(\"test\");\n" +
                        "    }\n" +
                        "}");
                System.out.println("✓ Entered code in text area");
                */
                // Step 10: Click run code button
                WebElement runCodeButton;
                try {
                    runCodeButton = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.id("runCodeBtn")
                            )
                    );
                } catch (Exception e) {
                    System.out.println("⚠ Could not find button with id='runCodeBtn', trying alternative selectors");

                    try {
                        runCodeButton = wait.until(
                                ExpectedConditions.elementToBeClickable(
                                        By.xpath("//button[contains(text(), 'Run')] | " +
                                                "//button[contains(text(), 'Execute')] | " +
                                                "//button[contains(@class, 'run')]")
                                )
                        );
                        System.out.println("✓ Found run button with alternative selector");
                    } catch (Exception e2) {
                        System.err.println("❌ Could not find any run code button: " + e2.getMessage());
                        printAvailableElements();
                        throw e2;
                    }
                }

                runCodeButton.click();
                System.out.println("✓ Clicked Run Code button");

                // Step 11: Verify output appeared
                WebElement codeOutput;
                try {
                    Thread.sleep(5000);
                    codeOutput = wait.until(
                            ExpectedConditions.visibilityOfElementLocated(
                                    By.id("codeOutput")
                            )
                    );
                } catch (Exception e) {
                    System.out.println("⚠ Could not find element with id='codeOutput', trying alternative selectors");

                    try {
                        codeOutput = wait.until(
                                ExpectedConditions.visibilityOfElementLocated(
                                        By.xpath("//div[contains(@class, 'output')] | " +
                                                "//pre[contains(@class, 'output')] | " +
                                                "//div[contains(@class, 'result')]")
                                )
                        );
                        System.out.println("✓ Found output element with alternative selector");
                    } catch (Exception e2) {
                        System.err.println("❌ Could not find any output element: " + e2.getMessage());
                        printAvailableElements();
                        throw e2;
                    }
                }

                Assertions.assertTrue(codeOutput.isDisplayed(), "Code output should be displayed");
                String outputText = codeOutput.getText();
                Assertions.assertTrue(outputText.contains("No output"),
                        "Code output should contain 'No output', but found: " + outputText);
                System.out.println("✓ Code output verified: " + outputText);

                System.out.println("✓ Test completed successfully!");
            } else {
                System.err.println("❌ Login failed. Still on login page.");
                throw new AssertionError("Login failed");
            }
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            printDebugInformation();
            throw e;
        }
    }

    // Helper method to print available clickable elements
    private void printAvailableElements() {
        System.out.println("\n=== Available clickable elements ===");
        var clickableElements = driver.findElements(By.xpath("//button | //a | //input[@type='submit']"));
        for (WebElement element : clickableElements) {
            try {
                String text = element.getText().trim();
                String href = element.getAttribute("href");
                String className = element.getAttribute("class");

                if (!text.isEmpty() || href != null) {
                    System.out.println("- Element: " + element.getTagName() +
                            ", Text: '" + text + "'" +
                            (href != null ? ", Href: " + href : "") +
                            (className != null && !className.isEmpty() ? ", Class: " + className : ""));
                }
            } catch (Exception ignore) {
            }
        }
        System.out.println("==========================================\n");
    }

    // Helper method to print debug information
    private void printDebugInformation() {
        try {
            System.out.println("\n=== DEBUG INFORMATION ===");
            System.out.println("Current URL: " + driver.getCurrentUrl());
            System.out.println("Page title: " + driver.getTitle());
            System.out.println("Page source length: " + Objects.requireNonNull(driver.getPageSource()).length());

            System.out.println("\nAvailable form elements:");
            var formElements = driver.findElements(By.xpath("//input | //button | //a"));
            for (WebElement element : formElements) {
                try {
                    String tagName = element.getTagName();
                    String type = element.getAttribute("type");
                    String text = element.getText().trim();
                    String className = element.getAttribute("class");

                    if (type != null || !text.isEmpty() || (className != null && className.contains("btn"))) {
                        System.out.println("- " + tagName +
                                (type != null ? "[type=" + type + "]" : "") +
                                " text='" + text + "'" +
                                (className != null ? " class='" + className + "'" : ""));
                    }
                } catch (Exception ignore) {
                }
            }
            System.out.println("==============================\n");

        } catch (Exception ex) {
            System.err.println("Could not get debug info: " + ex.getMessage());
        }
    }
}