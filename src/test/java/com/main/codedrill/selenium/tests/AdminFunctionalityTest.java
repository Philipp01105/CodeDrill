package com.main.codedrill.selenium.tests;

import com.main.codedrill.selenium.BaseSeleniumTest;
import com.main.codedrill.selenium.pages.LoginPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

class AdminFunctionalityTest extends BaseSeleniumTest {

    @BeforeEach
    void loginAsAdmin() {
        driver.get(getUrl("/login"));
        LoginPage loginPage = new LoginPage(driver, wait);
        loginPage.login("admin", "password");

        wait.until(ExpectedConditions.or(
                ExpectedConditions.urlContains("/"),
                ExpectedConditions.urlContains("/change-password"),
                ExpectedConditions.urlContains("/dashboard")
        ));
    }

    @Test
    void shouldAccessAdminDashboard() {
        driver.get(getUrl("/admin"));

        // Check if admin dashboard loads without redirect to login
        wait.until(ExpectedConditions.or(
                ExpectedConditions.titleContains("Admin"),
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("h1, h2")),
                ExpectedConditions.urlContains("/admin")
        ));

        assertTrue(driver.getCurrentUrl().contains("/admin") ||
                        driver.getPageSource().contains("Admin") ||
                        driver.getPageSource().contains("Dashboard"),
                "Should access admin area");
    }

    @Test
    void shouldAccessModeratorFunctionality() {
        driver.get(getUrl("/moderator"));

        // Admin should have access to moderator functions
        wait.until(ExpectedConditions.or(
                ExpectedConditions.urlContains("/moderator"),
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("h1, h2")),
                ExpectedConditions.titleContains("Moderator")
        ));

        // Should not be redirected to login
        assertFalse(driver.getCurrentUrl().contains("/login"),
                "Admin should access moderator functionality");
    }

    @Test
    void shouldViewAnalytics() {
        driver.get(getUrl("/analytics/admin"));

        // Check if analytics page loads
        wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("h1, h2")),
                ExpectedConditions.titleContains("Analytics"),
                ExpectedConditions.urlContains("/analytics")
        ));

        // Should not be redirected to login
        assertFalse(driver.getCurrentUrl().contains("/login"),
                "Admin should access analytics");
    }

    @Test
    void shouldManageTasks() {
        driver.get(getUrl("/moderator/tasks"));

        // Check if task management page loads
        wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("h1, h2")),
                ExpectedConditions.urlContains("/moderator/tasks"),
                ExpectedConditions.titleContains("Tasks")
        ));

        // Should have access to task management
        assertTrue(driver.getCurrentUrl().contains("/moderator") ||
                        driver.getPageSource().contains("Task") ||
                        !driver.getCurrentUrl().contains("/login"),
                "Should access task management");
    }
}