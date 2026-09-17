package com.formula1.apexai.oracle.service;

import com.formula1.apexai.oracle.tools.RaceEngineerTools;
import com.formula1.apexai.session.RaceSessionService;
import com.formula1.apexai.session.SessionContext;
import com.formula1.apexai.steward.service.StewardArchiveService;
import com.formula1.apexai.telemetry.service.TelemetryQueryService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class OracleAgentService {

	private static final Pattern SPEED_TRACE = Pattern.compile(
			"SPEED_TRACE\\s+driverNumber=(\\d+)\\s+lapNumber=(\\d+)", Pattern.CASE_INSENSITIVE);

	private static final Pattern LAP_NUMBER = Pattern.compile(
			"\\blap\\s*(?:number\\s*)?#?\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern DRIVER_NAME = Pattern.compile(
			"\\b(verstappen|hamilton|leclerc|norris|perez|alonso|sainz|russell|piastri|gasly|ocon|"
					+ "albon|tsunoda|stroll|bottas|zhou|hulkenberg|hülkenberg|magnussen|sargeant|lawson|"
					+ "antonelli|kimi|bearman|hadjar|bortoleto|colapinto|doohan|lindblad|"
					+ "max|lewis|charles|lando|sergio|fernando|carlos|george|oscar|nico|"
					+ "ver|ham|lec|nor|per|alo|sai|rus|pia|hul|mag|ant)\\b",
			Pattern.CASE_INSENSITIVE);

	private final ObjectProvider<ChatClient> chatClientProvider;
	private final TelemetryQueryService telemetryQueryService;
	private final StewardArchiveService stewardArchiveService;
	private final RaceEngineerTools tools;
	private final RaceSessionService raceSessionService;
	private final MeterRegistry meterRegistry;
	private final boolean aiEnabled;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public OracleAgentService(
			ObjectProvider<ChatClient> chatClientProvider,
			TelemetryQueryService telemetryQueryService,
			StewardArchiveService stewardArchiveService,
			RaceEngineerTools tools,
			RaceSessionService raceSessionService,
			MeterRegistry meterRegistry,
			@Value("${apexai.ai.enabled:false}") boolean aiEnabled) {
		this.chatClientProvider = chatClientProvider;
		this.telemetryQueryService = telemetryQueryService;
		this.stewardArchiveService = stewardArchiveService;
		this.tools = tools;
		this.raceSessionService = raceSessionService;
		this.meterRegistry = meterRegistry;
		this.aiEnabled = aiEnabled;
	}

	public SseEmitter streamAnswer(String question) {
		SseEmitter emitter = new SseEmitter(180_000L);
		AtomicBoolean completed = new AtomicBoolean(false);

		executor.submit(() -> {
			Timer.Sample sample = Timer.start(meterRegistry);
			try {
				SessionContext.ActiveSession session = raceSessionService.resolveFromQuestion(question);
				SessionContext.set(session);

				// Load data + notify UI; keep ingest chatter out of the spoken answer.
				raceSessionService.ensureIngested(session);
				emitter.send(SseEmitter.event()
						.name("session")
						.data("{\"sessionKey\":" + session.sessionKey()
								+ ",\"label\":\"" + escapeJson(session.label()) + "\"}"));

				String raw = resolveAnswer(question);
				Integer[] trace = extractSpeedTrace(raw, question, session.sessionKey());
				String answer = SPEED_TRACE.matcher(raw).replaceAll("").trim();

				streamText(emitter, answer);

				if (trace != null) {
					emitter.send(SseEmitter.event()
							.name("speed-trace")
							.data("{\"driverNumber\":" + trace[0]
									+ ",\"lapNumber\":" + trace[1]
									+ ",\"sessionKey\":" + trace[2] + "}"));
				}

				emitter.send(SseEmitter.event().name("done").data("[END]"));
				complete(emitter, completed);
			} catch (Exception ex) {
				log.error("Oracle agent failed", ex);
				try {
					emitter.send(SseEmitter.event().name("error").data(String.valueOf(ex.getMessage())));
				} catch (IOException ignored) {
					// ignore
				}
				emitter.completeWithError(ex);
			} finally {
				SessionContext.clear();
				sample.stop(Timer.builder("apexai.ai.latency")
						.description("Oracle agent end-to-end latency")
						.register(meterRegistry));
			}
		});

		emitter.onCompletion(() -> completed.set(true));
		emitter.onTimeout(() -> complete(emitter, completed));
		return emitter;
	}

	private String resolveAnswer(String question) {
		if (aiEnabled) {
			ChatClient client = chatClientProvider.getIfAvailable();
			if (client != null) {
				try {
					String content = client.prompt().user(question).call().content();
					if (content != null && !content.isBlank()) {
						return content;
					}
				} catch (Exception ex) {
					log.warn("LLM call failed, using hybrid fallback: {}", ex.getMessage());
				}
			}
		}
		return fallbackHybrid(question);
	}

	private String fallbackHybrid(String question) {
		String q = question.toLowerCase(Locale.ROOT);
		int lap = extractLapNumber(question).orElse(10);
		boolean wantsTrace = q.contains("speed") || q.contains("trace") || q.contains("telemetry");
		boolean wantsSteward = q.contains("steward") || q.contains("penalty") || q.contains("fia")
				|| q.contains("incident") || q.contains("track limit");
		boolean wantsSector = q.contains("s2") || q.contains("sector 2") || q.contains("sector2")
				|| q.contains("s1") || q.contains("sector 1") || q.contains("s3") || q.contains("sector 3");
		boolean wantsWinner = q.contains("who won") || q.contains("winner") || q.contains("who win")
				|| q.contains("race win");
		boolean wantsPodium = q.contains("podium");
		boolean wantsBrief = q.contains("brief") || q.contains("session stats") || q.contains("overview")
				|| (q.contains("results") && !wantsWinner);

		Optional<Integer> driverNumber = resolveDriverForQuestion(question);

		// Speed / telemetry traces must never fall through to race-winner answers.
		if (wantsTrace) {
			if (driverNumber.isPresent()) {
				int car = driverNumber.get();
				int useLap = extractLapNumber(question).orElse(lap);
				String ready = tools.loadSpeedTrace(car, useLap);
				return ready + "\n\nSPEED_TRACE driverNumber=" + car + " lapNumber=" + useLap;
			}
			return "Name a driver for the speed trace (e.g. Antonelli lap 3).";
		}

		if (driverNumber.isPresent() && !wantsSteward && !wantsWinner && !wantsPodium) {
			String summary = tools.queryTelemetry("DRIVER_SUMMARY", String.valueOf(driverNumber.get()), null);
			StringBuilder sb = new StringBuilder(summary);
			if (q.contains("lap") && extractLapNumber(question).isPresent()) {
				int car = driverNumber.get();
				sb.append("\n\nSPEED_TRACE driverNumber=").append(car).append(" lapNumber=").append(lap);
			}
			return sb.toString();
		}

		if (wantsSteward) {
			return stewardArchiveService.search(question);
		}

		if (wantsSector) {
			int sector = q.contains("s1") || q.contains("sector 1") ? 1
					: q.contains("s3") || q.contains("sector 3") ? 3 : 2;
			return tools.queryTelemetry("FASTEST_SECTOR", null, sector);
		}

		if (q.contains("fastest lap") || q.contains("purple")) {
			return tools.queryTelemetry("FASTEST_LAP", null, null);
		}

		if (wantsPodium && !wantsWinner) {
			return tools.queryTelemetry("PODIUM", null, null);
		}

		if (wantsWinner) {
			return tools.queryTelemetry("RACE_WINNER", null, null);
		}

		if (wantsBrief) {
			return tools.queryTelemetry("SESSION_STATS", null, null);
		}

		// Bare race mention with no specific ask → short winner
		if (raceSessionService.extractRaceHint(question).isPresent()) {
			return tools.queryTelemetry("RACE_WINNER", null, null);
		}

		return tools.queryTelemetry("RACE_WINNER", null, null);
	}

	private Optional<Integer> resolveDriverForQuestion(String question) {
		Optional<Integer> fromRoster = telemetryQueryService.resolveDriverFromQuestion(question);
		if (fromRoster.isPresent()) {
			return fromRoster;
		}
		return extractDriverHint(question).flatMap(telemetryQueryService::resolveDriverNumber);
	}

	private Integer[] extractSpeedTrace(String answer, String question, int sessionKey) {
		Optional<Integer> questionLap = extractLapNumber(question);

		Matcher matcher = SPEED_TRACE.matcher(answer);
		Integer fromAnswerDriver = null;
		Integer fromAnswerLap = null;
		if (matcher.find()) {
			fromAnswerDriver = Integer.parseInt(matcher.group(1));
			fromAnswerLap = Integer.parseInt(matcher.group(2));
		}

		Integer driver = resolveDriverForQuestion(question).orElse(fromAnswerDriver);
		if (driver == null) {
			return null;
		}

		int lap = questionLap.orElse(fromAnswerLap != null ? fromAnswerLap : 10);

		String q = question.toLowerCase(Locale.ROOT);
		boolean wantsTrace = q.contains("speed") || q.contains("trace") || q.contains("telemetry")
				|| fromAnswerLap != null
				|| (questionLap.isPresent() && q.contains("lap"));

		if (!wantsTrace && !looksLikeTelemetry(question)) {
			return null;
		}

		return new Integer[]{driver, lap, sessionKey};
	}

	private Optional<Integer> extractLapNumber(String question) {
		Matcher matcher = LAP_NUMBER.matcher(question);
		if (matcher.find()) {
			int lap = Integer.parseInt(matcher.group(1));
			if (lap >= 1 && lap <= 100) {
				return Optional.of(lap);
			}
		}
		return Optional.empty();
	}

	private void streamText(SseEmitter emitter, String text) throws IOException, InterruptedException {
		String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
		String[] lines = normalized.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (!line.isEmpty()) {
				emitter.send(SseEmitter.event().name("token").data(line));
				Thread.sleep(25);
			}
			if (i < lines.length - 1) {
				emitter.send(SseEmitter.event().name("token").data("{{NL}}"));
				Thread.sleep(15);
			}
		}
	}

	private boolean looksLikeTelemetry(String question) {
		String q = question.toLowerCase(Locale.ROOT);
		return q.contains("speed") || q.contains("trace") || q.contains("telemetry")
				|| q.contains("lap") || q.contains("perform") || q.contains("pace")
				|| DRIVER_NAME.matcher(q).find();
	}

	private Optional<String> extractDriverHint(String question) {
		Matcher matcher = DRIVER_NAME.matcher(question);
		if (matcher.find()) {
			String token = matcher.group(1).toLowerCase(Locale.ROOT).replace("ü", "u");
			return Optional.of(switch (token) {
				case "max", "ver" -> "VER";
				case "lewis", "ham" -> "HAM";
				case "charles", "lec" -> "LEC";
				case "lando", "nor" -> "NOR";
				case "sergio", "per" -> "PER";
				case "fernando", "alo" -> "ALO";
				case "carlos", "sai" -> "SAI";
				case "george", "rus" -> "RUS";
				case "oscar", "pia" -> "PIA";
				case "nico", "hul", "hulkenberg" -> "HUL";
				case "mag", "magnussen" -> "MAG";
				case "kimi", "antonelli", "ant" -> "ANT";
				case "bearman" -> "BEA";
				case "hadjar" -> "HAD";
				case "bortoleto" -> "BOR";
				case "colapinto" -> "COL";
				case "doohan" -> "DOO";
				case "lindblad" -> "LIN";
				default -> Character.toUpperCase(token.charAt(0)) + token.substring(1);
			});
		}
		return Optional.empty();
	}

	private String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private void complete(SseEmitter emitter, AtomicBoolean completed) {
		if (completed.compareAndSet(false, true)) {
			emitter.complete();
		}
	}
}
