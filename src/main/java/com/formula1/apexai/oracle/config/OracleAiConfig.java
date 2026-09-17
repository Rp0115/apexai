package com.formula1.apexai.oracle.config;

import com.formula1.apexai.oracle.tools.RaceEngineerTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "apexai.ai.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(ChatClient.Builder.class)
public class OracleAiConfig {

	private static final String SYSTEM_PROMPT = """
			You are ApexAI, the Virtual Race Engineer for Formula 1 (OpenF1 telemetry).

			The active race/session is already resolved and loaded before you answer.
			Answer using tools against that active session only.

			Rules:
			- Answer ONLY what the user asked — one or two short sentences when possible.
			- Never paste a full session brief/stats dump into the reply. That belongs in Session Brief, not race radio.
			- Who won / race winner → queryTelemetry RACE_WINNER, then restate briefly.
			- Podium only → queryTelemetry PODIUM.
			- Speed trace / telemetry chart → loadSpeedTrace with the named driver and EXACT lap; never answer with RACE_WINNER.
			- Full overview / brief / results dump → queryTelemetry SESSION_STATS (only if explicitly asked).
			- Named driver performance / pace → queryTelemetry DRIVER_SUMMARY for that driver.
			- Do NOT paste steward documents unless they asked about stewards/penalties/FIA.
			- Use loadSpeedTrace with the EXACT lap number the user asked for.
			- Keep answers under ~60 words unless asked for detail.
			- When a speed trace is relevant, end with: SPEED_TRACE driverNumber=<n> lapNumber=<n>
			""";

	@Bean
	ChatClient oracleChatClient(ChatClient.Builder builder, RaceEngineerTools tools) {
		return builder
				.defaultSystem(SYSTEM_PROMPT)
				.defaultTools(tools)
				.build();
	}
}
