package com.formula1.apexai.telemetry.repository;

import com.formula1.apexai.telemetry.model.RaceResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RaceResultRepository extends JpaRepository<RaceResult, Long> {
	List<RaceResult> findBySessionKeyOrderByFinishPositionAsc(Integer sessionKey);

	Optional<RaceResult> findFirstBySessionKeyAndFinishPosition(Integer sessionKey, Integer finishPosition);

	Optional<RaceResult> findBySessionKeyAndDriverNumber(Integer sessionKey, Integer driverNumber);

	long countBySessionKey(Integer sessionKey);
}
