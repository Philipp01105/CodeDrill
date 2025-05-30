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

@Service
public class AsyncAnalyticsService {

    @Autowired
    private UserAnalyticsRepository userAnalyticsRepository;

    @Autowired
    private AnalyticsService analyticsService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackUserLogoutAsync(String sessionId) {
        try {
            Optional<UserAnalytics> analyticsOpt = userAnalyticsRepository.findBySessionId(sessionId);
            if (analyticsOpt.isPresent()) {
                UserAnalytics analytics = analyticsOpt.get();
                analytics.setLogoutTime(LocalDateTime.now());
                userAnalyticsRepository.save(analytics);

                // Update system analytics with session time
                analyticsService.updateSystemAnalyticsForLogout(analytics);

                System.out.println("Async logout tracking completed for session: " + sessionId);
            }
        } catch (Exception e) {
            System.err.println("Error in async logout tracking: " + e.getMessage());
        }
    }
}