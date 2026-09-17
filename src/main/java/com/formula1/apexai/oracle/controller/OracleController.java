package com.formula1.apexai.oracle.controller;

import com.formula1.apexai.oracle.service.OracleAgentService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/oracle")
@RequiredArgsConstructor
public class OracleController {

	private final OracleAgentService oracleAgentService;

	@GetMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter askGet(@RequestParam @NotBlank String q) {
		return oracleAgentService.streamAnswer(q);
	}

	@PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter askPost(@RequestBody AskRequest request) {
		return oracleAgentService.streamAnswer(request.question());
	}

	public record AskRequest(@NotBlank String question) {
	}
}
