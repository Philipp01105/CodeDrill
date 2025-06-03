package com.main.codedrill.component;

import com.main.codedrill.model.UserAnalytics;
import com.main.codedrill.repository.UserAnalyticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;


@Component
public class ServerShutdownHandler implements ApplicationListener<ContextClosedEvent> {

    private final UserAnalyticsRepository userAnalyticsRepository;
    private static final Logger logger = LoggerFactory.getLogger(ServerShutdownHandler.class);

    @Autowired
    public ServerShutdownHandler(UserAnalyticsRepository userAnalyticsRepository) {
        this.userAnalyticsRepository = userAnalyticsRepository;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        LocalDateTime shutdownTime = LocalDateTime.now();
        logger.info("Server is shutting down. Closing open sessions...");

        // Hole alle UserAnalytics-Einträge ohne Logout-Zeit
        List<UserAnalytics> openAnalytics = userAnalyticsRepository.findByLogoutTimeIsNull();
        logger.info("{} open sessions found", openAnalytics.size());

        for (UserAnalytics analytics : openAnalytics) {
            analytics.setLogoutTime(shutdownTime);

            // Berechne die Sitzungsdauer
            if (analytics.getLoginTime() != null) {
                Duration timeSpent = Duration.between(analytics.getLoginTime(), shutdownTime);
                analytics.setTimeSpentSeconds(timeSpent.getSeconds());
            }
        }

        // Speichere alle aktualisierten Einträge
        if (!openAnalytics.isEmpty()) {
            userAnalyticsRepository.saveAll(openAnalytics);
            logger.info("All open sessions have been closed and saved successfully.");
        }
    }
}
