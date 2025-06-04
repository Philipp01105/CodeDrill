package com.main.codedrill.component;

import com.main.codedrill.service.AsyncAnalyticsService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.WebApplicationContextUtils;

@Component
public class SessionListener implements HttpSessionListener {

    private static final Logger logger = LoggerFactory.getLogger(SessionListener.class);

    private final AsyncAnalyticsService asyncAnalyticsService;

    public SessionListener(AsyncAnalyticsService asyncAnalyticsService) {
        this.asyncAnalyticsService = asyncAnalyticsService;
    }

    private volatile boolean isShuttingDown = false;

    @EventListener(ContextClosedEvent.class)
    public void handleContextClosed() {
        this.isShuttingDown = true;
        logger.info("Application context is closing, session tracking disabled");
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        try {
            String sessionId = se.getSession().getId();
            logger.debug("Session destroyed: {}", sessionId);

            if (isShuttingDown) {
                logger.debug("Server is shutting down, skipping logout tracking for session: {}", sessionId);
                return;
            }

            if (asyncAnalyticsService != null) {
                asyncAnalyticsService.trackUserLogoutAsync(sessionId);
            } else {
                ApplicationContext context = WebApplicationContextUtils
                        .getWebApplicationContext(se.getSession().getServletContext());

                if (context != null) {
                    AsyncAnalyticsService service = context.getBean(AsyncAnalyticsService.class);
                    service.trackUserLogoutAsync(sessionId);
                } else {
                    logger.error("Application context is null, cannot track logout.");
                }
            }
        } catch (Exception e) {
            logger.error("Error recording user logout: {}", e.getMessage());
            logger.debug("Stack trace:", e);
        }
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        logger.debug("Session created: {}", se.getSession().getId());
    }
}