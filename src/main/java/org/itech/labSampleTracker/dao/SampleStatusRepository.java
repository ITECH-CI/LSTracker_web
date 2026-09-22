/*
 * Java domain class for entity "SampleStatus" 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
 */
package org.itech.labSampleTracker.dao;

import java.util.List;
import java.util.Map;

import org.itech.labSampleTracker.entities.SampleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * <h2>SampleStatusRepository</h2>
 *
 * createdAt : 2024-03-31 - Time 19:08:04
 * <p>
 * Description: "SampleStatus" Repository
 */

public interface SampleStatusRepository
		extends JpaRepository<SampleStatus, Integer>, JpaSpecificationExecutor<SampleStatus> {
	public SampleStatus findByStatus(String status);

	/**
	 * Liste complete pour la page d'administration (consultation).
	 *
	 * Les codes {@code status} sont la machine d'etat du suivi : ils sont
	 * references en dur dans les tableaux de bord, les rapports et
	 * l'application mobile. La page d'administration n'autorise donc que la
	 * modification du libelle ({@code description}) ; le code n'est ni
	 * creable, ni modifiable, ni supprimable depuis l'interface.
	 */
	@Query(value = "SELECT ss.id AS id, ss.status AS status, ss.description AS description, "
			+ "ss.created_at AS created_at, "
			+ "(SELECT COUNT(*) FROM sample s WHERE s.sample_status_id = ss.id) AS sample_count "
			+ "FROM sample_status ss ORDER BY ss.id ASC", nativeQuery = true)
	List<Map<String, Object>> findAllForAdmin();
}
