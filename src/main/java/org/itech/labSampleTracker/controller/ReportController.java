package org.itech.labSampleTracker.controller;

import java.time.LocalDate;

import org.itech.labSampleTracker.dto.ReportItem;
import org.itech.labSampleTracker.service.AppUserService;
import org.itech.labSampleTracker.service.DistrictService;
import org.itech.labSampleTracker.service.LabService;
import org.itech.labSampleTracker.service.RegionService;
import org.itech.labSampleTracker.service.ReportItemService;
import org.itech.labSampleTracker.service.ReportService;
import org.itech.labSampleTracker.service.SiteService;
import org.itech.labSampleTracker.service.security.UserScopeService;
import org.itech.labSampleTracker.service.security.UserScopeService.ScopedFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.itech.labSampleTracker.security.ActivityLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(value = { "/report" })
public class ReportController {

	private static final Logger log = LoggerFactory.getLogger(ReportController.class);

	@Autowired
	private AppUserService userService;

	@Autowired
	private SiteService siteService;

	@Autowired
	private RegionService regionService;

	@Autowired
	private DistrictService districtService;

	@Autowired
	private LabService labService;

	@Autowired
	private ReportService reportService;

	@Autowired
	private ReportItemService reportItemService;

	@Autowired
	private UserScopeService userScopeService;

	@Autowired
	private ActivityLogService activityLog;

	@GetMapping(value = "")
	public String reportIndex(Model model) {

		model.addAttribute("regions", regionService.getRegionIdAndName());
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		model.addAttribute("sites", siteService.getSiteIdAndNames());
		model.addAttribute("labs", labService.getLabIdAndNames());
		model.addAttribute("riders", userService.getRiderIdAndName());
		return "report/index";
	}

	@GetMapping(value = "/print", produces = { MediaType.APPLICATION_PDF_VALUE })
	public ResponseEntity<Resource> printReport(@RequestParam(required = false, name = "region") Integer regionId,
			@RequestParam(required = false, name = "district") Integer districtId,
			@RequestParam(required = false, name = "lab") Integer labId,
			@RequestParam(required = false, name = "rider") Integer conveyorId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

		String fileName;
		String reportFileName;
		if (conveyorId != null) {
			reportFileName = "conveyor_report.jasper";
			fileName = "rapport_convoyeur.pdf";
		} else if (labId != null) {
			reportFileName = "lab_report.jasper";
			fileName = "rapport_labo.pdf";
		} else if (districtId != null) {
			reportFileName = "district_report.jasper";
			fileName = "rapport_district.pdf";
		} else {
			reportFileName = "region_report.jasper";
			fileName = "rapport_region.pdf";
		}

		activityLog.recordAction(ActivityLogService.EXPORT, "Rapport", null,
				"Rapport PDF " + fileName + " : du " + start + " au " + end
						+ (regionId != null ? ", région " + regionId : "") + (districtId != null ? ", district " + districtId : "")
						+ (labId != null ? ", labo " + labId : "") + (conveyorId != null ? ", convoyeur " + conveyorId : ""));

		// Apply geographic scope to silently restrict report content to the
		// user's accessible regions/districts/sites/labs.
		ScopedFilter scope = userScopeService.intersectCurrent(regionId, districtId, null, labId);
		ReportItem items;
		if (scope.isForceEmpty()) {
			log.info("Report request out of user scope (region={}, district={}, lab={}, rider={}), returning empty PDF",
					regionId, districtId, labId, conveyorId);
			items = new ReportItem();
		} else {
			items = reportItemService.buildForScope(start, end, scope.getRegionId(), scope.getDistrictId(), null,
					scope.getLabId(), conveyorId, scope.getAccessibleSiteIds());
			if (items == null) {
				items = new ReportItem();
			}
		}
		byte[] reportContent = reportService.getReportData(items, reportFileName);
		ByteArrayResource resource = new ByteArrayResource(reportContent);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.contentLength(resource.contentLength()).header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(fileName).build().toString())
				.body(resource);
	}

}
