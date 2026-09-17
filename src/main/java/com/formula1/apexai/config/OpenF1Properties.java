package com.formula1.apexai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apexai.openf1")
public record OpenF1Properties(
		String baseUrl,
		int sessionKey,
		int meetingKey,
		String sessionType
) {
}
