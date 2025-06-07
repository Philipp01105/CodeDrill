package com.main.codedrill.selenium.utils;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;
import java.util.List;

public class CodeEditorUtils {

    public static void enterCodeSafely(WebDriver driver, WebDriverWait wait, String code) {
        boolean success = false;
        Exception lastException = null;

        System.out.println("Versuche Code einzugeben: " + code.substring(0, Math.min(50, code.length())) + "...");

        // Methode 1: CodeMirror
        try {
            WebElement codeMirror = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector(".CodeMirror")));

            System.out.println("CodeMirror Editor gefunden, versuche Code-Eingabe...");

            Actions actions = new Actions(driver);
            actions.moveToElement(codeMirror).click().perform();

            // Warte kurz für Fokus
            Thread.sleep(300);

            // Lösche alles
            actions.keyDown(Keys.CONTROL).sendKeys("a").keyUp(Keys.CONTROL).perform();
            Thread.sleep(100);

            // Eingabe des Codes
            WebElement textarea = driver.findElement(By.cssSelector(".CodeMirror textarea"));
            textarea.sendKeys(code);

            success = true;
            System.out.println("Code erfolgreich in CodeMirror eingegeben");

        } catch (Exception e) {
            lastException = e;
            System.out.println("CodeMirror-Methode fehlgeschlagen: " + e.getMessage());
        }

        // Methode 2: Ace Editor
        if (!success) {
            try {
                WebElement aceEditor = wait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector(".ace_editor")));

                System.out.println("Ace Editor gefunden, versuche Code-Eingabe...");

                Actions actions = new Actions(driver);
                actions.moveToElement(aceEditor).click().perform();
                Thread.sleep(300);

                // Lösche alles und gebe neuen Code ein
                actions.keyDown(Keys.CONTROL).sendKeys("a").keyUp(Keys.CONTROL).perform();
                Thread.sleep(100);
                actions.sendKeys(code).perform();

                success = true;
                System.out.println("Code erfolgreich in Ace Editor eingegeben");

            } catch (Exception e) {
                lastException = e;
                System.out.println("Ace Editor-Methode fehlgeschlagen: " + e.getMessage());
            }
        }

        // Methode 3: Standard Textarea
        if (!success) {
            try {
                List<WebElement> textareas = driver.findElements(By.tagName("textarea"));
                System.out.println("Gefundene Textareas: " + textareas.size());

                for (int i = 0; i < textareas.size(); i++) {
                    WebElement textarea = textareas.get(i);

                    if (textarea.isDisplayed() && textarea.isEnabled()) {
                        System.out.println("Versuche Textarea " + (i + 1) + "...");

                        // Scrolle zum Element
                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});",
                                textarea);
                        Thread.sleep(500);

                        // Warte bis Element klickbar ist
                        wait.until(ExpectedConditions.elementToBeClickable(textarea));

                        // Klicke und fokussiere
                        Actions actions = new Actions(driver);
                        actions.moveToElement(textarea).click().perform();
                        Thread.sleep(200);

                        // Lösche vorherigen Inhalt
                        textarea.clear();
                        actions.keyDown(Keys.CONTROL).sendKeys("a").keyUp(Keys.CONTROL).perform();
                        Thread.sleep(100);

                        // Gebe Code ein
                        textarea.sendKeys(code);

                        // Überprüfe ob Eingabe erfolgreich war
                        String value = textarea.getAttribute("value");
                        if (value != null && value.contains(code.substring(0, Math.min(10, code.length())))) {
                            success = true;
                            System.out.println("Code erfolgreich in Textarea " + (i + 1) + " eingegeben");
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                lastException = e;
                System.out.println("Textarea-Methode fehlgeschlagen: " + e.getMessage());
            }
        }

        // Methode 4: JavaScript Fallback
        if (!success) {
            try {
                System.out.println("Versuche JavaScript-Fallback...");

                String jsScript = """
                    var editors = document.querySelectorAll('textarea, .CodeMirror');
                    var codeSet = false;
                    
                    console.log('Gefundene Editor-Elemente:', editors.length);
                    
                    for (var i = 0; i < editors.length; i++) {
                        var editor = editors[i];
                        
                        if (editor.tagName === 'TEXTAREA' && editor.offsetParent !== null) {
                            console.log('Setze Code in Textarea:', i);
                            editor.value = arguments[0];
                            editor.dispatchEvent(new Event('input', { bubbles: true }));
                            editor.dispatchEvent(new Event('change', { bubbles: true }));
                            codeSet = true;
                            break;
                        }
                        
                        if (editor.classList.contains('CodeMirror')) {
                            console.log('Versuche CodeMirror:', i);
                            if (editor.CodeMirror) {
                                editor.CodeMirror.setValue(arguments[0]);
                                codeSet = true;
                                break;
                            }
                        }
                    }
                    
                    return codeSet;
                    """;

                Boolean result = (Boolean) ((JavascriptExecutor) driver).executeScript(jsScript, code);

                if (Boolean.TRUE.equals(result)) {
                    success = true;
                    System.out.println("Code erfolgreich via JavaScript eingegeben");
                }

            } catch (Exception e) {
                lastException = e;
                System.out.println("JavaScript-Methode fehlgeschlagen: " + e.getMessage());
            }
        }

        if (!success) {
            throw new RuntimeException("Konnte Code nicht eingeben. Letzte Exception: " +
                    (lastException != null ? lastException.getMessage() : "Unbekannt"));
        }
    }

    public static void clickRunButton(WebDriver driver, WebDriverWait wait) {
        boolean clicked = false;
        Exception lastException = null;

        System.out.println("Suche nach Run-Button...");

        String[] runSelectors = {
                "#run-code-btn",
                ".run-button",
                "button[onclick*='runCode']",
                "button[onclick*='executeCode']",
                "input[type='submit'][value*='Run']",
                "input[type='button'][value*='Run']",
                ".btn-primary:contains('Run')",
                ".btn:contains('Execute')"
        };

        // Versuche spezifische Selektoren
        for (String selector : runSelectors) {
            try {
                WebElement button = wait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector(selector)));

                System.out.println("Run-Button gefunden mit Selektor: " + selector);

                ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});",
                        button);
                Thread.sleep(300);

                button.click();
                clicked = true;
                System.out.println("Run-Button geklickt via: " + selector);
                break;

            } catch (Exception e) {
                lastException = e;
                continue;
            }
        }

        // Fallback: Suche nach Button-Text
        if (!clicked) {
            try {
                System.out.println("Fallback: Suche nach Buttons mit Run/Execute Text...");

                List<WebElement> buttons = driver.findElements(By.tagName("button"));
                buttons.addAll(driver.findElements(By.cssSelector("input[type='submit']")));
                buttons.addAll(driver.findElements(By.cssSelector("input[type='button']")));

                System.out.println("Gefundene Buttons: " + buttons.size());

                for (WebElement button : buttons) {
                    String text = button.getText().toLowerCase();
                    String value = button.getAttribute("value");
                    if (value != null) value = value.toLowerCase();

                    System.out.println("Prüfe Button: Text='" + text + "', Value='" + value + "'");

                    if ((text.contains("run") || text.contains("execute") ||
                            (value != null && (value.contains("run") || value.contains("execute")))) &&
                            button.isDisplayed() && button.isEnabled()) {

                        System.out.println("Passender Button gefunden: " + text + "/" + value);

                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});",
                                button);
                        Thread.sleep(300);

                        wait.until(ExpectedConditions.elementToBeClickable(button));
                        button.click();
                        clicked = true;
                        System.out.println("Run-Button gefunden via Text: " + text + "/" + value);
                        break;
                    }
                }
            } catch (Exception e) {
                lastException = e;
                System.out.println("Button-Text-Suche fehlgeschlagen: " + e.getMessage());
            }
        }

        if (!clicked) {
            throw new RuntimeException("Konnte Run-Button nicht finden oder klicken. Letzte Exception: " +
                    (lastException != null ? lastException.getMessage() : "Unbekannt"));
        }
    }

    public static boolean hasCodeEditor(WebDriver driver) {
        // Prüfe verschiedene Editor-Typen
        boolean hasTextarea = !driver.findElements(By.tagName("textarea")).isEmpty();
        boolean hasCodeMirror = !driver.findElements(By.cssSelector(".CodeMirror")).isEmpty();
        boolean hasAceEditor = !driver.findElements(By.cssSelector(".ace_editor")).isEmpty();

        System.out.println("Editor-Check: Textarea=" + hasTextarea +
                ", CodeMirror=" + hasCodeMirror +
                ", AceEditor=" + hasAceEditor);

        return hasTextarea || hasCodeMirror || hasAceEditor;
    }

    public static void waitForEditorReady(WebDriver driver, WebDriverWait wait) {
        try {
            // Warte auf mindestens einen Editor-Typ
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.tagName("textarea")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".CodeMirror")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".ace_editor"))
            ));

            System.out.println("Code-Editor ist bereit");

            // Zusätzliche Wartezeit für JavaScript-Initialisierung
            Thread.sleep(1000);

        } catch (Exception e) {
            System.out.println("Fehler beim Warten auf Editor: " + e.getMessage());
            throw new RuntimeException("Code-Editor wurde nicht geladen", e);
        }
    }

    public static void debugEditorElements(WebDriver driver) {
        System.out.println("=== DEBUG: Editor-Elemente ===");

        // Alle Textareas
        List<WebElement> textareas = driver.findElements(By.tagName("textarea"));
        System.out.println("Textareas gefunden: " + textareas.size());
        for (int i = 0; i < textareas.size(); i++) {
            WebElement ta = textareas.get(i);
            System.out.println("  Textarea " + i + ": visible=" + ta.isDisplayed() +
                    ", enabled=" + ta.isEnabled() +
                    ", id=" + ta.getAttribute("id") +
                    ", class=" + ta.getAttribute("class"));
        }

        // CodeMirror
        List<WebElement> codeMirrors = driver.findElements(By.cssSelector(".CodeMirror"));
        System.out.println("CodeMirror gefunden: " + codeMirrors.size());

        // Ace Editor
        List<WebElement> aceEditors = driver.findElements(By.cssSelector(".ace_editor"));
        System.out.println("Ace Editor gefunden: " + aceEditors.size());

        // Alle Buttons
        List<WebElement> buttons = driver.findElements(By.tagName("button"));
        System.out.println("Buttons gefunden: " + buttons.size());
        for (int i = 0; i < Math.min(buttons.size(), 10); i++) { // Nur erste 10
            WebElement btn = buttons.get(i);
            System.out.println("  Button " + i + ": text='" + btn.getText() +
                    "', onclick=" + btn.getAttribute("onclick") +
                    ", id=" + btn.getAttribute("id"));
        }

        System.out.println("=== END DEBUG ===");
    }
}