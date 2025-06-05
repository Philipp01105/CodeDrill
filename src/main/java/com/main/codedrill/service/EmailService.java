package com.main.codedrill.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final UserService userservice;
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    public EmailService(JavaMailSender mailSender, UserService userservice) {
        this.mailSender = mailSender;
        this.userservice = userservice;
    }

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Sends a verification email to the user
     *
     * @param to      The recipient's email address
     * @param subject The email subject
     * @param token   The verification token
     */
    public void sendVerificationEmail(String to, String subject, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);

        String confirmationUrl = baseUrl + "/verify?token=" + token;

        String emailBody = "Hello,\n\n" +
                "Thank you for registering at CodeDrill. " +
                "Please confirm your email address by clicking on the link below:\n\n" +
                confirmationUrl + "\n\n" +
                "This link is valid for 24 hours.\n\n" +
                "If you didn't request this email, you can ignore it.\n\n" +
                "Best regards,\n" +
                "The CodeDrill Team";

        message.setText(emailBody);
        mailSender.send(message);
    }

    /**
     * Sends a verification email to the user
     *
     * @param testResult The recipient's email address
     * @return true if the email was sent successfully, false otherwise
     */
    public boolean sendTestResult(String testResult) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(userservice.findById(1L).getEmail());
            message.setSubject("Startup Test Result - CodeDrill");
            String emailBody = """
                    Hello,
                    Here are the results of your startup test:
                    
                    %s
                    
                    If you have any questions, feel free to reach out.
                    Best regards,
                    The CodeDrill Team""".formatted(testResult);

            message.setText(emailBody);
            mailSender.send(message);
            return true;

        } catch (MailException e) {
            logger.error("Failed to send email: {}", e.getMessage(), e);
            return false;
        }
    }
}
