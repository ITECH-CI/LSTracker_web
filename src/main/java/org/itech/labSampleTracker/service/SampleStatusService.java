/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
 */
package org.itech.labSampleTracker.service;

import org.itech.labSampleTracker.entities.SampleStatus;

import java.util.List;
import java.util.Map;

/**
 * <h2>SampleStatusServiceimpl</h2>
 */
public interface SampleStatusService {
	SampleStatus create(SampleStatus d);

	SampleStatus update(SampleStatus d);

	SampleStatus getOne(int id);

	List<SampleStatus> getAll();

	long getTotal();

	boolean delete(int id);

	SampleStatus findByStatus(String status);

	/**
	 * Liste complete pour la page d'administration (consultation seule).
	 * Voir {@code SampleStatusRepository#findAllForAdmin()} : les codes de
	 * statut ne sont pas modifiables depuis l'interface.
	 */
	List<Map<String, Object>> findAllForAdmin();

	/**
	 * Met a jour le seul libelle affiche d'un statut. Le code metier
	 * ({@code status}) reste inchange.
	 *
	 * @return le statut mis a jour, ou {@code null} s'il n'existe pas.
	 */
	SampleStatus updateDescription(int id, String description);
}
