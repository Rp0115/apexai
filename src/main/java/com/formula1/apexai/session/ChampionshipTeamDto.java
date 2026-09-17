package com.formula1.apexai.session;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChampionshipTeamDto(
		@JsonProperty("session_key") Integer sessionKey,
		@JsonProperty("team_name") String teamName,
		@JsonProperty("position_current") Integer positionCurrent,
		@JsonProperty("points_current") Double pointsCurrent
) {
}
