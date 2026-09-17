package com.formula1.apexai.telemetry.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "speed_trace_points", indexes = {
		@Index(name = "idx_speed_trace", columnList = "driverNumber, lapNumber, sessionKey, sampleIndex")
})
@Data
public class SpeedTracePoint {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Integer driverNumber;
	private Integer lapNumber;
	private Integer sessionKey;
	private Integer sampleIndex;
	private Double distanceMeters;
	private Double speedKph;
	private Integer gear;
	private Double throttle;
	private Boolean brake;
}
