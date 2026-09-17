package com.formula1.apexai.session;

import com.formula1.apexai.config.OpenF1Properties;
import com.formula1.apexai.telemetry.repository.LapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RaceSessionService {

	private static final Pattern YEAR = Pattern.compile("\\b(20\\d{2})\\b");

	/**
	 * Venue-specific phrases → circuit / location needles (NOT country).
	 * Used when a country has multiple GPs (e.g. Spain: Catalunya vs Madrid).
	 */
	private static final Map<String, List<String>> VENUE_ALIASES = new LinkedHashMap<>();

	/** Country-level phrases → OpenF1 country_name. */
	private static final Map<String, String> COUNTRY_ALIASES = new LinkedHashMap<>();

	static {
		// --- Venue-specific (prefer these over country) ---
		VENUE_ALIASES.put("barcelona", List.of("barcelona", "catalunya"));
		VENUE_ALIASES.put("catalunya", List.of("catalunya", "barcelona"));
		VENUE_ALIASES.put("madrid", List.of("madrid", "madring"));
		VENUE_ALIASES.put("madring", List.of("madring", "madrid"));
		VENUE_ALIASES.put("suzuka", List.of("suzuka"));
		VENUE_ALIASES.put("monaco", List.of("monaco", "monte carlo"));
		VENUE_ALIASES.put("monte carlo", List.of("monte carlo", "monaco"));
		VENUE_ALIASES.put("spa", List.of("spa", "spa-francorchamps"));
		VENUE_ALIASES.put("silverstone", List.of("silverstone"));
		VENUE_ALIASES.put("monza", List.of("monza"));
		VENUE_ALIASES.put("hungaroring", List.of("hungaroring", "budapest"));
		VENUE_ALIASES.put("zandvoort", List.of("zandvoort"));
		// USA — three distinct GPs; venue must win over country=United States
		VENUE_ALIASES.put("austin", List.of("austin", "cota"));
		VENUE_ALIASES.put("cota", List.of("austin", "cota"));
		VENUE_ALIASES.put("circuit of the americas", List.of("austin", "cota"));
		VENUE_ALIASES.put("americas", List.of("austin", "cota"));
		VENUE_ALIASES.put("texas", List.of("austin", "cota"));
		VENUE_ALIASES.put("miami", List.of("miami"));
		VENUE_ALIASES.put("las vegas", List.of("las vegas"));
		VENUE_ALIASES.put("vegas", List.of("las vegas"));
		VENUE_ALIASES.put("lvgp", List.of("las vegas"));
		VENUE_ALIASES.put("interlagos", List.of("interlagos", "são paulo", "sao paulo"));
		VENUE_ALIASES.put("sao paulo", List.of("interlagos", "são paulo", "sao paulo"));
		VENUE_ALIASES.put("marina bay", List.of("singapore", "marina bay"));
		VENUE_ALIASES.put("sakhir", List.of("sakhir", "bahrain"));
		VENUE_ALIASES.put("jeddah", List.of("jeddah"));
		VENUE_ALIASES.put("melbourne", List.of("melbourne", "albert park"));
		VENUE_ALIASES.put("albert park", List.of("melbourne", "albert park"));
		VENUE_ALIASES.put("baku", List.of("baku"));
		VENUE_ALIASES.put("montreal", List.of("montreal", "montréal"));
		VENUE_ALIASES.put("spielberg", List.of("spielberg", "red bull ring"));
		VENUE_ALIASES.put("red bull ring", List.of("spielberg", "red bull ring"));
		VENUE_ALIASES.put("lusail", List.of("lusail"));
		VENUE_ALIASES.put("yas marina", List.of("yas marina"));
		VENUE_ALIASES.put("abu dhabi", List.of("yas marina", "abu dhabi"));
		VENUE_ALIASES.put("shanghai", List.of("shanghai"));
		VENUE_ALIASES.put("mexico city", List.of("mexico city"));

		// --- Country-level (ambiguous if multiple venues) ---
		COUNTRY_ALIASES.put("japan", "Japan");
		COUNTRY_ALIASES.put("japanese", "Japan");
		COUNTRY_ALIASES.put("belgium", "Belgium");
		COUNTRY_ALIASES.put("belgian", "Belgium");
		COUNTRY_ALIASES.put("britain", "United Kingdom");
		COUNTRY_ALIASES.put("british", "United Kingdom");
		COUNTRY_ALIASES.put("uk", "United Kingdom");
		COUNTRY_ALIASES.put("england", "United Kingdom");
		COUNTRY_ALIASES.put("great britain", "United Kingdom");
		COUNTRY_ALIASES.put("italy", "Italy");
		COUNTRY_ALIASES.put("italian", "Italy");
		COUNTRY_ALIASES.put("spain", "Spain");
		COUNTRY_ALIASES.put("spanish", "Spain");
		COUNTRY_ALIASES.put("hungary", "Hungary");
		COUNTRY_ALIASES.put("hungarian", "Hungary");
		COUNTRY_ALIASES.put("dutch", "Netherlands");
		COUNTRY_ALIASES.put("netherlands", "Netherlands");
		COUNTRY_ALIASES.put("usa", "United States");
		COUNTRY_ALIASES.put("united states", "United States");
		// Note: do not alias bare "america" — it false-positives on "Circuit of the Americas"
		COUNTRY_ALIASES.put("mexico", "Mexico");
		COUNTRY_ALIASES.put("mexican", "Mexico");
		COUNTRY_ALIASES.put("brazil", "Brazil");
		COUNTRY_ALIASES.put("brazilian", "Brazil");
		COUNTRY_ALIASES.put("singapore", "Singapore");
		COUNTRY_ALIASES.put("bahrain", "Bahrain");
		COUNTRY_ALIASES.put("saudi", "Saudi Arabia");
		COUNTRY_ALIASES.put("saudi arabia", "Saudi Arabia");
		COUNTRY_ALIASES.put("australia", "Australia");
		COUNTRY_ALIASES.put("australian", "Australia");
		COUNTRY_ALIASES.put("azerbaijan", "Azerbaijan");
		COUNTRY_ALIASES.put("canada", "Canada");
		COUNTRY_ALIASES.put("canadian", "Canada");
		COUNTRY_ALIASES.put("austria", "Austria");
		COUNTRY_ALIASES.put("austrian", "Austria");
		COUNTRY_ALIASES.put("qatar", "Qatar");
		COUNTRY_ALIASES.put("uae", "United Arab Emirates");
		COUNTRY_ALIASES.put("china", "China");
		COUNTRY_ALIASES.put("chinese", "China");
	}

	private final OpenF1Properties openF1Properties;
	private final RestClient.Builder restClientBuilder;
	private final LapRepository lapRepository;
	private final OpenF1IngestionService ingestionService;

	public SessionContext.ActiveSession defaultSession() {
		return new SessionContext.ActiveSession(
				openF1Properties.sessionKey(),
				openF1Properties.meetingKey(),
				2023,
				openF1Properties.sessionType(),
				openF1Properties.sessionType(),
				"Japan",
				"Suzuka",
				"Japan 2023 — Race (Suzuka)");
	}

	/** Resolve metadata for a known session_key (used by telemetry stats / brief). */
	public SessionContext.ActiveSession resolveBySessionKey(int sessionKey) {
		if (sessionKey == openF1Properties.sessionKey()) {
			return defaultSession();
		}
		try {
			RestClient client = restClientBuilder.build();
			List<OpenF1SessionDto> sessions = client.get()
					.uri(openF1Properties.baseUrl() + "/sessions?session_key=" + sessionKey)
					.retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
			if (sessions != null && !sessions.isEmpty()) {
				return toActive(sessions.getFirst());
			}
		} catch (Exception ex) {
			log.warn("Could not resolve OpenF1 metadata for session {}: {}", sessionKey, ex.getMessage());
		}
		return new SessionContext.ActiveSession(
				sessionKey, 0, 2023, "Race", "Race", "?", "?", "Session " + sessionKey);
	}

	public Optional<OpenF1SessionDto> findSession(int year, String countryOrCircuit, String sessionName) {
		List<OpenF1SessionDto> sessions = fetchSessions(year);
		if (sessions.isEmpty()) {
			return Optional.empty();
		}
		String wantedSession = sessionName == null ? "Race" : sessionName;
		String needle = countryOrCircuit.toLowerCase(Locale.ROOT);

		// 1) Venue-first: circuit / location needles
		List<String> venueNeedles = VENUE_ALIASES.getOrDefault(needle, List.of(needle));
		Optional<OpenF1SessionDto> venueHit = bestByScore(sessions, wantedSession, venueNeedles, true);
		if (venueHit.isPresent()) {
			return venueHit;
		}

		// 2) Country fallback
		String country = COUNTRY_ALIASES.getOrDefault(needle, countryOrCircuit);
		return bestByScore(sessions, wantedSession, List.of(country.toLowerCase(Locale.ROOT)), false);
	}

	public List<OpenF1SessionDto> listRaces(int year) {
		return fetchSessions(year).stream()
				.filter(s -> s.sessionKey() != null)
				.filter(s -> sessionNameMatches(s, "Race"))
				.sorted(Comparator.comparing(OpenF1SessionDto::dateStart, Comparator.nullsLast(String::compareTo)))
				.toList();
	}

	/**
	 * Resolve which race the user means. Falls back to the configured default (Japan).
	 */
	public SessionContext.ActiveSession resolveFromQuestion(String question) {
		int year = extractYear(question).orElse(2023);
		Optional<String> raceHint = extractRaceHint(question);
		String sessionName = extractSessionName(question);

		if (raceHint.isEmpty()) {
			return defaultSession();
		}

		String hint = raceHint.get();
		List<OpenF1SessionDto> sessions = fetchSessions(year);
		String wantedSession = sessionName;

		// Venue-specific hint (barcelona, madrid, miami, …) always wins over country
		if (VENUE_ALIASES.containsKey(hint)) {
			Optional<OpenF1SessionDto> venue = bestByScore(
					sessions, wantedSession, VENUE_ALIASES.get(hint), true);
			if (venue.isPresent()) {
				log.info("Resolved venue hint '{}' → {}", hint, venue.get().displayLabel());
				return toActive(venue.get());
			}
		}

		// Country hint — if multiple GPs in that country, prefer the one whose
		// circuit/location also appears in the question; else earliest race.
		if (COUNTRY_ALIASES.containsKey(hint)) {
			String country = COUNTRY_ALIASES.get(hint);
			List<OpenF1SessionDto> countryRaces = sessions.stream()
					.filter(s -> s.sessionKey() != null)
					.filter(s -> sessionNameMatches(s, wantedSession))
					.filter(s -> contains(s.countryName(), country))
					.sorted(Comparator.comparing(OpenF1SessionDto::dateStart, Comparator.nullsLast(String::compareTo)))
					.toList();

			if (countryRaces.size() == 1) {
				return toActive(countryRaces.getFirst());
			}
			if (countryRaces.size() > 1) {
				String q = question.toLowerCase(Locale.ROOT);
				Optional<OpenF1SessionDto> disambiguated = countryRaces.stream()
						.filter(s -> venueMentionedInQuestion(s, q))
						.findFirst();
				if (disambiguated.isPresent()) {
					log.info("Disambiguated {} → {}", country, disambiguated.get().displayLabel());
					return toActive(disambiguated.get());
				}
				// Ambiguous "Spain 2026" with no venue — use earliest and label clearly
				OpenF1SessionDto first = countryRaces.getFirst();
				log.warn("Ambiguous country '{}' ({} races); using earliest: {}",
						country, countryRaces.size(), first.displayLabel());
				return toActive(first);
			}
		}

		// Raw needle search (circuit/location/country substring)
		Optional<OpenF1SessionDto> found = bestByScore(sessions, wantedSession, List.of(hint), true);
		if (found.isPresent()) {
			return toActive(found.get());
		}
		found = bestByScore(sessions, wantedSession, List.of(hint), false);
		if (found.isPresent()) {
			return toActive(found.get());
		}

		return defaultSession();
	}

	public boolean isIngested(int sessionKey) {
		return lapRepository.countBySessionKey(sessionKey) > 0;
	}

	public String ensureIngested(SessionContext.ActiveSession session) {
		if (isIngested(session.sessionKey())) {
			return "Using cached data for " + session.label() + ".";
		}
		log.info("On-demand ingest for session {} ({})", session.sessionKey(), session.label());
		ingestionService.ingestSession(session.sessionKey(), session.sessionType());
		return "Loaded " + session.label() + " from OpenF1 ("
				+ lapRepository.countBySessionKey(session.sessionKey()) + " laps).";
	}

	public SessionContext.ActiveSession toActive(OpenF1SessionDto dto) {
		return new SessionContext.ActiveSession(
				dto.sessionKey(),
				dto.meetingKey() != null ? dto.meetingKey() : 0,
				dto.year() != null ? dto.year() : 2023,
				dto.sessionName(),
				dto.sessionType(),
				dto.countryName(),
				dto.circuitShortName(),
				dto.displayLabel());
	}

	public Optional<Integer> extractYear(String question) {
		Matcher matcher = YEAR.matcher(question);
		if (matcher.find()) {
			return Optional.of(Integer.parseInt(matcher.group(1)));
		}
		return Optional.empty();
	}

	public Optional<String> extractRaceHint(String question) {
		String q = question.toLowerCase(Locale.ROOT);
		List<String> keys = new ArrayList<>();
		keys.addAll(VENUE_ALIASES.keySet());
		keys.addAll(COUNTRY_ALIASES.keySet());
		return keys.stream()
				.sorted(Comparator.comparingInt(String::length).reversed())
				.filter(q::contains)
				.findFirst();
	}

	private List<OpenF1SessionDto> fetchSessions(int year) {
		RestClient client = restClientBuilder.build();
		List<OpenF1SessionDto> sessions = client.get()
				.uri(openF1Properties.baseUrl() + "/sessions?year=" + year)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
		return sessions == null ? List.of() : sessions;
	}

	/**
	 * @param venueOnly when true, only score circuit/location (not country) so
	 *                  "barcelona" cannot match Madrid via country=Spain.
	 */
	private Optional<OpenF1SessionDto> bestByScore(
			List<OpenF1SessionDto> sessions,
			String wantedSession,
			List<String> needles,
			boolean venueOnly) {

		record Scored(OpenF1SessionDto session, int score) {
		}

		return sessions.stream()
				.filter(s -> s.sessionKey() != null)
				.filter(s -> sessionNameMatches(s, wantedSession))
				.map(s -> new Scored(s, scoreSession(s, needles, venueOnly)))
				.filter(s -> s.score() > 0)
				.max(Comparator.comparingInt(Scored::score)
						.thenComparing(s -> s.session().dateStart(), Comparator.nullsLast(String::compareTo)))
				.map(Scored::session);
	}

	private int scoreSession(OpenF1SessionDto s, List<String> needles, boolean venueOnly) {
		int best = 0;
		for (String needle : needles) {
			String n = needle.toLowerCase(Locale.ROOT);
			if (contains(s.location(), n)) {
				best = Math.max(best, 100);
			}
			if (contains(s.circuitShortName(), n)) {
				best = Math.max(best, 100);
			}
			if (!venueOnly && contains(s.countryName(), n)) {
				best = Math.max(best, 20);
			}
		}
		return best;
	}

	private boolean venueMentionedInQuestion(OpenF1SessionDto s, String qLower) {
		return (s.location() != null && qLower.contains(s.location().toLowerCase(Locale.ROOT)))
				|| (s.circuitShortName() != null && qLower.contains(s.circuitShortName().toLowerCase(Locale.ROOT)))
				|| VENUE_ALIASES.entrySet().stream()
				.filter(e -> qLower.contains(e.getKey()))
				.anyMatch(e -> e.getValue().stream().anyMatch(n ->
						contains(s.location(), n) || contains(s.circuitShortName(), n)));
	}

	private String extractSessionName(String question) {
		String q = question.toLowerCase(Locale.ROOT);
		if (q.contains("qualifying") || q.contains(" quali")) {
			return "Qualifying";
		}
		if (q.contains("sprint")) {
			return "Sprint";
		}
		if (q.contains("practice 3") || q.contains("fp3")) {
			return "Practice 3";
		}
		if (q.contains("practice 2") || q.contains("fp2")) {
			return "Practice 2";
		}
		if (q.contains("practice 1") || q.contains("fp1")) {
			return "Practice 1";
		}
		return "Race";
	}

	private boolean sessionNameMatches(OpenF1SessionDto s, String wanted) {
		if (wanted.equalsIgnoreCase("Race")) {
			return "Race".equalsIgnoreCase(s.sessionName()) || "Race".equalsIgnoreCase(s.sessionType());
		}
		return wanted.equalsIgnoreCase(s.sessionName()) || wanted.equalsIgnoreCase(s.sessionType());
	}

	private boolean contains(String value, String needle) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
	}
}
