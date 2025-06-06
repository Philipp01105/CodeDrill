package com.main.codedrill.component;

import com.main.codedrill.model.User;
import com.main.codedrill.service.AnalyticsService;
import com.main.codedrill.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuthenticationEventListener {

    private final Logger logger = org.slf4j.LoggerFactory.getLogger(AuthenticationEventListener.class);

    private final AnalyticsService analyticsService;
    private final UserService userService;

    public AuthenticationEventListener(AnalyticsService analyticsService, UserService userService) {
        this.analyticsService = analyticsService;
        this.userService = userService;
    }

    @EventListener
    public void handleAuthenticationSuccess(AuthenticationSuccessEvent event) {
        try {
            Authentication auth = event.getAuthentication();
            String username = auth.getName();

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attributes.getRequest();
            HttpSession session = request.getSession(true);

            User user = userService.findByUsername(username);
            if (user != null) {
                analyticsService.trackUserLogin(user, session.getId(), request);
                logger.info("Tracked login for user: {}", username);
            }
        } catch (Exception e) {
            logger.error("Error tracking login: {}", e.getMessage());
        }
    }
}