package com.openclaw.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class WeatherService {

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public WeatherService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendDailyForecast(WeatherLocation location) {
        try {
            String dailyJson = fetchOpenMeteoDaily(location);
            String hourlyJson = fetchOpenMeteoHourly(location);
            String alertInfo = fetchEnvironmentCanadaAlerts(location);
            String emailBody = buildCompactEmailBody(location, dailyJson, hourlyJson, alertInfo);
            sendEmail(location, emailBody);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send forecast for " + location.getName(), e);
        }
    }

    private String fetchEnvironmentCanadaAlerts(WeatherLocation location) {
        try {
            String url = String.format(
                "https://weather.gc.ca/en/location/index.html?coords=%.4f%%2C%.4f",
                location.getLat(), location.getLon()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String html = response.body();

            // Simple parsing for warnings
            if (html.contains("warning") || html.contains("alert") || html.contains("vigilance")) {
                // Extract warning text if present
                if (html.contains("Special Weather Statement") || html.contains("Weather Advisory")) {
                    return "Special Weather Statement active";
                }
                if (html.contains("Rainfall Warning") || html.contains("Thunderstorm Warning")) {
                    return "Rain/Thunderstorm warning active";
                }
                return "Weather alert active";
            }

            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String fetchOpenMeteoDaily(WeatherLocation location) throws IOException, InterruptedException {
        String url = String.format(
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=%s&forecast_days=8",
            location.getLat(), location.getLon(), location.getTimezone().replace("/", "%2F")
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String fetchOpenMeteoHourly(WeatherLocation location) throws IOException, InterruptedException {
        String url = String.format(
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&hourly=temperature_2m,weather_code,precipitation_probability&timezone=%s&forecast_days=1",
            location.getLat(), location.getLon(), location.getTimezone().replace("/", "%2F")
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String buildCompactEmailBody(WeatherLocation location, String dailyJson, String hourlyJson, String alertInfo) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode daily = root.path("daily");

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif; max-width: 360px; margin: 0 auto;'>");
        sb.append("<h2 style='margin:0 0 8px 0;'>").append(location.getName()).append(" weather forecast</h2>");
        sb.append("<p style='margin:0 0 16px 0; color:#666; font-size:13px;'>").append(LocalDate.now()).append("–").append(LocalDate.now().plusDays(7)).append("</p>");

        // Hourly table
        sb.append(buildHourlyTable(hourlyJson));

        sb.append("<table style='width:100%; border-collapse: collapse; font-size:13px;'>");
        sb.append("<tr style='background:#f8f9fa;'>")
          .append("<th style='padding:6px; text-align:left;'>Day</th>")
          .append("<th style='padding:6px; text-align:left;'>Weather</th>")
          .append("<th style='padding:6px; text-align:center;'>H/L</th>")
          .append("<th style='padding:6px; text-align:center;'>Rain</th>")
          .append("</tr>");

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEE dd");
        for (int i = 0; i < daily.path("time").size(); i++) {
            String date = daily.path("time").get(i).asText();
            int code = daily.path("weather_code").get(i).asInt();
            int max = (int) Math.round(daily.path("temperature_2m_max").get(i).asDouble());
            int min = (int) Math.round(daily.path("temperature_2m_min").get(i).asDouble());
            int rain = daily.path("precipitation_probability_max").get(i).asInt();

            String weather = mapWeatherCode(code);
            String day = LocalDate.parse(date).format(dateFmt);

            sb.append("<tr>")
              .append("<td style='padding:6px; border-top:1px solid #eee;'>").append(day).append("</td>")
              .append("<td style='padding:6px; border-top:1px solid #eee;'>").append(weather).append("</td>")
              .append("<td style='padding:6px; border-top:1px solid #eee; text-align:center;'>").append(max).append("/").append(min).append("°C</td>")
              .append("<td style='padding:6px; border-top:1px solid #eee; text-align:center;'>").append(rain).append("%</td>")
              .append("</tr>");
        }
        sb.append("</table>");
        String alertSection = alertInfo.isEmpty() ? "No active alerts" : alertInfo;
        sb.append("<p style='margin-top:16px; font-size:11px; color:#888;'>Open-Meteo • ").append(alertSection).append("</p>");
        sb.append("</div>");

        return sb.toString();
    }

    private String buildHourlyTable(String hourlyJson) throws IOException {
        JsonNode root = objectMapper.readTree(hourlyJson);
        JsonNode hourly = root.path("hourly");

        StringBuilder sb = new StringBuilder();
        sb.append("<h3 style='margin:16px 0 8px 0; font-size:14px;'>Hourly Forecast</h3>");
        sb.append("<table style='width:100%; border-collapse: collapse; font-size:12px; margin-bottom:16px;'>");
        sb.append("<tr style='background:#f0f0f0;'>")
          .append("<th style='padding:4px; text-align:left;'>Time</th>")
          .append("<th style='padding:4px; text-align:center;'>Temp</th>")
          .append("<th style='padding:4px; text-align:center;'>Weather</th>")
          .append("<th style='padding:4px; text-align:center;'>Rain</th>")
          .append("</tr>");

        // Target hours: 7, 10, 12, 15, 17, 19, 22
        int[] targetHours = {7, 10, 12, 15, 17, 19, 22};

        for (int h : targetHours) {
            for (int i = 0; i < hourly.path("time").size(); i++) {
                String timeStr = hourly.path("time").get(i).asText();
                if (timeStr.contains("T" + String.format("%02d", h) + ":")) {
                    double temp = hourly.path("temperature_2m").get(i).asDouble();
                    int code = hourly.path("weather_code").get(i).asInt();
                    int rain = hourly.path("precipitation_probability").get(i).asInt();

                    String weather = mapWeatherCode(code);
                    String timeLabel = String.format("%02d:00", h);

                    sb.append("<tr>")
                      .append("<td style='padding:4px; border-top:1px solid #eee;'>").append(timeLabel).append("</td>")
                      .append("<td style='padding:4px; border-top:1px solid #eee; text-align:center;'>").append(Math.round(temp)).append("°C</td>")
                      .append("<td style='padding:4px; border-top:1px solid #eee; text-align:center;'>").append(weather).append("</td>")
                      .append("<td style='padding:4px; border-top:1px solid #eee; text-align:center;'>").append(rain).append("%</td>")
                      .append("</tr>");
                    break;
                }
            }
        }

        sb.append("</table>");
        return sb.toString();
    }

    private String mapWeatherCode(int code) {
        return switch (code) {
            case 0 -> "Clear";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61 -> "Light rain";
            case 63 -> "Moderate rain";
            case 65 -> "Heavy rain";
            case 66, 67 -> "Freezing rain";
            case 71 -> "Light snow";
            case 73 -> "Moderate snow";
            case 75 -> "Heavy snow";
            case 77 -> "Snow grains";
            case 80 -> "Light rain showers";
            case 81 -> "Moderate rain showers";
            case 82 -> "Violent rain showers";
            case 85 -> "Light snow showers";
            case 86 -> "Heavy snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm + hail";
            default -> "Cloudy";
        };
    }

    private void sendEmail(WeatherLocation location, String htmlBody) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(mailFrom);
        helper.setTo(location.getRecipients().toArray(new String[0]));
        helper.setSubject(location.getName() + " weather forecast — " + LocalDate.now());

        helper.setText(htmlBody, true); // HTML

        mailSender.send(message);
    }
}