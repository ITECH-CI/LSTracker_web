/*
 * Java domain class for entity "SampleRejectionType" 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
 */
package org.itech.labSampleTracker.dao;

import java.util.List;
import java.util.Map;

import org.itech.labSampleTracker.entities.SampleRejectionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * <h2>SampleRejectionTypeRepository</h2>
 *
 * createdAt : 2024-03-31 - Time 19:08:04
 * <p>
 * Description: "SampleRejectionType" Repository
 */


public interface SampleRejectionTypeRepository  extends JpaRepository<SampleRejectionType, Integer> , JpaSpecificationExecutor<SampleRejectionType> {

	/**
	 * Nombre de rejets rattaches a un motif : garde de suppression.
	 */
	@Query(value = "SELECT COUNT(*) FROM sample_rejection WHERE sample_rejection_type_id = :typeId",
			nativeQuery = true)
	long countRejections(@Param("typeId") Integer typeId);

	/**
	 * Liste paginee pour l'administration, avec le nombre de rejets rattaches.
	 */
	@Query(value = "SELECT rt.id AS id, rt.rejection_type AS rejection_type, "
			+ "rt.is_active AS is_active, rt.created_at AS created_at, "
			+ "(SELECT COUNT(*) FROM sample_rejection sr WHERE sr.sample_rejection_type_id = rt.id) AS rejection_count "
			+ "FROM sample_rejection_type rt "
			+ "WHERE (:search IS NULL OR rt.rejection_type ILIKE CONCAT('%', :search, '%')) "
			+ "ORDER BY rt.rejection_type ASC",
			countQuery = "SELECT COUNT(*) FROM sample_rejection_type rt "
			+ "WHERE (:search IS NULL OR rt.rejection_type ILIKE CONCAT('%', :search, '%'))",
			nativeQuery = true)
	Page<Map<String, Object>> findForAdmin(Pageable pageable, @Param("search") String search);

	/**
	 * Motifs actifs, pour les listes deroulantes de saisie.
	 */
	@Query(value = "SELECT rt.id AS id, rt.rejection_type AS rejection_type FROM sample_rejection_type rt "
			+ "WHERE rt.is_active = TRUE ORDER BY rt.rejection_type ASC", nativeQuery = true)
	List<Map<String, Object>> findActiveIdAndLabel();
}
