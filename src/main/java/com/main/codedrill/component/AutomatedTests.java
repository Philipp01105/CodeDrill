package com.main.codedrill.component;

import com.main.codedrill.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.stereotype.Component;


@Component
public class AutomatedTests {

    EmailService emailService;
    
    Logger logger = LoggerFactory.getLogger(AutomatedTests.class);
    
    public AutomatedTests(EmailService emailService) {
        this.emailService = emailService;
    }

    @EventListener(ApplicationStartup.class)
    public void runTests() {
        String testResult = "";
        logger.info("Running automated tests...");


        logger.info("Automated tests completed.");
        if(!emailService.sendTestResult(testResult))
        {
            logger.info("Failed to send test results via email - Sending it to console.");
            sendToConsole(testResult);
        }
        
    }

    private void sendToConsole(String testResult) {
        logger.info(testResult);
    }
}
