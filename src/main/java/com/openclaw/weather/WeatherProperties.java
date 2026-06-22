package com.openclaw.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "weather")
public class WeatherProperties {

    private List<WeatherLocation> locations;

    public List<WeatherLocation> getLocations() {
        return locations;
    }

    public void setLocations(List<WeatherLocation> locations) {
        this.locations = locations;
    }
}