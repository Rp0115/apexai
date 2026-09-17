package com.formula1.apexai.session;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenF1SessionDto(
		@JsonProperty("session_key") Integer sessionKey,
		@JsonProperty("meeting_key") Integer meetingKey,
		@JsonProperty("session_name") String sessionName,
		@JsonProperty("session_type") String sessionType,
		@JsonProperty("country_name") String countryName,
		@JsonProperty("circuit_short_name") String circuitShortName,
		@JsonProperty("location") String location,
		@JsonProperty("year") Integer year,
		@JsonProperty("date_start") String dateStart,
		@JsonProperty("is_cancelled") Boolean isCancelled
) {
	public String displayLabel() {
		String circuit = circuitShortName != null ? circuitShortName : location;
		String country = countryName != null ? countryName : "Unknown";
		String name = sessionName != null ? sessionName : sessionType;
		return String.format("%s %d — %s (%s)", country, year != null ? year : 0, name, circuit);
	}
}
