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

import org.itech.labSampleTracker.dao.SiteRepository;
import org.itech.labSampleTracker.dao.UserScopeRepository;
import org.itech.labSampleTracker.entities.Site;
import org.itech.labSampleTracker.service.SiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * <h2>SiteServiceimpl</h2>
 */
@Service
@Transactional
public class SiteServiceImpl implements SiteService {

	@Autowired
	private SiteRepository siteRepo;

	@Autowired
	private UserScopeRepository userScopeRepo;

	@PersistenceContext
	private EntityManager em;

	@Override
	public Site create(Site d) {

		Site entity;

		try {
			entity = siteRepo.save(d);

		} catch (Exception ex) {
			return null;
		}
		return entity;
	}

	@Override
	public Site update(Site d) {
		Site c;

		try {
			c = siteRepo.saveAndFlush(d);

		} catch (Exception ex) {
			return null;
		}
		return c;
	}

	@Override
	public Site getOne(int id) {
		Site t;

		try {
			t = siteRepo.findById(id).orElse(null);

		} catch (Exception ex) {
			return null;
		}
		return t;
	}

	@Override
	public List<Site> getAll() {
		List<Site> lst;

		try {
			lst = siteRepo.findAll();

		} catch (Exception ex) {
			return Collections.emptyList();
		}
		return lst;
	}

	@Override
	public long getTotal() {
		long total;

		try {
			total = siteRepo.count();
		} catch (Exception ex) {
			return 0;
		}
		return total;
	}

	@Override
	public boolean delete(int id) {
		try {
			siteRepo.deleteById(id);
			return true;

		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public List<Integer> getSitesIdsByUser(Integer userId) {
		if (userId == null) {
			return java.util.Collections.emptyList();
		}
		return userScopeRepo.findAccessibleSiteIds(userId);
	}

	@Override
	public Map<String, String> getSiteIdAndRegionName() {
		String querySQL = "SELECT f.id,r.name FROM facilitys f JOIN district d ON f.district_id = d.id JOIN region r ON r.id = d.region_id ";
		Map<String, String> response = new HashMap<String, String>();
		try {
			Query query = em.createNativeQuery(querySQL);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				response.put(o[0].toString(), o[1].toString());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public Map<String, String> getSiteIdAndDistrictName() {
		String querySQL = "SELECT f.id,d.name FROM facilitys f JOIN district d ON f.district_id = d.id ";
		Map<String, String> response = new HashMap<String, String>();
		try {
			Query query = em.createNativeQuery(querySQL);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				response.put(o[0].toString(), o[1].toString());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public List<Integer> getSitesIdsByDistricts(List<Integer> districtIds) {
		String querySQL = "SELECT f.id FROM site f WHERE f.district_id in :ids";
		List<Integer> response = new ArrayList<Integer>();
		try {
			Query query = em.createNativeQuery(querySQL);
			query.setParameter("ids", districtIds);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				response.add(Integer.parseInt(o[0].toString()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public Site getOneBySiteCode(String siteCode) {
		return siteRepo.findByDhisCode(siteCode);
	}

	@Override
	public List<Map<String, Object>> getSiteIdAndNames() {
		String sql = "SELECT s.id,s.name,s.dhis_code FROM site s ORDER BY s.name";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("id", o[0]);
				map.put("name", o[1]);
				map.put("site_code", o[2]);
				response.add(map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getSiteIdAndNamesAndCicuit() {
		String sql = "SELECT s.id,s.name,s.dhis_code, c.id FROM site s JOIN circuit_site cs ON s.id=cs.site_id LEFT JOIN circuit c ON c.id = cs.circuit_id ORDER BY s.name";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("id", o[0]);
				map.put("name", o[1]);
				map.put("site_code", o[2]);
				map.put("circuitId", o[3]);
				response.add(map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getSiteIdAndNamesByDistrict(Integer districtId) {
		String sql = "SELECT id,name FROM site where district_id = :id ORDER BY name ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("id", districtId);
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
	public List<Map<String, Object>> getSiteIdAndNamesByDistricts(List<Integer> districtIds) {
		String sql = "SELECT id,name FROM site where district_id in :ids ORDER BY name ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("ids", districtIds);
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
	public List<Map<String, Object>> getSiteIdAndCodeAndNamesByUser(Integer userId) {
		// Sites des axes actifs de l'utilisateur (rattachement actif), ou affectés
		// directement.
		String sql = "SELECT distinct s.id,dhis_code,name FROM site s "
				+ " where exists (select 1 from circuit_site cs join circuit c on c.id = cs.circuit_id "
				+ "   join app_user_has_circuit auhc on auhc.circuit_id = cs.circuit_id "
				+ "   where cs.site_id = s.id and auhc.app_user_id = :userId and c.is_active and cs.is_active) "
				+ " or exists (select 1 from app_user_has_site auhs where auhs.site_id = s.id and auhs.app_user_id = :userId)";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("userId", userId);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("id", o[0]);
				map.put("site_code", o[1]);
				map.put("name", o[2]);
				response.add(map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getSiteIdAndCodeAndNamesAndCircuitByUser(Integer userId) {
		String sql = "SELECT s.id,dhis_code,name,c.id FROM site s join app_user_has_site aus on s.id = aus.site_id "
				+ " JOIN circuit_site cs ON s.id=cs.site_id "
				+ " LEFT JOIN circuit c ON c.id = cs.circuit_id where aus.app_user_id = :userId ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("userId", userId);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("id", o[0]);
				map.put("site_code", o[1]);
				map.put("name", o[2]);
				map.put("circuitId", o[3]);
				response.add(map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

}
