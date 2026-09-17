package com.formula1.apexai.telemetry.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "laps", indexes = {
		@Index(name = "idx_laps_driver_lap", columnList = "driverNumber, lapNumber, sessionKey"),
		@Index(name = "idx_laps_sector2", columnList = "sector2Time")
})
@Data
public class Lap {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String driverName;
	private Integer driverNumber;
	private Integer lapNumber;
	private String sessionType;
	private Integer sessionKey;
	private Double sector1Time;
	private Double sector2Time;
	private Double sector3Time;
	private Double totalLapTime;
	private Double speedTrap;
}
