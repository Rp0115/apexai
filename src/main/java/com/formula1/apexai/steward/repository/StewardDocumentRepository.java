package com.formula1.apexai.steward.repository;

import com.formula1.apexai.steward.model.StewardDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StewardDocumentRepository extends JpaRepository<StewardDocument, Long> {

	@Query("""
			SELECT d FROM StewardDocument d
			WHERE LOWER(d.content) LIKE LOWER(CONCAT('%', :q, '%'))
			   OR LOWER(d.title) LIKE LOWER(CONCAT('%', :q, '%'))
			""")
	List<StewardDocument> searchText(@Param("q") String query);
}
