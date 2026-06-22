#!/bin/bash

# Gmail credentials for sending weather forecast emails
export MAIL_USERNAME=sergey.moskovskiy@gmail.com
export MAIL_PASSWORD=$1

echo "Starting Weather Forecast Spring Boot application..."
mvn spring-boot:run