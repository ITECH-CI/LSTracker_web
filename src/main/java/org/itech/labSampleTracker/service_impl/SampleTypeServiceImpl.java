/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
*/
package org.itech.labSampleTracker.service_impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.itech.labSampleTracker.dao.SampleTypeRepository;
import org.itech.labSampleTracker.entities.SampleType;
import org.itech.labSampleTracker.service.SampleTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * <h2>SampleTypeServiceimpl</h2>
 */
@Service
@Transactional
public class SampleTypeServiceImpl implements SampleTypeService {

	@Autowired
	private SampleTypeRepository sampletypeRepo;
	
	@PersistenceContext
	private EntityManager em;

	@Override
	public SampleType create(SampleType d) {

		SampleType entity;

		try {
			entity = sampletypeRepo.save(d);

		} catch (Exception ex) {
			return null;
		}
		return entity;
	}

	@Override
	public SampleType update(SampleType d) {
		SampleType c;

		try {
			c = sampletypeRepo.saveAndFlush(d);

		} catch (Exception ex) {
			return null;
		}
		return c;
	}

	@Override
	public SampleType getOne(int id) {
		SampleType t;

		try {
			t = sampletypeRepo.findById(id).orElse(null);

		} catch (Exception ex) {
			return null;
		}
		return t;
	}

	@Override
	public List<SampleType> getAll() {
		List<SampleType> lst;

		try {
			lst = sampletypeRepo.findAll();

		} catch (Exception ex) {
			return Collections.emptyList();
		}
		return lst;
	}

	@Override
	public long getTotal() {
		long total;

		try {
			total = sampletypeRepo.count();
		} catch (Exception ex) {
			return 0;
		}
		return total;
	}

	@Override
	public boolean delete(int id) {
		try {
			sampletypeRepo.deleteById(id);
			return true;

		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public SampleType findByName(String name) {
		SampleType t;

		try {
			t = sampletypeRepo.findByName(name);

		} catch (Exception ex) {
			return null;
		}
		return t;
	}

	@Override
	public List<Map<String, Object>> getSampleTypeIdAndName() {
		String sql = "SELECT id,name FROM sample_type ORDER BY name ";
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
		return sampletypeRepo.findForAdmin(pageable, search);
	}

	@Override
	public long countSamples(Integer typeId) {
		if (typeId == null) {
			return 0L;
		}
		try {
			return sampletypeRepo.countSamples(typeId);
		} catch (Exception ex) {
			// En cas de doute on renvoie une valeur non nulle : la garde de
			// suppression doit echouer en securite, pas laisser passer.
			return Long.MAX_VALUE;
		}
	}

	@Override
	public List<Map<String, Object>> getActiveIdAndName() {
		try {
			return sampletypeRepo.findActiveIdAndName();
		} catch (Exception ex) {
			return Collections.emptyList();
		}
	}

}
