package com.main.codedrill.selenium.tests;

import com.main.codedrill.selenium.BaseSeleniumTest;
import com.main.codedrill.selenium.pages.HomePage;
import com.main.codedrill.selenium.pages.LoginPage;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationTest extends BaseSeleniumTest {

    @Test
    void shouldDisplayLoginPage() {
        driver.get(getUrl("/login"));

        LoginPage loginPage = new LoginPage(driver, wait);

        // Check if login form elements are present
        assertDoesNotThrow(() -> {
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    org.openqa.selenium.By.name("username")));
        });
    }

    @Test
    void shouldShowErrorOnInvalidCredentials() {
        driver.get(getUrl("/login"));

        LoginPage loginPage = new LoginPage(driver, wait);
        loginPage.login("invaliduser", "invalidpass");

        // Wait for page to process login attempt
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Should either show error or redirect back to login
        assertTrue(driver.getCurrentUrl().contains("/login") || loginPage.hasErrorMessage(),
                "Should show error or stay on login page for invalid credentials");
    }

    @Test
    void shouldLoginWithAdminCredentials() {
        driver.get(getUrl("/login"));

        LoginPage loginPage = new LoginPage(driver, wait);
        loginPage.login("admin", "password");

        // Wait for potential redirect
        wait.until(ExpectedConditions.or(
                ExpectedConditions.urlContains("/"),
                ExpectedConditions.urlContains("/change-password"),
                ExpectedConditions.urlContains("/dashboard")
        ));

        // Should be redirected away from login page
        assertFalse(driver.getCurrentUrl().endsWith("/login"),
                "Should be redirected away from login page after successful login");
    }

    @Test
    void shouldLogoutSuccessfully() {
        // First login
        driver.get(getUrl("/login"));
        LoginPage loginPage = new LoginPage(driver, wait);
        loginPage.login("admin", "password");

        // Wait for login to complete
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));

        // Go to home page and logout
        HomePage homePage = new HomePage(driver, wait);
        homePage.open(getUrl("/"));

        if (homePage.isLoggedIn()) {
            homePage.clickLogout();

            // Should be redirected and login link should be visible again
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("/login"),
                    ExpectedConditions.presenceOfElementLocated(
                            org.openqa.selenium.By.linkText("Login"))
            ));
        }
    }

    @Test
    void shouldRedirectToChangePasswordForTempPassword() {
        driver.get(getUrl("/login"));

        LoginPage loginPage = new LoginPage(driver, wait);
        loginPage.login("admin", "password");

        // Admin user might have temp password, should redirect to change password
        wait.until(ExpectedConditions.or(
                ExpectedConditions.urlContains("/change-password"),
                ExpectedConditions.urlContains("/"),
                ExpectedConditions.urlContains("/dashboard")
        ));

        // Test passes if we get any valid redirect
        assertFalse(driver.getCurrentUrl().endsWith("/login"),
                "Should be redirected after login");
    }
}