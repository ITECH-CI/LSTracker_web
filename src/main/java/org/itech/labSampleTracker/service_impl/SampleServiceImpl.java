/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
*/
package org.itech.labSampleTracker.service_impl;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.dao.SampleRepository;
import org.itech.labSampleTracker.entities.Sample;
import org.itech.labSampleTracker.enums.ESampleStatus;
import org.itech.labSampleTracker.service.SampleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * <h2>SampleServiceimpl</h2>
 */
@Service
@Transactional
public class SampleServiceImpl implements SampleService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SampleServiceImpl.class);

	@Autowired
	private SampleRepository sampleRepo;

	@PersistenceContext
	private EntityManager em;

	@Override
	public Sample create(Sample d) {

		Sample entity;

		try {
			entity = sampleRepo.save(d);

		} catch (Exception ex) {
			log.error("create failed (sample={}): {}", d, ex.getMessage(), ex);
			return null;
		}
		return entity;
	}

	@Override
	public Sample update(Sample d) {
		Sample c;

		try {
			c = sampleRepo.saveAndFlush(d);

		} catch (Exception ex) {
			log.error("update failed (sample={}): {}", d, ex.getMessage(), ex);
			return null;
		}
		return c;
	}

	@Override
	public Sample getOne(int id) {
		Sample t;

		try {
			t = sampleRepo.findById(id).orElse(null);

		} catch (Exception ex) {
			log.error("getOne failed (id={}): {}", id, ex.getMessage(), ex);
			return null;
		}
		return t;
	}

	@Override
	public List<Sample> getAll() {
		List<Sample> lst;

		try {
			lst = sampleRepo.findAll();

		} catch (Exception ex) {
			log.error("getAll failed: {}", ex.getMessage(), ex);
			return Collections.emptyList();
		}
		return lst;
	}

	@Override
	public long getTotal() {
		long total;

		try {
			total = sampleRepo.count();
		} catch (Exception ex) {
			log.error("getTotal failed: {}", ex.getMessage(), ex);
			return 0;
		}
		return total;
	}

	@Override
	public boolean delete(int id) {
		try {
			sampleRepo.deleteById(id);
			return true;

		} catch (Exception ex) {
			log.error("delete failed (id={}): {}", id, ex.getMessage(), ex);
			return false;
		}
	}

	@Override
	public Sample findBySampleIdentifier(String identifier) {
		Sample t;

		try {
			t = sampleRepo.findBySampleIdentifier(identifier);

		} catch (Exception ex) {
			log.error("findBySampleIdentifier failed (identifier={}): {}", identifier, ex.getMessage(), ex);
			return null;
		}
		return t;
	}

	@Override
	public List<Map<String, Object>> getSampleInTransitForUser(Integer userId) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		String sql = "select auhc.app_user_id, s.id,c.id ,sr.id,sr2.id,sr2.comment,  "
				+ "sr.site_id, s.hub_id, s.reference_lab_id ,s.patient_identifier, s.sample_identifier,  "
				+ "c.circuit_number , s.collection_date , s.deliver_at_hub_date, s.deliver_at_lab_date , "
				+ "s.accepted_at_hub_date , s.accepted_at_lab_date , st.name ,s.lab_number , "
				+ "s.result_collection_date , s.result_delivery_date , ss2.status, s.destination_lab_id,"
				+ "s.requester_site_name,s.destination_lab_name,s.analysis_completed_date, "
				+ "s.analysis_released_date, s.result_reported_date, s.collection_start_mileage, s.collection_end_mileage,"
				+ " s.result_start_mileage,s.result_end_mileage from sample s  "
				+ "join sample_type st on st.id =s.sample_type_id  "
				+ "join sample_status ss2 on ss2.id = s.sample_status_id  "
				+ "left join sample_rejection sr2 on sr2.sample_id = s.id "
				+ "join sample_retrieving sr on s.sample_retrieving_id  = sr.id  "
				+ "join circuit_site cs on cs.site_id  = sr.site_id join circuit c on c.id = cs.circuit_id  "
				+ "join app_user_has_circuit auhc on auhc.circuit_id  = cs.circuit_id   "
				+ "where ss2.status = :status and auhc.app_user_id = :userId order by s.collection_date DESC";

		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("userId", userId);
			query.setParameter("status", ESampleStatus.ON_TRANSIT.name());
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("userId", o[0]);
				map.put("sampleId", o[1]);
				map.put("circuitId", o[2]);
				map.put("sampleRetrievingId", o[3]);
				map.put("rejectionTypeId", o[4]);
				map.put("rejectionComment", o[5]);
				map.put("siteId", o[6]);
				map.put("hubId", o[7]);
				map.put("labId", o[8]);
				map.put("patientIdentifier", o[9]);
				map.put("sampleIdentifier", o[10]);
				map.put("circuitNumber", o[11]);
				map.put("collectionDate", dateFormat.format(o[12]));
				map.put("deliveredAtHubDate", ObjectUtils.isNotEmpty(o[13]) ? dateFormat.format(o[13]) : null);
				map.put("deliveredAtReferenceLabDate", ObjectUtils.isNotEmpty(o[14]) ? dateFormat.format(o[14]) : null);
				map.put("acceptedAtHubDate", ObjectUtils.isNotEmpty(o[15]) ? dateFormat.format(o[15]) : null);
				map.put("acceptedAtReferenceLabDate", ObjectUtils.isNotEmpty(o[16]) ? dateFormat.format(o[16]) : null);
				map.put("sampleType", o[17]);
				map.put("labNumber", o[18]);
				map.put("resultCollectionDate", ObjectUtils.isNotEmpty(o[19]) ? dateFormat.format(o[19]) : null);
				map.put("resultDeliveryDate", ObjectUtils.isNotEmpty(o[20]) ? dateFormat.format(o[20]) : null);
				map.put("status", o[21]);
				map.put("destinationLabId", o[22]);
				map.put("requesterSiteName", o[23]);
				map.put("destinationLabName", o[24]);
				map.put("analysisCompletedDate", ObjectUtils.isNotEmpty(o[25]) ? dateFormat.format(o[25]) : null);
				map.put("analysisReleasedDate", ObjectUtils.isNotEmpty(o[26]) ? dateFormat.format(o[26]) : null);
				map.put("analysisResultReportedDate", ObjectUtils.isNotEmpty(o[27]) ? dateFormat.format(o[27]) : null);
				map.put("collectionStartMileage", o[28]);
				map.put("collectionEndMileage", o[29]);
				map.put("resultStartMileage", o[30]);
				map.put("resultEndMileage", o[31]);

				response.add(map);
			}
		} catch (Exception ex) {
			log.error("getSampleInTransitForUser failed (userId={}): {}", userId, ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getSampleAtHubForAcceptance(Integer hubId) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		String sql = "select auhs.app_user_id, s.id,cs.circuit_id ,sr.id,sr2.id,sr2.comment,  "
				+ "auhs.site_id, s.hub_id, s.reference_lab_id ,s.patient_identifier, s.sample_identifier,  "
				+ "c.circuit_number , s.collection_date , s.deliver_at_hub_date, s.deliver_at_lab_date , "
				+ "s.accepted_at_hub_date , s.accepted_at_lab_date , st.name ,s.lab_number , "
				+ "s.result_collection_date , s.result_delivery_date , ss2.status, s.destination_lab_id,"
				+ "s.requester_site_name,s.destination_lab_name,s.analysis_completed_date, "
				+ "s.analysis_released_date, s.result_reported_date , s.collection_start_mileage, s.collection_end_mileage,"
				+ "  s.result_start_mileage,s.result_end_mileage  from sample s  "
				+ "join sample_type st on st.id =s.sample_type_id  "
				+ "join sample_status ss2 on ss2.id = s.sample_status_id  "
				+ "left join sample_rejection sr2 on sr2.sample_id = s.id "
				+ "join sample_retrieving sr on s.sample_retrieving_id  = sr.id  "
				+ "join app_user_has_site auhs on auhs.app_user_id = sr.app_user_id  "
				+ "join circuit_site cs on cs.site_id = auhs.site_id  join circuit c on c.id = cs.circuit_id  "
				+ "where ss2.status = :status and s.hub_id = :hubId order by s.collection_date DESC";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("hubId", hubId);
			query.setParameter("status", ESampleStatus.RECEIVED_AT_HUB.name());
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("userId", o[0]);
				map.put("sampleId", o[1]);
				map.put("circuitId", o[2]);
				map.put("sampleRetrievingId", o[3]);
				map.put("rejectionTypeId", o[4]);
				map.put("rejectionComment", o[5]);
				map.put("siteId", o[6]);
				map.put("hubId", o[7]);
				map.put("labId", o[8]);
				map.put("patientIdentifier", o[9]);
				map.put("sampleIdentifier", o[10]);
				map.put("circuitNumber", o[11]);
				map.put("collectionDate", ObjectUtils.isNotEmpty(o[12]) ? dateFormat.format(o[12]) : null);
				map.put("deliveredAtHubDate", ObjectUtils.isNotEmpty(o[13]) ? dateFormat.format(o[13]) : null);
				map.put("deliveredAtReferenceLabDate", ObjectUtils.isNotEmpty(o[14]) ? dateFormat.format(o[14]) : null);
				map.put("acceptedAtHubDate", ObjectUtils.isNotEmpty(o[15]) ? dateFormat.format(o[15]) : null);
				map.put("acceptedAtReferenceLabDate", ObjectUtils.isNotEmpty(o[16]) ? dateFormat.format(o[16]) : null);
				map.put("sampleType", o[17]);
				map.put("labNumber", o[18]);
				map.put("resultCollectionDate", ObjectUtils.isNotEmpty(o[19]) ? dateFormat.format(o[19]) : null);
				map.put("resultDeliveryDate", ObjectUtils.isNotEmpty(o[20]) ? dateFormat.format(o[20]) : null);
				map.put("status", o[21]);
				map.put("destinationLabId", o[22]);
				map.put("requesterSiteName", o[23]);
				map.put("destinationLabName", o[24]);
				map.put("analysisCompletedDate", ObjectUtils.isNotEmpty(o[25]) ? dateFormat.format(o[25]) : null);
				map.put("analysisReleasedDate", ObjectUtils.isNotEmpty(o[26]) ? dateFormat.format(o[26]) : null);
				map.put("analysisResultReportedDate", ObjectUtils.isNotEmpty(o[27]) ? dateFormat.format(o[27]) : null);
				map.put("collectionStartMileage", o[28]);
				map.put("collectionEndMileage", o[29]);
				map.put("resultStartMileage", o[30]);
				map.put("resultEndMileage", o[31]);
				response.add(map);
			}
		} catch (Exception ex) {
			log.error("getSampleAtHubForAcceptance failed (hubId={}): {}", hubId, ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getAcceptedSampleAtHub(Integer hubId) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		String sql = "select sr.app_user_id, s.id,cs.circuit_id ,sr.id,sr2.id,sr2.comment,  "
				+ "sr.site_id, s.hub_id, s.reference_lab_id ,s.patient_identifier, s.sample_identifier,  "
				+ "c.circuit_number ,s.collection_date , s.deliver_at_hub_date, s.deliver_at_lab_date , "
				+ "s.accepted_at_hub_date , s.accepted_at_lab_date , st.name ,s.lab_number , "
				+ "s.result_collection_date , s.result_delivery_date , ss2.status , s.destination_lab_id,"
				+ "s.requester_site_name,s.destination_lab_name,s.analysis_completed_date, "
				+ "s.analysis_released_date, s.result_reported_date , s.collection_start_mileage, s.collection_end_mileage,"
				+ "  s.result_start_mileage,s.result_end_mileage  from sample s  "
				+ "join sample_type st on st.id =s.sample_type_id  "
				+ "join sample_status ss2 on ss2.id = s.sample_status_id "
				+ "left join sample_rejection sr2 on sr2.sample_id = s.id "
				+ "join sample_retrieving sr on s.sample_retrieving_id  = sr.id  "
				+ "join circuit_site cs on cs.site_id = sr.site_id join circuit c on c.id = cs.circuit_id  "
				+ "where ss2.status = :status and s.reference_lab_id = :labId order by s.collection_date DESC";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("hubId", hubId);
			query.setParameter("status", ESampleStatus.ACCEPTED_AT_HUB.name());
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("userId", o[0]);
				map.put("sampleId", o[1]);
				map.put("circuitId", o[2]);
				map.put("sampleRetrievingId", o[3]);
				map.put("rejectionTypeId", o[4]);
				map.put("rejectionComment", o[5]);
				map.put("siteId", o[6]);
				map.put("hubId", o[7]);
				map.put("labId", o[8]);
				map.put("patientIdentifier", o[9]);
				map.put("sampleIdentifier", o[10]);
				map.put("circuitNumber", o[11]);
				map.put("collectionDate", ObjectUtils.isNotEmpty(o[12]) ? dateFormat.format(o[12]) : null);
				map.put("deliveredAtHubDate", ObjectUtils.isNotEmpty(o[13]) ? dateFormat.format(o[13]) : null);
				map.put("deliveredAtReferenceLabDate", ObjectUtils.isNotEmpty(o[14]) ? dateFormat.format(o[14]) : null);
				map.put("acceptedAtHubDate", ObjectUtils.isNotEmpty(o[15]) ? dateFormat.format(o[15]) : null);
				map.put("acceptedAtReferenceLabDate", ObjectUtils.isNotEmpty(o[16]) ? dateFormat.format(o[16]) : null);
				map.put("sampleType", o[17]);
				map.put("labNumber", o[18]);
				map.put("resultCollectionDate", ObjectUtils.isNotEmpty(o[19]) ? dateFormat.format(o[19]) : null);
				map.put("resultDeliveryDate", ObjectUtils.isNotEmpty(o[20]) ? dateFormat.format(o[20]) : null);
				map.put("status", o[21]);
				map.put("destinationLabId", o[22]);
				map.put("requesterSiteName", o[23]);
				map.put("destinationLabName", o[24]);
				map.put("analysisCompletedDate", ObjectUtils.isNotEmpty(o[25]) ? dateFormat.format(o[25]) : null);
				map.put("analysisReleasedDate", ObjectUtils.isNotEmpty(o[26]) ? dateFormat.format(o[26]) : null);
				map.put("analysisResultReportedDate", ObjectUtils.isNotEmpty(o[27]) ? dateFormat.format(o[27]) : null);
				map.put("collectionStartMileage", o[28]);
				map.put("collectionEndMileage", o[29]);
				map.put("resultStartMileage", o[30]);
				map.put("resultEndMileage", o[31]);
				response.add(map);
			}
		} catch (Exception ex) {
			log.error("getAcceptedSampleAtHub failed (hubId={}): {}", hubId, ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getSampleAtLabForAcceptance(Integer labId, String status) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		String sql = "select sr.app_user_id, s.id,cs.circuit_id ,sr.id,sr2.id,sr2.comment,  "
				+ "sr.site_id, s.hub_id, s.reference_lab_id ,s.patient_identifier, s.sample_identifier,  "
				+ "c.circuit_number ,s.collection_date , s.deliver_at_hub_date, s.deliver_at_lab_date , "
				+ "s.accepted_at_hub_date , s.accepted_at_lab_date , st.name ,s.lab_number , "
				+ "s.result_collection_date , s.result_delivery_date , ss2.status , s.destination_lab_id,"
				+ "s.requester_site_name,s.destination_lab_name,s.analysis_completed_date, "
				+ "s.analysis_released_date, s.result_reported_date , s.collection_start_mileage, s.collection_end_mileage, "
				+ " s.result_start_mileage,s.result_end_mileage  from sample s  "
				+ "join sample_type st on st.id =s.sample_type_id  "
				+ "join sample_status ss2 on ss2.id = s.sample_status_id "
				+ "left join sample_rejection sr2 on sr2.sample_id = s.id "
				+ "join sample_retrieving sr on s.sample_retrieving_id  = sr.id  "
				+ "join circuit_site cs on cs.site_id = sr.site_id join circuit c on c.id = cs.circuit_id  "
				+ "where ss2.status = :status and s.reference_lab_id = :labId order by s.collection_date DESC";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("labId", labId);
			query.setParameter("status", status);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("userId", o[0]);
				map.put("sampleId", o[1]);
				map.put("circuitId", o[2]);
				map.put("sampleRetrievingId", o[3]);
				map.put("rejectionTypeId", o[4]);
				map.put("rejectionComment", o[5]);
				map.put("siteId", o[6]);
				map.put("hubId", o[7]);
				map.put("labId", o[8]);
				map.put("patientIdentifier", o[9]);
				map.put("sampleIdentifier", o[10]);
				map.put("circuitNumber", o[11]);
				map.put("collectionDate", ObjectUtils.isNotEmpty(o[12]) ? dateFormat.format(o[12]) : null);
				map.put("deliveredAtHubDate", ObjectUtils.isNotEmpty(o[13]) ? dateFormat.format(o[13]) : null);
				map.put("deliveredAtReferenceLabDate", ObjectUtils.isNotEmpty(o[14]) ? dateFormat.format(o[14]) : null);
				map.put("acceptedAtHubDate", ObjectUtils.isNotEmpty(o[15]) ? dateFormat.format(o[15]) : null);
				map.put("acceptedAtReferenceLabDate", ObjectUtils.isNotEmpty(o[16]) ? dateFormat.format(o[16]) : null);
				map.put("sampleType", o[17]);
				map.put("labNumber", o[18]);
				map.put("resultCollectionDate", ObjectUtils.isNotEmpty(o[19]) ? dateFormat.format(o[19]) : null);
				map.put("resultDeliveryDate", ObjectUtils.isNotEmpty(o[20]) ? dateFormat.format(o[20]) : null);
				map.put("status", o[21]);
				map.put("destinationLabId", o[22]);
				map.put("requesterSiteName", o[23]);
				map.put("destinationLabName", o[24]);
				map.put("analysisCompletedDate", ObjectUtils.isNotEmpty(o[25]) ? dateFormat.format(o[25]) : null);
				map.put("analysisReleasedDate", ObjectUtils.isNotEmpty(o[26]) ? dateFormat.format(o[26]) : null);
				map.put("analysisResultReportedDate", ObjectUtils.isNotEmpty(o[27]) ? dateFormat.format(o[27]) : null);
				map.put("collectionStartMileage", o[28]);
				map.put("collectionEndMileage", o[29]);
				map.put("resultStartMileage", o[30]);
				map.put("resultEndMileage", o[31]);
				response.add(map);
			}
		} catch (Exception ex) {
			log.error("getSampleAtLabForAcceptance failed (labId={}, status={}): {}", labId, status, ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getAcceptedSampleAtLab(Integer labId, String status) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		String sql = "select sr.app_user_id, s.id,cs.circuit_id ,sr.id,sr2.id,sr2.comment,  "
				+ "sr.site_id, s.hub_id, s.reference_lab_id ,s.patient_identifier, s.sample_identifier,  "
				+ "c.circuit_number ,s.collection_date , s.deliver_at_hub_date, s.deliver_at_lab_date , "
				+ "s.accepted_at_hub_date , s.accepted_at_lab_date , st.name ,s.lab_number , "
				+ "s.result_collection_date , s.result_delivery_date , ss2.status , s.destination_lab_id,"
				+ "s.requester_site_name,s.destination_lab_name,s.analysis_completed_date, "
				+ "s.analysis_released_date, s.result_reported_date , s.collection_start_mileage, s.collection_end_mileage,"
				+ "  s.result_start_mileage,s.result_end_mileage  from sample s  "
				+ "join sample_type st on st.id =s.sample_type_id  "
				+ "join sample_status ss2 on ss2.id = s.sample_status_id "
				+ "left join sample_rejection sr2 on sr2.sample_id = s.id "
				+ "join sample_retrieving sr on s.sample_retrieving_id  = sr.id  "
				+ "join circuit_site cs on cs.site_id = sr.site_id join circuit c on c.id = cs.circuit_id  "
				+ "where ss2.status = :status and s.reference_lab_id = :labId order by s.collection_date DESC";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("labId", labId);
			query.setParameter("status", status);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("userId", o[0]);
				map.put("sampleId", o[1]);
				map.put("circuitId", o[2]);
				map.put("sampleRetrievingId", o[3]);
				map.put("rejectionTypeId", o[4]);
				map.put("rejectionComment", o[5]);
				map.put("siteId", o[6]);
				map.put("hubId", o[7]);
				map.put("labId", o[8]);
				map.put("patientIdentifier", o[9]);
				map.put("sampleIdentifier", o[10]);
				map.put("collectionDate", ObjectUtils.isNotEmpty(o[12]) ? dateFormat.format(o[12]) : null);
				map.put("deliveredAtHubDate", ObjectUtils.isNotEmpty(o[13]) ? dateFormat.format(o[13]) : null);
				map.put("deliveredAtReferenceLabDate", ObjectUtils.isNotEmpty(o[14]) ? dateFormat.format(o[14]) : null);
				map.put("acceptedAtHubDate", ObjectUtils.isNotEmpty(o[15]) ? dateFormat.format(o[15]) : null);
				map.put("acceptedAtReferenceLabDate", ObjectUtils.isNotEmpty(o[16]) ? dateFormat.format(o[16]) : null);
				map.put("sampleType", o[17]);
				map.put("labNumber", o[18]);
				map.put("resultCollectionDate", ObjectUtils.isNotEmpty(o[19]) ? dateFormat.format(o[19]) : null);
				map.put("resultDeliveryDate", ObjectUtils.isNotEmpty(o[20]) ? dateFormat.format(o[20]) : null);
				map.put("status", o[21]);
				map.put("destinationLabId", o[22]);
				map.put("requesterSiteName", o[23]);
				map.put("destinationLabName", o[24]);
				map.put("analysisCompletedDate", ObjectUtils.isNotEmpty(o[25]) ? dateFormat.format(o[25]) : null);
				map.put("analysisReleasedDate", ObjectUtils.isNotEmpty(o[26]) ? dateFormat.format(o[26]) : null);
				map.put("analysisResultReportedDate", ObjectUtils.isNotEmpty(o[27]) ? dateFormat.format(o[27]) : null);
				map.put("collectionStartMileage", o[28]);
				map.put("collectionEndMileage", o[29]);
				map.put("resultStartMileage", o[30]);
				map.put("resultEndMileage", o[31]);
				response.add(map);
			}
		} catch (Exception ex) {
			log.error("getAcceptedSampleAtLab failed (labId={}, status={}): {}", labId, status, ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getAnalysisDoneAtLab(Integer labId) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		String sql = "select sr.app_user_id, s.id,cs.circuit_id ,sr.id,sr2.id,sr2.comment,  "
				+ "sr.site_id, s.hub_id, s.reference_lab_id ,s.patient_identifier, s.sample_identifier,  "
				+ "c.circuit_number , s.collection_date , s.deliver_at_hub_date, s.deliver_at_lab_date , "
				+ "s.accepted_at_hub_date , s.accepted_at_lab_date , st.name ,s.lab_number , "
				+ "s.result_collection_date , s.result_delivery_date , ss2.status, s.destination_lab_id, "
				+ " s.requester_site_name, s.destination_lab_name,s.analysis_completed_date,  "
				+ "	s.analysis_released_date, s.result_reported_date , s.collection_start_mileage, s.collection_end_mileage, "
				+ " s.result_start_mileage,s.result_end_mileage  from sample s  "
				+ "join sample_type st on st.id =s.sample_type_id  "
				+ "join sample_status ss2 on ss2.id = s.sample_status_id  "
				+ "left join sample_rejection sr2 on sr2.sample_id = s.id "
				+ "join sample_retrieving sr on s.sample_retrieving_id  = sr.id  "
				+ "join circuit_site cs on cs.site_id = sr.site_id  join circuit c on c.id = cs.circuit_id  "
				+ "where ss2.status = :status and s.reference_lab_id = :labId order by s.collection_date desc";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("labId", labId);
			query.setParameter("status", ESampleStatus.ANALYSIS_DONE.name());
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("userId", o[0]);
				map.put("sampleId", o[1]);
				map.put("circuitId", o[2]);
				map.put("sampleRetrievingId", o[3]);
				map.put("rejectionTypeId", o[4]);
				map.put("rejectionComment", o[5]);
				map.put("siteId", o[6]);
				map.put("hubId", o[7]);
				map.put("labId", o[8]);
				map.put("patientIdentifier", o[9]);
				map.put("sampleIdentifier", o[10]);
				map.put("circuitNumber", o[11]);
				map.put("collectionDate", ObjectUtils.isNotEmpty(o[12]) ? dateFormat.format(o[12]) : null);
				map.put("deliveredAtHubDate", ObjectUtils.isNotEmpty(o[13]) ? dateFormat.format(o[13]) : null);
				map.put("deliveredAtReferenceLabDate", ObjectUtils.isNotEmpty(o[14]) ? dateFormat.format(o[14]) : null);
				map.put("acceptedAtHubDate", ObjectUtils.isNotEmpty(o[15]) ? dateFormat.format(o[15]) : null);
				map.put("acceptedAtReferenceLabDate", ObjectUtils.isNotEmpty(o[16]) ? dateFormat.format(o[16]) : null);
				map.put("sampleType", o[17]);
				map.put("labNumber", o[18]);
				map.put("resultCollectionDate", ObjectUtils.isNotEmpty(o[19]) ? dateFormat.format(o[19]) : null);
				map.put("resultDeliveryDate", ObjectUtils.isNotEmpty(o[20]) ? dateFormat.format(o[20]) : null);
				map.put("status", o[21]);
				map.put("destinationLabId", o[22]);
				map.put("requesterSiteName", o[23]);
				map.put("destinationLabName", o[24]);
				map.put("analysisCompletedDate", ObjectUtils.isNotEmpty(o[25]) ? dateFormat.format(o[25]) : null);
				map.put("analysisReleasedDate", ObjectUtils.isNotEmpty(o[26]) ? dateFormat.format(o[26]) : null);
				map.put("analysisResultReportedDate", ObjectUtils.isNotEmpty(o[27]) ? dateFormat.format(o[27]) : null);
				map.put("collectionStartMileage", o[28]);
				map.put("collectionEndMileage", o[29]);
				map.put("resultStartMileage", o[30]);
				map.put("resultEndMileage", o[31]);
				response.add(map);
			}
		} catch (Exception ex) {
			log.error("getAnalysisDoneAtLab failed (labId={}): {}", labId, ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getAnalysisFailedAtLab(Integer labId) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		String sql = "select auhs.app_user_id, s.id,cs.circuit_id ,sr.id,sr2.id,sr2.comment,  "
				+ "auhs.site_id, s.hub_id, s.reference_lab_id ,s.patient_identifier, s.sample_identifier,  "
				+ "c.circuit_number , s.collection_date , s.deliver_at_hub_date, s.deliver_at_lab_date , "
				+ "s.accepted_at_hub_date , s.accepted_at_lab_date , st.name ,s.lab_number , "
				+ "s.result_collection_date , s.result_delivery_date , ss2.status, s.destination_lab_id,"
				+ "s.requester_site_name,s.destination_lab_name,s.analysis_completed_date, "
				+ "s.analysis_released_date, s.result_reported_date , s.collection_start_mileage, "
				+ "s.collection_end_mileage, s.result_start_mileage,s.result_end_mileage  from sample s  "
				+ "join sample_type st on st.id =s.sample_type_id  "
				+ "join sample_status ss2 on ss2.id = s.sample_status_id  "
				+ "left join sample_rejection sr2 on sr2.sample_id = s.id "
				+ "join sample_retrieving sr on s.sample_retrieving_id  = sr.id  "
				+ "join app_user_has_site auhs on auhs.app_user_id = sr.app_user_id  "
				+ "join circuit_site cs on cs.site_id = auhs.site_id  join circuit c on c.id = cs.circuit_id  "
				+ "where ss2.status = :status and s.reference_lab_id = :labId order by s.collection_date DESC";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("labId", labId);
			query.setParameter("status", ESampleStatus.ANALYSIS_FAILED.name());
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("userId", o[0]);
				map.put("sampleId", o[1]);
				map.put("circuitId", o[2]);
				map.put("sampleRetrievingId", o[3]);
				map.put("rejectionTypeId", o[4]);
				map.put("rejectionComment", o[5]);
				map.put("siteId", o[6]);
				map.put("hubId", o[7]);
				map.put("labId", o[8]);
				map.put("patientIdentifier", o[9]);
				map.put("sampleIdentifier", o[10]);
				map.put("circuitNumber", o[11]);
				map.put("collectionDate", ObjectUtils.isNotEmpty(o[12]) ? dateFormat.format(o[12]) : null);
				map.put("deliveredAtHubDate", ObjectUtils.isNotEmpty(o[13]) ? dateFormat.format(o[13]) : null);
				map.put("deliveredAtReferenceLabDate", ObjectUtils.isNotEmpty(o[14]) ? dateFormat.format(o[14]) : null);
				map.put("acceptedAtHubDate", ObjectUtils.isNotEmpty(o[15]) ? dateFormat.format(o[15]) : null);
				map.put("acceptedAtReferenceLabDate", ObjectUtils.isNotEmpty(o[16]) ? dateFormat.format(o[16]) : null);
				map.put("sampleType", o[17]);
				map.put("labNumber", o[18]);
				map.put("resultCollectionDate", ObjectUtils.isNotEmpty(o[19]) ? dateFormat.format(o[19]) : null);
				map.put("resultDeliveryDate", ObjectUtils.isNotEmpty(o[20]) ? dateFormat.format(o[20]) : null);
				map.put("status", o[21]);
				map.put("destinationLabId", o[22]);
				map.put("requesterSiteName", o[23]);
				map.put("destinationLabName", o[24]);
				map.put("analysisCompletedDate", ObjectUtils.isNotEmpty(o[25]) ? dateFormat.format(o[25]) : null);
				map.put("analysisReleasedDate", ObjectUtils.isNotEmpty(o[26]) ? dateFormat.format(o[26]) : null);
				map.put("analysisResultReportedDate", ObjectUtils.isNotEmpty(o[27]) ? dateFormat.format(o[27]) : null);
				map.put("collectionStartMileage", o[28]);
				map.put("collectionEndMileage", o[29]);
				map.put("resultStartMileage", o[30]);
				map.put("resultEndMileage", o[31]);
				response.add(map);
			}
		} catch (Exception ex) {
			log.error("getAnalysisFailedAtLab failed (labId={}): {}", labId, ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getPendingResultAtLab(Integer labId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Object> getSampleCountByStatus() {
		// REFONTE (audit rapports) : compteurs basés sur les dates
		// persistantes plutôt que sur ss.status (qui est écrasé).
		// Garantit la cohérence avec le dashboard web.
		return getSampleCountByStatus(null, null, null, null, null);
	}

	@Override
	public Map<String, Object> getSampleCountByStatus(Integer regionId, Integer districtId, Integer siteId,
			Date startDate, Date endDate) {
		// REFONTE (audit rapports) : on calcule directement via SUM(CASE)
		// sur dates persistantes. Plus de dispatch en Java sur ss.status.
		// FIX BONUS : le bug "sqlQuery.append APRÈS createNativeQuery"
		// (ligne 697 historique) qui rendait le filtre region inopérant
		// est intrinsèquement éliminé par cette refonte.
		StringBuffer sql = new StringBuffer();
		sql.append("SELECT "
				+ "  COUNT(*) AS all_count, "
				+ "  SUM(CASE WHEN ss.status = 'ON_TRANSIT' THEN 1 ELSE 0 END) AS in_transit, "
				+ "  SUM(CASE WHEN COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date) IS NOT NULL THEN 1 ELSE 0 END) AS delivered, "
				+ "  SUM(CASE WHEN s.analysis_released_date IS NOT NULL THEN 1 ELSE 0 END) AS analysis_done, "
				+ "  SUM(CASE WHEN ss.status = 'ANALYSIS_FAILED' AND s.analysis_completed_date IS NOT NULL THEN 1 ELSE 0 END) AS analysis_failed, "
				+ "  SUM(CASE WHEN s.result_collection_date IS NOT NULL THEN 1 ELSE 0 END) AS result_collected, "
				+ "  SUM(CASE WHEN s.result_delivery_date IS NOT NULL THEN 1 ELSE 0 END) AS result_delivered, "
				+ "  SUM(CASE WHEN ss.status = 'NON_CONFORM' AND s.rejection_date IS NOT NULL THEN 1 ELSE 0 END) AS rejected "
				+ "FROM sample s "
				+ "JOIN sample_status ss ON ss.id = s.sample_status_id ");

		boolean needsSite = ObjectUtils.isNotEmpty(siteId);
		boolean needsDistrict = ObjectUtils.isNotEmpty(districtId);
		boolean needsRegion = ObjectUtils.isNotEmpty(regionId);
		if (needsSite || needsDistrict || needsRegion) {
			sql.append(" LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id ")
			   .append(" LEFT JOIN site ON site.id = sr.site_id ");
			if (needsDistrict || needsRegion) {
				sql.append(" LEFT JOIN district d ON d.id = site.district_id ");
			}
		}

		sql.append(" WHERE TRUE ");
		if (needsSite)     sql.append(" AND site.id = :siteId ");
		if (needsDistrict) sql.append(" AND d.id = :districtId ");
		if (needsRegion)   sql.append(" AND d.region_id = :regionId ");

		boolean hasPeriod = ObjectUtils.isNotEmpty(startDate) && ObjectUtils.isNotEmpty(endDate);
		if (hasPeriod) {
			// CAST(... AS DATE) au lieu de ::date : avec em.createNativeQuery,
			// Hibernate parse `::` et avale le 2e ":" en croyant à un
			// paramètre nommé. CAST AS DATE est du SQL standard et sûr.
			sql.append(" AND CAST(s.collection_date AS DATE) BETWEEN :startDate AND :endDate ");
		}

		Map<String, Object> response = new HashMap<>();
		try {
			Query query = em.createNativeQuery(sql.toString());
			if (needsSite)     query.setParameter("siteId", siteId);
			if (needsDistrict) query.setParameter("districtId", districtId);
			if (needsRegion)   query.setParameter("regionId", regionId);
			if (hasPeriod) {
				query.setParameter("startDate", startDate);
				query.setParameter("endDate", endDate);
			}
			Object[] row = (Object[]) query.getSingleResult();
			response.put("all",             toIntSafe(row[0]));
			response.put("inTransit",       toIntSafe(row[1]));
			response.put("delivered",       toIntSafe(row[2]));
			response.put("analysisDone",    toIntSafe(row[3]));
			response.put("analysisFailed",  toIntSafe(row[4]));
			response.put("resultCollected", toIntSafe(row[5]));
			response.put("resultDelivered", toIntSafe(row[6]));
			response.put("rejected",        toIntSafe(row[7]));
		} catch (Exception ex) {
			log.error("getSampleCountByStatus failed (regionId={}, districtId={}, siteId={}, startDate={}, endDate={}): {}",
					regionId, districtId, siteId, startDate, endDate, ex.getMessage(), ex);
			// Renvoie des zéros plutôt qu'une réponse vide pour ne pas
			// casser le client mobile.
			response.put("all", 0);
			response.put("inTransit", 0);
			response.put("delivered", 0);
			response.put("analysisDone", 0);
			response.put("analysisFailed", 0);
			response.put("resultCollected", 0);
			response.put("resultDelivered", 0);
			response.put("rejected", 0);
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getSampleDetails() {
		String sql = "SELECT s.id, d.name AS district, site.name AS site, "
				+ "    st.name AS sample_type, s.sample_identifier, s.patient_identifier, "
				+ "    s.collection_date, destination_lab.lab_name AS destination_lab, "
				+ "    ss2.description, COALESCE(lab.lab_name, hub.lab_name) AS lab, "
				+ "    COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date) AS reception_date, "
				+ "    s.lab_number,  (s.collection_end_mileage - s.collection_start_mileage) distance, "
				+ "    analysis_completed_date, analysis_released_date, result_collection_date, "
				+ "    result_delivery_date, "
				+ "    DATE_PART('DAY', COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date, now()) - s.collection_date) AS tat,  "
				+ "  sr2.comment FROM sample s JOIN sample_type st ON st.id = s.sample_type_id JOIN "
				+ "    sample_status ss2 ON ss2.id = s.sample_status_id JOIN "
				+ "    sample_retrieving sr ON s.sample_retrieving_id = sr.id JOIN "
				+ "    site ON sr.site_id = site.id JOIN district d ON d.id = site.district_id  "
				+ " LEFT JOIN sample_rejection sr2 on sr2.sample_id = s.id  "
				+ "LEFT JOIN lab ON lab.id = s.reference_lab_id LEFT JOIN "
				+ "    lab hub ON hub.id = s.hub_id LEFT JOIN "
				+ "    lab destination_lab ON destination_lab.id = s.destination_lab_id ";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("sampleId", o[0]);
				map.put("district", o[1]);
				map.put("site", o[2]);
				map.put("sampleType", o[3]);
				map.put("sampleIdentifier", o[4]);
				map.put("patientIdentifier", o[5]);
				map.put("collectionDate", o[6]);
				map.put("destinationLab", o[7]);
				map.put("status", o[8]);
				map.put("lab", o[9]);
				map.put("receptionDate", o[10]);
				map.put("labNumber", o[11]);
				map.put("distance", o[12]);
				map.put("analysisCompletedDate", o[13]);
				map.put("analysisReleasedDate", o[14]);
				map.put("resultCollectionDate", o[15]);
				map.put("resultDeliveryDate", o[16]);
				map.put("TAT", o[17]);
				map.put("rejectionComment", o[18]);
				response.add(map);
			}
		} catch (Exception ex) {
			log.error("getSampleDetails failed: {}", ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public Map<String, Map<String, Integer>> getSampleStatusBySampleType(Integer regionId, Integer districtId,
			Integer siteId, Integer labId, LocalDate startDate, LocalDate endDate, List<Integer> accessibleSiteIds) {
		// REFONTE complète (audit rapports) : on n'agrège plus par ss.status
		// (qui est ÉCRASÉ à chaque transition) mais directement par dates
		// persistantes via SUM(CASE WHEN date IS NOT NULL). Garantit que
		// rejected ⊆ accepted ⊆ received pour chaque type d'échantillon
		// et résout les divergences avec les cards du dashboard
		// (DashboardNativeRepository.summary utilise déjà ce pattern).
		StringBuffer sql = new StringBuffer();
		sql.append("SELECT st.name AS sample_type, "
				+ "  COUNT(*) AS sample_collected, "
				+ "  SUM(CASE WHEN COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date) IS NOT NULL THEN 1 ELSE 0 END) AS sample_received, "
				+ "  SUM(CASE WHEN COALESCE(s.accepted_at_lab_date, s.accepted_at_hub_date) IS NOT NULL THEN 1 ELSE 0 END) AS sample_accepted, "
				+ "  SUM(CASE WHEN s.analysis_released_date IS NOT NULL THEN 1 ELSE 0 END) AS analysis_done, "
				+ "  SUM(CASE WHEN s.result_collection_date IS NOT NULL THEN 1 ELSE 0 END) AS result_collected, "
				+ "  SUM(CASE WHEN s.result_delivery_date IS NOT NULL THEN 1 ELSE 0 END) AS result_on_site, "
				+ "  SUM(CASE WHEN ss2.status = 'NON_CONFORM' AND s.rejection_date IS NOT NULL THEN 1 ELSE 0 END) AS non_conform, "
				+ "  SUM(CASE WHEN ss2.status = 'ANALYSIS_FAILED' AND s.analysis_completed_date IS NOT NULL THEN 1 ELSE 0 END) AS failed "
				+ "FROM sample s "
				+ "JOIN sample_type st ON st.id = s.sample_type_id "
				+ "JOIN sample_status ss2 ON ss2.id = s.sample_status_id "
				+ "JOIN sample_retrieving sr ON s.sample_retrieving_id = sr.id "
				+ "JOIN site ON sr.site_id = site.id "
				+ "JOIN district d ON d.id = site.district_id "
				+ "WHERE true ");
		if (ObjectUtils.isNotEmpty(regionId))
			sql.append(" AND d.region_id = :regionId ");
		if (ObjectUtils.isNotEmpty(districtId))
			sql.append(" AND d.id = :districtId ");
		if (ObjectUtils.isNotEmpty(siteId))
			sql.append(" AND site.id = :siteId ");
		if (ObjectUtils.isNotEmpty(labId))
			sql.append(" AND s.destination_lab_id = :labId ");
		// Scope user (non-admin) : restreint aux sites accessibles. Liste non
		// vide ⇒ on applique le IN ; admin / global ⇒ accessibleSiteIds null
		// ou vide, pas de filtre.
		boolean scopeActive = accessibleSiteIds != null && !accessibleSiteIds.isEmpty();
		if (scopeActive)
			sql.append(" AND site.id IN (:accessibleSiteIds) ");
		// CAST AS DATE plutôt que ::date : Hibernate parse `::` comme un
		// paramètre nommé via em.createNativeQuery et casse la syntaxe.
		sql.append(" AND (CAST(s.collection_date AS DATE) BETWEEN :startDate AND :endDate) ");
		sql.append(" GROUP BY st.name");

		// Réponse initialisée à 0 pour tous les types d'échantillons connus
		// (l'UI attend ces clés même si la table sample_type n'en a pas).
		Map<String, Map<String, Integer>> response = new LinkedHashMap<>();
		for (String type : new String[] { "BI", "BS", "CV", "EID", "TB", "HPV", "PrEP", "IVSA", "Autre" }) {
			Map<String, Integer> emptyItem = new LinkedHashMap<>();
			emptyItem.put("SAMPLE_COLLECTED", 0);
			emptyItem.put("SAMPLE_RECEIVED", 0);
			emptyItem.put("NON_CONFORM", 0);
			emptyItem.put("FAILED", 0);
			emptyItem.put("SAMPLE_ACCEPTED", 0);
			emptyItem.put("ANALYSIS_DONE", 0);
			emptyItem.put("RESULT_COLLECTED", 0);
			emptyItem.put("RESULT_ON_SITE", 0);
			response.put(type, emptyItem);
		}

		try {
			Query query = em.createNativeQuery(sql.toString());
			if (ObjectUtils.isNotEmpty(regionId))
				query.setParameter("regionId", regionId);
			if (ObjectUtils.isNotEmpty(districtId))
				query.setParameter("districtId", districtId);
			if (ObjectUtils.isNotEmpty(siteId))
				query.setParameter("siteId", siteId);
			if (ObjectUtils.isNotEmpty(labId))
				query.setParameter("labId", labId);
			if (scopeActive)
				query.setParameter("accessibleSiteIds", accessibleSiteIds);
			query.setParameter("startDate", startDate);
			query.setParameter("endDate", endDate);
			@SuppressWarnings("unchecked")
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				String type = o[0] != null ? o[0].toString() : "Autre";
				Map<String, Integer> map = response.get(type);
				if (map == null) {
					// Type d'échantillon inattendu (donnée legacy) : on
					// le rajoute dynamiquement plutôt que de l'ignorer.
					map = new LinkedHashMap<>();
					response.put(type, map);
				}
				map.put("SAMPLE_COLLECTED", toIntSafe(o[1]));
				map.put("SAMPLE_RECEIVED", toIntSafe(o[2]));
				map.put("SAMPLE_ACCEPTED", toIntSafe(o[3]));
				map.put("ANALYSIS_DONE", toIntSafe(o[4]));
				map.put("RESULT_COLLECTED", toIntSafe(o[5]));
				map.put("RESULT_ON_SITE", toIntSafe(o[6]));
				map.put("NON_CONFORM", toIntSafe(o[7]));
				map.put("FAILED", toIntSafe(o[8]));
			}
		} catch (Exception ex) {
			log.error("getSampleStatusBySampleType failed (regionId={}, districtId={}, siteId={}, labId={}, startDate={}, endDate={}): {}",
					regionId, districtId, siteId, labId, startDate, endDate, ex.getMessage(), ex);
		}
		return response;
	}

	/** Convertit un Object SQL (BigInteger/Long/null) en int de manière défensive. */
	private static int toIntSafe(Object v) {
		if (v == null) return 0;
		if (v instanceof Number n) return n.intValue();
		String str = v.toString();
		return str.matches("-?\\d+") ? Integer.parseInt(str) : 0;
	}

	@Override
	public Map<String, Map<String, Integer>> getSampleStatusBySampleType() {
		String sql = "select st.name sample_type, ss2.status, count(s.id) from sample s  "
				+ "join sample_type st on st.id =s.sample_type_id  "
				+ "join sample_status ss2 on ss2.id = s.sample_status_id group by st.name, ss2.status ";
		Map<String, Map<String, Integer>> response = new LinkedHashMap<String, Map<String, Integer>>();
		Map<String, Integer> biItem = new LinkedHashMap<String, Integer>();
		Map<String, Integer> bsItem = new LinkedHashMap<String, Integer>();
		Map<String, Integer> cvItem = new LinkedHashMap<String, Integer>();
		Map<String, Integer> eidItem = new LinkedHashMap<String, Integer>();
		Map<String, Integer> tbItem = new LinkedHashMap<String, Integer>();
		Map<String, Integer> hpvItem = new LinkedHashMap<String, Integer>();
		biItem.put("COLLECTES", 0);
		biItem.put("TRANSMIS", 0);
		biItem.put("NON CONFORME", 0);
		biItem.put("ECHEC", 0);

		bsItem.put("COLLECTES", 0);
		bsItem.put("TRANSMIS", 0);
		bsItem.put("NON CONFORME", 0);
		bsItem.put("ECHEC", 0);

		cvItem.put("COLLECTES", 0);
		cvItem.put("TRANSMIS", 0);
		cvItem.put("NON CONFORME", 0);
		cvItem.put("ECHEC", 0);

		eidItem.put("COLLECTES", 0);
		eidItem.put("TRANSMIS", 0);
		eidItem.put("NON CONFORME", 0);
		eidItem.put("ECHEC", 0);

		tbItem.put("COLLECTES", 0);
		tbItem.put("TRANSMIS", 0);
		tbItem.put("NON CONFORME", 0);
		tbItem.put("ECHEC", 0);

		hpvItem.put("COLLECTES", 0);
		hpvItem.put("TRANSMIS", 0);
		hpvItem.put("NON CONFORME", 0);
		hpvItem.put("ECHEC", 0);

		response.put("BI", biItem);
		response.put("BS", bsItem);
		response.put("CV", cvItem);
		response.put("EID", eidItem);
		response.put("TB", tbItem);
		response.put("HPV", hpvItem);
		List<String> transmisStatusList = Arrays.asList(new String[] { "ACCEPTED_AT_REFERENCE_LAB",
				"ACCEPTED_AT_TB_LAB", "ACCEPTED_AT_DISTRICT_LAB", "ACCEPTED_AT_HUB", "NON_CONFORM", "RESULT_COLLECTED",
				"RESULT_ON_SITE", "RECEIVED_AT_REFERENCE_LAB", "RECEIVED_AT_TB_LAB", "RECEIVED_AT_DISTRICT_LAB",
				"ANALYSIS_FAILED", "ANALYSIS_DONE", "RECEIVED_AT_HUB" });
		try {
			Query query = em.createNativeQuery(sql);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				String entry = o[0].toString();
				Map<String, Integer> map = response.get(entry);
				map.replace("COLLECTES", map.get("COLLECTES") + Integer.parseInt(o[2].toString()));
				if (transmisStatusList.contains(o[1].toString())) {
					map.replace("TRANSMIS", map.get("TRANSMIS") + Integer.parseInt(o[2].toString()));
				}
				if (o[1].toString().equalsIgnoreCase("NON_CONFORM")) {
					map.replace("NON CONFORME", map.get("NON CONFORME") + Integer.parseInt(o[2].toString()));
				}
				if (o[1].toString().equalsIgnoreCase("ANALYSIS_FAILED")) {
					map.replace("ECHEC", map.get("ECHEC") + Integer.parseInt(o[2].toString()));
				}
				response.replace(entry, map);
			}
		} catch (Exception ex) {
			log.error("getSampleStatusBySampleType failed: {}", ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getSampleInTransitForUserAndLab(Integer userId, Integer labId) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		String sql = "select auhc.app_user_id, s.id,c.id ,sr.id,sr2.id,sr2.comment,  "
				+ "sr.site_id, s.hub_id, s.reference_lab_id ,s.patient_identifier, s.sample_identifier,  "
				+ "c.circuit_number , s.collection_date , s.deliver_at_hub_date, s.deliver_at_lab_date , "
				+ "s.accepted_at_hub_date , s.accepted_at_lab_date , st.name ,s.lab_number , "
				+ "s.result_collection_date , s.result_delivery_date , ss2.status, s.destination_lab_id,"
				+ "s.requester_site_name,s.destination_lab_name,s.analysis_completed_date, "
				+ "s.analysis_released_date, s.result_reported_date , s.collection_start_mileage, s.collection_end_mileage,"
				+ " s.result_start_mileage,s.result_end_mileage  from sample s  "
				+ "join sample_type st on st.id =s.sample_type_id  "
				+ "join sample_status ss2 on ss2.id = s.sample_status_id  "
				+ "left join sample_rejection sr2 on sr2.sample_id = s.id "
				+ "join sample_retrieving sr on s.sample_retrieving_id  = sr.id  "
				+ "join circuit_site cs on cs.site_id  = sr.site_id join circuit c on c.id = cs.circuit_id  "
				+ "join app_user_has_circuit auhc on auhc.circuit_id  = cs.circuit_id   "
				+ "where (s.destination_lab_id = :labId or s.destination_lab_id is null) "
				+ " and ss2.status = :status and auhc.app_user_id = :userId order by s.collection_date DESC";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("userId", userId);
			query.setParameter("status", ESampleStatus.ON_TRANSIT.name());
			query.setParameter("labId", labId);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("userId", o[0]);
				map.put("sampleId", o[1]);
				map.put("circuitId", o[2]);
				map.put("sampleRetrievingId", o[3]);
				map.put("rejectionTypeId", o[4]);
				map.put("rejectionComment", o[5]);
				map.put("siteId", o[6]);
				map.put("hubId", o[7]);
				map.put("labId", o[8]);
				map.put("patientIdentifier", o[9]);
				map.put("sampleIdentifier", o[10]);
				map.put("circuitNumber", o[11]);
				map.put("collectionDate", dateFormat.format(o[12]));
				map.put("deliveredAtHubDate", ObjectUtils.isNotEmpty(o[13]) ? dateFormat.format(o[13]) : null);
				map.put("deliveredAtReferenceLabDate", ObjectUtils.isNotEmpty(o[14]) ? dateFormat.format(o[14]) : null);
				map.put("acceptedAtHubDate", ObjectUtils.isNotEmpty(o[15]) ? dateFormat.format(o[15]) : null);
				map.put("acceptedAtReferenceLabDate", ObjectUtils.isNotEmpty(o[16]) ? dateFormat.format(o[16]) : null);
				map.put("sampleType", o[17]);
				map.put("labNumber", o[18]);
				map.put("resultCollectionDate", ObjectUtils.isNotEmpty(o[19]) ? dateFormat.format(o[19]) : null);
				map.put("resultDeliveryDate", ObjectUtils.isNotEmpty(o[20]) ? dateFormat.format(o[20]) : null);
				map.put("status", o[21]);
				map.put("destinationLabId", o[22]);
				map.put("requesterSiteName", o[23]);
				map.put("destinationLabName", o[24]);
				map.put("analysisCompletedDate", ObjectUtils.isNotEmpty(o[25]) ? dateFormat.format(o[25]) : null);
				map.put("analysisReleasedDate", ObjectUtils.isNotEmpty(o[26]) ? dateFormat.format(o[26]) : null);
				map.put("analysisResultReportedDate", ObjectUtils.isNotEmpty(o[27]) ? dateFormat.format(o[27]) : null);
				map.put("collectionStartMileage", o[28]);
				map.put("collectionEndMileage", o[29]);
				map.put("resultStartMileage", o[30]);
				map.put("resultEndMileage", o[31]);
				response.add(map);
			}
		} catch (Exception ex) {
			log.error("getSampleInTransitForUserAndLab failed (userId={}, labId={}): {}", userId, labId, ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getSampleResultForUserAndSite(Integer userId, Integer siteId) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		String sql = "select auhc.app_user_id, s.id,c.id ,sr.id,sr2.id,sr2.comment,  "
				+ "sr.site_id, s.hub_id, s.reference_lab_id ,s.patient_identifier, s.sample_identifier,  "
				+ "c.circuit_number , s.collection_date , s.deliver_at_hub_date, s.deliver_at_lab_date , "
				+ "s.accepted_at_hub_date , s.accepted_at_lab_date , st.name ,s.lab_number , "
				+ "s.result_collection_date , s.result_delivery_date , ss2.status , s.destination_lab_id,"
				+ "s.requester_site_name,s.destination_lab_name,s.analysis_completed_date, "
				+ "s.analysis_released_date, s.result_reported_date , s.collection_start_mileage, s.collection_end_mileage,"
				+ " s.result_start_mileage,s.result_end_mileage  from sample s  "
				+ "join sample_type st on st.id =s.sample_type_id  "
				+ "join sample_status ss2 on ss2.id = s.sample_status_id  "
				+ "left join sample_rejection sr2 on sr2.sample_id = s.id "
				+ "join sample_retrieving sr on s.sample_retrieving_id  = sr.id  "
				+ "join circuit_site cs on cs.site_id  = sr.site_id join circuit c on c.id = cs.circuit_id  "
				+ "join app_user_has_circuit auhc on auhc.circuit_id  = cs.circuit_id   "
				+ "where sr.site_id = :siteId and ss2.status = :status and auhc.app_user_id = :userId order by s.collection_date DESC";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("userId", userId);
			query.setParameter("status", ESampleStatus.RESULT_COLLECTED.name());
			query.setParameter("siteId", siteId);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("userId", o[0]);
				map.put("sampleId", o[1]);
				map.put("circuitId", o[2]);
				map.put("sampleRetrievingId", o[3]);
				map.put("rejectionTypeId", o[4]);
				map.put("rejectionComment", o[5]);
				map.put("siteId", o[6]);
				map.put("hubId", o[7]);
				map.put("labId", o[8]);
				map.put("patientIdentifier", o[9]);
				map.put("sampleIdentifier", o[10]);
				map.put("circuitNumber", o[11]);
				map.put("collectionDate", dateFormat.format(o[12]));
				map.put("deliveredAtHubDate", ObjectUtils.isNotEmpty(o[13]) ? dateFormat.format(o[13]) : null);
				map.put("deliveredAtReferenceLabDate", ObjectUtils.isNotEmpty(o[14]) ? dateFormat.format(o[14]) : null);
				map.put("acceptedAtHubDate", ObjectUtils.isNotEmpty(o[15]) ? dateFormat.format(o[15]) : null);
				map.put("acceptedAtReferenceLabDate", ObjectUtils.isNotEmpty(o[16]) ? dateFormat.format(o[16]) : null);
				map.put("sampleType", o[17]);
				map.put("labNumber", o[18]);
				map.put("resultCollectionDate", ObjectUtils.isNotEmpty(o[19]) ? dateFormat.format(o[19]) : null);
				map.put("resultDeliveryDate", ObjectUtils.isNotEmpty(o[20]) ? dateFormat.format(o[20]) : null);
				map.put("status", o[21]);
				map.put("destinationLabId", o[22]);
				map.put("requesterSiteName", o[23]);
				map.put("destinationLabName", o[24]);
				map.put("analysisCompletedDate", ObjectUtils.isNotEmpty(o[25]) ? dateFormat.format(o[25]) : null);
				map.put("analysisReleasedDate", ObjectUtils.isNotEmpty(o[26]) ? dateFormat.format(o[26]) : null);
				map.put("analysisResultReportedDate", ObjectUtils.isNotEmpty(o[27]) ? dateFormat.format(o[27]) : null);
				map.put("collectionStartMileage", o[28]);
				map.put("collectionEndMileage", o[29]);
				map.put("resultStartMileage", o[30]);
				map.put("resultEndMileage", o[31]);
				response.add(map);
			}
		} catch (Exception ex) {
			log.error("getSampleResultForUserAndSite failed (userId={}, siteId={}): {}", userId, siteId, ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public List<Map<String, Object>> getSampleInTransitForUserAndLab(Integer userId, Integer labId,
			List<String> sampleTypes) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		String sql = "select auhc.app_user_id, s.id,c.id ,sr.id,sr2.id,sr2.comment,  "
				+ "sr.site_id, s.hub_id, s.reference_lab_id ,s.patient_identifier, s.sample_identifier,  "
				+ "c.circuit_number , s.collection_date , s.deliver_at_hub_date, s.deliver_at_lab_date , "
				+ "s.accepted_at_hub_date , s.accepted_at_lab_date , st.name ,s.lab_number , "
				+ "s.result_collection_date , s.result_delivery_date , ss2.status, s.destination_lab_id,"
				+ "s.requester_site_name,s.destination_lab_name,s.analysis_completed_date, "
				+ "s.analysis_released_date, s.result_reported_date , s.collection_start_mileage, s.collection_end_mileage,"
				+ " s.result_start_mileage,s.result_end_mileage  from sample s  "
				+ "join sample_type st on st.id =s.sample_type_id  "
				+ "join sample_status ss2 on ss2.id = s.sample_status_id  "
				+ "left join sample_rejection sr2 on sr2.sample_id = s.id "
				+ "join sample_retrieving sr on s.sample_retrieving_id  = sr.id  "
				+ "join circuit_site cs on cs.site_id  = sr.site_id join circuit c on c.id = cs.circuit_id  "
				+ "join app_user_has_circuit auhc on auhc.circuit_id  = cs.circuit_id   "
				+ "where (s.destination_lab_id = :labId or s.destination_lab_id is null) and ss2.status = :status "
				+ " and auhc.app_user_id = :userId and st.name in (:sampleTypes) order by s.collection_date DESC";
		List<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
		try {
			Query query = em.createNativeQuery(sql);
			query.setParameter("userId", userId);
			query.setParameter("status", ESampleStatus.ON_TRANSIT.name());
			query.setParameter("labId", labId);
			query.setParameter("sampleTypes", sampleTypes);
			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("userId", o[0]);
				map.put("sampleId", o[1]);
				map.put("circuitId", o[2]);
				map.put("sampleRetrievingId", o[3]);
				map.put("rejectionTypeId", o[4]);
				map.put("rejectionComment", o[5]);
				map.put("siteId", o[6]);
				map.put("hubId", o[7]);
				map.put("labId", o[8]);
				map.put("patientIdentifier", o[9]);
				map.put("sampleIdentifier", o[10]);
				map.put("circuitNumber", o[11]);
				map.put("collectionDate", dateFormat.format(o[12]));
				map.put("deliveredAtHubDate", ObjectUtils.isNotEmpty(o[13]) ? dateFormat.format(o[13]) : null);
				map.put("deliveredAtReferenceLabDate", ObjectUtils.isNotEmpty(o[14]) ? dateFormat.format(o[14]) : null);
				map.put("acceptedAtHubDate", ObjectUtils.isNotEmpty(o[15]) ? dateFormat.format(o[15]) : null);
				map.put("acceptedAtReferenceLabDate", ObjectUtils.isNotEmpty(o[16]) ? dateFormat.format(o[16]) : null);
				map.put("sampleType", o[17]);
				map.put("labNumber", o[18]);
				map.put("resultCollectionDate", ObjectUtils.isNotEmpty(o[19]) ? dateFormat.format(o[19]) : null);
				map.put("resultDeliveryDate", ObjectUtils.isNotEmpty(o[20]) ? dateFormat.format(o[20]) : null);
				map.put("status", o[21]);
				map.put("destinationLabId", o[22]);
				map.put("requesterSiteName", o[23]);
				map.put("destinationLabName", o[24]);
				map.put("analysisCompletedDate", ObjectUtils.isNotEmpty(o[25]) ? dateFormat.format(o[25]) : null);
				map.put("analysisReleasedDate", ObjectUtils.isNotEmpty(o[26]) ? dateFormat.format(o[26]) : null);
				map.put("analysisResultReportedDate", ObjectUtils.isNotEmpty(o[27]) ? dateFormat.format(o[27]) : null);
				map.put("collectionStartMileage", o[28]);
				map.put("collectionEndMileage", o[29]);
				map.put("resultStartMileage", o[30]);
				map.put("resultEndMileage", o[31]);
				response.add(map);
			}
		} catch (Exception ex) {
			log.error("getSampleInTransitForUserAndLab failed (userId={}, labId={}, sampleTypes={}): {}", userId, labId, sampleTypes, ex.getMessage(), ex);
		}
		return response;
	}

	@Override
	public Map<String, Object> getSampleCountByStatus(Date startDate, Date endDate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Object> getSampleCountByStatusForRegion(Integer regionId, Date startDate, Date endDate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Object> getSampleCountByStatusForDistrict(Integer districtId, Date startDate, Date endDate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Object> getSampleCountByStatusForSite(Integer siteId, Date startDate, Date endDate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Page<Map<String, Object>> getSampleDetailsScoped(Pageable pageable, Integer regionId, Integer districtId,
			Integer siteId, Integer labId, Date startDate, Date endDate, Integer status, Integer sampleType,
			String patientIdentifier, List<Integer> accessibleSiteIds) {

		return sampleRepo.getSampleDetailsScoped(pageable, regionId, districtId, siteId, labId, startDate, endDate,
				status, sampleType, patientIdentifier, accessibleSiteIds);
	}

	@Override
	public Page<Map<String, Object>> getSampleDetailsAdvanced(Pageable pageable,
			Integer regionId, Integer districtId, Integer siteId, Integer labId,
			Date startDate, Date endDate,
			List<Integer> statusIds, List<Integer> typeIds,
			List<String> natures,
			List<Integer> accessibleSiteIds,
			String searchText, Boolean onlyRejected,
			Integer stuckDays,
			String sortKey, String sortDir) {

		boolean statusActive = statusIds != null && !statusIds.isEmpty();
		boolean typeActive = typeIds != null && !typeIds.isEmpty();
		boolean naturesActive = natures != null && !natures.isEmpty();
		boolean accessibleActive = accessibleSiteIds != null && !accessibleSiteIds.isEmpty();
		boolean stuckActive = stuckDays != null && stuckDays > 0;
		String search = (searchText == null || searchText.isBlank()) ? null : searchText.trim();
		boolean onlyRej = Boolean.TRUE.equals(onlyRejected);
		String key = (sortKey == null || sortKey.isBlank()) ? "collection_date" : sortKey;
		String dir = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";

		return sampleRepo.getSampleDetailsAdvanced(pageable,
				regionId, districtId, siteId, labId, startDate, endDate,
				statusActive ? statusIds : Collections.singletonList(-1), statusActive,
				typeActive ? typeIds : Collections.singletonList(-1), typeActive,
				naturesActive ? natures : Collections.singletonList("__none__"), naturesActive,
				accessibleActive ? accessibleSiteIds : Collections.singletonList(-1), accessibleActive,
				search,
				onlyRej,
				stuckActive ? stuckDays : 0, stuckActive,
				key, dir);
	}

	@Override
	public List<String> getDistinctSampleNatures() {
		try {
			return sampleRepo.findDistinctSampleNatures();
		} catch (Exception ex) {
			log.error("getDistinctSampleNatures failed: {}", ex.getMessage(), ex);
			return Collections.emptyList();
		}
	}

	@Override
	public boolean removeById(Integer d) {
		try {
			sampleRepo.deleteById(d);
			return true;
		} catch (Exception ex) {
			log.error("removeById failed (id={}): {}", d, ex.getMessage(), ex);
			return false;
		}
	}

	@Override
	public List<Map<String, String>> getAll(Integer region, Integer district, Integer site, Integer lab, Date startDate,
			Date endDate, Integer status, Integer sampleType, String patientIdentifier, List<Integer> accessibleSiteIds) {
		StringBuffer sql = new StringBuffer();
		sql.append(" SELECT s.id,"
				+ " reg.name AS region, d.name AS district, site.name AS site, concat(au.last_name,' ', au.first_name) rider, "
				+ "st.name AS sample_type, s.patient_identifier, " // 6
				+ "to_char(s.collection_date,'dd/MM/yyyy HH24:MI') collection_date,  "
				+ "destination_lab.lab_name AS destination_lab, ss2.description status, "
				+ "sr2.comment rejection_comment, hub.lab_name AS hub_name, lab.lab_name lab_name, " // 12
				+ "to_char(COALESCE(s.deliver_at_hub_date,s.deliver_at_lab_date),'dd/MM/yyyy HH24:MI') AS deliver_date,"// 13
				// + "to_char(s.deliver_at_lab_date,'dd/MM/yyyy HH24:MI') AS lab_deliver_date,"
				+ "to_char(s.accepted_at_hub_date,'dd/MM/yyyy HH24:MI') AS hub_accepted_date,"
				+ "to_char(s.accepted_at_lab_date,'dd/MM/yyyy HH24:MI') AS lab_accepted_date," + " s.lab_number, "// 16
				+ "(s.collection_end_mileage - s.collection_start_mileage) distance, "
				+ "to_char(analysis_completed_date,'dd/MM/yyyy HH24:MI') analysis_completed_date,  "
				+ "to_char(analysis_released_date,'dd/MM/yyyy HH24:MI') analysis_released_date, "
				+ "to_char(result_collection_date,'dd/MM/yyyy HH24:MI')  result_collection_date, "
				+ "to_char(result_delivery_date,'dd/MM/yyyy HH24:MI') result_delivery_date, "// 21
				+ "srt.rejection_type rejection_type,  to_char(s.result_reported_date,'dd/MM/yyyy HH24:MI') result_reported_date,   "// 23
				+ "to_char(COALESCE(s.pickup_date, sr.sample_retrieve_date),'dd/MM/yyyy HH24:MI') pickup_date, "// 24
				+ " s.sample_nature, "
				+ "CAST(DATE_PART('DAY', COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date) - COALESCE(s.pickup_date,s.collection_date)) AS INTEGER), "
				+ "CAST(DATE_PART('DAY', COALESCE(s.accepted_at_lab_date, s.accepted_at_hub_date) - COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date)) AS INTEGER), "
				+ "CAST(DATE_PART('DAY', COALESCE(s.result_reported_date, s.analysis_released_date, now()) - COALESCE(s.accepted_at_lab_date, s.accepted_at_hub_date)) AS INTEGER), "
				+ "CAST(DATE_PART('DAY', COALESCE(s.result_collection_date, now()) - COALESCE(s.result_reported_date, s.analysis_released_date)) AS INTEGER), "
				+ "CAST(DATE_PART('DAY', COALESCE(s.result_delivery_date) - COALESCE(s.result_collection_date)) AS INTEGER), "
				// Délai global = TAT canonique (collecte → livraison du résultat), cf. TatSql.
				+ "CAST(FLOOR(" + org.itech.labSampleTracker.dao.TatSql.DAYS + ") AS INTEGER) "
				+ "FROM sample s JOIN sample_type st ON st.id = s.sample_type_id  "
				+ "join sample_status ss2 ON ss2.id = s.sample_status_id JOIN "
				+ "sample_retrieving sr ON s.sample_retrieving_id = sr.id JOIN "
				+ "site ON sr.site_id = site.id JOIN district d ON d.id = site.district_id "
				+ " JOIN region reg ON reg.id = d.region_id LEFT JOIN sample_rejection sr2 on sr2.sample_id = s.id "
				+ " LEFT JOIN sample_rejection_type srt on srt.id = sr2.sample_rejection_type_id "
				+ " LEFT JOIN lab ON lab.id = s.reference_lab_id LEFT JOIN lab hub ON hub.id = s.hub_id LEFT JOIN "
				+ " lab destination_lab ON destination_lab.id = s.destination_lab_id  "
				+ " join app_user au on au.id = sr.app_user_id where true ");
		// Périmètre de l'utilisateur (cahier V.3) : sans cette restriction, l'export
		// renvoyait tous les échantillons du pays, identifiants patients compris.
		boolean scoped = accessibleSiteIds != null && !accessibleSiteIds.isEmpty();
		if (scoped)
			sql.append(" AND site.id IN (:accessibleSiteIds) ");
		if (ObjectUtils.isNotEmpty(region))
			sql.append(" AND reg.id = :regionId ");
		if (ObjectUtils.isNotEmpty(district))
			sql.append(" AND d.id = :districtId ");
		if (ObjectUtils.isNotEmpty(site))
			sql.append(" AND site.id = :siteId ");
		if (ObjectUtils.isNotEmpty(lab))
			sql.append(" AND s.destination_lab_id = :labId ");
		if (ObjectUtils.isNotEmpty(status))
			sql.append(" AND (:status IS NULL OR ss2.id = :status)");
		if (ObjectUtils.isNotEmpty(sampleType))
			sql.append(" AND st.id = :sampleType ");
		if (ObjectUtils.isNotEmpty(patientIdentifier))
			sql.append("AND s.patient_identifier LIKE CONCAT('%', :patientIdentifier , '%')");

		// CAST AS DATE plutôt que ::date (cf. comment ci-dessus).
		sql.append(" AND (CAST(s.collection_date AS DATE) BETWEEN :startDate AND :endDate) order by s.collection_date DESC");

		List<Map<String, String>> response = new ArrayList<Map<String, String>>();
		try {
			Query query = em.createNativeQuery(sql.toString());
			if (scoped)
				query.setParameter("accessibleSiteIds", accessibleSiteIds);
			if (ObjectUtils.isNotEmpty(region))
				query.setParameter("regionId", region);
			if (ObjectUtils.isNotEmpty(district))
				query.setParameter("districtId", district);
			if (ObjectUtils.isNotEmpty(site))
				query.setParameter("siteId", site);
			if (ObjectUtils.isNotEmpty(lab))
				query.setParameter("labId", lab);
			if (ObjectUtils.isNotEmpty(status))
				query.setParameter("status", status);
			query.setParameter("startDate", startDate);
			query.setParameter("endDate", endDate);

			List<Object[]> results = query.getResultList();
			for (Object[] o : results) {
				Map<String, String> map = new HashMap<String, String>();
				map.put("sampleId", Objects.toString(o[0], null));
				map.put("region", Objects.toString(o[1], null));
				map.put("district", Objects.toString(o[2], null));
				map.put("site", Objects.toString(o[3], null));
				map.put("rider", Objects.toString(o[4], null));
				map.put("sampleType", Objects.toString(o[5], null));
				map.put("patientIdentifier", Objects.toString(o[6], null));
				map.put("pickupDate", Objects.toString(o[24], null));
				map.put("collectionDate", Objects.toString(o[7], null));
				map.put("destinationLab", Objects.toString(o[8], null));
				map.put("status", Objects.toString(o[9], null));
				map.put("rejectionComment", Objects.toString(o[10], null));
				map.put("hubName", Objects.toString(o[11], null));
				map.put("labName", Objects.toString(o[12], null));
				map.put("deliverDate", Objects.toString(o[13], null));
				map.put("hubAcceptedDate", Objects.toString(o[14], null));
				map.put("labAcceptedDate", Objects.toString(o[15], null));
				map.put("labNumber", Objects.toString(o[16], null));
				map.put("distance", Objects.toString(o[17], null));
				map.put("analysisCompletedDate", Objects.toString(o[18], null));
				map.put("analysisReleasedDate", Objects.toString(o[19], null));
				map.put("resultCollectionDate", Objects.toString(o[20], null));
				map.put("resultDeliveryDate", Objects.toString(o[21], null));
				map.put("rejectionType", Objects.toString(o[22], null));
				map.put("resultReportedDate", Objects.toString(o[23], null));
				map.put("sampleNature", Objects.toString(o[25], null));

				map.put("sampleTransmissionDelay", Objects.toString(o[26], null));
				map.put("sampleReceptionDelay", Objects.toString(o[27], null));
				map.put("sampleProcessingDelay", Objects.toString(o[28], null));
				map.put("resultCollectionDelay", Objects.toString(o[29], null));
				map.put("resultTransmissionDelay", Objects.toString(o[30], null));
				map.put("globalDelay", Objects.toString(o[31], null));
				response.add(map);
			}
		} catch (Exception ex) {
			log.error("getAll failed (region={}, district={}, site={}, lab={}, startDate={}, endDate={}, status={}, sampleType={}, patientIdentifier={}): {}", region, district, site, lab, startDate, endDate, status, sampleType, patientIdentifier, ex.getMessage(), ex);
		}
		return response;
	}

}
