package com.formula1.apexai.telemetry.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "drivers", indexes = {
		@Index(name = "idx_drivers_number_session", columnList = "driverNumber, sessionKey", unique = true)
})
@Data
public class Driver {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String broadcastName;
	private Integer driverNumber;
	private String firstName;
	private String lastName;
	private String nameAcronym;
	private String teamName;
	@Column(length = 512)
	private String headshotUrl;
	private Integer sessionKey;
}
