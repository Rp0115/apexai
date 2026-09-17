package com.formula1.apexai.telemetry.service;

import com.formula1.apexai.config.OpenF1Properties;
import com.formula1.apexai.session.SessionContext;
import com.formula1.apexai.telemetry.dto.SessionResultDTO;
import com.formula1.apexai.telemetry.dto.SpeedTraceResponse;
import com.formula1.apexai.telemetry.model.Driver;
import com.formula1.apexai.telemetry.model.Lap;
import com.formula1.apexai.telemetry.model.RaceResult;
import com.formula1.apexai.telemetry.model.SpeedTracePoint;
import com.formula1.apexai.telemetry.repository.DriverRepository;
import com.formula1.apexai.telemetry.repository.LapRepository;
import com.formula1.apexai.telemetry.repository.RaceResultRepository;
import com.formula1.apexai.telemetry.repository.SpeedTraceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryQueryService {

	private final LapRepository lapRepository;
	private final DriverRepository driverRepository;
	private final SpeedTraceRepository speedTraceRepository;
	private final RaceResultRepository raceResultRepository;
	private final OpenF1Properties openF1Properties;
	private final RestClient.Builder restClientBuilder;

	public String fastestSector(int sector) {
		int sessionKey = activeSessionKey();
		Optional<Lap> lap = switch (sector) {
			case 1 -> lapRepository.findFirstBySessionKeyAndSector1TimeIsNotNullOrderBySector1TimeAsc(sessionKey);
			case 2 -> lapRepository.findFirstBySessionKeyAndSector2TimeIsNotNullOrderBySector2TimeAsc(sessionKey);
			case 3 -> lapRepository.findFirstBySessionKeyAndSector3TimeIsNotNullOrderBySector3TimeAsc(sessionKey);
			default -> Optional.empty();
		};

		return lap.map(l -> String.format(Locale.US,
						"Fastest S%d: %s (#%d) on lap %d — %.3fs",
						sector, l.getDriverName(), l.getDriverNumber(), l.getLapNumber(),
						sector == 1 ? l.getSector1Time() : sector == 2 ? l.getSector2Time() : l.getSector3Time()))
				.orElse("No sector " + sector + " data found for session " + sessionKey + ". Run the OpenF1 batch job first.");
	}

	public String fastestLap(String driverHint) {
		int sessionKey = activeSessionKey();
		Integer driverNumber = resolveDriverNumber(driverHint).orElse(null);

		List<Lap> laps = lapRepository.findFastestLaps(sessionKey, driverNumber);
		if (laps.isEmpty()) {
			return "No lap times found for this session. Ask the Oracle about the race (it will auto-load) or POST /api/batch/ingest?race=Monaco&year=2023";
		}

		Lap best = laps.getFirst();
		return String.format(Locale.US,
				"Fastest lap%s: %s (#%d) lap %d — %.3fs (S1 %.3f / S2 %.3f / S3 %.3f, speed trap %.0f km/h)",
				driverHint == null || driverHint.isBlank() ? "" : " for " + driverHint,
				best.getDriverName(), best.getDriverNumber(), best.getLapNumber(),
				best.getTotalLapTime(),
				nz(best.getSector1Time()), nz(best.getSector2Time()), nz(best.getSector3Time()),
				nz(best.getSpeedTrap()));
	}

	public String driverSummary(String driverHint) {
		Optional<Integer> number = resolveDriverNumber(driverHint);
		if (number.isEmpty()) {
			return "Could not resolve driver '" + driverHint + "'. Try an acronym like VER, HAM, or LEC.";
		}

		int sessionKey = activeSessionKey();
		Driver driver = driverRepository.findByDriverNumberAndSessionKey(number.get(), sessionKey).orElse(null);
		List<Lap> laps = lapRepository.findByDriverNumberAndSessionKeyOrderByLapNumberAsc(number.get(), sessionKey);
		if (laps.isEmpty()) {
			return "No laps stored for driver #" + number.get();
		}

		List<Lap> timed = laps.stream().filter(l -> l.getTotalLapTime() != null).toList();
		Lap best = timed.stream()
				.min((a, b) -> Double.compare(a.getTotalLapTime(), b.getTotalLapTime()))
				.orElse(laps.getFirst());

		String name = driver != null
				? driver.getFirstName() + " " + driver.getLastName()
				: best.getDriverName();
		String team = driver != null ? driver.getTeamName() : "Unknown team";
		int car = number.get();

		return String.format(Locale.US,
				"""
				%s (#%d) — %s
				%s

				Laps completed: %d
				Best lap: %d in %.3fs
				Best sectors: S1 %.3fs · S2 %.3fs · S3 %.3fs
				Speed trap (best lap): %.0f km/h
				""",
				name, car, team,
				activeSessionLabel(),
				laps.size(),
				best.getLapNumber(), nz(best.getTotalLapTime()),
				nz(best.getSector1Time()), nz(best.getSector2Time()), nz(best.getSector3Time()),
				nz(best.getSpeedTrap())).trim();
	}

	/** One-line race winner for Oracle answers (not the full session brief). */
	public String raceWinner() {
		ensureRaceResultsLoaded();
		int sessionKey = activeSessionKey();
		return raceResultRepository.findFirstBySessionKeyAndFinishPosition(sessionKey, 1)
				.map(r -> {
					String name = driverDisplayName(r.getDriverNumber());
					int pts = nzInt(r.getPoints());
					return String.format(Locale.US,
							"%s won %s (P1%s).",
							name,
							activeSessionLabel(),
							pts > 0 ? ", " + pts + " pts" : "");
				})
				.orElse("Winner unknown for " + activeSessionLabel() + ".");
	}

	/** Short podium line for Oracle answers. */
	public String podiumSummary() {
		ensureRaceResultsLoaded();
		int sessionKey = activeSessionKey();
		String podium = formatPodium(sessionKey);
		if (podium.startsWith("not loaded")) {
			return "Podium not available for " + activeSessionLabel() + ".";
		}
		return "Podium at " + activeSessionLabel() + ": " + podium + ".";
	}

	public String sessionStats() {
		ensureRaceResultsLoaded();

		int sessionKey = activeSessionKey();
		long drivers = driverRepository.findBySessionKey(sessionKey).size();
		long laps = lapRepository.countBySessionKey(sessionKey);

		String podium = formatPodium(sessionKey);
		String winner = raceResultRepository.findFirstBySessionKeyAndFinishPosition(sessionKey, 1)
				.map(r -> driverLabel(r.getDriverNumber()) + " (P1, " + nzInt(r.getPoints()) + " pts)")
				.orElse("Winner unknown — check OpenF1 connectivity or re-run ingestion");

		SessionContext.ActiveSession active = SessionContext.get();
		String sessionType = active != null && active.sessionType() != null
				? active.sessionType()
				: openF1Properties.sessionType();

		return String.format(Locale.US,
				"""
				%s
				Session %d · %s · %d drivers · %d laps stored

				Race winner: %s
				Podium: %s

				%s
				%s
				%s
				%s
				%s
				""",
				activeSessionLabel(),
				sessionKey, sessionType, drivers, laps,
				winner,
				podium,
				fastestLap(null),
				fastestSector(1),
				fastestSector(2),
				fastestSector(3),
				fastestSpeedTrap()).trim();
	}

	/**
	 * If classification was never ingested (older DB), pull it live from OpenF1 once.
	 */
	@Transactional
	public void ensureRaceResultsLoaded() {
		int sessionKey = activeSessionKey();
		if (raceResultRepository.countBySessionKey(sessionKey) > 0) {
			return;
		}
		try {
			RestClient client = restClientBuilder.build();
			List<SessionResultDTO> external = client.get()
					.uri(openF1Properties.baseUrl() + "/session_result?session_key=" + sessionKey)
					.retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
			if (external == null || external.isEmpty()) {
				return;
			}
			for (SessionResultDTO data : external) {
				if (data.driverNumber() == null) {
					continue;
				}
				RaceResult result = raceResultRepository
						.findBySessionKeyAndDriverNumber(sessionKey, data.driverNumber())
						.orElseGet(RaceResult::new);
				result.setSessionKey(sessionKey);
				result.setDriverNumber(data.driverNumber());
				result.setFinishPosition(data.position());
				result.setNumberOfLaps(data.numberOfLaps());
				result.setPoints(data.points());
				result.setDnf(Boolean.TRUE.equals(data.dnf()));
				result.setDns(Boolean.TRUE.equals(data.dns()));
				result.setDsq(Boolean.TRUE.equals(data.dsq()));
				result.setGapToLeader(data.gapAsString());
				raceResultRepository.save(result);
			}
			log.info("Auto-loaded {} race results from OpenF1 for session {}", external.size(), sessionKey);
		} catch (Exception ex) {
			log.warn("Could not auto-load race results: {}", ex.getMessage());
		}
	}

	private String formatPodium(int sessionKey) {
		List<RaceResult> classified = raceResultRepository.findBySessionKeyOrderByFinishPositionAsc(sessionKey).stream()
				.filter(r -> r.getFinishPosition() != null && r.getFinishPosition() >= 1 && r.getFinishPosition() <= 3)
				.sorted(Comparator.comparing(RaceResult::getFinishPosition))
				.toList();
		if (classified.isEmpty()) {
			return "not loaded yet (POST /api/batch/japan-2023)";
		}
		return classified.stream()
				.map(r -> "P" + r.getFinishPosition() + " " + driverLabel(r.getDriverNumber()))
				.collect(Collectors.joining(" · "));
	}

	private String fastestSpeedTrap() {
		int sessionKey = activeSessionKey();
		return lapRepository.findFirstBySessionKeyAndSpeedTrapIsNotNullOrderBySpeedTrapDesc(sessionKey)
				.map(l -> String.format(Locale.US,
						"Top speed trap: %s (#%d) lap %d — %.0f km/h",
						l.getDriverName(), l.getDriverNumber(), l.getLapNumber(), nz(l.getSpeedTrap())))
				.orElse("Top speed trap: n/a");
	}

	private String driverLabel(Integer driverNumber) {
		return driverRepository.findByDriverNumberAndSessionKey(driverNumber, activeSessionKey())
				.map(d -> d.getBroadcastName() != null ? d.getBroadcastName() : ("#" + driverNumber))
				.orElse("#" + driverNumber);
	}

	private String driverDisplayName(Integer driverNumber) {
		return driverRepository.findByDriverNumberAndSessionKey(driverNumber, activeSessionKey())
				.map(d -> {
					if (d.getFirstName() != null && d.getLastName() != null) {
						return d.getFirstName() + " " + d.getLastName();
					}
					return d.getBroadcastName() != null ? d.getBroadcastName() : ("#" + driverNumber);
				})
				.orElse("#" + driverNumber);
	}

	private int nzInt(Double value) {
		return value == null ? 0 : (int) Math.round(value);
	}

	@Transactional(readOnly = true)
	public SpeedTraceResponse speedTrace(Integer driverNumber, Integer lapNumber) {
		return speedTrace(driverNumber, lapNumber, null);
	}

	@Transactional(readOnly = true)
	public SpeedTraceResponse speedTrace(Integer driverNumber, Integer lapNumber, Integer sessionKeyOverride) {
		int sessionKey = sessionKeyOverride != null ? sessionKeyOverride : activeSessionKey();
		String driverName = driverRepository.findByDriverNumberAndSessionKey(driverNumber, sessionKey)
				.map(Driver::getBroadcastName)
				.orElse("Driver #" + driverNumber);

		List<SpeedTracePoint> points = speedTraceRepository
				.findByDriverNumberAndLapNumberAndSessionKeyOrderBySampleIndexAsc(driverNumber, lapNumber, sessionKey);

		if (points.isEmpty()) {
			points = synthesizeTrace(driverNumber, lapNumber, sessionKey);
		}

		List<SpeedTraceResponse.SpeedSample> samples = points.stream()
				.map(p -> new SpeedTraceResponse.SpeedSample(
						p.getSampleIndex(), p.getDistanceMeters(), p.getSpeedKph(), p.getGear()))
				.toList();

		return new SpeedTraceResponse(driverNumber, driverName, lapNumber, samples);
	}

	public Optional<Integer> resolveDriverNumber(String hint) {
		if (hint == null || hint.isBlank()) {
			return Optional.empty();
		}
		String cleaned = hint.trim();
		if (cleaned.chars().allMatch(Character::isDigit)) {
			return Optional.of(Integer.parseInt(cleaned));
		}

		int sessionKey = activeSessionKey();
		List<Driver> sessionDrivers = driverRepository.findBySessionKey(sessionKey);
		Optional<Integer> inSession = sessionDrivers.stream()
				.filter(d -> driverMatchesHint(d, cleaned))
				.map(Driver::getDriverNumber)
				.findFirst();
		if (inSession.isPresent()) {
			return inSession;
		}

		return driverRepository.findFirstByNameAcronymIgnoreCase(cleaned)
				.or(() -> driverRepository.findFirstByLastNameIgnoreCase(cleaned))
				.or(() -> driverRepository.findFirstByFirstNameIgnoreCase(cleaned))
				.or(() -> driverRepository.findFirstByBroadcastNameContainingIgnoreCase(cleaned))
				.map(Driver::getDriverNumber);
	}

	/**
	 * Find a driver mentioned in free text using the active session roster
	 * (works for new grid names like Antonelli without hardcoding).
	 */
	public Optional<Integer> resolveDriverFromQuestion(String question) {
		if (question == null || question.isBlank()) {
			return Optional.empty();
		}
		String q = question.toLowerCase(Locale.ROOT);
		List<Driver> drivers = driverRepository.findBySessionKey(activeSessionKey());
		return drivers.stream()
				.map(d -> new DriverHit(d, matchScore(q, d)))
				.filter(h -> h.score() > 0)
				.max(Comparator.comparingInt(DriverHit::score)
						.thenComparingInt(h -> h.driver().getLastName() != null
								? h.driver().getLastName().length() : 0))
				.map(h -> h.driver().getDriverNumber());
	}

	private boolean driverMatchesHint(Driver d, String hint) {
		String h = hint.toLowerCase(Locale.ROOT);
		return equalsIgnore(d.getNameAcronym(), h)
				|| equalsIgnore(d.getLastName(), h)
				|| equalsIgnore(d.getFirstName(), h)
				|| (d.getBroadcastName() != null && d.getBroadcastName().toLowerCase(Locale.ROOT).contains(h));
	}

	private int matchScore(String qLower, Driver d) {
		int best = 0;
		if (containsWord(qLower, d.getLastName())) {
			best = Math.max(best, 100 + d.getLastName().length());
		}
		if (containsWord(qLower, d.getFirstName())) {
			best = Math.max(best, 60 + d.getFirstName().length());
		}
		if (containsWord(qLower, d.getNameAcronym())) {
			best = Math.max(best, 80);
		}
		if (d.getBroadcastName() != null) {
			String bn = d.getBroadcastName().toLowerCase(Locale.ROOT);
			if (qLower.contains(bn)) {
				best = Math.max(best, 90 + bn.length());
			}
		}
		return best;
	}

	private boolean containsWord(String haystack, String needle) {
		if (needle == null || needle.isBlank()) {
			return false;
		}
		String n = needle.toLowerCase(Locale.ROOT);
		if (n.length() <= 2) {
			return Pattern.compile("\\b" + Pattern.quote(n) + "\\b", Pattern.CASE_INSENSITIVE)
					.matcher(haystack).find();
		}
		return haystack.contains(n);
	}

	private boolean equalsIgnore(String value, String hint) {
		return value != null && value.equalsIgnoreCase(hint);
	}

	private record DriverHit(Driver driver, int score) {
	}

	private List<SpeedTracePoint> synthesizeTrace(Integer driverNumber, Integer lapNumber, int sessionKey) {
		Lap lap = lapRepository.findByDriverNumberAndLapNumberAndSessionKey(driverNumber, lapNumber, sessionKey)
				.orElse(null);

		double s1 = lap != null && lap.getSector1Time() != null ? lap.getSector1Time() : 30;
		double s2 = lap != null && lap.getSector2Time() != null ? lap.getSector2Time() : 40;
		double s3 = lap != null && lap.getSector3Time() != null ? lap.getSector3Time() : 35;
		double trap = lap != null && lap.getSpeedTrap() != null ? lap.getSpeedTrap() : 300;

		List<SpeedTracePoint> points = new ArrayList<>();
		int samples = 60;
		double trackLength = 5807; // Suzuka approximate
		for (int i = 0; i < samples; i++) {
			double progress = i / (double) (samples - 1);
			double sectorProgress = progress < 0.33 ? progress / 0.33
					: progress < 0.66 ? (progress - 0.33) / 0.33
					: (progress - 0.66) / 0.34;
			double baseSpeed = progress < 0.33 ? 220 + sectorProgress * 40
					: progress < 0.66 ? 180 + Math.sin(sectorProgress * Math.PI) * 50
					: 200 + sectorProgress * (trap - 200);

			SpeedTracePoint point = new SpeedTracePoint();
			point.setDriverNumber(driverNumber);
			point.setLapNumber(lapNumber);
			point.setSessionKey(sessionKey);
			point.setSampleIndex(i);
			point.setDistanceMeters(progress * trackLength);
			point.setSpeedKph(Math.round(baseSpeed * 10.0) / 10.0);
			point.setGear(Math.min(8, 2 + (int) (baseSpeed / 40)));
			point.setThrottle(baseSpeed > 200 ? 1.0 : 0.6);
			point.setBrake(baseSpeed < 160);
			points.add(point);
		}
		return points;
	}

	private double nz(Double value) {
		return value == null ? 0.0 : value;
	}

	public int activeSessionKey() {
		Integer override = SessionContext.sessionKeyOrNull();
		return override != null ? override : openF1Properties.sessionKey();
	}

	public String activeSessionLabel() {
		SessionContext.ActiveSession active = SessionContext.get();
		if (active != null && active.label() != null) {
			return active.label();
		}
		return "Japan 2023 — Race (Suzuka)";
	}
}
