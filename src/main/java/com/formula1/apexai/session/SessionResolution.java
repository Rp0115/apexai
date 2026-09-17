package com.formula1.apexai.session;

/**
 * Result of mapping a natural-language question to an OpenF1 session.
 */
public record SessionResolution(
		SessionContext.ActiveSession session,
		String message,
		boolean resolved
) {
	public static SessionResolution ok(SessionContext.ActiveSession session) {
		return new SessionResolution(session, null, true);
	}

	public static SessionResolution notFound(String message) {
		return new SessionResolution(null, message, false);
	}
}
