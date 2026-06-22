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
}