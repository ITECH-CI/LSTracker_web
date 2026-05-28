package org.itech.labSampleTracker.api.meta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.itech.labSampleTracker.api.meta.MetaDtos.CircuitDto;
import org.itech.labSampleTracker.api.meta.MetaDtos.CircuitSiteDto;
import org.itech.labSampleTracker.api.meta.MetaDtos.LabDto;
import org.itech.labSampleTracker.api.meta.MetaDtos.MetaFullResponse;
import org.itech.labSampleTracker.api.meta.MetaDtos.RejectionTypeDto;
import org.itech.labSampleTracker.api.meta.MetaDtos.SiteDto;
import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.enums.UserType;
import org.itech.labSampleTracker.service.CircuitService;
import org.itech.labSampleTracker.service.CircuitSiteService;
import org.itech.labSampleTracker.service.LabService;
import org.itech.labSampleTracker.service.SampleRejectionTypeService;
import org.itech.labSampleTracker.service.SiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MetaService {
	@Autowired
	private CircuitService circuitService;

	@Autowired
	private LabService labService;

	@Autowired
	private SiteService siteService;

	@Autowired
	private CircuitSiteService circuitSiteService;

	@Autowired
	private SampleRejectionTypeService rejectionTypeService;

	// Détermine le "type effectif" en regardant role ET user_type. Historiquement
	// le code ne lisait que user_type, mais un admin dont user_type était mal
	// saisi (ex: "AUTRE") tombait dans la branche non-admin → métadonnées filtrées
	// par affectation géo (vide pour un admin) → impossible de saisir une collecte.
	// On considère donc qu'un user EST d'un type donné si role OU user_type matche.
	private boolean isType(AppUser user, UserType type) {
		String t = type.getType();
		return t.equalsIgnoreCase(user.getUserType()) || t.equalsIgnoreCase(user.getRole());
	}

	public MetaFullResponse loadAll(AppUser user) {

		boolean isAdmin = isType(user, UserType.ADMIN);

		List<Map<String, Object>> circuitList = new ArrayList<Map<String, Object>>();
		if (isAdmin) {
			circuitList = circuitService.getCircuitIdAndNumber();
		} else {
			circuitList = circuitService.getCircuitIdAndNumberByUser(user.getId());
		}

		List<Map<String, Object>> labList = new ArrayList<Map<String, Object>>();
		if (isType(user, UserType.BIOLOGIST)) {
			labList = labService.getAllLabIdAndNamesByLabUser(user.getId());
		} else if (isType(user, UserType.RIDER)) {
			labList = labService.getAllLabIdAndNamesByRider(user.getId());
		} else {
			labList = labService.getLabIdAndNames();
		}

		List<Map<String, Object>> siteList = new ArrayList<Map<String, Object>>();
		if (isAdmin) {
			siteList = siteService.getSiteIdAndNames();
		} else {
			siteList = siteService.getSiteIdAndCodeAndNamesByUser(user.getId());
		}

		List<Map<String, Object>> circuitSiteList = new ArrayList<Map<String, Object>>();
		if (isAdmin) {
			circuitSiteList = circuitSiteService.getAllCircuitSite();
		} else {
			circuitSiteList = circuitSiteService.getCircuitSiteByUser(user.getId());
		}

		List<Map<String, Object>> types = rejectionTypeService.getIdAndNames();

		var labs = labList.stream().map(l -> new LabDto(Long.parseLong(l.get("id").toString()),
				l.get("name").toString(), l.get("labType").toString())).collect(Collectors.toList());

		var circuits = circuitList.stream()
				.map(l -> new CircuitDto(Long.parseLong(l.get("id").toString()), l.get("name").toString()))
				.collect(Collectors.toList());

		var sites = siteList.stream().map(l -> new SiteDto(Long.parseLong(l.get("id").toString()),
				l.get("name").toString(), l.get("site_code").toString())).collect(Collectors.toList());

		var circuitSites = circuitSiteList.stream()
				.map(l -> new CircuitSiteDto(Long.parseLong(l.get("circuit_id").toString()),
						Long.parseLong(l.get("site_id").toString())))
				.collect(Collectors.toList());

		var rejections = types.stream()
				.map(l -> new RejectionTypeDto(Long.parseLong(l.get("id").toString()), l.get("name").toString()))
				.collect(Collectors.toList());

		var version = "v-" + labs.size() + "-" + circuits.size() + "-" + sites.size() + "-" + rejections.size() + "-"
				+ Instant.now().toString();

		return new MetaFullResponse(version, labs, circuits, sites, rejections, circuitSites);
	}
}