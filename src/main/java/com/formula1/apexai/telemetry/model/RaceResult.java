package com.formula1.apexai.telemetry.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "race_results", indexes = {
		@Index(name = "idx_race_results_session_pos", columnList = "sessionKey, finishPosition")
})
@Data
public class RaceResult {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Integer sessionKey;
	private Integer driverNumber;
	private Integer finishPosition;
	private Integer numberOfLaps;
	private Double points;
	private Boolean dnf;
	private Boolean dns;
	private Boolean dsq;
	private String gapToLeader;
}
