package com.formula1.apexai.telemetry.repository;

import com.formula1.apexai.telemetry.model.SpeedTracePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpeedTraceRepository extends JpaRepository<SpeedTracePoint, Long> {
	List<SpeedTracePoint> findByDriverNumberAndLapNumberAndSessionKeyOrderBySampleIndexAsc(
			Integer driverNumber, Integer lapNumber, Integer sessionKey);

	void deleteByDriverNumberAndLapNumberAndSessionKey(Integer driverNumber, Integer lapNumber, Integer sessionKey);

	boolean existsByDriverNumberAndLapNumberAndSessionKey(Integer driverNumber, Integer lapNumber, Integer sessionKey);
}
