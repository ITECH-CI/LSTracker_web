/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:03 )
 * @author Pascal
*/
package org.itech.labSampleTracker.service_impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.dao.LabRepository;
import org.itech.labSampleTracker.entities.Lab;
import org.itech.labSampleTracker.service.LabService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * <h2>LabServiceimpl</h2>
 */
@Service
@Transactional
public class LabServiceImpl implements LabService {

	@Autowired
	private LabRepository labRepo;

	@PersistenceContext
	private EntityManager em;

	@Override
	public Lab create(Lab d) {

		Lab entity;

		try {
			entity = labRepo.save(d);

		} catch (Exception ex) {
			return null;
		}
		return entity;
	}

	@Override
	public Lab update(Lab d) {
		Lab c;

		try {
			c = labRepo.saveAndFlush(d);

		} catch (Exception ex) {
			return null;
		}
		return c;
	}

	@Override
	public Lab getOne(int id) {
		Lab t;

		try {
			t = labRepo.findById(id).orElse(null);

		} catch (Exception ex) {
			return null;
		}
		return t;
	}

	@Override
	public List<Lab> getAll() {
		List<Lab> lst;

		try {
			lst = labRepo.findAll();

		} catch (Exception ex) {
			return Collections.emptyList();
		}
		return lst;
	}

	@Override
	public long getTotal() {
		long total;

		try {
			total = labRepo.count();
		} catch (Exception ex) {
			return 0;
		}
		return total;
	}

	@Override
	public boolean delete(int id) {
		try {
			labRepo.deleteById(id);
			return true;

		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public List<Integer> getLabsIdsByUser(Integer userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Integer> getLabsIdsByDistricts(List<Integer> districtIds) {
		String sql = "SELECT id FROM lab where district_id in :ids ";
		List<Integer> response = new ArrayList<Integer>();
		try {
			Query query = em.createNativeQuery(sql);
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
	public List<Map<String, Object>> getLabIdAndNames() {
		String sql = "SELECT id,lab_name,lab_type FROM lab ORDER BY lab_type,lab_name ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("id", o[0]);
				map.put("name", o[1]);
				map.put("labType", o[2]);
				response.add(map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getLabIdAndNamesByDistricts(List<Integer> districtIds) {
		String sql = "SELECT id,lab_name FROM lab where district_id in :ids ORDER BY lab_name ";
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
	public List<Map<String, Object>> getLabIdAndNamesByDistrict(Integer districtId) {
		String sql = "SELECT id,lab_name FROM lab where district_id = :id ORDER BY lab_name ";
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
	public List<Map<String, Object>> getReferenceLabIdAndNames() {
		String sql = "SELECT id,lab_name FROM lab where lab_type = :type ORDER BY lab_name ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("type", "PLATEFORME");
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
	public List<Map<String, Object>> getHubIdAndNames() {
		String sql = "SELECT id,lab_name FROM lab where lab_type = :type ORDER BY lab_name ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("type", "RELAIS");
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
	public List<Map<String, Object>> getAllLabIdAndNamesByRider(Integer userId) {
		// Règle métier : un convoyeur a accès aux laboratoires situés dans les
		// districts où il intervient (circuit -> site -> district -> labo).
		// Seuls les circuits, affectations circuit/site et labos actifs comptent.
		String sql = "select distinct lab.id,lab.lab_name,lab.lab_type from lab \n"
				+ "join site s on s.district_id = lab.district_id join circuit_site cs on cs.site_id = s.id \n"
				+ "join circuit c on c.id = cs.circuit_id \n"
				+ "join app_user_has_circuit auhc on auhc.circuit_id = cs.circuit_id \n"
				+ "where auhc.app_user_id =:userId and lab.is_active and cs.is_active and c.is_active \n"
				+ "ORDER BY lab_type,lab_name ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("userId", userId);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("id", o[0]);
				map.put("name", o[1]);
				map.put("labType", o[2]);
				response.add(map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getAllLabIdAndNamesByLabUser(Integer userId) {
		String sql = "select lab.id,lab.lab_name,lab.lab_type from lab \n"
				+ "join app_user_has_lab auhl on auhl.lab_id = lab.id\n"
				+ "where auhl.app_user_id =:userId ORDER BY lab_type,lab_name	";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("userId", userId);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("id", o[0]);
				map.put("name", o[1]);
				map.put("labType", o[2]);
				response.add(map);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	@Override
	public String resolveLabName(Integer id) {
		String t = "";

		try {
			Lab lab = labRepo.findById(id).orElse(null);
			if (ObjectUtils.isNotEmpty(lab))
				t = lab.getLabName();

		} catch (Exception ex) {
			return null;
		}
		return t;
	}

}
