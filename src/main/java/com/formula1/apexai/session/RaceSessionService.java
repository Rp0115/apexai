package com.formula1.apexai.session;

import com.formula1.apexai.config.OpenF1Properties;
import com.formula1.apexai.telemetry.repository.LapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
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
				.filter(s -> !Boolean.TRUE.equals(s.isCancelled()))
				.sorted(Comparator.comparing(OpenF1SessionDto::dateStart, Comparator.nullsLast(String::compareTo)))
				.toList();
	}

	/** Latest non-cancelled main Race of a season (for championship standings). */
	public Optional<OpenF1SessionDto> lastMainRaceOfYear(int year) {
		List<OpenF1SessionDto> races = listRaces(year);
		if (races.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(races.getLast());
	}

	/**
	 * Resolve which race the user means.
	 * Does <strong>not</strong> silently fall back to Japan when a race/year was named
	 * but that session type (e.g. Sprint) does not exist.
	 */
	public SessionResolution resolveDetailed(String question) {
		Optional<Integer> explicitYear = extractYear(question);
		int year = explicitYear.orElse(2023);

		if (ChampionshipService.looksLikeChampionshipQuestion(question)) {
			Optional<OpenF1SessionDto> finale = lastMainRaceOfYear(year);
			if (finale.isPresent()) {
				log.info("Championship question for {} → session {}", year, finale.get().displayLabel());
				return SessionResolution.ok(toActive(finale.get()));
			}
			return SessionResolution.notFound(
					"No championship Race sessions found for " + year + " in OpenF1.");
		}

		Optional<String> raceHint = extractRaceHint(question);
		String sessionName = extractSessionName(question);

		// "latest sprint" / "most recent race" with no venue → newest matching session in OpenF1
		if (raceHint.isEmpty() && looksLikeLatest(question)) {
			Optional<OpenF1SessionDto> latest = findLatestSession(
					sessionName, explicitYear.orElse(null));
			if (latest.isPresent()) {
				log.info("Resolved latest {} → {}", sessionName, latest.get().displayLabel());
				return SessionResolution.ok(toActive(latest.get()));
			}
			return SessionResolution.notFound(
					"No " + sessionName + " sessions found in OpenF1 yet (coverage is 2023+).");
		}

		if (raceHint.isEmpty()) {
			return SessionResolution.ok(defaultSession());
		}

		String hint = raceHint.get();
		List<OpenF1SessionDto> sessions = fetchSessions(year);
		String wantedSession = sessionName;

		Optional<OpenF1SessionDto> match = findMatchingSession(sessions, hint, wantedSession, question);
		if (match.isPresent()) {
			log.info("Resolved '{}' ({}) → {}", hint, wantedSession, match.get().displayLabel());
			return SessionResolution.ok(toActive(match.get()));
		}

		// Sprint asked but this weekend has no Sprint — explain, don't jump to Japan
		if ("Sprint".equalsIgnoreCase(wantedSession)) {
			Optional<OpenF1SessionDto> mainRace = findMatchingSession(sessions, hint, "Race", question);
			if (mainRace.isPresent()) {
				return SessionResolution.notFound(String.format(
						"%s %d did not have a Sprint weekend in OpenF1 (standard GP only: %s). "
								+ "Ask about the Race instead, e.g. \"Who won Austria %d?\".",
						countryOrVenueLabel(hint), year, mainRace.get().displayLabel(), year));
			}
			return SessionResolution.notFound(String.format(
					"No Sprint session found for %s %d in OpenF1.", countryOrVenueLabel(hint), year));
		}

		return SessionResolution.notFound(String.format(
				"No %s session found for %s %d in OpenF1 (coverage is 2023+).",
				wantedSession, countryOrVenueLabel(hint), year));
	}

	/** @deprecated prefer {@link #resolveDetailed(String)} */
	public SessionContext.ActiveSession resolveFromQuestion(String question) {
		SessionResolution resolution = resolveDetailed(question);
		if (resolution.resolved()) {
			return resolution.session();
		}
		return defaultSession();
	}

	private Optional<OpenF1SessionDto> findMatchingSession(
			List<OpenF1SessionDto> sessions,
			String hint,
			String wantedSession,
			String question) {

		if (VENUE_ALIASES.containsKey(hint)) {
			Optional<OpenF1SessionDto> venue = bestByScore(
					sessions, wantedSession, VENUE_ALIASES.get(hint), true);
			if (venue.isPresent()) {
				return venue;
			}
		}

		if (COUNTRY_ALIASES.containsKey(hint)) {
			String country = COUNTRY_ALIASES.get(hint);
			List<OpenF1SessionDto> countryRaces = sessions.stream()
					.filter(s -> s.sessionKey() != null)
					.filter(s -> sessionNameMatches(s, wantedSession))
					.filter(s -> contains(s.countryName(), country))
					.sorted(Comparator.comparing(OpenF1SessionDto::dateStart, Comparator.nullsLast(String::compareTo)))
					.toList();

			if (countryRaces.size() == 1) {
				return Optional.of(countryRaces.getFirst());
			}
			if (countryRaces.size() > 1) {
				String q = question.toLowerCase(Locale.ROOT);
				Optional<OpenF1SessionDto> disambiguated = countryRaces.stream()
						.filter(s -> venueMentionedInQuestion(s, q))
						.findFirst();
				if (disambiguated.isPresent()) {
					return disambiguated;
				}
				return Optional.of(countryRaces.getFirst());
			}
		}

		Optional<OpenF1SessionDto> found = bestByScore(sessions, wantedSession, List.of(hint), true);
		if (found.isPresent()) {
			return found;
		}
		return bestByScore(sessions, wantedSession, List.of(hint), false);
	}

	private String countryOrVenueLabel(String hint) {
		if (COUNTRY_ALIASES.containsKey(hint)) {
			return COUNTRY_ALIASES.get(hint);
		}
		return Character.toUpperCase(hint.charAt(0)) + hint.substring(1);
	}

	private boolean looksLikeLatest(String question) {
		String q = question.toLowerCase(Locale.ROOT);
		return q.contains("latest")
				|| q.contains("most recent")
				|| q.matches(".*\\blast\\s+(sprint|race|gp|grand prix)\\b.*");
	}

	/**
	 * Newest non-cancelled session of the given type that has already started
	 * (so "latest sprint" does not pick a future calendar entry).
	 */
	private Optional<OpenF1SessionDto> findLatestSession(String wantedSession, Integer yearFilter) {
		int fromYear = yearFilter != null ? yearFilter : java.time.Year.now(java.time.ZoneOffset.UTC).getValue();
		int toYear = yearFilter != null ? yearFilter : 2023;
		Instant now = Instant.now();

		OpenF1SessionDto best = null;
		Instant bestStart = null;
		for (int y = fromYear; y >= toYear; y--) {
			for (OpenF1SessionDto s : fetchSessions(y)) {
				if (s.sessionKey() == null || !sessionNameMatches(s, wantedSession)) {
					continue;
				}
				if (Boolean.TRUE.equals(s.isCancelled()) || s.dateStart() == null) {
					continue;
				}
				Instant start;
				try {
					start = Instant.parse(s.dateStart());
				} catch (Exception ex) {
					continue;
				}
				if (start.isAfter(now)) {
					continue;
				}
				if (best == null || start.isAfter(bestStart)) {
					best = s;
					bestStart = start;
				}
			}
			// Once we have a hit in a newer year, older years cannot be later
			if (yearFilter == null && best != null && y < fromYear) {
				break;
			}
		}
		return Optional.ofNullable(best);
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
		// Prefer session_name in the type slot used for display ("Race" vs "Sprint").
		// OpenF1 sets session_type=Race for Sprints, which is misleading in the UI.
		String kind = dto.sessionName() != null ? dto.sessionName() : dto.sessionType();
		return new SessionContext.ActiveSession(
				dto.sessionKey(),
				dto.meetingKey() != null ? dto.meetingKey() : 0,
				dto.year() != null ? dto.year() : 2023,
				dto.sessionName(),
				kind,
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
				.filter(key -> hintMatches(q, key))
				.findFirst();
	}

	/** Avoid "spa" matching inside "sprint". */
	private boolean hintMatches(String questionLower, String key) {
		if (key.length() <= 3) {
			return Pattern.compile("\\b" + Pattern.quote(key) + "\\b", Pattern.CASE_INSENSITIVE)
					.matcher(questionLower).find();
		}
		return questionLower.contains(key);
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
		// Sprint must be explicit ("Belgium sprint 2023"). "Belgium GP" → main Race.
		if (q.contains("sprint quali") || q.contains("sprint qualifying")) {
			return "Sprint Qualifying";
		}
		if (q.contains("sprint")) {
			return "Sprint";
		}
		if (q.contains("qualifying") || q.contains("qualify") || q.contains(" quali") || q.contains("pole")) {
			return "Qualifying";
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

	/**
	 * OpenF1 marks Sprint as {@code session_type=Race} with {@code session_name=Sprint}.
	 * For the main GP we must match {@code session_name} exactly — never fall back to type alone.
	 */
	private boolean sessionNameMatches(OpenF1SessionDto s, String wanted) {
		if (wanted.equalsIgnoreCase("Race")) {
			return isMainRace(s);
		}
		if (wanted.equalsIgnoreCase("Sprint")) {
			return isSprintRace(s);
		}
		if (wanted.equalsIgnoreCase("Qualifying")) {
			// Exact name — do not match Sprint Qualifying via session_type
			return "Qualifying".equalsIgnoreCase(s.sessionName());
		}
		if (wanted.equalsIgnoreCase("Sprint Qualifying")) {
			return "Sprint Qualifying".equalsIgnoreCase(s.sessionName());
		}
		return wanted.equalsIgnoreCase(s.sessionName())
				|| wanted.equalsIgnoreCase(s.sessionType());
	}

	/** Sunday GP only — excludes Sprint / Sprint Qualifying. */
	private boolean isMainRace(OpenF1SessionDto s) {
		return s != null
				&& "Race".equalsIgnoreCase(s.sessionName())
				&& !Boolean.TRUE.equals(s.isCancelled());
	}

	private boolean isSprintRace(OpenF1SessionDto s) {
		return s != null && "Sprint".equalsIgnoreCase(s.sessionName());
	}

	private boolean contains(String value, String needle) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
	}
}
