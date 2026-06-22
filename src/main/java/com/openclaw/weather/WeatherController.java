package com.openclaw.weather;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/weather")
public class WeatherController {

    private final WeatherService weatherService;
    private final WeatherProperties weatherProperties;

    public WeatherController(WeatherService weatherService, WeatherProperties weatherProperties) {
        this.weatherService = weatherService;
        this.weatherProperties = weatherProperties;
    }

    @PostMapping("/send-all")
    public String sendAllForecasts() {
        List<WeatherLocation> locations = weatherProperties.getLocations();
        for (WeatherLocation loc : locations) {
            weatherService.sendDailyForecast(loc);
        }
        return "Forecast emails sent to all locations.";
    }

    @PostMapping("/send/{name}")
    public String sendForecast(@PathVariable String name) {
        WeatherLocation location = weatherProperties.getLocations().stream()
                .filter(l -> l.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Location not found: " + name));

        weatherService.sendDailyForecast(location);
        return "Forecast sent for " + name;
    }
}