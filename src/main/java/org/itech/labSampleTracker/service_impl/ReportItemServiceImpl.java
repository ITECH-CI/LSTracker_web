package org.itech.labSampleTracker.service_impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.dao.ReportJdbcRepository;
import org.itech.labSampleTracker.dto.DashboardFilter;
import org.itech.labSampleTracker.dto.ReportItem;
import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.entities.AppUserHasCircuit;
import org.itech.labSampleTracker.entities.Circuit;
import org.itech.labSampleTracker.entities.CircuitSite;
import org.itech.labSampleTracker.entities.District;
import org.itech.labSampleTracker.entities.Lab;
import org.itech.labSampleTracker.entities.Site;
import org.itech.labSampleTracker.service.AppUserService;
import org.itech.labSampleTracker.service.CircuitSiteService;
import org.itech.labSampleTracker.service.DistrictService;
import org.itech.labSampleTracker.service.LabService;
import org.itech.labSampleTracker.service.RegionService;
import org.itech.labSampleTracker.service.ReportItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReportItemServiceImpl implements ReportItemService {

	@Autowired
	private ReportJdbcRepository jdbcRepository;

	@Autowired
	private AppUserService appUserService;

	@Autowired
	private CircuitSiteService circuitSiteService;

	@Autowired
	private RegionService regionService;

	@Autowired
	private DistrictService districtService;

	@Autowired
	private LabService labService;

	public ReportItem buildForScope(LocalDate start, LocalDate end, Integer regionId, Integer districtId,
			Integer siteId, Integer labId, Integer riderId, List<Integer> accessibleSiteIds) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

		String focalPoint = null;
		ReportItem it = new ReportItem();
		it.setStartDate(start.format(formatter));
		it.setEndDate(end.format(formatter));
		if (ObjectUtils.isNotEmpty(regionId))
			it.setRegionName(regionService.resolveRegionName(regionId));

		if (ObjectUtils.isNotEmpty(districtId)) {
			it.setDistrictName(districtService.resolveDistrictName(districtId));
			District thisDistrict = districtService.getOne(districtId);
			if (ObjectUtils.isNotEmpty(thisDistrict)) {
				it.setRegionName(regionService.resolveRegionName(thisDistrict.getRegionId()));
			}

		}

		if (ObjectUtils.isNotEmpty(riderId)) {
			it.setConveyorName(appUserService.resolveUserName(riderId));

			AppUser rider = appUserService.getOne(riderId);
			Circuit circuit = null;
			Site site = null;
			District district = null;

			if (ObjectUtils.isNotEmpty(rider)) {
				List<AppUserHasCircuit> userCircuits = appUserService.getUserCircuits(riderId);

				if (ObjectUtils.isNotEmpty(userCircuits)) {
					AppUserHasCircuit userCircuit = userCircuits.get(0);
					if (ObjectUtils.isNotEmpty(userCircuit)) {
						circuit = userCircuit.getCircuit();
					}
				}
			}

			if (ObjectUtils.isNotEmpty(circuit)) {
				List<CircuitSite> circuitSites = circuitSiteService.getAllByCircuit(circuit.getId());

				if (ObjectUtils.isNotEmpty(circuitSites)) {
					CircuitSite thisCircuitSite = circuitSites.get(0);
					if (ObjectUtils.isNotEmpty(thisCircuitSite)) {
						site = thisCircuitSite.getSite();
					}
				}
			}

			if (ObjectUtils.isNotEmpty(site)) {
				district = site.getDistrict();
			}

			if (ObjectUtils.isNotEmpty(district)) {
				it.setDistrictName(districtService.resolveDistrictName(district.getId()));
				it.setRegionName(regionService.resolveRegionName(district.getRegionId()));
			}
		}

		if (ObjectUtils.isNotEmpty(labId)) {
			it.setLabName(labService.resolveLabName(labId));
			Lab thisLab = labService.getOne(labId);
			if (ObjectUtils.isNotEmpty(thisLab)) {
				it.setDistrictName(districtService.resolveDistrictName(thisLab.getDistrictId()));
				if (ObjectUtils.isNotEmpty(thisLab.getDistrictId())) {
					District thisDistrict = districtService.getOne(thisLab.getDistrictId());
					it.setRegionName(regionService.resolveRegionName(thisDistrict.getRegionId()));
				}
			}
		}
		it.setFocalPointName(focalPoint);

		DashboardFilter filter = new DashboardFilter(start, end, regionId, districtId, siteId, labId, null, null,
				accessibleSiteIds);

		// 1) collectés
		applyByType(jdbcRepository.collectedByType(filter, riderId),
				(type, n) -> setCount(it, "collected", type, n));

		// 2) réceptionnés par catégorie
		for (Map<String, Object> row : jdbcRepository.receivedByTypeAndLabKind(filter, riderId)) {
			String type = String.valueOf(row.get("type_code"));
			String kind = String.valueOf(row.get("lab_kind")); // DISTRICT/RELAIS/CAT/BM
			int n = toInt(row.get("cnt"));
			switch (kind) {
			case "DISTRICT" -> setCount(it, "deliveredDistrict", type, n);
			case "RELAIS" -> setCount(it, "deliveredRelais", type, n);
			case "CAT" -> setCount(it, "deliveredCAT", type, n);
			case "BM" -> setCount(it, "deliveredBM", type, n);
			}
		}

		// 3) acceptés
		applyByType(jdbcRepository.acceptedByType(filter, riderId),
				(type, n) -> setCount(it, "accepted", type, n));

		// 4) prêts (analysis_done)
		applyByType(jdbcRepository.resultReadyByType(filter, riderId),
				(type, n) -> setCount(it, "resultReady", type, n));

		// 5) résultats collectés / déposés
		applyByType(jdbcRepository.resultCollectedByType(filter, riderId),
				(type, n) -> setCount(it, "resultCollected", type, n));
		applyByType(jdbcRepository.resultDeliveredByType(filter, riderId),
				(type, n) -> setCount(it, "resultDelivered", type, n));

		// 6) rejets + échecs
		applyByType(jdbcRepository.rejectedByType(filter, riderId),
				(type, n) -> setCount(it, "rejected", type, n));
		applyByType(jdbcRepository.failedByType(filter, riderId),
				(type, n) -> setCount(it, "failed", type, n));

		// 7) TAT médian (jours)
		for (Map<String, Object> row : jdbcRepository.tatMedianDaysByType(filter, riderId)) {
			String type = String.valueOf(row.get("type_code"));
			int days = (int) Math.ceil(toDouble(row.get("median_days")));
			setTat(it, type, days);
		}

		return it;
	}

	private interface BiIntConsumer {
		void accept(String type, int n);
	}

	private void applyByType(List<Map<String, Object>> rows, BiIntConsumer consumer) {
		for (Map<String, Object> r : rows) {
			String type = String.valueOf(r.get("type_code")).toUpperCase(Locale.ROOT);
			int n = toInt(r.get("cnt"));
			consumer.accept(type, n);
		}
	}

	private int toInt(Object o) {
		if (o == null)
			return 0;
		if (o instanceof Number n)
			return n.intValue();
		try {
			return Integer.parseInt(o.toString());
		} catch (Exception e) {
			return 0;
		}
	}

	private double toDouble(Object o) {
		if (o == null)
			return 0d;
		if (o instanceof Number n)
			return n.doubleValue();
		try {
			return Double.parseDouble(o.toString());
		} catch (Exception e) {
			return 0d;
		}
	}

	// setter générique selon préfixe & type
	private void setCount(ReportItem it, String prefix, String type, int n) {
		switch ((prefix + "|" + type).toUpperCase(Locale.ROOT)) {
		// collected.*
		case "COLLECTED|BI" -> it.setCollectedBICount(n);
		case "COLLECTED|BS" -> it.setCollectedBSCount(n);
		case "COLLECTED|CV" -> it.setCollectedCVCount(n);
		case "COLLECTED|EID" -> it.setCollectedEIDCount(n);
		case "COLLECTED|HPV" -> it.setCollectedHPVCount(n);
		case "COLLECTED|TB" -> it.setCollectedTBCount(n);
		case "COLLECTED|PREP" -> it.setCollectedPREPCount(n);
		case "COLLECTED|IVSA" -> it.setCollectedIVSACount(n);
		case "COLLECTED|AUTRE", "COLLECTED|AUTRES" -> it.setCollectedAUTRECount(n);

		// deliveredDistrict.*
		case "DELIVEREDDISTRICT|BI" -> it.setDeliveredBICountDistrict(n);
		case "DELIVEREDDISTRICT|BS" -> it.setDeliveredBSCountDistrict(n);
		case "DELIVEREDDISTRICT|CV" -> it.setDeliveredCVCountDistrict(n);
		case "DELIVEREDDISTRICT|EID" -> it.setDeliveredEIDCountDistrict(n);
		case "DELIVEREDDISTRICT|HPV" -> it.setDeliveredHPVCountDistrict(n);
		case "DELIVEREDDISTRICT|TB" -> it.setDeliveredTBCountDistrict(n);
		case "DELIVEREDDISTRICT|PREP" -> it.setDeliveredPREPCountDistrict(n);
		case "DELIVEREDDISTRICT|IVSA" -> it.setDeliveredIVSACountDistrict(n);
		case "DELIVEREDDISTRICT|AUTRE", "DELIVEREDDISTRICT|AUTRES" -> it.setDeliveredAUTRECountDistrict(n);

		// deliveredRelais.*
		case "DELIVEREDRELAIS|BI" -> it.setDeliveredBICountRelais(n);
		case "DELIVEREDRELAIS|BS" -> it.setDeliveredBSCountRelais(n);
		case "DELIVEREDRELAIS|CV" -> it.setDeliveredCVCountRelais(n);
		case "DELIVEREDRELAIS|EID" -> it.setDeliveredEIDCountRelais(n);
		case "DELIVEREDRELAIS|HPV" -> it.setDeliveredHPVCountRelais(n);
		case "DELIVEREDRELAIS|TB" -> it.setDeliveredTBCountRelais(n);
		case "DELIVEREDRELAIS|PREP" -> it.setDeliveredPREPCountRelais(n);
		case "DELIVEREDRELAIS|IVSA" -> it.setDeliveredIVSACountRelais(n);
		case "DELIVEREDRELAIS|AUTRE", "DELIVEREDRELAIS|AUTRES" -> it.setDeliveredAUTRECountRelais(n);

		// deliveredCAT.*
		case "DELIVEREDCAT|TB" -> it.setDeliveredTBCountCAT(n);

		// deliveredBM.*
		case "DELIVEREDBM|BI" -> it.setDeliveredBICountBM(n);
		case "DELIVEREDBM|BS" -> it.setDeliveredBSCountBM(n);
		case "DELIVEREDBM|CV" -> it.setDeliveredCVCountBM(n);
		case "DELIVEREDBM|EID" -> it.setDeliveredEIDCountBM(n);
		case "DELIVEREDBM|HPV" -> it.setDeliveredHPVCountBM(n);
		case "DELIVEREDBM|TB" -> it.setDeliveredTBCountBM(n);
		case "DELIVEREDBM|PREP" -> it.setDeliveredPREPCountBM(n);
		case "DELIVEREDBM|IVSA" -> it.setDeliveredIVSACountBM(n);
		case "DELIVEREDBM|AUTRE", "DELIVEREDBM|AUTRES" -> it.setDeliveredAUTRECountBM(n);

		// accepted.*
		case "ACCEPTED|BI" -> it.setAcceptedBICount(n);
		case "ACCEPTED|BS" -> it.setAcceptedBSCount(n);
		case "ACCEPTED|CV" -> it.setAcceptedCVCount(n);
		case "ACCEPTED|EID" -> it.setAcceptedEIDCount(n);
		case "ACCEPTED|HPV" -> it.setAcceptedHPVCount(n);
		case "ACCEPTED|TB" -> it.setAcceptedTBCount(n);
		case "ACCEPTED|PREP" -> it.setAcceptedPREPCount(n);
		case "ACCEPTED|IVSA" -> it.setAcceptedIVSACount(n);
		case "ACCEPTED|AUTRE", "ACCEPTED|AUTRES" -> it.setAcceptedAUTRECount(n);

		// resultReady.*
		case "RESULTREADY|BI" -> it.setResultReadyBICount(n);
		case "RESULTREADY|BS" -> it.setResultReadyBSCount(n);
		case "RESULTREADY|CV" -> it.setResultReadyCVCount(n);
		case "RESULTREADY|EID" -> it.setResultReadyEIDCount(n);
		case "RESULTREADY|HPV" -> it.setResultReadyHPVCount(n);
		case "RESULTREADY|TB" -> it.setResultReadyTBCount(n);
		case "RESULTREADY|PREP" -> it.setResultReadyPREPCount(n);
		case "RESULTREADY|IVSA" -> it.setResultReadyIVSACount(n);
		case "RESULTREADY|AUTRE", "RESULTREADY|AUTRES" -> it.setResultReadyAUTRECount(n);

		// resultCollected.*
		case "RESULTCOLLECTED|BI" -> it.setResultCollectedBICount(n);
		case "RESULTCOLLECTED|BS" -> it.setResultCollectedBSCount(n);
		case "RESULTCOLLECTED|CV" -> it.setResultCollectedCVCount(n);
		case "RESULTCOLLECTED|EID" -> it.setResultCollectedEIDCount(n);
		case "RESULTCOLLECTED|HPV" -> it.setResultCollectedHPVCount(n);
		case "RESULTCOLLECTED|TB" -> it.setResultCollectedTBCount(n);
		case "RESULTCOLLECTED|PREP" -> it.setResultCollectedPREPCount(n);
		case "RESULTCOLLECTED|IVSA" -> it.setResultCollectedIVSACount(n);
		case "RESULTCOLLECTED|AUTRE", "RESULTCOLLECTED|AUTRES" -> it.setResultCollectedAUTRECount(n);

		// resultDelivered.*
		case "RESULTDELIVERED|BI" -> it.setResultDeliveredBICount(n);
		case "RESULTDELIVERED|BS" -> it.setResultDeliveredBSCount(n);
		case "RESULTDELIVERED|CV" -> it.setResultDeliveredCVCount(n);
		case "RESULTDELIVERED|EID" -> it.setResultDeliveredEIDCount(n);
		case "RESULTDELIVERED|HPV" -> it.setResultDeliveredHPVCount(n);
		case "RESULTDELIVERED|TB" -> it.setResultDeliveredTBCount(n);
		case "RESULTDELIVERED|PREP" -> it.setResultDeliveredPREPCount(n);
		case "RESULTDELIVERED|IVSA" -> it.setResultDeliveredIVSACount(n);
		case "RESULTDELIVERED|AUTRE", "RESULTDELIVERED|AUTRES" -> it.setResultDeliveredAUTRECount(n);

		// rejected.*
		case "REJECTED|BI" -> it.setRejectedBICount(n);
		case "REJECTED|BS" -> it.setRejectedBSCount(n);
		case "REJECTED|CV" -> it.setRejectedCVCount(n);
		case "REJECTED|EID" -> it.setRejectedEIDCount(n);
		case "REJECTED|HPV" -> it.setRejectedHPVCount(n);
		case "REJECTED|TB" -> it.setRejectedTBCount(n);
		case "REJECTED|PREP" -> it.setRejectedPREPCount(n);
		case "REJECTED|IVSA" -> it.setRejectedIVSACount(n);
		case "REJECTED|AUTRE", "REJECTED|AUTRES" -> it.setRejectedAUTRECount(n);

		// failed.*
		case "FAILED|BI" -> it.setFailedBICount(n);
		case "FAILED|BS" -> it.setFailedBSCount(n);
		case "FAILED|CV" -> it.setFailedCVCount(n);
		case "FAILED|EID" -> it.setFailedEIDCount(n);
		case "FAILED|HPV" -> it.setFailedHPVCount(n);
		case "FAILED|TB" -> it.setFailedTBCount(n);
		case "FAILED|PREP" -> it.setFailedPREPCount(n);
		case "FAILED|IVSA" -> it.setFailedIVSACount(n);
		case "FAILED|AUTRE", "FAILED|AUTRES" -> it.setFailedAUTRECount(n);
		}
	}

	private void setTat(ReportItem it, String type, int days) {
		switch (type.toUpperCase(Locale.ROOT)) {
		case "BI" -> it.setBITAT(days);
		case "BS" -> it.setBSTAT(days);
		case "CV" -> it.setCVTAT(days);
		case "EID" -> it.setEIDTAT(days);
		case "HPV" -> it.setHPVTAT(days);
		case "TB" -> it.setTBTAT(days);
		case "PREP" -> it.setPREPTAT(days);
		case "IVSA" -> it.setIVSATAT(days);
		default -> it.setAUTRETAT(days);
		}
	}
}
