package com.formula1.apexai.telemetry.controller;

import com.formula1.apexai.config.OpenF1Properties;
import com.formula1.apexai.session.RaceSessionService;
import com.formula1.apexai.session.SessionContext;
import com.formula1.apexai.telemetry.dto.PodiumResponse;
import com.formula1.apexai.telemetry.dto.SpeedTraceResponse;
import com.formula1.apexai.telemetry.model.Driver;
import com.formula1.apexai.telemetry.model.Lap;
import com.formula1.apexai.telemetry.repository.DriverRepository;
import com.formula1.apexai.telemetry.repository.LapRepository;
import com.formula1.apexai.telemetry.service.TelemetryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

	private final TelemetryQueryService telemetryQueryService;
	private final DriverRepository driverRepository;
	private final LapRepository lapRepository;
	private final OpenF1Properties openF1Properties;
	private final RaceSessionService raceSessionService;

	@GetMapping("/stats")
	public Map<String, String> stats(@RequestParam(required = false) Integer sessionKey) {
		try {
			bindSession(sessionKey);
			return Map.of(
					"summary", telemetryQueryService.sessionStats(),
					"session", telemetryQueryService.activeSessionLabel(),
					"sessionKey", String.valueOf(telemetryQueryService.activeSessionKey()));
		} finally {
			SessionContext.clear();
		}
	}

	@GetMapping("/fastest-lap")
	public Map<String, String> fastestLap(
			@RequestParam(required = false) String driver,
			@RequestParam(required = false) Integer sessionKey) {
		try {
			bindSession(sessionKey);
			return Map.of("result", telemetryQueryService.fastestLap(driver));
		} finally {
			SessionContext.clear();
		}
	}

	@GetMapping("/fastest-sector/{sector}")
	public Map<String, String> fastestSector(
			@PathVariable int sector,
			@RequestParam(required = false) Integer sessionKey) {
		try {
			bindSession(sessionKey);
			return Map.of("result", telemetryQueryService.fastestSector(sector));
		} finally {
			SessionContext.clear();
		}
	}

	@GetMapping("/drivers")
	public List<Driver> drivers(@RequestParam(required = false) Integer sessionKey) {
		int key = sessionKey != null ? sessionKey : openF1Properties.sessionKey();
		return driverRepository.findBySessionKey(key);
	}

	@GetMapping("/laps")
	public List<Lap> laps(
			@RequestParam(required = false) Integer driverNumber,
			@RequestParam(required = false) Integer sessionKey) {
		int key = sessionKey != null ? sessionKey : openF1Properties.sessionKey();
		if (driverNumber != null) {
			return lapRepository.findByDriverNumberAndSessionKeyOrderByLapNumberAsc(driverNumber, key);
		}
		return lapRepository.findFastestLaps(key, null).stream().limit(50).toList();
	}

	@GetMapping("/podium")
	public PodiumResponse podium(@RequestParam(required = false) Integer sessionKey) {
		try {
			bindSession(sessionKey);
			return telemetryQueryService.podiumResponse();
		} finally {
			SessionContext.clear();
		}
	}

	@GetMapping("/speed-trace")
	public SpeedTraceResponse speedTrace(
			@RequestParam Integer driverNumber,
			@RequestParam(defaultValue = "1") Integer lapNumber,
			@RequestParam(required = false) Integer sessionKey) {
		return telemetryQueryService.speedTrace(driverNumber, lapNumber, sessionKey);
	}

	private void bindSession(Integer sessionKey) {
		if (sessionKey == null) {
			SessionContext.set(raceSessionService.defaultSession());
			return;
		}
		SessionContext.set(raceSessionService.resolveBySessionKey(sessionKey));
	}
}
