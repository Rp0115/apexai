package com.formula1.apexai.telemetry.dto;

public record SpeedTraceResponse(
		Integer driverNumber,
		String driverName,
		Integer lapNumber,
		java.util.List<SpeedSample> samples
) {
	public record SpeedSample(Integer index, Double distanceMeters, Double speedKph, Integer gear) {
	}
}
