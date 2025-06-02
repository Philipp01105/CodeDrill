package com.main.codedrill.controller;

import com.main.codedrill.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/api/verification")
public class VerificationController {

    @Autowired
    private UserService userService;

    @GetMapping("/verify")
    public String verifyEmail(@RequestParam String code,
                              @RequestParam(required = false) Boolean api,
                              RedirectAttributes redirectAttributes) {
        boolean verified = userService.verifyEmail(code);

        if (Boolean.TRUE.equals(api)) {
            return "forward:/api/verification/verify-api?code=" + code;
        }

        if (verified) {
            redirectAttributes.addFlashAttribute("verificationSuccess", true);
            redirectAttributes.addFlashAttribute("message", "Email successfully verified! You can now log in.");
            return "redirect:/login";
        } else {
            redirectAttributes.addFlashAttribute("verificationError", true);
            redirectAttributes.addFlashAttribute("message", "Invalid or expired verification code.");
            return "redirect:/login";
        }
    }

    @GetMapping("/verify-api")
    @ResponseBody
    public ResponseEntity<String> verifyEmailApi(@RequestParam String code) {
        boolean verified = userService.verifyEmail(code);

        if (verified) {
            return ResponseEntity.ok("Email successfully verified!");
        } else {
            return ResponseEntity.badRequest().body("Invalid or expired verification code.");
        }
    }

    @PostMapping("/resend")
    @ResponseBody
    public ResponseEntity<String> resendVerificationCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        userService.resendVerificationCode(email);
        return ResponseEntity.ok("A new verification code has been sent if the email is associated with an unverified account.");
    }
}