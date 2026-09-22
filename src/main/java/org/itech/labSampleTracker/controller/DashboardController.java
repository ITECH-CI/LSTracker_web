package org.itech.labSampleTracker.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.dto.StepDurationDTO;
import org.itech.labSampleTracker.service.DashboardService;
import org.itech.labSampleTracker.service.DistrictService;
import org.itech.labSampleTracker.service.LabService;
import org.itech.labSampleTracker.service.RegionService;
import org.itech.labSampleTracker.service.SampleService;
import org.itech.labSampleTracker.service.SampleTypeService;
import org.itech.labSampleTracker.service.SiteService;
import org.itech.labSampleTracker.service.security.UserScopeService;
import org.itech.labSampleTracker.service.security.UserScopeService.ScopedFilter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/dashboard")
public class DashboardController {

	private final DashboardService service;

	private final SampleService sampleService;

	private final SiteService siteService;

	private final RegionService regionService;

	private final DistrictService districtService;

	private final SampleTypeService sampleTypeService;

	private final LabService labService;

	private final UserScopeService userScopeService;

	private final org.itech.labSampleTracker.dao.DashboardAdvancedRepository advancedRepo;

	@GetMapping(value = { "", "/data/summary" })
	public String analysisIndex(Model model,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(required = false, defaultValue = "0") Integer region,
			@RequestParam(required = false, defaultValue = "0") Integer district,
			@RequestParam(required = false, defaultValue = "0") Integer site,
			@RequestParam(required = false, defaultValue = "0") Integer lab,
			@RequestParam(required = false, defaultValue = "0") Integer circuit,
			@RequestParam(required = false, name = "sample_type", defaultValue = "0") Integer sampleType) {

		if (startDate == null)
			startDate = LocalDate.now().minusYears(2);
		if (endDate == null)
			endDate = LocalDate.now();

		if (region == 0) {
			region = null;
		}
		if (district == 0) {
			district = null;
		}
		if (site == 0) {
			site = null;
		}
		if (circuit == 0) {
			circuit = null;
		}
		if (lab == 0) {
			lab = null;
		}

		ScopedFilter scope = userScopeService.intersectCurrent(region, district, site, lab);
		Map<String, Object> summary;
		if (scope.isForceEmpty()) {
			summary = service.getSummary(startDate, endDate, region, district, site, lab, java.util.List.of(-1));
		} else {
			summary = service.getSummary(startDate, endDate, scope.getRegionId(), scope.getDistrictId(),
					scope.getSiteId(), scope.getLabId(), scope.getAccessibleSiteIds());
		}

		List<Map<String, Object>> sites = siteService.getSiteIdAndNames();
		List<Map<String, Object>> regions = regionService.getRegionIdAndName();
		List<Map<String, Object>> districts = districtService.getDistrictIdAndNames();
		List<Map<String, Object>> labs = labService.getLabIdAndNames();
		List<Map<String, Object>> sampleTypes = sampleTypeService.getSampleTypeIdAndName();
		if (ObjectUtils.isNotEmpty(region)) {
			districts = districtService.getDistrictIdAndNamesByRegion(region);
		}
		if (ObjectUtils.isNotEmpty(district)) {
			sites = siteService.getSiteIdAndNamesByDistrict(district);
		}

		model.addAttribute("sampleCount", summary);
		model.addAttribute("labs", labs);
		model.addAttribute("regions", regions);
		model.addAttribute("districts", districts);
		model.addAttribute("sampleTypes", sampleTypes);
		model.addAttribute("sites", sites);
		model.addAttribute("selectedSite", site);
		model.addAttribute("selectedRegion", region);
		model.addAttribute("selectedDistrict", district);
		model.addAttribute("selectedStartDate", startDate);
		model.addAttribute("selectedEndDate", endDate);
		return "home/index";
	}

