package com.formula1.apexai.telemetry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SessionResultDTO(
		@JsonProperty("position") Integer position,
		@JsonProperty("driver_number") Integer driverNumber,
		@JsonProperty("number_of_laps") Integer numberOfLaps,
		@JsonProperty("points") Double points,
		@JsonProperty("dnf") Boolean dnf,
		@JsonProperty("dns") Boolean dns,
		@JsonProperty("dsq") Boolean dsq,
		@JsonProperty("gap_to_leader") Object gapToLeader
) {
	public String gapAsString() {
		return gapToLeader == null ? null : String.valueOf(gapToLeader);
	}
}
