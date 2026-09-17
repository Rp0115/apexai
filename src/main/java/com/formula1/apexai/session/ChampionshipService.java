package com.formula1.apexai.session;

import com.formula1.apexai.config.OpenF1Properties;
import com.formula1.apexai.telemetry.dto.DriverDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Season drivers / constructors championship from OpenF1 standings
 * after the latest (usually final) Race of a year.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChampionshipService {

	public enum Scope {
		DRIVERS, CONSTRUCTORS, BOTH
	}

	private final OpenF1Properties openF1Properties;
	private final RestClient.Builder restClientBuilder;
	private final RaceSessionService raceSessionService;

	public String summarize(int year, Scope scope) {
		Optional<OpenF1SessionDto> lastRace = raceSessionService.lastMainRaceOfYear(year);
		if (lastRace.isEmpty()) {
			return "No Race sessions found for " + year + " in OpenF1 (coverage is 2023+).";
		}

		OpenF1SessionDto session = lastRace.get();
		int sessionKey = session.sessionKey();
		String afterLabel = session.displayLabel();

		StringBuilder sb = new StringBuilder();
		if (scope == Scope.DRIVERS || scope == Scope.BOTH) {
			sb.append(driversLine(year, sessionKey, afterLabel));
		}
		if (scope == Scope.CONSTRUCTORS || scope == Scope.BOTH) {
			if (!sb.isEmpty()) {
				sb.append('\n');
			}
			sb.append(constructorsLine(year, sessionKey, afterLabel));
		}
		return sb.toString().trim();
	}

	public static Scope scopeFromQuestion(String question) {
		String q = question.toLowerCase(Locale.ROOT);
		boolean drivers = q.contains("driver") || q.contains("wdc")
				|| q.contains("drivers' championship") || q.contains("drivers championship");
		boolean constructors = q.contains("constructor") || q.contains("team") || q.contains("wcc")
				|| q.contains("constructors' championship") || q.contains("constructors championship");
		if (drivers && !constructors) {
			return Scope.DRIVERS;
		}
		if (constructors && !drivers) {
			return Scope.CONSTRUCTORS;
		}
		return Scope.BOTH;
	}

	public static boolean looksLikeChampionshipQuestion(String question) {
		String q = question.toLowerCase(Locale.ROOT);
		return q.contains("championship")
				|| q.contains("standings")
				|| q.contains("wdc")
				|| q.contains("wcc")
				|| ((q.contains("drivers") || q.contains("constructors") || q.contains("constructor"))
				&& (q.contains("won") || q.contains("winner") || q.contains("champion") || q.contains("title")));
	}

	private String driversLine(int year, int sessionKey, String afterLabel) {
		List<ChampionshipDriverDto> rows = fetchDrivers(sessionKey);
		Optional<ChampionshipDriverDto> leader = rows.stream()
				.filter(r -> r.positionCurrent() != null && r.positionCurrent() == 1)
				.findFirst();
		if (leader.isEmpty()) {
			return "Drivers' championship data unavailable for " + year + ".";
		}
		ChampionshipDriverDto d = leader.get();
		String name = resolveDriverName(sessionKey, d.driverNumber());
		return String.format(Locale.US,
				"%d Drivers' Champion: %s (#%d) — %.0f pts (standings after %s).",
				year, name, d.driverNumber(), nz(d.pointsCurrent()), afterLabel);
	}

	private String constructorsLine(int year, int sessionKey, String afterLabel) {
		List<ChampionshipTeamDto> rows = fetchTeams(sessionKey);
		Optional<ChampionshipTeamDto> leader = rows.stream()
				.filter(r -> r.positionCurrent() != null && r.positionCurrent() == 1)
				.findFirst();
		if (leader.isEmpty()) {
			return "Constructors' championship data unavailable for " + year + ".";
		}
		ChampionshipTeamDto t = leader.get();
		String team = t.teamName() != null && !t.teamName().isBlank() ? t.teamName() : "Unknown team";
		return String.format(Locale.US,
				"%d Constructors' Champion: %s — %.0f pts (standings after %s).",
				year, team, nz(t.pointsCurrent()), afterLabel);
	}

	private List<ChampionshipDriverDto> fetchDrivers(int sessionKey) {
		try {
			RestClient client = restClientBuilder.build();
			List<ChampionshipDriverDto> body = client.get()
					.uri(openF1Properties.baseUrl() + "/championship_drivers?session_key=" + sessionKey)
					.retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
			return body == null ? List.of() : body;
		} catch (Exception ex) {
			log.warn("championship_drivers failed for {}: {}", sessionKey, ex.getMessage());
			return List.of();
		}
	}

	private List<ChampionshipTeamDto> fetchTeams(int sessionKey) {
		try {
			RestClient client = restClientBuilder.build();
			List<ChampionshipTeamDto> body = client.get()
					.uri(openF1Properties.baseUrl() + "/championship_teams?session_key=" + sessionKey)
					.retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
			return body == null ? List.of() : body;
		} catch (Exception ex) {
			log.warn("championship_teams failed for {}: {}", sessionKey, ex.getMessage());
			return List.of();
		}
	}

	private String resolveDriverName(int sessionKey, Integer driverNumber) {
		if (driverNumber == null) {
			return "Unknown";
		}
		try {
			RestClient client = restClientBuilder.build();
			List<DriverDTO> drivers = client.get()
					.uri(openF1Properties.baseUrl() + "/drivers?session_key=" + sessionKey
							+ "&driver_number=" + driverNumber)
					.retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
			if (drivers != null && !drivers.isEmpty()) {
				DriverDTO d = drivers.getFirst();
				if (d.firstName() != null && d.lastName() != null) {
					return d.firstName() + " " + d.lastName();
				}
				if (d.broadcastName() != null) {
					return d.broadcastName();
				}
			}
		} catch (Exception ex) {
			log.debug("Driver name lookup failed: {}", ex.getMessage());
		}
		return "#" + driverNumber;
	}

	private double nz(Double value) {
		return value == null ? 0 : value;
	}
}
