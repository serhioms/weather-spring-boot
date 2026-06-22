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
            String forecastJson = fetchOpenMeteo(location);
            String alertInfo = fetchEnvironmentCanadaAlerts(location);
            String emailBody = buildCompactEmailBody(location, forecastJson, alertInfo);
            sendEmail(location, emailBody);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send forecast for " + location.getName(), e);
        }
    }

    private String fetchEnvironmentCanadaAlerts(WeatherLocation location) {
        // Basic placeholder - in production you would scrape or call EC API
        // For now we return empty (no active alerts)
        return "";
    }

    private String fetchOpenMeteo(WeatherLocation location) throws IOException, InterruptedException {
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

    private String buildCompactEmailBody(WeatherLocation location, String json, String alertInfo) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode daily = root.path("daily");

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif; max-width: 360px; margin: 0 auto;'>");
        sb.append("<h2 style='margin:0 0 8px 0;'>").append(location.getName()).append(" weather forecast</h2>");
        sb.append("<p style='margin:0 0 16px 0; color:#666; font-size:13px;'>").append(LocalDate.now()).append("–").append(LocalDate.now().plusDays(7)).append("</p>");

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