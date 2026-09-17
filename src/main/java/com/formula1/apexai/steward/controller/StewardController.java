package com.formula1.apexai.steward.controller;

import com.formula1.apexai.steward.model.StewardDocument;
import com.formula1.apexai.steward.service.StewardArchiveService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/steward")
@RequiredArgsConstructor
public class StewardController {

	private final StewardArchiveService stewardArchiveService;

	@GetMapping("/search")
	public Map<String, String> search(@RequestParam String q) {
		return Map.of("query", q, "results", stewardArchiveService.search(q));
	}

	@PostMapping("/ingest")
	public StewardDocument ingest(@RequestBody IngestRequest request) {
		return stewardArchiveService.ingest(request.title(), request.content(), request.source());
	}

	public record IngestRequest(
			@NotBlank String title,
			@NotBlank String content,
			String source
	) {
	}
}
