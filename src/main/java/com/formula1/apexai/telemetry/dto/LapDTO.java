package com.formula1.apexai.telemetry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LapDTO(
		@JsonProperty("driver_number") Integer driverNumber,
		@JsonProperty("lap_number") Integer lapNumber,
		@JsonProperty("lap_duration") Double totalLapTime,
		@JsonProperty("duration_sector_1") Double s1Duration,
		@JsonProperty("duration_sector_2") Double s2Duration,
		@JsonProperty("duration_sector_3") Double s3Duration,
		@JsonProperty("st_speed") Double speedTrap
) {
}
