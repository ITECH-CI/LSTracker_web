/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
 */
package org.itech.labSampleTracker.service;

import org.itech.labSampleTracker.entities.SampleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * <h2>SampleTypeServiceimpl</h2>
 */
public interface SampleTypeService {
	SampleType create(SampleType d);

	SampleType update(SampleType d);

	SampleType getOne(int id);

	List<SampleType> getAll();

	long getTotal();

	boolean delete(int id);

	SampleType findByName(String name);

	List<Map<String, Object>> getSampleTypeIdAndName();

	/**
	 * Liste paginee pour l'administration, avec recherche plein texte.
	 */
	Page<Map<String, Object>> findForAdmin(Pageable pageable, String searchText);

	/**
	 * Nombre d'echantillons rattaches a ce type (garde de suppression).
	 */
	long countSamples(Integer typeId);

	/**
	 * Types actifs seulement, pour les listes deroulantes de saisie.
	 */
	List<Map<String, Object>> getActiveIdAndName();
}
