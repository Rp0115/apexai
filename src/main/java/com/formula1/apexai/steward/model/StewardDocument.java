package com.formula1.apexai.steward.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "steward_documents")
@Data
public class StewardDocument {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String title;
	private String source;
	private String eventName;
	private Integer year;

	@Column(columnDefinition = "TEXT")
	private String content;

	private Instant ingestedAt = Instant.now();
}
