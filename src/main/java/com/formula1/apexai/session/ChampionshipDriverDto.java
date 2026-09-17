package com.formula1.apexai.session;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChampionshipDriverDto(
		@JsonProperty("session_key") Integer sessionKey,
		@JsonProperty("driver_number") Integer driverNumber,
		@JsonProperty("position_current") Integer positionCurrent,
		@JsonProperty("points_current") Double pointsCurrent
) {
}
