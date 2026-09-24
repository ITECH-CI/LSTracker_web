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

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.dao.RegionRepository;
import org.itech.labSampleTracker.entities.Region;
import org.itech.labSampleTracker.service.RegionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Service
@Transactional
public class RegionServiceImpl implements RegionService {

	@Autowired
	private RegionRepository regionRepo;

	@PersistenceContext
	private EntityManager em;

	@Override
	public Region create(Region d) {
		// On ne catche PAS l'exception ici : être dans une méthode
		// @Transactional + catch silencieux marque la TX comme
		// rollback-only et fait remonter "Transaction silently rolled
		// back" depuis le commit Spring. Le controller a son propre
		// try/catch pour afficher le message à l'utilisateur.
		return regionRepo.save(d);
	}

	@Override
	public Region update(Region d) {
		// Cf. create() : on laisse remonter pour ne pas casser la TX.
		return regionRepo.saveAndFlush(d);
	}

	@Override
	public Region getOne(int id) {
		Region t;
		try {
			t = regionRepo.findById(id).orElse(null);

		} catch (Exception ex) {
			return null;
		}
		return t;
	}

	@Override
	public List<Region> getAll() {
		List<Region> lst;
		try {
			lst = regionRepo.findAll();

		} catch (Exception ex) {
			return Collections.emptyList();
		}
		return lst;
	}

	@Override
	public long getTotal() {
		long total;
		try {
			total = regionRepo.count();
		} catch (Exception ex) {
			return 0;
		}
		return total;
	}

	@Override
	public boolean delete(int id) {
		try {
			regionRepo.deleteById(id);
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public List<Map<String, Object>> getRegionIdAndName() {
		String sql = "SELECT id,name FROM region WHERE is_active ORDER BY name ";
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
	public String resolveRegionName(Integer id) {
		String t = "";

		try {
			Region region = regionRepo.findById(id).orElse(null);
			if (ObjectUtils.isNotEmpty(region))
				t = region.getName();

		} catch (Exception ex) {
			return "";
		}
		return t;
	}

	@Override
	public Page<Map<String, Object>> findRegionsAdvanced(Pageable pageable, String searchText) {
		String search = (searchText == null || searchText.isBlank()) ? null : searchText.trim();
		return regionRepo.findRegionsAdvanced(pageable, search);
	}

	@Override
	public long countDistricts(Integer regionId) {
		if (regionId == null) {
			return 0L;
		}
		try {
			return regionRepo.countDistrictsByRegion(regionId);
		} catch (Exception ex) {
			return 0L;
		}
	}

}
