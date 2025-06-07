package com.main.codedrill.selenium.pages;

import com.main.codedrill.selenium.utils.CodeEditorUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

public class CodeEditorPage {
    private final WebDriver driver;
    private final WebDriverWait wait;
    private final WebDriverWait longWait;

    @FindBy(css = ".task-title, h1, h2")
    private WebElement taskTitle;

    @FindBy(css = ".task-description, .description")
    private WebElement taskDescription;

    @FindBy(css = "#output, .output-area, .console-output, .result")
    private WebElement outputArea;

    @FindBy(css = ".success-message, .correct-message, .alert-success")
    private WebElement successMessage;

    @FindBy(css = ".error-message, .alert-danger")
    private WebElement errorMessage;

    public CodeEditorPage(WebDriver driver, WebDriverWait wait, WebDriverWait longWait) {
        this.driver = driver;
        this.wait = wait;
        this.longWait = longWait;
        PageFactory.initElements(driver, this);
    }

    public boolean isPageLoaded() {
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("h1, h2, .task-title")));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getTaskTitle() {
        try {
            return taskTitle.getText();
        } catch (Exception e) {
            // Fallback: suche nach anderen möglichen Title-Elementen
            List<WebElement> titles = driver.findElements(By.cssSelector("h1, h2, h3, .title"));
            return titles.isEmpty() ? "No title found" : titles.get(0).getText();
        }
    }

    public String getTaskDescription() {
        try {
            return taskDescription.getText();
        } catch (Exception e) {
            return "No description found";
        }
    }

    public void enterCode(String code) {
        // Verwende die Utils-Klasse für robuste Code-Eingabe
        CodeEditorUtils.enterCodeSafely(driver, wait, code);
    }

    public void clickRun() {
        // Verwende die Utils-Klasse für robustes Button-Klicken
        CodeEditorUtils.clickRunButton(driver, wait);
    }

    public void runCode(String code) {
        System.out.println("Eingabe von Code...");
        enterCode(code);

        try {
            Thread.sleep(1000); // Kurze Pause zwischen Code-Eingabe und Ausführung
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Klicke Run-Button...");
        clickRun();
    }

    public String getOutput() {
        try {
            // Warte länger auf Output da Code-Ausführung Zeit braucht
            longWait.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#output")),
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".output-area")),
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".console-output")),
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".result"))
            ));

            // Versuche verschiedene Output-Selektoren
            String[] outputSelectors = {
                    "#output", ".output-area", ".console-output", ".result",
                    ".execution-result", ".code-output", "#console"
            };

            for (String selector : outputSelectors) {
                try {
                    WebElement output = driver.findElement(By.cssSelector(selector));
                    if (output.isDisplayed() && !output.getText().trim().isEmpty()) {
                        return output.getText();
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            return "No output found";
        } catch (Exception e) {
            return "Error getting output: " + e.getMessage();
        }
    }

    public boolean hasSuccessMessage() {
        try {
            return successMessage.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasErrorMessage() {
        try {
            return errorMessage.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isCodeExecuted() {
        try {
            // Warte auf Output oder Resultate
            longWait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("#output")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".output-area")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".console-output")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".result")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".execution-result"))
            ));
            return true;
        } catch (Exception e) {
            System.out.println("Timeout beim Warten auf Code-Ausführung: " + e.getMessage());
            return false;
        }
    }

    public boolean hasCodeEditor() {
        // Prüfe ob mindestens ein Code-Editor-Element vorhanden ist
        return CodeEditorUtils.hasCodeEditor(driver);
    }

    public void waitForEditor() {
        // Warte bis Code-Editor bereit ist
        CodeEditorUtils.waitForEditorReady(driver, wait);
    }
}