/*
 * Java domain class for entity "SampleType" 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
 */
package org.itech.labSampleTracker.dao;

import java.util.List;
import java.util.Map;

import org.itech.labSampleTracker.entities.SampleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * <h2>SampleTypeRepository</h2>
 *
 * createdAt : 2024-03-31 - Time 19:08:04
 * <p>
 * Description: "SampleType" Repository
 */

public interface SampleTypeRepository extends JpaRepository<SampleType, Integer>, JpaSpecificationExecutor<SampleType> {
	public SampleType findByName(String name);

	/**
	 * Nombre d'echantillons rattaches a un type : garde de suppression.
	 * Un type reference par des echantillons ne peut pas etre supprime
	 * (contrainte de cle etrangere cote base, et perte de sens metier).
	 */
	@Query(value = "SELECT COUNT(*) FROM sample WHERE sample_type_id = :typeId", nativeQuery = true)
	long countSamples(@Param("typeId") Integer typeId);

	/**
	 * Liste paginee pour l'administration : recherche plein texte sur le nom
	 * et la description, avec le nombre d'echantillons rattaches.
	 */
	@Query(value = "SELECT st.id AS id, st.name AS name, st.description AS description, "
			+ "st.is_active AS is_active, st.created_at AS created_at, "
			+ "(SELECT COUNT(*) FROM sample s WHERE s.sample_type_id = st.id) AS sample_count "
			+ "FROM sample_type st "
			+ "WHERE (:search IS NULL OR st.name ILIKE CONCAT('%', :search, '%') "
			+ "       OR COALESCE(st.description, '') ILIKE CONCAT('%', :search, '%')) "
			+ "ORDER BY st.name ASC",
			countQuery = "SELECT COUNT(*) FROM sample_type st "
			+ "WHERE (:search IS NULL OR st.name ILIKE CONCAT('%', :search, '%') "
			+ "       OR COALESCE(st.description, '') ILIKE CONCAT('%', :search, '%'))",
			nativeQuery = true)
	Page<Map<String, Object>> findForAdmin(Pageable pageable, @Param("search") String search);

	/**
	 * Types actifs, pour les listes deroulantes de saisie.
	 */
	@Query(value = "SELECT st.id AS id, st.name AS name FROM sample_type st "
			+ "WHERE st.is_active = TRUE ORDER BY st.name ASC", nativeQuery = true)
	List<Map<String, Object>> findActiveIdAndName();
}
