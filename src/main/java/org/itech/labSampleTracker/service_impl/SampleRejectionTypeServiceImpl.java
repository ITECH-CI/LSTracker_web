/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
*/
package org.itech.labSampleTracker.service_impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.beans.factory.annotation.Autowired;
import org.itech.labSampleTracker.dao.SampleRejectionTypeRepository;
import org.itech.labSampleTracker.entities.SampleRejectionType;
import org.itech.labSampleTracker.service.SampleRejectionTypeService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <h2>SampleRejectionTypeServiceimpl</h2>
 */
@Service
@Transactional
public class SampleRejectionTypeServiceImpl implements SampleRejectionTypeService {

	@Autowired
	private SampleRejectionTypeRepository samplerejectiontypeRepo;

	@PersistenceContext
	private EntityManager em;

	@Override
	public SampleRejectionType create(SampleRejectionType d) {

		SampleRejectionType entity;

		try {
			entity = samplerejectiontypeRepo.save(d);

		} catch (Exception ex) {
			return null;
		}
		return entity;
	}

	@Override
	public SampleRejectionType update(SampleRejectionType d) {
		SampleRejectionType c;

		try {
			c = samplerejectiontypeRepo.saveAndFlush(d);

		} catch (Exception ex) {
			return null;
		}
		return c;
	}

	@Override
	public SampleRejectionType getOne(int id) {
		SampleRejectionType t;

		try {
			t = samplerejectiontypeRepo.findById(id).orElse(null);

		} catch (Exception ex) {
			return null;
		}
		return t;
	}

	@Override
	public List<SampleRejectionType> getAll() {
		List<SampleRejectionType> lst;

		try {
			lst = samplerejectiontypeRepo.findAll();

		} catch (Exception ex) {
			return Collections.emptyList();
		}
		return lst;
	}

	@Override
	public long getTotal() {
		long total;

		try {
			total = samplerejectiontypeRepo.count();
		} catch (Exception ex) {
			return 0;
		}
		return total;
	}

	@Override
	public boolean delete(int id) {
		try {
			samplerejectiontypeRepo.deleteById(id);
			return true;

		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public List<Map<String, Object>> getIdAndNames() {
		String sql = "SELECT id,rejection_type FROM sample_tracker.sample_rejection_type ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("id", o[0]);
				map.put("name", o[1]);
				response.add(map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public Page<Map<String, Object>> findForAdmin(Pageable pageable, String searchText) {
		String search = (searchText == null || searchText.isBlank()) ? null : searchText.trim();
		return samplerejectiontypeRepo.findForAdmin(pageable, search);
	}

	@Override
	public long countRejections(Integer typeId) {
		if (typeId == null) {
			return 0L;
		}
		try {
			return samplerejectiontypeRepo.countRejections(typeId);
		} catch (Exception ex) {
			// Echec en securite : on bloque la suppression en cas de doute.
			return Long.MAX_VALUE;
		}
	}

	@Override
	public List<Map<String, Object>> getActiveIdAndLabel() {
		try {
			return samplerejectiontypeRepo.findActiveIdAndLabel();
		} catch (Exception ex) {
			return Collections.emptyList();
		}
	}

}
