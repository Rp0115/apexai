package com.formula1.apexai.telemetry.dto;

import java.util.List;

public record PodiumResponse(
		String label,
		List<PodiumEntry> entries
) {
	public record PodiumEntry(
			int position,
			int driverNumber,
			String broadcastName,
			String teamName,
			String headshotUrl
	) {
	}
}
