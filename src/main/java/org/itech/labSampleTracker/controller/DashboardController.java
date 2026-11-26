package org.itech.labSampleTracker.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.itech.labSampleTracker.dao.DashboardAdvancedRepository;
import org.itech.labSampleTracker.dto.DashboardFilter;
import org.itech.labSampleTracker.dto.StepDurationDTO;
import org.itech.labSampleTracker.enums.LabType;
import org.itech.labSampleTracker.service.DashboardService;
import org.itech.labSampleTracker.service.DistrictService;
import org.itech.labSampleTracker.service.LabService;
import org.itech.labSampleTracker.service.RegionService;
import org.itech.labSampleTracker.service.SampleTypeService;
import org.itech.labSampleTracker.service.SiteService;
import org.itech.labSampleTracker.service.security.UserScopeService;
import org.itech.labSampleTracker.service.security.UserScopeService.ScopedFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import lombok.RequiredArgsConstructor;

/**
 * Tableau de bord. Chaque point d'accès reçoit les mêmes filtres
 * ({@link DashboardQuery}), intersectés avec le périmètre de l'utilisateur :
 * hors périmètre, la réponse est vide.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/dashboard")
public class DashboardController {

	private final DashboardService service;

	private final SiteService siteService;

	private final RegionService regionService;

	private final DistrictService districtService;

	private final SampleTypeService sampleTypeService;

	private final LabService labService;

	private final UserScopeService userScopeService;

	private final DashboardAdvancedRepository advancedRepo;

	/**
	 * Filtres de l'écran intersectés avec le périmètre de l'utilisateur ; vide
	 * si la sélection sort de son périmètre.
	 */
	private Optional<DashboardFilter> scoped(DashboardQuery q) {
		ScopedFilter scope = userScopeService.intersectCurrent(q.regionId(), q.districtId(), q.siteId(), q.labId());
		if (scope.isForceEmpty()) {
			return Optional.empty();
		}
		return Optional.of(DashboardFilter.of(q.start(), q.end(), scope, q.labTypeCode(), q.sampleTypeId()));
	}

	@GetMapping(value = { "", "/data/summary" })
	public String analysisIndex(Model model, DashboardQuery q) {
		Map<String, Object> summary = scoped(q).map(service::getSummary).orElseGet(Map::of);

		// Listes complètes : la page les garde pour revenir à « Tout », et
		// applique elle-même la cascade région › district › site.
		List<Map<String, Object>> labs = labService.getLabIdAndNames();
		List<Map<String, Object>> districts = districtService.getDistrictIdAndNames();
		List<Map<String, Object>> sites = siteService.getSiteIdAndNames();
		List<Map<String, Object>> regions = regionService.getRegionIdAndName();
		// Listes des filtres limitées au périmètre (tout le référentiel pour un compte national).
		UserScopeService.Scope zone = userScopeService.resolveCurrent();
		regions = UserScopeService.visible(zone, UserScopeService.ZoneList.REGIONS, regions);
		districts = UserScopeService.visible(zone, UserScopeService.ZoneList.DISTRICTS, districts);
		sites = UserScopeService.visible(zone, UserScopeService.ZoneList.SITES, sites);
		labs = UserScopeService.visible(zone, UserScopeService.ZoneList.LABS, labs);

		model.addAttribute("sampleCount", summary);
		model.addAttribute("labs", labs);
		model.addAttribute("labTypes", labTypeOptions(labs));
		model.addAttribute("regions", regions);
		model.addAttribute("districts", districts);
		model.addAttribute("sampleTypes", sampleTypeService.getSampleTypeIdAndName());
		model.addAttribute("sites", sites);
		model.addAttribute("selectedSite", q.siteId());
		model.addAttribute("selectedRegion", q.regionId());
		model.addAttribute("selectedDistrict", q.districtId());
		model.addAttribute("selectedLab", q.labId());
		model.addAttribute("selectedLabType", q.labTypeCode());
		model.addAttribute("selectedSampleType", q.sampleTypeId());
		model.addAttribute("selectedStartDate", q.start());
		model.addAttribute("selectedEndDate", q.end());
		return "home/index";
	}

	/**
	 * Types de laboratoire présents dans le référentiel ({@code lab.lab_type}),
	 * avec leur libellé, dans l'ordre des libellés.
	 */
	static List<Map<String, String>> labTypeOptions(List<Map<String, Object>> labs) {
		Map<String, String> byCode = new LinkedHashMap<>();
		for (Map<String, Object> l : labs) {
			Object code = l.get("labType");
			if (code != null && !code.toString().isBlank()) {
				byCode.putIfAbsent(code.toString().trim(), LabType.label(code.toString()));
			}
		}
		List<Map<String, String>> out = new ArrayList<>();
		byCode.entrySet().stream().sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
				.forEach(e -> out.add(Map.of("code", e.getKey(), "label", e.getValue())));
		return out;
	}

	@GetMapping(value = "/sample_status_by_sample_type", produces = "application/json")
	public ResponseEntity<Map<String, Object>> getSampleStatusBySampleType(DashboardQuery q) {
		Map<String, Map<String, Integer>> sampleCount = scoped(q).map(service::stepsByType)
				.orElseGet(Map::of);
		Set<String> categories = sampleCount.keySet();
		List<Integer> collected = new ArrayList<Integer>();
		List<Integer> delivered = new ArrayList<Integer>();
		List<Integer> nonConform = new ArrayList<Integer>();
		List<Integer> failed = new ArrayList<Integer>();
		List<Integer> analysisDone = new ArrayList<Integer>();
		List<Integer> resultCollected = new ArrayList<Integer>();
		List<Integer> resultOnSite = new ArrayList<Integer>();

		categories.forEach(el -> {
			Map<String, Integer> v = sampleCount.get(el);
			collected.add(v.get("SAMPLE_COLLECTED"));
			delivered.add(v.get("SAMPLE_RECEIVED"));
			nonConform.add(v.get("NON_CONFORM"));
			failed.add(v.get("FAILED"));
			analysisDone.add(v.get("ANALYSIS_DONE"));
			resultCollected.add(v.get("RESULT_COLLECTED"));
			resultOnSite.add(v.get("RESULT_ON_SITE"));
		});
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("categories", categories);
		response.put("collected", collected);
		response.put("delivered", delivered);
		response.put("nonConform", nonConform);
		response.put("failed", failed);
		response.put("analysisDone", analysisDone);
		response.put("resultCollected", resultCollected);
		response.put("resultOnSite", resultOnSite);

		return new ResponseEntity<Map<String, Object>>(response, HttpStatus.OK);
	}

	@GetMapping("/data/series")
	@ResponseBody
	public Map<String, Object> series(DashboardQuery q) {
		Map<String, Object> out = new HashMap<>();
		Optional<DashboardFilter> f = scoped(q);
		out.put("collected", f.map(service::seriesCollected).orElseGet(List::of));
		out.put("deposited", f.map(service::seriesDeposited).orElseGet(List::of));
		out.put("analysed", f.map(service::seriesAnalysed).orElseGet(List::of));
		out.put("delivered", f.map(service::seriesDelivered).orElseGet(List::of));
		return out;
	}

	@GetMapping("/data/step-durations")
	@ResponseBody
	public List<StepDurationDTO> stepDurations(DashboardQuery q) {
		return scoped(q).map(service::stepDurations).orElseGet(List::of);
	}

	/**
	 * Consolidated KPIs for the redesigned dashboard cards & funnel.
	 * Returns total, in_transit, at_hub, at_lab, analysed, result_collected,
	 * delivered, non_conform, failed, tat_avg_days.
	 */
	@GetMapping(value = "/data/funnel", produces = "application/json")
	@ResponseBody
	public Map<String, Object> funnel(DashboardQuery q) {
		return scoped(q).map(advancedRepo::funnel).orElseGet(DashboardController::emptyFunnel);
	}

	private static Map<String, Object> emptyFunnel() {
		return Map.of("total", 0, "in_transit", 0, "at_hub", 0, "at_lab", 0,
				"analysed", 0, "result_collected", 0, "delivered", 0,
				"non_conform", 0, "failed", 0, "tat_avg_days", 0);
	}

	/**
	 * Same KPIs as {@link #funnel} but for the previous period (used for the
	 * trend on the cards).
	 *
	 * <p>With a calendar {@code period} (today, week, month, quarter, semester,
	 * year — set by the dashboard presets), the previous period is the same span
	 * shifted by one calendar unit: month to date 01/09 → 23/09 is compared with
	 * 01/08 → 23/08, today with yesterday. Otherwise (dates typed by hand), it
	 * is the period of equal length immediately before.
	 *
	 * <p>The response carries {@code prev_start} / {@code prev_end} so the page
	 * can show which period the comparison refers to.
	 */
	@GetMapping(value = "/data/funnel-previous", produces = "application/json")
	@ResponseBody
	public Map<String, Object> funnelPrevious(DashboardQuery q, @RequestParam(required = false) String period) {
		LocalDate[] prev = previousPeriod(q.start(), q.end(), period);
		Map<String, Object> result = new HashMap<>();
		scoped(q).ifPresentOrElse(f -> result.putAll(advancedRepo.funnel(f.withPeriod(prev[0], prev[1]))),
				() -> result.put("total", 0));
		result.put("prev_start", prev[0].toString());
		result.put("prev_end", prev[1].toString());
		return result;
	}

	/** [start, end] de la période de comparaison (cf. {@link #funnelPrevious}). */
	static LocalDate[] previousPeriod(LocalDate start, LocalDate end, String period) {
		if (period != null) {
			switch (period) {
			case "today":
				return new LocalDate[] { start.minusDays(1), end.minusDays(1) };
			case "week":
				return new LocalDate[] { start.minusWeeks(1), end.minusWeeks(1) };
			case "month":
				return new LocalDate[] { start.minusMonths(1), end.minusMonths(1) };
			case "quarter":
				return new LocalDate[] { start.minusMonths(3), end.minusMonths(3) };
			case "semester":
				return new LocalDate[] { start.minusMonths(6), end.minusMonths(6) };
			case "year":
				return new LocalDate[] { start.minusYears(1), end.minusYears(1) };
			default:
				break;
			}
		}
		long lengthDays = java.time.temporal.ChronoUnit.DAYS.between(start, end);
		LocalDate prevEnd = start.minusDays(1);
		return new LocalDate[] { prevEnd.minusDays(lengthDays), prevEnd };
	}

	/**
	 * Samples that have not moved for at least N days and are not in a terminal
	 * state. Limit 50 by default to keep the dashboard responsive.
	 */
	@GetMapping(value = "/data/stuck", produces = "application/json")
	@ResponseBody
	public List<Map<String, Object>> stuckSamples(DashboardQuery q,
			@RequestParam(name = "stuck_days", defaultValue = "7") int stuckDays,
			@RequestParam(defaultValue = "50") int limit) {
		return scoped(q).map(f -> advancedRepo.stuckSamples(f, Math.max(stuckDays, 1),
				Math.min(Math.max(limit, 10), 500))).orElseGet(List::of);
	}

	// Répartition par région / district / site. Tous les filtres de l'écran
	// s'appliquent aux trois niveaux. Le dépliage d'une région
	// (by-district?region=X) ou d'un district (by-site?district=Y) utilise le
	// même paramètre : déplier revient à filtrer sur le parent.
	@GetMapping(value = "/data/by-region", produces = "application/json")
	@ResponseBody
	public List<Map<String, Object>> statsByRegion(DashboardQuery q) {
		return scoped(q).map(advancedRepo::statsByRegion).orElseGet(List::of);
	}

	@GetMapping(value = "/data/by-district", produces = "application/json")
	@ResponseBody
	public List<Map<String, Object>> statsByDistrict(DashboardQuery q) {
		return scoped(q).map(advancedRepo::statsByDistrict).orElseGet(List::of);
	}

	@GetMapping(value = "/data/by-site", produces = "application/json")
	@ResponseBody
	public List<Map<String, Object>> statsBySite(DashboardQuery q) {
		return scoped(q).map(advancedRepo::statsBySite).orElseGet(List::of);
	}

	/**
	 * Program coverage indicators for the selected period: active sites/districts/
	 * regions, conveyors, labs, total distance, average distance per sample.
	 */
	@GetMapping(value = "/data/coverage", produces = "application/json")
	@ResponseBody
	public Map<String, Object> coverage(DashboardQuery q) {
		return scoped(q).map(advancedRepo::coverage).orElseGet(Map::of);
	}

	/**
	 * Sample-type breakdown for the period (used by the coverage section's
	 * mini doughnut chart).
	 */
	@GetMapping(value = "/data/type-breakdown", produces = "application/json")
	@ResponseBody
	public List<Map<String, Object>> typeBreakdown(DashboardQuery q) {
		return scoped(q).map(advancedRepo::typeBreakdown).orElseGet(List::of);
	}

	/**
	 * Indicateurs de performance : sites au taux de non-conformité le plus
	 * élevé, laboratoires les plus lents et circuits. Trois listes en une
	 * réponse, pour un seul aller-retour.
	 */
	@GetMapping(value = "/data/top-performers", produces = "application/json")
	@ResponseBody
	public Map<String, Object> topPerformers(DashboardQuery q,
			@RequestParam(name = "limit", defaultValue = "5") int limit,
			@RequestParam(name = "min_samples", defaultValue = "5") int minSamples) {
		Optional<DashboardFilter> scoped = scoped(q);
		if (scoped.isEmpty()) {
			return Map.of("rejection_sites", List.of(), "slowest_labs", List.of(), "circuits", List.of());
		}
		DashboardFilter f = scoped.get();
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("rejection_sites", advancedRepo.topRejectionSites(f, limit, minSamples));
		out.put("slowest_labs", advancedRepo.slowestLabs(f, limit, minSamples));
		out.put("circuits", topCircuits(advancedRepo.circuits(f), limit));
		return out;
	}

	/**
	 * Les {@code limit} circuits les plus actifs, suivis des lignes « Sites
	 * partagés entre plusieurs circuits » et « Non rattaché » quand elles
	 * existent, quel que soit leur rang.
	 */
	static List<Map<String, Object>> topCircuits(List<Map<String, Object>> rows, int limit) {
		List<Map<String, Object>> out = new ArrayList<>();
		Map<String, Object> shared = null;
		Map<String, Object> unattached = null;
		for (Map<String, Object> r : rows) {
			Object kind = r.get("kind");
			if (DashboardAdvancedRepository.SHARED_KIND.equals(kind)) {
				shared = r;
			} else if (DashboardAdvancedRepository.UNATTACHED_KIND.equals(kind)) {
				unattached = r;
			} else if (out.size() < limit) {
				out.add(r);
			}
		}
		if (shared != null) {
			out.add(shared);
		}
		if (unattached != null) {
			out.add(unattached);
		}
		return out;
	}

	/**
	 * Détail d'une zone du tableau de répartition (ligne dépliable) : la zone
	 * est passée comme filtre (region, district ou site), avec les autres
	 * filtres de l'écran. Répartition par type d'échantillon, durée médiane de
	 * chaque étape et non-conformités par motif.
	 */
	@GetMapping(value = "/data/zone-details", produces = "application/json")
	@ResponseBody
	public Map<String, Object> zoneDetails(DashboardQuery q) {
		Optional<DashboardFilter> scoped = scoped(q);
		if (scoped.isEmpty()) {
			return Map.of("by_type", List.of(), "durations", List.of(), "non_conform_reasons", List.of());
		}
		DashboardFilter f = scoped.get();
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("by_type", advancedRepo.zoneByType(f));
		out.put("durations", service.stepMedians(f));
		out.put("non_conform_reasons", advancedRepo.zoneNonConformReasons(f));
		return out;
	}
}