	@GetMapping(value = "/sample_status_by_sample_type", produces = "application/json")
	public ResponseEntity<Map<String, Object>> getSampleStatusBySampleType(
			@RequestParam(required = false, defaultValue = "0") Integer region,
			@RequestParam(required = false, defaultValue = "0") Integer district,
			@RequestParam(required = false, defaultValue = "0") Integer site,
			@RequestParam(required = false, defaultValue = "0") Integer lab,
			@RequestParam(required = false, defaultValue = "0") Integer circuit,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
		Map<String, Object> response = new HashMap<String, Object>();

		if (startDate == null)
			startDate = LocalDate.now().minusYears(2);
		if (endDate == null)
			endDate = LocalDate.now();

		if (region == 0) {
			region = null;
		}
		if (district == 0) {
			district = null;
		}
		if (site == 0) {
			site = null;
		}
		if (lab == 0) {
			lab = null;
		}
		if (circuit == 0) {
			circuit = null;
		}

		ScopedFilter scopeByType = userScopeService.intersectCurrent(region, district, site, lab);
		Map<String, Map<String, Integer>> sampleCount;
		if (scopeByType.isForceEmpty()) {
			sampleCount = java.util.Collections.emptyMap();
		} else {
			sampleCount = sampleService.getSampleStatusBySampleType(scopeByType.getRegionId(),
					scopeByType.getDistrictId(), scopeByType.getSiteId(), scopeByType.getLabId(),
					startDate, endDate, scopeByType.getAccessibleSiteIds());
		}
		Set<String> categories = sampleCount.keySet();
		List<Integer> collected = new ArrayList<Integer>();
		List<Integer> delivered = new ArrayList<Integer>();
		List<Integer> nonConform = new ArrayList<Integer>();
		List<Integer> failed = new ArrayList<Integer>();
		List<Integer> analysisDone = new ArrayList<Integer>();
		List<Integer> resultCollected = new ArrayList<Integer>();
		List<Integer> resultOnSite = new ArrayList<Integer>();

		categories.forEach(el -> {
			sampleCount.forEach((k, v) -> {
				if (el.equalsIgnoreCase(k)) {
					collected.add(v.get("SAMPLE_COLLECTED"));
					delivered.add(v.get("SAMPLE_RECEIVED"));
					nonConform.add(v.get("NON_CONFORM"));
					failed.add(v.get("FAILED"));
					// accepted.add(v.get("SAMPLE_ACCEPTED"));
					analysisDone.add(v.get("ANALYSIS_DONE"));
					resultCollected.add(v.get("RESULT_COLLECTED"));
					resultOnSite.add(v.get("RESULT_ON_SITE"));
				}
			});
		});
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
	public Map<String, Object> series(
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(name = "region", required = false) Integer regionId,
			@RequestParam(name = "district", required = false) Integer districtId,
			@RequestParam(name = "site", required = false) Integer siteId,
			@RequestParam(name = "lab", required = false) Integer labId) {

		if (startDate == null)
			startDate = LocalDate.now().minusYears(2);
		if (endDate == null)
			endDate = LocalDate.now();

		ScopedFilter scope = userScopeService.intersectCurrent(regionId, districtId, siteId, labId);
		Map<String, Object> out = new HashMap<>();
		if (scope.isForceEmpty()) {
			out.put("collected", java.util.List.of());
			out.put("deposited", java.util.List.of());
			out.put("analysed", java.util.List.of());
			out.put("delivered", java.util.List.of());
			return out;
		}
		Integer r = scope.getRegionId();
		Integer d = scope.getDistrictId();
		Integer si = scope.getSiteId();
		Integer la = scope.getLabId();
		java.util.List<Integer> accessible = scope.getAccessibleSiteIds();
		out.put("collected", service.seriesCollected(startDate, endDate, r, d, si, la, accessible));
		out.put("deposited", service.seriesDeposited(startDate, endDate, r, d, si, la, accessible));
		out.put("analysed", service.seriesAnalysed(startDate, endDate, r, d, si, la, accessible));
		out.put("delivered", service.seriesDelivered(startDate, endDate, r, d, si, la, accessible));
		return out;
	}

	@GetMapping("/data/step-durations")
	@ResponseBody
	public List<StepDurationDTO> stepDurations(
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(name = "region", required = false) Integer regionId,
			@RequestParam(name = "district", required = false) Integer districtId,
			@RequestParam(name = "site", required = false) Integer siteId,
			@RequestParam(name = "lab", required = false) Integer labId) {
		if (startDate == null)
			startDate = LocalDate.now().minusYears(2);
		if (endDate == null)
			endDate = LocalDate.now();

		ScopedFilter scope = userScopeService.intersectCurrent(regionId, districtId, siteId, labId);
		if (scope.isForceEmpty()) {
			return java.util.List.of();
		}
		return service.stepDurations(startDate, endDate, scope.getRegionId(), scope.getDistrictId(),
				scope.getSiteId(), scope.getLabId(), scope.getAccessibleSiteIds());
	}

	/**
	 * Consolidated KPIs for the redesigned dashboard cards & funnel.
	 * Returns total, in_transit, at_hub, at_lab, analysed, result_collected,
	 * delivered, non_conform, failed, tat_avg_days.
	 */
	@GetMapping(value = "/data/funnel", produces = "application/json")
	@ResponseBody
	public Map<String, Object> funnel(
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(required = false) Integer region, @RequestParam(required = false) Integer district,
			@RequestParam(required = false) Integer site, @RequestParam(required = false) Integer lab) {
		if (startDate == null) startDate = LocalDate.now().minusYears(2);
		if (endDate == null) endDate = LocalDate.now();

		ScopedFilter scope = userScopeService.intersectCurrent(region, district, site, lab);
		if (scope.isForceEmpty()) {
			return java.util.Map.of("total", 0, "in_transit", 0, "at_hub", 0, "at_lab", 0,
					"analysed", 0, "result_collected", 0, "delivered", 0,
					"non_conform", 0, "failed", 0, "tat_avg_days", 0);
		}
		return advancedRepo.funnel(startDate, endDate, scope.getRegionId(), scope.getDistrictId(),
				scope.getSiteId(), scope.getLabId(), scope.getAccessibleSiteIds());
	}

	/**
	 * Same KPIs as {@link #funnel} but for the immediately previous period of
	 * equal length (used for trend arrows on the cards).
	 */
	@GetMapping(value = "/data/funnel-previous", produces = "application/json")
	@ResponseBody
	public Map<String, Object> funnelPrevious(
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(required = false) Integer region, @RequestParam(required = false) Integer district,
			@RequestParam(required = false) Integer site, @RequestParam(required = false) Integer lab) {
		if (startDate == null) startDate = LocalDate.now().minusYears(2);
		if (endDate == null) endDate = LocalDate.now();
		long lengthDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
		LocalDate prevEnd = startDate.minusDays(1);
		LocalDate prevStart = prevEnd.minusDays(lengthDays);

		ScopedFilter scope = userScopeService.intersectCurrent(region, district, site, lab);
		if (scope.isForceEmpty()) {
			return java.util.Map.of("total", 0);
		}
		return advancedRepo.funnel(prevStart, prevEnd, scope.getRegionId(), scope.getDistrictId(),
				scope.getSiteId(), scope.getLabId(), scope.getAccessibleSiteIds());
	}

	/**
	 * Samples that have not moved for at least N days and are not in a terminal
	 * state. Limit 50 by default to keep the dashboard responsive.
	 */
	@GetMapping(value = "/data/stuck", produces = "application/json")
	@ResponseBody
	public List<Map<String, Object>> stuckSamples(
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(required = false) Integer region, @RequestParam(required = false) Integer district,
			@RequestParam(required = false) Integer site, @RequestParam(required = false) Integer lab,
			@RequestParam(name = "stuck_days", defaultValue = "7") int stuckDays,
			@RequestParam(defaultValue = "50") int limit) {
		if (startDate == null) startDate = LocalDate.now().minusYears(2);
		if (endDate == null) endDate = LocalDate.now();

		ScopedFilter scope = userScopeService.intersectCurrent(region, district, site, lab);
		if (scope.isForceEmpty()) return java.util.List.of();
		return advancedRepo.stuckSamples(startDate, endDate, scope.getRegionId(), scope.getDistrictId(),
				scope.getSiteId(), scope.getLabId(), scope.getAccessibleSiteIds(),
				Math.max(stuckDays, 1), Math.min(Math.max(limit, 10), 500));
	}

	/**
	 * Aggregated stats by region — top level of the hierarchical drill-down.
	 */
	@GetMapping(value = "/data/by-region", produces = "application/json")
	@ResponseBody
	public List<Map<String, Object>> statsByRegion(
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(name = "lab", required = false) Integer labId) {
		if (startDate == null) startDate = LocalDate.now().minusYears(2);
		if (endDate == null) endDate = LocalDate.now();
		ScopedFilter scope = userScopeService.intersectCurrent(null, null, null, labId);
		if (scope.isForceEmpty()) return java.util.List.of();
		return advancedRepo.statsByRegion(startDate, endDate, scope.getLabId(), scope.getAccessibleSiteIds());
	}

	@GetMapping(value = "/data/by-district", produces = "application/json")
	@ResponseBody
	public List<Map<String, Object>> statsByDistrict(
			@RequestParam(name = "region", required = false) Integer regionId,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(name = "lab", required = false) Integer labId) {
		if (startDate == null) startDate = LocalDate.now().minusYears(2);
		if (endDate == null) endDate = LocalDate.now();
		ScopedFilter scope = userScopeService.intersectCurrent(regionId, null, null, labId);
		if (scope.isForceEmpty()) return java.util.List.of();
		return advancedRepo.statsByDistrict(regionId, startDate, endDate, scope.getLabId(), scope.getAccessibleSiteIds());
	}

	@GetMapping(value = "/data/by-site", produces = "application/json")
	@ResponseBody
	public List<Map<String, Object>> statsBySite(
			@RequestParam(name = "district", required = false) Integer districtId,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(name = "lab", required = false) Integer labId) {
		if (startDate == null) startDate = LocalDate.now().minusYears(2);
		if (endDate == null) endDate = LocalDate.now();
		ScopedFilter scope = userScopeService.intersectCurrent(null, districtId, null, labId);
		if (scope.isForceEmpty()) return java.util.List.of();
		return advancedRepo.statsBySite(districtId, startDate, endDate, scope.getLabId(), scope.getAccessibleSiteIds());
	}

	/**
	 * Program coverage indicators for the selected period: active sites/districts/
	 * regions, conveyors, labs, total distance, average distance per sample.
	 */
	@GetMapping(value = "/data/coverage", produces = "application/json")
	@ResponseBody
	public Map<String, Object> coverage(
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(required = false) Integer region, @RequestParam(required = false) Integer district,
			@RequestParam(required = false) Integer site, @RequestParam(required = false) Integer lab) {
		if (startDate == null) startDate = LocalDate.now().minusYears(2);
		if (endDate == null) endDate = LocalDate.now();

		ScopedFilter scope = userScopeService.intersectCurrent(region, district, site, lab);
		if (scope.isForceEmpty()) {
			return java.util.Map.of();
		}
		return advancedRepo.coverage(startDate, endDate, scope.getRegionId(), scope.getDistrictId(),
				scope.getSiteId(), scope.getLabId(), scope.getAccessibleSiteIds());
	}

	/**
	 * Sample-type breakdown for the period (used by the coverage section's
	 * mini doughnut chart).
	 */
	@GetMapping(value = "/data/type-breakdown", produces = "application/json")
	@ResponseBody
	public List<Map<String, Object>> typeBreakdown(
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(required = false) Integer region, @RequestParam(required = false) Integer district,
			@RequestParam(required = false) Integer site, @RequestParam(required = false) Integer lab) {
		if (startDate == null) startDate = LocalDate.now().minusYears(2);
		if (endDate == null) endDate = LocalDate.now();

		ScopedFilter scope = userScopeService.intersectCurrent(region, district, site, lab);
		if (scope.isForceEmpty()) {
			return java.util.List.of();
		}
		return advancedRepo.typeBreakdown(startDate, endDate, scope.getRegionId(), scope.getDistrictId(),
				scope.getSiteId(), scope.getLabId(), scope.getAccessibleSiteIds());
	}

	/**
	 * Top performers / laggards: rejection-prone sites, slowest labs and
	 * most active conveyors. Three separate lists in one response so the
	 * dashboard does a single round-trip.
	 */
	@GetMapping(value = "/data/top-performers", produces = "application/json")
	@ResponseBody
	public Map<String, Object> topPerformers(
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
			@RequestParam(name = "limit", defaultValue = "5") int limit,
			@RequestParam(name = "min_samples", defaultValue = "5") int minSamples) {
		if (startDate == null) startDate = LocalDate.now().minusYears(2);
		if (endDate == null) endDate = LocalDate.now();

		ScopedFilter scope = userScopeService.intersectCurrent(null, null, null, null);
		if (scope.isForceEmpty()) {
			return java.util.Map.of("rejection_sites", java.util.List.of(),
					"slowest_labs", java.util.List.of(),
					"top_conveyors", java.util.List.of());
		}

		Map<String, Object> out = new java.util.LinkedHashMap<>();
		out.put("rejection_sites", advancedRepo.topRejectionSites(startDate, endDate,
				scope.getAccessibleSiteIds(), limit, minSamples));
		out.put("slowest_labs", advancedRepo.slowestLabs(startDate, endDate,
				scope.getAccessibleSiteIds(), limit, minSamples));
		out.put("top_conveyors", advancedRepo.topConveyors(startDate, endDate,
				scope.getAccessibleSiteIds(), limit));
		return out;
	}

}
