package com.main.apcsataskwebsite.config;

import com.main.apcsataskwebsite.service.AsyncAnalyticsService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.WebApplicationContextUtils;

@Component
public class SessionListener implements HttpSessionListener {

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        try {
            String sessionId = se.getSession().getId();
            System.out.println("Session destroyed: " + sessionId);

            ApplicationContext context = WebApplicationContextUtils
                    .getWebApplicationContext(se.getSession().getServletContext());

            if (context != null) {
                AsyncAnalyticsService asyncAnalyticsService = context.getBean(AsyncAnalyticsService.class);
                asyncAnalyticsService.trackUserLogoutAsync(sessionId);
            }
        } catch (Exception e) {
            System.err.println("Error in session listener: " + e.getMessage());
        }
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        System.out.println("Session created: " + se.getSession().getId());
    }
}