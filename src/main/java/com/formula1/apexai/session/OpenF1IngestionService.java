package com.formula1.apexai.session;

import com.formula1.apexai.telemetry.dto.DriverDTO;
import com.formula1.apexai.telemetry.dto.LapDTO;
import com.formula1.apexai.telemetry.dto.SessionResultDTO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenF1IngestionService {

	private final RestClient.Builder restClientBuilder;
	private final DriverRepository driverRepository;
	private final LapRepository lapRepository;
	private final RaceResultRepository raceResultRepository;
	private final SpeedTraceRepository speedTraceRepository;
	private final com.formula1.apexai.config.OpenF1Properties openF1Properties;

	@Transactional
	public IngestSummary ingestSession(int sessionKey, String sessionType) {
		String type = sessionType == null || sessionType.isBlank() ? "Race" : sessionType;
		int drivers = ingestDrivers(sessionKey);
		int laps = ingestLaps(sessionKey, type);
		int results = ingestResults(sessionKey);
		seedSpeedTraces(sessionKey);
		log.info("Ingest complete session={} drivers={} laps={} results={}", sessionKey, drivers, laps, results);
		return new IngestSummary(sessionKey, drivers, laps, results);
	}

	private int ingestDrivers(int sessionKey) {
		RestClient client = restClientBuilder.build();
		List<DriverDTO> external = client.get()
				.uri(openF1Properties.baseUrl() + "/drivers?session_key=" + sessionKey)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
		int saved = 0;
		if (external != null) {
			for (DriverDTO data : external) {
				Driver driver = driverRepository
						.findByDriverNumberAndSessionKey(data.driverNumber(), sessionKey)
						.orElseGet(Driver::new);
				driver.setBroadcastName(data.broadcastName());
				driver.setDriverNumber(data.driverNumber());
				driver.setFirstName(data.firstName());
				driver.setLastName(data.lastName());
				driver.setNameAcronym(data.nameAcronym());
				driver.setTeamName(data.teamName());
				driver.setSessionKey(sessionKey);
				driverRepository.save(driver);
				saved++;
			}
		}
		return saved;
	}

	private int ingestLaps(int sessionKey, String sessionType) {
		RestClient client = restClientBuilder.build();
		List<LapDTO> external = client.get()
				.uri(openF1Properties.baseUrl() + "/laps?session_key=" + sessionKey)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
				});

		Map<Integer, String> names = new HashMap<>();
		driverRepository.findBySessionKey(sessionKey)
				.forEach(d -> names.put(d.getDriverNumber(), d.getBroadcastName()));

		int saved = 0;
		if (external != null) {
			List<Lap> batch = new ArrayList<>();
			for (LapDTO data : external) {
				if (data.lapNumber() == null) {
					continue;
				}
				Lap existing = lapRepository
						.findByDriverNumberAndLapNumberAndSessionKey(
								data.driverNumber(), data.lapNumber(), sessionKey)
						.orElseGet(Lap::new);
				existing.setDriverNumber(data.driverNumber());
				existing.setDriverName(names.getOrDefault(data.driverNumber(), "Driver #" + data.driverNumber()));
				existing.setLapNumber(data.lapNumber());
				existing.setSessionKey(sessionKey);
				existing.setSessionType(sessionType);
				existing.setSector1Time(data.s1Duration());
				existing.setSector2Time(data.s2Duration());
				existing.setSector3Time(data.s3Duration());
				existing.setTotalLapTime(data.totalLapTime());
				existing.setSpeedTrap(data.speedTrap());
				batch.add(existing);
				saved++;
				if (batch.size() >= 200) {
					lapRepository.saveAll(batch);
					batch.clear();
				}
			}
			if (!batch.isEmpty()) {
				lapRepository.saveAll(batch);
			}
		}
		return saved;
	}

	private int ingestResults(int sessionKey) {
		RestClient client = restClientBuilder.build();
		List<SessionResultDTO> external = client.get()
				.uri(openF1Properties.baseUrl() + "/session_result?session_key=" + sessionKey)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
		int saved = 0;
		if (external != null) {
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
				saved++;
			}
		}
		return saved;
	}

	private void seedSpeedTraces(int sessionKey) {
		seedSyntheticTrace(1, 10, sessionKey, 318);
		seedSyntheticTrace(16, 10, sessionKey, 305);
		seedSyntheticTrace(44, 10, sessionKey, 300);
		seedSyntheticTrace(27, 10, sessionKey, 300);
	}

	private void seedSyntheticTrace(int driverNumber, int lapNumber, int sessionKey, double trapSpeed) {
		if (speedTraceRepository.existsByDriverNumberAndLapNumberAndSessionKey(driverNumber, lapNumber, sessionKey)) {
			return;
		}
		Lap lap = lapRepository.findByDriverNumberAndLapNumberAndSessionKey(driverNumber, lapNumber, sessionKey)
				.orElse(null);
		double trap = lap != null && lap.getSpeedTrap() != null ? lap.getSpeedTrap() : trapSpeed;

		List<SpeedTracePoint> points = new ArrayList<>();
		int samples = 80;
		for (int i = 0; i < samples; i++) {
			double progress = i / (double) (samples - 1);
			double speed = progress < 0.25 ? 240 + progress * 80
					: progress < 0.55 ? 160 + Math.sin(progress * 12) * 35
					: progress < 0.8 ? 200 + (progress - 0.55) * 200
					: trap - (1 - progress) * 40;

			SpeedTracePoint point = new SpeedTracePoint();
			point.setDriverNumber(driverNumber);
			point.setLapNumber(lapNumber);
			point.setSessionKey(sessionKey);
			point.setSampleIndex(i);
			point.setDistanceMeters(progress * 5807);
			point.setSpeedKph(Math.round(speed * 10.0) / 10.0);
			point.setGear(Math.min(8, Math.max(1, (int) (speed / 40))));
			point.setThrottle(speed > 200 ? 1.0 : 0.55);
			point.setBrake(speed < 170);
			points.add(point);
		}
		speedTraceRepository.saveAll(points);
	}

	public record IngestSummary(int sessionKey, int drivers, int laps, int results) {
	}
}
