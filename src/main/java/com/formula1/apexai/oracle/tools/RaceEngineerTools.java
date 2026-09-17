package com.formula1.apexai.oracle.tools;

import com.formula1.apexai.session.ChampionshipService;
import com.formula1.apexai.steward.service.StewardArchiveService;
import com.formula1.apexai.telemetry.dto.SpeedTraceResponse;
import com.formula1.apexai.telemetry.service.TelemetryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RaceEngineerTools {

	private final TelemetryQueryService telemetryQueryService;
	private final StewardArchiveService stewardArchiveService;
	private final ChampionshipService championshipService;

	@Tool(description = "Query precise SQL lap timing data. Use RACE_WINNER for who-won race questions, QUALIFYING for qualify/pole/grid position (pass driver when named), PODIUM for podium-only, FASTEST_LAP / FASTEST_SECTOR for timing, DRIVER_SUMMARY for a named driver's race pace, and SESSION_STATS only when the user asks for a full brief/overview. Do NOT use this for season championship questions.")
	public String queryTelemetry(
			@ToolParam(description = "One of: RACE_WINNER, QUALIFYING, PODIUM, FASTEST_LAP, FASTEST_SECTOR, DRIVER_SUMMARY, SESSION_STATS") String queryType,
			@ToolParam(description = "Driver acronym/name/number when relevant, else empty") String driver,
			@ToolParam(description = "Sector number 1, 2, or 3 when queryType is FASTEST_SECTOR") Integer sector) {
		return switch (queryType == null ? "" : queryType.toUpperCase()) {
			case "RACE_WINNER", "WINNER" -> telemetryQueryService.raceWinner();
			case "QUALIFYING", "QUALI", "POLE", "GRID" -> telemetryQueryService.qualifyingSummary(driver);
			case "PODIUM" -> telemetryQueryService.podiumSummary();
			case "FASTEST_LAP" -> telemetryQueryService.fastestLap(driver);
			case "FASTEST_SECTOR" -> telemetryQueryService.fastestSector(sector == null ? 2 : sector);
			case "DRIVER_SUMMARY" -> telemetryQueryService.driverSummary(driver);
			case "SESSION_STATS" -> telemetryQueryService.sessionStats();
			default -> telemetryQueryService.raceWinner();
		};
	}

	@Tool(description = "Season drivers' and/or constructors' championship winners (or current leaders) from OpenF1 standings after the latest Race of that year. Use for WDC/WCC / championship questions.")
	public String queryChampionship(
			@ToolParam(description = "Season year, e.g. 2024 or 2025") Integer year,
			@ToolParam(description = "One of: DRIVERS, CONSTRUCTORS, BOTH") String scope) {
		int y = year == null ? 2025 : year;
		ChampionshipService.Scope s = switch (scope == null ? "BOTH" : scope.toUpperCase()) {
			case "DRIVERS", "DRIVER", "WDC" -> ChampionshipService.Scope.DRIVERS;
			case "CONSTRUCTORS", "CONSTRUCTOR", "TEAMS", "TEAM", "WCC" -> ChampionshipService.Scope.CONSTRUCTORS;
			default -> ChampionshipService.Scope.BOTH;
		};
		return championshipService.summarize(y, s);
	}

	@Tool(description = "Search FIA steward reports only when the user asks about stewards, penalties, track limits, or FIA decisions. Do not use for driver pace/performance questions.")
	public String searchStewardArchive(
			@ToolParam(description = "Natural language question about steward reports") String question) {
		return stewardArchiveService.search(question);
	}

	@Tool(description = "Load a speed trace for charting. Pass the driver number AND the lap number the user requested (do not invent lap 10).")
	public String loadSpeedTrace(
			@ToolParam(description = "Driver number, e.g. 27 for Hulkenberg") Integer driverNumber,
			@ToolParam(description = "Lap number from the user's question") Integer lapNumber) {
		int lap = lapNumber == null || lapNumber < 1 ? 1 : lapNumber;
		SpeedTraceResponse trace = telemetryQueryService.speedTrace(driverNumber, lap);
		double peak = trace.samples().stream()
				.mapToDouble(s -> s.speedKph() == null ? 0 : s.speedKph())
				.max()
				.orElse(0);
		return String.format(
				"Speed trace ready for %s lap %d (%d samples, peak %.1f km/h). SPEED_TRACE driverNumber=%d lapNumber=%d",
				trace.driverName(), trace.lapNumber(), trace.samples().size(), peak, driverNumber, trace.lapNumber());
	}
}
