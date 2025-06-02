package com.main.codedrill.controller;

import com.main.codedrill.model.User;
import com.main.codedrill.model.VerificationToken;
import com.main.codedrill.service.EmailService;
import com.main.codedrill.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class VerificationController {

    private final UserService userService;
    private final EmailService emailService;

    @Autowired
    public VerificationController(UserService userService, EmailService emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

    @GetMapping("/verify")
    public String verifyAccount(@RequestParam String token, Model model) {
        boolean isVerified = userService.verifyUser(token);

        if (isVerified) {
            model.addAttribute("verificationSuccess", true);
            model.addAttribute("message", "Your email address has been successfully verified. You can now log in.");
        } else {
            model.addAttribute("verificationSuccess", false);
            model.addAttribute("message", "The verification link is invalid or has expired.");
        }

        return "verification-result";
    }

    @GetMapping("/resend-verification")
    public String resendVerificationForm() {
        return "resend-verification";
    }

    @PostMapping("/resend-verification")
    public String resendVerification(@RequestParam String email, RedirectAttributes redirectAttributes) {
        User user = userService.findByEmail(email);

        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "This email address was not found.");
            return "redirect:/resend-verification";
        }

        if (user.isEnabled()) {
            redirectAttributes.addFlashAttribute("error", "Your account is already verified.");
            return "redirect:/resend-verification";
        }

        // Create a new token and send email
        VerificationToken token = userService.generateNewVerificationToken(user);
        try {
            emailService.sendVerificationEmail(
                user.getEmail(),
                "CodeDrill - Verify Your Email Address",
                token.getToken()
            );
            redirectAttributes.addFlashAttribute("success", "A new verification link has been sent to your email address.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "An error occurred while sending the email. Please try again later.");
        }

        return "redirect:/resend-verification";
    }
}
