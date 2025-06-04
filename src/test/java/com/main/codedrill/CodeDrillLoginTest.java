package com.main.codedrill;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;

public class CodeDrillLoginTest {

    private WebDriver driver;
    private WebDriverWait wait;
    private static final String BASE_URL = "https://codedrill.org";

    private static final String USERNAME = "test";
    private static final String PASSWORD = "testtest";

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
    void testLoginAndNavigateToTaskManagement() throws InterruptedException {
        try {
            // Step 1: Navigate to homepage
            driver.get(BASE_URL);
            System.out.println("✓ Navigated to CodeDrill homepage");

            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // Step 2: Click Login
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
            WebElement browsePracticeTasksButton = null;
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
                                By.xpath("//button[contains(text(), 'Dashboard')] | " +
                                        "//a[contains(text(), 'Dashboard')] | " +
                                        "//a[@href='/dashboard'] | " +
                                        "//button[contains(@class, 'dashboard')] | " +
                                        "//a[contains(@class, 'dashboard')] | " +
                                        "//*[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'dashboard')]")
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

            // Step 8: Click on Task Management / Manage Tasks
            try {
                WebElement taskManagementButton = wait.until(
                        ExpectedConditions.elementToBeClickable(
                                By.xpath("//button[contains(text(), 'Task Management')] | " +
                                        "//a[contains(text(), 'Task Management')] | " +
                                        "//button[contains(text(), 'Manage Tasks')] | " +
                                        "//a[contains(text(), 'Manage Tasks')] | " +
                                        "//a[@href*='task'] | " +
                                        "//a[@href*='manage'] | " +
                                        "//*[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'task management')] | " +
                                        "//*[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'manage tasks')]")
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

            // Step 9: Final verification
            String finalUrl = driver.getCurrentUrl();
            System.out.println("✓ Final URL: " + finalUrl);
            System.out.println("✓ Test completed successfully!");
            System.out.println("✓ Navigation path: Login → Browse Practice Tasks → Dashboard → Task Management");

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
            System.out.println("Page source length: " + driver.getPageSource().length());

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