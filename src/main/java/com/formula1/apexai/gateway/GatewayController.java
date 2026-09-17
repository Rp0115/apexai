package com.formula1.apexai.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class GatewayController {

	@GetMapping("/health")
	public Map<String, Object> health() {
		return Map.of(
				"status", "UP",
				"service", "ApexAI Paddock Gateway",
				"routes", Map.of(
						"telemetry", "/api/telemetry/**",
						"steward", "/api/steward/**",
						"oracle", "/api/oracle/ask",
						"batch", "/api/batch/ingest",
						"races", "/api/batch/races"));
	}
}
