package com.formula1.apexai.telemetry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DriverDTO(
		@JsonProperty("broadcast_name") String broadcastName,
		@JsonProperty("driver_number") Integer driverNumber,
		@JsonProperty("first_name") String firstName,
		@JsonProperty("last_name") String lastName,
		@JsonProperty("name_acronym") String nameAcronym,
		@JsonProperty("team_name") String teamName,
		@JsonProperty("headshot_url") String headshotUrl
) {
}
