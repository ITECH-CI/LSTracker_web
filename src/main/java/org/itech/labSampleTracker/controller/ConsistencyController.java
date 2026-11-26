package org.itech.labSampleTracker.controller;

import org.itech.labSampleTracker.security.AdminRead;
import org.itech.labSampleTracker.security.AdminWrite;
import java.time.LocalDate;
import java.util.List;

import org.itech.labSampleTracker.dto.DashboardFilter;
import org.itech.labSampleTracker.service.LabService;
import org.itech.labSampleTracker.service.RegionService;
import org.itech.labSampleTracker.service.SampleTypeService;
import org.itech.labSampleTracker.service_impl.ConsistencyCheckService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Administration → Contrôle de cohérence : vérifie, pour une période et une
 * région (et, au choix, un type de laboratoire et un type d'échantillon),
 * que chaque indicateur a la même valeur sur tous les écrans et que
 * les niveaux s'additionnent (cahier VI.4, V.5, XII.3). Outil de recette.
 */
@Controller
@AdminRead
public class ConsistencyController {

	private final ConsistencyCheckService checks;
	private final RegionService regions;
	private final LabService labs;
	private final SampleTypeService sampleTypes;

	public ConsistencyController(ConsistencyCheckService checks, RegionService regions, LabService labs,
			SampleTypeService sampleTypes) {
		this.checks = checks;
		this.regions = regions;
		this.labs = labs;
		this.sampleTypes = sampleTypes;
	}

	@GetMapping("/coherence")
	public String index(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
			@RequestParam(required = false) Integer region,
			@RequestParam(name = "lab_type", required = false) String labType,
			@RequestParam(name = "sample_type", required = false) Integer sampleType, Model model) {
		LocalDate e = end != null ? end : LocalDate.now();
		LocalDate s = start != null ? start : e.withDayOfMonth(1);
		if (s.isAfter(e)) {
			LocalDate t = s;
			s = e;
			e = t;
		}
		DashboardQuery q = new DashboardQuery(s, e, region, null, null, null, labType, sampleType);
		DashboardFilter f = new DashboardFilter(s, e, q.regionId(), null, null, null, q.labTypeCode(),
				q.sampleTypeId(), null);
		List<ConsistencyCheckService.Check> indicators = checks.indicators(f);
		List<ConsistencyCheckService.HierarchyGap> gaps = checks.hierarchy(f);
		model.addAttribute("start", s);
		model.addAttribute("end", e);
		model.addAttribute("region", region);
		model.addAttribute("regions", regions.getRegionIdAndName());
		model.addAttribute("labType", f.labType());
		model.addAttribute("labTypes", DashboardController.labTypeOptions(labs.getLabIdAndNames()));
		model.addAttribute("sampleType", f.sampleTypeId());
		model.addAttribute("sampleTypes", sampleTypes.getSampleTypeIdAndName());
		model.addAttribute("indicators", indicators);
		model.addAttribute("gaps", gaps);
		model.addAttribute("failures", indicators.stream().filter(c -> !c.ok()).count() + gaps.size());
		return "coherence/index";
	}
}
