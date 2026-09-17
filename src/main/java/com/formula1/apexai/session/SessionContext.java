package com.formula1.apexai.session;

/**
 * Per-request active OpenF1 session. Telemetry queries read from here when set.
 */
public final class SessionContext {

	private static final ThreadLocal<ActiveSession> CURRENT = new ThreadLocal<>();

	private SessionContext() {
	}

	public static void set(ActiveSession session) {
		CURRENT.set(session);
	}

	public static ActiveSession get() {
		return CURRENT.get();
	}

	public static Integer sessionKeyOrNull() {
		ActiveSession session = CURRENT.get();
		return session == null ? null : session.sessionKey();
	}

	public static void clear() {
		CURRENT.remove();
	}

	public record ActiveSession(
			int sessionKey,
			int meetingKey,
			int year,
			String sessionName,
			String sessionType,
			String countryName,
			String circuitShortName,
			String label
	) {
	}
}
