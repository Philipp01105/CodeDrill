package com.main.codedrill.service;

import com.main.codedrill.model.UserAnalytics;
import com.main.codedrill.repository.UserAnalyticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class AsyncAnalyticsService {


    private final Logger logger = LoggerFactory.getLogger(AsyncAnalyticsService.class);
    private final UserAnalyticsRepository userAnalyticsRepository;
    private final AnalyticsService analyticsService;

    public AsyncAnalyticsService(UserAnalyticsRepository userAnalyticsRepository, AnalyticsService analyticsService) {
        this.userAnalyticsRepository = userAnalyticsRepository;
        this.analyticsService = analyticsService;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackUserLogoutAsync(String sessionId) {
        try {
            if (sessionId == null || sessionId.isEmpty()) {
                logger.error("Warning: Attempted to track logout with null or empty sessionId");
                CompletableFuture.completedFuture(false);
                return;
            }

            logger.info("Starting async logout tracking for session: {}", sessionId);
            Optional<UserAnalytics> analyticsOpt = userAnalyticsRepository.findBySessionId(sessionId);

            if (analyticsOpt.isPresent()) {
                UserAnalytics analytics = analyticsOpt.get();
                analytics.setLogoutTime(LocalDateTime.now());
                userAnalyticsRepository.save(analytics);

                analyticsService.updateSystemAnalyticsForLogout(analytics);

                logger.info("Async logout tracking completed for session: {}", sessionId);
                CompletableFuture.completedFuture(true);
            } else {
                logger.info("No analytics record found for session: {}", sessionId);
                CompletableFuture.completedFuture(false);
            }
        } catch (Exception e) {
            logger.error("Error in async logout tracking: {}", e.getMessage());
            CompletableFuture.failedFuture(e);
        }
    }
}

