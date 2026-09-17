package com.formula1.apexai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

	@GetMapping("/")
	public Map<String, Object> welcome() {
		return Map.of(
				"name", "ApexAI",
				"tagline", "The Virtual Race Engineer",
				"docs", "/api/health",
				"ask", "/api/oracle/ask?q=Who+had+the+fastest+S2+at+Suzuka+2023%3F");
	}
}
