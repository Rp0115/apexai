package com.formula1.apexai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apexai.gateway")
public record GatewayProperties(int rateLimitPerMinute) {
}
