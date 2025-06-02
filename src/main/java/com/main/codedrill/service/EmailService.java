package com.main.codedrill.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Sends a verification email to the user
     *
     * @param to The recipient's email address
     * @param subject The email subject
     * @param token The verification token
     */
    public void sendVerificationEmail(String to, String subject, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);

        String confirmationUrl = baseUrl + "/verify?token=" + token;

        String emailBody = "Hello,\n\n" +
                "Thank you for registering with CodeDrill. " +
                "Please confirm your email address by clicking on the link below:\n\n" +
                confirmationUrl + "\n\n" +
                "This link is valid for 24 hours.\n\n" +
                "If you didn't request this email, you can ignore it.\n\n" +
                "Best regards,\n" +
                "The CodeDrill Team";

        message.setText(emailBody);
        mailSender.send(message);
    }
}
