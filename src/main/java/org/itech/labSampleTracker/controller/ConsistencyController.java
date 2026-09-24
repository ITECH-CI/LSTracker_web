package org.itech.labSampleTracker.controller;

import java.time.LocalDate;
import java.util.List;

import org.itech.labSampleTracker.service.RegionService;
import org.itech.labSampleTracker.service_impl.ConsistencyCheckService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Administration → Contrôle de cohérence : vérifie, pour une période et une
 * région, que chaque indicateur a la même valeur sur tous les écrans et que
 * les niveaux s'additionnent (cahier VI.4, V.5, XII.3). Outil de recette.
 */
@Controller
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class ConsistencyController {

	private final ConsistencyCheckService checks;
	private final RegionService regions;

	public ConsistencyController(ConsistencyCheckService checks, RegionService regions) {
		this.checks = checks;
		this.regions = regions;
	}

	@GetMapping("/coherence")
	public String index(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
			@RequestParam(required = false) Integer region, Model model) {
		LocalDate e = end != null ? end : LocalDate.now();
		LocalDate s = start != null ? start : e.withDayOfMonth(1);
		if (s.isAfter(e)) {
			LocalDate t = s;
			s = e;
			e = t;
		}
		List<ConsistencyCheckService.Check> indicators = checks.indicators(s, e, region);
		List<ConsistencyCheckService.HierarchyGap> gaps = checks.hierarchy(s, e, region);
		model.addAttribute("start", s);
		model.addAttribute("end", e);
		model.addAttribute("region", region);
		model.addAttribute("regions", regions.getRegionIdAndName());
		model.addAttribute("indicators", indicators);
		model.addAttribute("gaps", gaps);
		model.addAttribute("failures", indicators.stream().filter(c -> !c.ok()).count() + gaps.size());
		return "coherence/index";
	}
}
