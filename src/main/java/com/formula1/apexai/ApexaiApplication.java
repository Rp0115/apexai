package com.formula1.apexai;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class ApexaiApplication {

	public static void main(String[] args) {
		loadDotEnv();
		SpringApplication.run(ApexaiApplication.class, args);
	}

	/**
	 * Loads a local {@code .env} file into JVM system properties (gitignored).
	 * Existing real environment variables always win.
	 */
	private static void loadDotEnv() {
		try {
			Dotenv dotenv = Dotenv.configure()
					.directory("./")
					.ignoreIfMalformed()
					.ignoreIfMissing()
					.load();
			dotenv.entries().forEach(entry -> {
				String key = entry.getKey();
				String value = entry.getValue();
				if (value == null || value.isBlank()) {
					return;
				}
				if (System.getenv(key) != null) {
					return;
				}
				// Spring Boot reads the profile from spring.profiles.active (not the env-style name)
				if ("SPRING_PROFILES_ACTIVE".equals(key) && System.getProperty("spring.profiles.active") == null) {
					System.setProperty("spring.profiles.active", value);
				}
				if (System.getProperty(key) == null) {
					System.setProperty(key, value);
				}
			});
		} catch (Exception ignored) {
			// App still boots; secrets can come from the real environment instead.
		}
	}
}
