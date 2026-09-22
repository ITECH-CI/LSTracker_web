/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
*/
package org.itech.labSampleTracker.service_impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.itech.labSampleTracker.dao.SampleStatusRepository;
import org.itech.labSampleTracker.entities.SampleStatus;
import org.itech.labSampleTracker.service.SampleStatusService;

import java.util.Collections;
import java.util.List;

/**
 * <h2>SampleStatusServiceimpl</h2>
 */
@Service
@Transactional
public class SampleStatusServiceImpl implements SampleStatusService {

	@Autowired
	private SampleStatusRepository samplestatusRepo;

	@Override
	public SampleStatus create(SampleStatus d) {

		SampleStatus entity;

		try {
			entity = samplestatusRepo.save(d);

		} catch (Exception ex) {
			return null;
		}
		return entity;
	}

	@Override
	public SampleStatus update(SampleStatus d) {
		SampleStatus c;

		try {
			c = samplestatusRepo.saveAndFlush(d);

		} catch (Exception ex) {
			return null;
		}
		return c;
	}

	@Override
	public SampleStatus getOne(int id) {
		SampleStatus t;

		try {
			t = samplestatusRepo.findById(id).orElse(null);

		} catch (Exception ex) {
			return null;
		}
		return t;
	}

	@Override
	public List<SampleStatus> getAll() {
		List<SampleStatus> lst;

		try {
			lst = samplestatusRepo.findAll();

		} catch (Exception ex) {
			return Collections.emptyList();
		}
		return lst;
	}

	@Override
	public long getTotal() {
		long total;

		try {
			total = samplestatusRepo.count();
		} catch (Exception ex) {
			return 0;
		}
		return total;
	}

	@Override
	public boolean delete(int id) {
		try {
			samplestatusRepo.deleteById(id);
			return true;

		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public SampleStatus findByStatus(String status) {
		SampleStatus t;

		try {
			t = samplestatusRepo.findByStatus(status);

		} catch (Exception ex) {
			return null;
		}
		return t;
	}

	@Override
	public java.util.List<java.util.Map<String, Object>> findAllForAdmin() {
		try {
			return samplestatusRepo.findAllForAdmin();
		} catch (Exception ex) {
			return java.util.Collections.emptyList();
		}
	}

	@Override
	public SampleStatus updateDescription(int id, String description) {
		SampleStatus entity = samplestatusRepo.findById(id).orElse(null);
		if (entity == null) {
			return null;
		}
		// Seul le libelle est modifiable : le code `status` est la machine
		// d'etat referencee en dur cote web et cote mobile.
		entity.setDescription(description);
		return samplestatusRepo.saveAndFlush(entity);
	}

}
