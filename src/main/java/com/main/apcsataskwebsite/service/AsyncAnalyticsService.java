package com.main.apcsataskwebsite.service;

import com.main.apcsataskwebsite.model.UserAnalytics;
import com.main.apcsataskwebsite.repository.UserAnalyticsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class AsyncAnalyticsService {

    @Autowired
    private UserAnalyticsRepository userAnalyticsRepository;

    @Autowired
    private AnalyticsService analyticsService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Boolean> trackUserLogoutAsync(String sessionId) {
        try {
            if (sessionId == null || sessionId.isEmpty()) {
                System.err.println("Warning: Attempted to track logout with null or empty sessionId");
                return CompletableFuture.completedFuture(false);
            }

            System.out.println("Starting async logout tracking for session: " + sessionId);
            Optional<UserAnalytics> analyticsOpt = userAnalyticsRepository.findBySessionId(sessionId);

            if (analyticsOpt.isPresent()) {
                UserAnalytics analytics = analyticsOpt.get();
                analytics.setLogoutTime(LocalDateTime.now());
                userAnalyticsRepository.save(analytics);

                // Update system analytics with session time
                analyticsService.updateSystemAnalyticsForLogout(analytics);

                System.out.println("Async logout tracking completed for session: " + sessionId);
                return CompletableFuture.completedFuture(true);
            } else {
                System.out.println("No analytics record found for session: " + sessionId);
                return CompletableFuture.completedFuture(false);
            }
        } catch (Exception e) {
            System.err.println("Error in async logout tracking: " + e.getMessage());
            e.printStackTrace();
            return CompletableFuture.failedFuture(e);
        }
    }
}

