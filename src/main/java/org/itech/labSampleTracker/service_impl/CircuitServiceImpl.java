/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:03 )
 * @author Pascal
*/
package org.itech.labSampleTracker.service_impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.itech.labSampleTracker.dao.CircuitRepository;
import org.itech.labSampleTracker.entities.Circuit;
import org.itech.labSampleTracker.service.CircuitService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <h2>CircuitServiceimpl</h2>
 */
@Slf4j
@Service
@Transactional
public class CircuitServiceImpl implements CircuitService {

	@Autowired
	private CircuitRepository circuitRepo;

	@PersistenceContext
	private EntityManager em;

	@Override
	public Circuit create(Circuit d) {

		Circuit entity;

		try {
			entity = circuitRepo.save(d);

		} catch (Exception ex) {
			return null;
		}
		return entity;
	}

	@Override
	public Circuit update(Circuit d) {
		Circuit c;

		try {
			c = circuitRepo.saveAndFlush(d);

		} catch (Exception ex) {
			return null;
		}
		return c;
	}

	@Override
	public Circuit getOne(int id) {
		Circuit t;

		try {
			t = circuitRepo.findById(id).orElse(null);

		} catch (Exception ex) {
			return null;
		}
		return t;
	}

	@Override
	public List<Circuit> getAll() {
		List<Circuit> lst;

		try {
			lst = circuitRepo.findAll();

		} catch (Exception ex) {
			return Collections.emptyList();
		}
		return lst;
	}

	@Override
	public long getTotal() {
		long total;

		try {
			total = circuitRepo.count();
		} catch (Exception ex) {
			return 0;
		}
		return total;
	}

	@Override
	public boolean delete(int id) {
		try {
			circuitRepo.deleteById(id);
			return true;

		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public List<Map<String, Object>> getCircuitIdAndNumber() {
		String sql = "SELECT id,circuit_number FROM circuit ORDER BY circuit_number ";
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
	public List<Map<String, Object>> getCircuitSitesIdAndName(Integer circuit) {
		String sql = "SELECT s.id,s.name,s.dhis_code, c.id FROM circuit c JOIN circuit_site cs ON c.id=cs.circuit_id JOIN site s ON s.id = cs.site_id ORDER BY s.name ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("id", o[0]);
				map.put("name", o[1]);
				map.put("dhisCode", o[2]);
				map.put("circuitId", o[3]);
				response.add(map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getAllCircuitsDetails() {
		String sql = "select c.id circuit_id ,c.circuit_number circuit_number "
				+ ",string_agg(distinct au.login,',') rider ,string_agg(distinct s.name, ',') sites "
				+ "from circuit c left join circuit_site cs on c.id = cs.circuit_id "
				+ "left join site s on s.id = cs.site_id "
				+ "left join app_user_has_circuit auhc on auhc.circuit_id = c.id "
				+ "left join app_user au on au.id = auhc.app_user_id group by c.id,c.circuit_number ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("id", o[0]);
				map.put("circuitNumber", o[1]);
				map.put("rider", o[2]);
				map.put("sites", o[3]);
				response.add(map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public Circuit getCircuitByNumber(String circuitNumber) {
		Circuit c;
		try {
			c = circuitRepo.findByCircuitNumber(circuitNumber);
		}catch (Exception e) {
			log.error(e.getMessage());
			return null;
		}
		return c;
	}

	@Override
	public List<Map<String, Object>> getCircuitIdAndNumberByUser(Integer userId) {
		// Axes et rattachements actifs uniquement (métadonnées mobile, listes).
		String sql = "select distinct c.id circuit_id ,c.circuit_number circuit_number "
				+ "from circuit c join circuit_site cs on c.id = cs.circuit_id "
				+ "join app_user_has_circuit auhc on auhc.circuit_id = c.id  where auhc.app_user_id = :userId "
				+ "and c.is_active and cs.is_active";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("userId", userId);
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

}
