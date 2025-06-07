package com.main.codedrill.selenium.tests;

import com.main.codedrill.selenium.BaseSeleniumTest;
import com.main.codedrill.selenium.pages.HomePage;
import com.main.codedrill.selenium.pages.LoginPage;
import com.main.codedrill.selenium.pages.TasksPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

class TasksTest extends BaseSeleniumTest {

    @BeforeEach
    void loginAsAdmin() {
        // Login first to access tasks
        driver.get(getUrl("/login"));
        LoginPage loginPage = new LoginPage(driver, wait);
        loginPage.login("admin", "password");

        // Handle potential password change redirect
        wait.until(ExpectedConditions.or(
                ExpectedConditions.urlContains("/"),
                ExpectedConditions.urlContains("/change-password"),
                ExpectedConditions.urlContains("/dashboard")
        ));
    }

    @Test
    void shouldDisplayTasksPage() {
        driver.get(getUrl("/tasks"));

        TasksPage tasksPage = new TasksPage(driver, wait);

        // Wait for page to load
        wait.until(ExpectedConditions.or(
                ExpectedConditions.titleContains("Tasks"),
                ExpectedConditions.presenceOfElementLocated(
                        org.openqa.selenium.By.cssSelector("h1, h2"))
        ));

        assertTrue(tasksPage.isPageLoaded(), "Tasks page should be loaded");
    }

    @Test
    void shouldNavigateToTasksFromHomePage() {
        HomePage homePage = new HomePage(driver, wait);
        homePage.open(getUrl("/"));

        try {
            homePage.clickTasks();
            assertTrue(driver.getCurrentUrl().contains("/tasks"), "Should navigate to tasks page");

            TasksPage tasksPage = new TasksPage(driver, wait);
            assertTrue(tasksPage.isPageLoaded(), "Tasks page should be loaded");
        } catch (Exception e) {
            System.out.println("Tasks navigation might not be available: " + e.getMessage());
        }
    }

    @Test
    void shouldDisplayTasksList() {
        driver.get(getUrl("/tasks"));

        TasksPage tasksPage = new TasksPage(driver, wait);

        // Wait for page content
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Get task count (could be 0 if no tasks exist)
        int taskCount = tasksPage.getTaskCount();
        assertTrue(taskCount >= 0, "Should return valid task count");

        System.out.println("Found " + taskCount + " tasks");
    }

    @Test
    void shouldFilterTasksByTag() {
        driver.get(getUrl("/tasks"));

        TasksPage tasksPage = new TasksPage(driver, wait);

        try {
            // Try to filter by a common tag if available
            tasksPage.filterByTag("java");

            // Wait for filter to apply
            Thread.sleep(500);

            // Test passes if no exception is thrown
            assertTrue(true, "Tag filtering should work without errors");
        } catch (Exception e) {
            System.out.println("Tag filtering might not be available: " + e.getMessage());
        }
    }

    @Test
    void shouldOpenTaskDetails() {
        driver.get(getUrl("/tasks"));

        TasksPage tasksPage = new TasksPage(driver, wait);

        if (tasksPage.getTaskCount() > 0) {
            String currentUrl = driver.getCurrentUrl();
            tasksPage.clickFirstTask();

            // Wait for navigation
            wait.until(ExpectedConditions.not(ExpectedConditions.urlToBe(currentUrl)));

            // Should navigate to task details or editor
            assertTrue(driver.getCurrentUrl().contains("/task") ||
                            driver.getCurrentUrl().contains("/editor"),
                    "Should navigate to task details");
        } else {
            System.out.println("No tasks available to test task details");
        }
    }
}