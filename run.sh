#!/bin/bash

# Gmail credentials for sending weather forecast emails
export MAIL_USERNAME=sergey.moskovskiy@gmail.com
export MAIL_PASSWORD=your-gmail-app-password-here

echo "Starting Weather Forecast Spring Boot application..."
./mvnw spring-boot:run