package com.main.codedrill.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    private static final String VERIFICATION_BASE_URL = "https://ph-studios.net/api/verification/verify?code=";

    public void sendVerificationEmail(String toEmail, String verificationCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            Context context = new Context();
            context.setVariable("verificationCode", verificationCode);
            context.setVariable("verificationUrl", VERIFICATION_BASE_URL + verificationCode);

            String htmlContent = templateEngine.process("verificationEmail", context);

            helper.setTo(toEmail);
            helper.setSubject("CodeDrill - Email Verification");
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            sendSimpleVerificationEmail(toEmail, verificationCode);
        }
    }

    public void sendSimpleVerificationEmail(String toEmail, String verificationCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("CodeDrill - Email Verification");
        String verificationUrl = VERIFICATION_BASE_URL + verificationCode;
        message.setText("Your verification code is: " + verificationCode +
                "\n\nPlease click the following link to verify your email: " +
                verificationUrl);
        mailSender.send(message);
    }
}