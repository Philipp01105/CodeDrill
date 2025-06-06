package com.main.codedrill.config;

import com.main.codedrill.model.User;
import com.main.codedrill.service.UserService;
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

        String username = authentication.getName();
        User user = userService.findByUsername(username);
        
        if (user != null && user.isUsingTempPassword()) {
            getRedirectStrategy().sendRedirect(request, response, "/change-password");
        } else {
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
} 