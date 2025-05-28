package com.main.apcsataskwebsite.config;

import com.main.apcsataskwebsite.model.User;
import com.main.apcsataskwebsite.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;

public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserService userService;

    public LoginSuccessHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        // Get current user
        String username = authentication.getName();
        User user = userService.findByUsername(username);
        
        // Redirect to change password page if user is using a temporary password
        if (user != null && user.isUsingTempPassword()) {
            getRedirectStrategy().sendRedirect(request, response, "/change-password");
        } else {
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
} 