package com.formula1.apexai.telemetry.repository;

import com.formula1.apexai.telemetry.model.Lap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LapRepository extends JpaRepository<Lap, Long> {

	List<Lap> findByDriverNumberAndSessionKeyOrderByLapNumberAsc(Integer driverNumber, Integer sessionKey);

	List<Lap> findByLapNumberAndSessionKey(Integer lapNumber, Integer sessionKey);

	Optional<Lap> findFirstBySessionKeyAndTotalLapTimeIsNotNullOrderByTotalLapTimeAsc(Integer sessionKey);

	Optional<Lap> findFirstBySessionKeyAndSector2TimeIsNotNullOrderBySector2TimeAsc(Integer sessionKey);

	Optional<Lap> findFirstBySessionKeyAndSector1TimeIsNotNullOrderBySector1TimeAsc(Integer sessionKey);

	Optional<Lap> findFirstBySessionKeyAndSector3TimeIsNotNullOrderBySector3TimeAsc(Integer sessionKey);

	Optional<Lap> findByDriverNumberAndLapNumberAndSessionKey(Integer driverNumber, Integer lapNumber, Integer sessionKey);

	@Query("""
			SELECT l FROM Lap l
			WHERE l.sessionKey = :sessionKey
			  AND l.totalLapTime IS NOT NULL
			  AND (:driverNumber IS NULL OR l.driverNumber = :driverNumber)
			ORDER BY l.totalLapTime ASC
			""")
	List<Lap> findFastestLaps(@Param("sessionKey") Integer sessionKey, @Param("driverNumber") Integer driverNumber);

	Optional<Lap> findFirstBySessionKeyAndSpeedTrapIsNotNullOrderBySpeedTrapDesc(Integer sessionKey);

	long countBySessionKey(Integer sessionKey);
}
