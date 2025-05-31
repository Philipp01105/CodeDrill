package com.main.apcsataskwebsite.config;

import com.main.apcsataskwebsite.service.AsyncAnalyticsService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.WebApplicationContextUtils;

@Component
public class SessionListener implements HttpSessionListener {

    @Autowired
    private AsyncAnalyticsService asyncAnalyticsService;

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        try {
            String sessionId = se.getSession().getId();
            System.out.println("Session destroyed: " + sessionId);

            // Verwenden Sie den injizierten Service direkt, wenn möglich
            if (asyncAnalyticsService != null) {
                asyncAnalyticsService.trackUserLogoutAsync(sessionId);
            } else {
                // Fallback zum WebApplicationContext, falls der Service nicht injiziert wurde
                ApplicationContext context = WebApplicationContextUtils
                        .getWebApplicationContext(se.getSession().getServletContext());

                if (context != null) {
                    AsyncAnalyticsService service = context.getBean(AsyncAnalyticsService.class);
                    service.trackUserLogoutAsync(sessionId);
                } else {
                    System.err.println("Application context is null, cannot track logout.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error in session listener: " + e.getMessage());
            e.printStackTrace(); // Ausführlichere Fehlermeldung für die Diagnose
        }
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        System.out.println("Session created: " + se.getSession().getId());
    }
}