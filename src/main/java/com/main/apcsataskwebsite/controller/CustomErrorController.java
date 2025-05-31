package com.main.apcsataskwebsite.controller;

import com.main.apcsataskwebsite.service.UserService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

    @Autowired
    private UserService userService;

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Exception exception = (Exception) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            httpStatus = HttpStatus.valueOf(statusCode);
        }

        model.addAttribute("errorCode", httpStatus.value());
        model.addAttribute("errorMessage", httpStatus.getReasonPhrase());

        // Prüfen, ob ein Admin eingeloggt ist
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = false;

        if (auth != null && auth.isAuthenticated()) {
            if(userService.findByUsername(auth.getName()).isAdmin()) {
                isAdmin = true;
            }
        }

        // Detaillierte Fehlermeldung nur für Admins
        if (isAdmin) {
            model.addAttribute("isAdmin", true);
            model.addAttribute("exception", exception != null ? exception.getMessage() : "Unbekannter Fehler");
            model.addAttribute("path", path);
            model.addAttribute("trace", exception != null ? exception.getStackTrace() : null);
        } else {
            model.addAttribute("isAdmin", false);
        }

        return "error";
    }
}