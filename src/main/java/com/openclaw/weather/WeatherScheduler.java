package com.openclaw.weather;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WeatherScheduler {

    private final WeatherService weatherService;
    private final WeatherProperties weatherProperties;

    public WeatherScheduler(WeatherService weatherService, WeatherProperties weatherProperties) {
        this.weatherService = weatherService;
        this.weatherProperties = weatherProperties;
    }

    // Run every day at 6:00 AM in the location's timezone
    // For simplicity, we run at 10:00 UTC (covers most North American morning times)
    @Scheduled(cron = "0 0 10 * * *", zone = "UTC")
    public void sendDailyForecasts() {
        List<WeatherLocation> locations = weatherProperties.getLocations();
        for (WeatherLocation location : locations) {
            try {
                weatherService.sendDailyForecast(location);
            } catch (Exception e) {
                System.err.println("Failed to send forecast for " + location.getName() + ": " + e.getMessage());
            }
        }
    }

    // Nightly warning (runs at 20:00 UTC)
    @Scheduled(cron = "0 0 20 * * *", zone = "UTC")
    public void sendNightlyWarnings() {
        List<WeatherLocation> locations = weatherProperties.getLocations();
        for (WeatherLocation location : locations) {
            try {
                weatherService.sendNightlyWarning(location);
            } catch (Exception e) {
                System.err.println("Nightly warning failed for " + location.getName() + ": " + e.getMessage());
            }
        }
    }

    // 30-minute onset alerts (runs at :00 and :30 from 6:00 to 22:30 UTC)
    @Scheduled(cron = "0 0,30 6-22 * * *", zone = "UTC")
    public void checkOnsetAlerts() {
        List<WeatherLocation> locations = weatherProperties.getLocations();
        for (WeatherLocation location : locations) {
            try {
                weatherService.checkAndSendOnsetAlerts(location);
            } catch (Exception e) {
                System.err.println("Alert check failed for " + location.getName() + ": " + e.getMessage());
            }
        }
    }
}