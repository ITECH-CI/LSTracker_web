/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
 */
package org.itech.labSampleTracker.service;

import org.itech.labSampleTracker.entities.SampleRejectionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * <h2>SampleRejectionTypeServiceimpl</h2>
 */
public interface SampleRejectionTypeService {
	SampleRejectionType create(SampleRejectionType d);

	SampleRejectionType update(SampleRejectionType d);

	SampleRejectionType getOne(int id);

	List<SampleRejectionType> getAll();

	long getTotal();

	boolean delete(int id);

	List<Map<String, Object>> getIdAndNames();

	/**
	 * Liste paginee pour l'administration, avec recherche plein texte.
	 */
	Page<Map<String, Object>> findForAdmin(Pageable pageable, String searchText);

	/**
	 * Nombre de rejets rattaches a ce motif (garde de suppression).
	 */
	long countRejections(Integer typeId);

	/**
	 * Motifs actifs seulement, pour les listes deroulantes de saisie.
	 */
	List<Map<String, Object>> getActiveIdAndLabel();
}
