package com.formula1.apexai.batch;

import com.formula1.apexai.config.OpenF1Properties;
import com.formula1.apexai.session.OpenF1IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class OpenF1BatchConfig {

	private final OpenF1Properties openF1Properties;
	private final OpenF1IngestionService ingestionService;

	@Bean
	Job openF1IngestJob(JobRepository jobRepository, Step ingestConfiguredSessionStep) {
		return new JobBuilder("openF1IngestJob", jobRepository)
				.start(ingestConfiguredSessionStep)
				.build();
	}

	@Bean
	Step ingestConfiguredSessionStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
		return new StepBuilder("ingestConfiguredSessionStep", jobRepository)
				.tasklet((contribution, chunkContext) -> {
					int sessionKey = openF1Properties.sessionKey();
					var summary = ingestionService.ingestSession(sessionKey, openF1Properties.sessionType());
					log.info("Batch ingest finished: {}", summary);
					return RepeatStatus.FINISHED;
				}, txManager)
				.build();
	}
}
