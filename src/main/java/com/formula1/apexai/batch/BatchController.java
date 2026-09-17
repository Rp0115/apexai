package com.formula1.apexai.batch;

import com.formula1.apexai.session.OpenF1IngestionService;
import com.formula1.apexai.session.OpenF1SessionDto;
import com.formula1.apexai.session.RaceSessionService;
import com.formula1.apexai.session.SessionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchController {

	private final JobOperator jobOperator;
	private final Job openF1IngestJob;
	private final OpenF1IngestionService ingestionService;
	private final RaceSessionService raceSessionService;

	/** Legacy alias — ingests the session configured in application properties / .env */
	@PostMapping({"/japan-2023", "/default"})
	public ResponseEntity<Map<String, Object>> runConfiguredIngestion() throws Exception {
		JobExecution execution = jobOperator.start(
				openF1IngestJob,
				new JobParametersBuilder()
						.addLong("timestamp", System.currentTimeMillis())
						.toJobParameters());

		return ResponseEntity.accepted().body(Map.of(
				"job", execution.getJobInstance().getJobName(),
				"status", execution.getStatus().toString(),
				"message", "Configured session OpenF1 ingestion finished"));
	}

	/**
	 * Ingest any OpenF1 session by key, or resolve by year + country/circuit.
	 * Examples:
	 *   POST /api/batch/ingest?sessionKey=9173
	 *   POST /api/batch/ingest?year=2023&race=Monaco
	 */
	@PostMapping("/ingest")
	public ResponseEntity<Map<String, Object>> ingest(
			@RequestParam(required = false) Integer sessionKey,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) String race,
			@RequestParam(defaultValue = "Race") String sessionName) {

		SessionContext.ActiveSession session;
		if (sessionKey != null) {
			session = new SessionContext.ActiveSession(
					sessionKey, 0, year != null ? year : 2023, sessionName, sessionName,
					race != null ? race : "Custom", "?", "Session " + sessionKey);
		} else if (race != null && !race.isBlank()) {
			int y = year != null ? year : 2023;
			OpenF1SessionDto dto = raceSessionService.findSession(y, race, sessionName)
					.orElseThrow(() -> new IllegalArgumentException(
							"No OpenF1 session for year=" + y + " race=" + race + " session=" + sessionName));
			session = raceSessionService.toActive(dto);
		} else {
			session = raceSessionService.defaultSession();
		}

		var summary = ingestionService.ingestSession(session.sessionKey(), session.sessionType());
		return ResponseEntity.accepted().body(Map.of(
				"session", session.label(),
				"sessionKey", summary.sessionKey(),
				"drivers", summary.drivers(),
				"laps", summary.laps(),
				"results", summary.results()));
	}

	@GetMapping("/races")
	public List<Map<String, Object>> listRaces(@RequestParam(defaultValue = "2023") int year) {
		return raceSessionService.listRaces(year).stream()
				.map(s -> Map.<String, Object>of(
						"sessionKey", s.sessionKey(),
						"meetingKey", s.meetingKey() != null ? s.meetingKey() : 0,
						"label", s.displayLabel(),
						"country", s.countryName() != null ? s.countryName() : "",
						"circuit", s.circuitShortName() != null ? s.circuitShortName() : "",
						"date", s.dateStart() != null ? s.dateStart() : ""))
				.toList();
	}
}
