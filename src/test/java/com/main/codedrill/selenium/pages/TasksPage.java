package com.main.codedrill.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

public class TasksPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    @FindBy(css = ".task-card")
    private List<WebElement> taskCards;

    @FindBy(css = "select[name='tag']")
    private WebElement tagFilter;

    @FindBy(css = ".tag-filter button")
    private List<WebElement> tagButtons;

    @FindBy(css = "h1, h2")
    private WebElement pageTitle;

    public TasksPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
        PageFactory.initElements(driver, this);
    }

    public boolean isPageLoaded() {
        try {
            return pageTitle.isDisplayed() &&
                    (pageTitle.getText().contains("Tasks") || pageTitle.getText().contains("Aufgaben"));
        } catch (Exception e) {
            return false;
        }
    }

    public int getTaskCount() {
        return taskCards.size();
    }

    public void clickTask(int index) {
        if (index < taskCards.size()) {
            taskCards.get(index).click();
        }
    }

    public void clickFirstTask() {
        if (!taskCards.isEmpty()) {
            taskCards.get(0).click();
        }
    }

    public boolean hasTaskWithTitle(String title) {
        return taskCards.stream()
                .anyMatch(card -> card.getText().contains(title));
    }

    public void filterByTag(String tag) {
        for (WebElement tagButton : tagButtons) {
            if (tagButton.getText().equals(tag)) {
                tagButton.click();
                break;
            }
        }
    }

    public List<String> getTaskTitles() {
        return taskCards.stream()
                .map(card -> card.findElement(By.cssSelector("h3, .task-title")).getText())
                .toList();
    }
}