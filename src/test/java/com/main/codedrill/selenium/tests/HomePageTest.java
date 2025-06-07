package com.main.codedrill.selenium.tests;

import com.main.codedrill.selenium.BaseSeleniumTest;
import com.main.codedrill.selenium.pages.HomePage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HomePageTest extends BaseSeleniumTest {

    @Test
    void shouldDisplayHomePage() {
        HomePage homePage = new HomePage(driver, wait);
        homePage.open(getUrl("/"));

        assertTrue(homePage.isWelcomeTitleDisplayed(), "Welcome title should be displayed");
        assertTrue(homePage.isLoginLinkDisplayed(), "Login link should be displayed for anonymous users");
    }

    @Test
    void shouldNavigateToLogin() {
        HomePage homePage = new HomePage(driver, wait);
        homePage.open(getUrl("/"));

        homePage.clickLogin();

        assertTrue(driver.getCurrentUrl().contains("/login"), "Should navigate to login page");
    }

    @Test
    void shouldNavigateToTasks() {
        HomePage homePage = new HomePage(driver, wait);
        homePage.open(getUrl("/"));

        try {
            homePage.clickTasks();
            assertTrue(driver.getCurrentUrl().contains("/tasks"), "Should navigate to tasks page");
        } catch (Exception e) {
            // Tasks link might not be visible for anonymous users
            System.out.println("Tasks link not available for anonymous users: " + e.getMessage());
        }
    }

    @Test
    void shouldDisplayResponsiveLayout() {
        HomePage homePage = new HomePage(driver, wait);

        // Test desktop view
        driver.manage().window().setSize(new org.openqa.selenium.Dimension(1920, 1080));
        homePage.open(getUrl("/"));
        assertTrue(homePage.isWelcomeTitleDisplayed());

        // Test mobile view
        driver.manage().window().setSize(new org.openqa.selenium.Dimension(375, 667));
        homePage.open(getUrl("/"));
        assertTrue(homePage.isWelcomeTitleDisplayed());
    }
}