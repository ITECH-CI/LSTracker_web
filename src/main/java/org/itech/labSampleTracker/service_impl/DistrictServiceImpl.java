/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:03 )
 * @author Pascal
*/
package org.itech.labSampleTracker.service_impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.dao.DistrictRepository;
import org.itech.labSampleTracker.entities.District;
import org.itech.labSampleTracker.entities.Region;
import org.itech.labSampleTracker.service.DistrictService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <h2>DistrictServiceimpl</h2>
 */
@Service
@Transactional
public class DistrictServiceImpl implements DistrictService {

	@Autowired
	private DistrictRepository districtRepo;

	@PersistenceContext
	private EntityManager em;

	@Override
	public District create(District d) {
		// On ne catche PAS l'exception ici : être dans une méthode
		// @Transactional + catch silencieux marque la TX comme
		// rollback-only et fait remonter "Transaction silently rolled
		// back" depuis le commit Spring. Le controller a son propre
		// try/catch pour afficher le message à l'utilisateur.
		return districtRepo.save(d);
	}

	@Override
	public District update(District d) {
		// Cf. create() : on laisse remonter pour ne pas casser la TX.
		return districtRepo.saveAndFlush(d);
	}

	@Override
	public District getOne(int id) {
		District t;

		try {
			t = districtRepo.findById(id).orElse(null);

		} catch (Exception ex) {
			return null;
		}
		return t;
	}

	@Override
	public List<District> getAll() {
		List<District> lst;

		try {
			lst = districtRepo.findAll();

		} catch (Exception ex) {
			return Collections.emptyList();
		}
		return lst;
	}

	@Override
	public long getTotal() {
		long total;

		try {
			total = districtRepo.count();
		} catch (Exception ex) {
			return 0;
		}
		return total;
	}

	@Override
	public boolean delete(int id) {
		try {
			districtRepo.deleteById(id);
			return true;

		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public List<Map<String, Object>> getDistrictIdAndNames() {
		String sql = "SELECT id,name FROM district WHERE is_active ORDER BY name ";
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
	public List<Map<String, Object>> getDistrictIdAndNamesByRegion(Integer regionId) {
		String sql = "SELECT id,name FROM district where region_id = :id AND is_active ORDER BY name ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("id", regionId);
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
	public List<Map<String, Object>> getDistrictIdAndNamesByRegions(List<Integer> regions) {
		String sql = "SELECT id,name FROM district where region_id in :ids AND is_active ORDER BY name ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("ids", regions);
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
	public String resolveDistrictName(Integer id) {
		String t = "";

		try {
			District district = districtRepo.findById(id).orElse(null);
			if (ObjectUtils.isNotEmpty(district))
				t = district.getName();

		} catch (Exception ex) {
			return "";
		}
		return t;
	}

	@Override
	public Page<Map<String, Object>> findDistrictsAdvanced(Pageable pageable, String searchText, Integer regionId) {
		String search = (searchText == null || searchText.isBlank()) ? null : searchText.trim();
		return districtRepo.findDistrictsAdvanced(pageable, search, regionId);
	}

	@Override
	public long countSites(Integer districtId) {
		if (districtId == null) {
			return 0L;
		}
		try {
			return districtRepo.countSitesByDistrict(districtId);
		} catch (Exception ex) {
			return 0L;
		}
	}

}
