package com.main.codedrill.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.WebDriverWait;

public class HomePage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    @FindBy(css = "h2")
    private WebElement welcomeTitle;

    @FindBy(linkText = "Login")
    private WebElement loginLink;

    @FindBy(linkText = "Dashboard")
    private WebElement dashboardLink;

    @FindBy(css = "button[type='submit']")
    private WebElement logoutButton;

    @FindBy(linkText = "Tasks")
    private WebElement tasksLink;

    public HomePage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
        PageFactory.initElements(driver, this);
    }

    public void open(String baseUrl) {
        driver.get(baseUrl);
    }

    public boolean isWelcomeTitleDisplayed() {
        return welcomeTitle.isDisplayed() && welcomeTitle.getText().contains("Welcome to CodeDrill");
    }

    public boolean isLoginLinkDisplayed() {
        try {
            return loginLink.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isDashboardLinkDisplayed() {
        try {
            return dashboardLink.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    public void clickLogin() {
        loginLink.click();
    }

    public void clickLogout() {
        logoutButton.click();
    }

    public void clickTasks() {
        if (tasksLink != null && tasksLink.isDisplayed()) {
            tasksLink.click();
        } else {
            driver.findElement(By.linkText("Tasks")).click();
        }
    }

    public boolean isLoggedIn() {
        return !isLoginLinkDisplayed();
    }
}