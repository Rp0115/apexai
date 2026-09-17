package com.formula1.apexai.telemetry.repository;

import com.formula1.apexai.telemetry.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
	Optional<Driver> findByDriverNumberAndSessionKey(Integer driverNumber, Integer sessionKey);

	List<Driver> findBySessionKey(Integer sessionKey);

	Optional<Driver> findFirstByNameAcronymIgnoreCase(String nameAcronym);

	Optional<Driver> findFirstByLastNameIgnoreCase(String lastName);

	Optional<Driver> findFirstByFirstNameIgnoreCase(String firstName);

	Optional<Driver> findFirstByBroadcastNameContainingIgnoreCase(String fragment);
}
