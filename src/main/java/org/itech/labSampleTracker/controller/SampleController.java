package org.itech.labSampleTracker.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.dto.SampleUpdateDTO;
import org.itech.labSampleTracker.entities.Lab;
import org.itech.labSampleTracker.entities.Sample;
import org.itech.labSampleTracker.entities.SampleRejection;
import org.itech.labSampleTracker.entities.SampleStatus;
import org.itech.labSampleTracker.enums.ESampleStatus;
import org.itech.labSampleTracker.exception.ResourceNotFoundException;
import org.itech.labSampleTracker.helper.ExportUtils;
import org.itech.labSampleTracker.helper.SampleDateValidator;
import org.itech.labSampleTracker.service.DistrictService;
import org.itech.labSampleTracker.service.LabService;
import org.itech.labSampleTracker.service.RegionService;
import org.itech.labSampleTracker.service.SampleRejectionService;
import org.itech.labSampleTracker.service.SampleRejectionTypeService;
import org.itech.labSampleTracker.service.SampleService;
import org.itech.labSampleTracker.service.SampleStatusService;
import org.itech.labSampleTracker.service.SampleTypeService;
import org.itech.labSampleTracker.service.SiteService;
import org.itech.labSampleTracker.service.security.UserScopeService;
import org.itech.labSampleTracker.service.security.UserScopeService.ScopedFilter;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/sample")
@Slf4j
public class SampleController {

	@Autowired
	private SampleService sampleService;

	@Autowired
	private SampleTypeService sampleTypeService;

	@Autowired
	private SiteService siteService;

	@Autowired
	private LabService labService;

	@Autowired
	private RegionService regionService;

	@Autowired
	private DistrictService districtService;

	@Autowired
	private SampleStatusService sampleStatusService;

	@Autowired
	private SampleRejectionService sampleRejectionService;

	@Autowired
	private SampleRejectionTypeService sampleRejectionTypeService;

	@Autowired
	private UserScopeService userScopeService;

	@Autowired
	private org.itech.labSampleTracker.dao.TrackingEventRepository trackingEventRepo;

	@Autowired
	private org.itech.labSampleTracker.service.AppUserService appUserService;

	@InitBinder
	public void initBinder(WebDataBinder binder) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
		dateFormat.setLenient(false);
		binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));
	}

	@PostMapping(value = "")
	public String createSample(@Valid Sample model) {

		SampleDateValidator.validate(model);
		Sample data = sampleService.create(model);
		if (data != null) {
			return "";
		} else {
			return "";
		}
	}

	@GetMapping(value = "")
	public String getAllSample(Model model, @RequestParam(defaultValue = "collection_date,desc") String[] sort,
			@RequestParam(required = false, defaultValue = "0") Integer region,
			@RequestParam(required = false, defaultValue = "0") Integer district,
			@RequestParam(required = false, defaultValue = "0") Integer site,
			@RequestParam(required = false, defaultValue = "0") Integer circuit,
			@RequestParam(required = false, defaultValue = "0") Integer lab,
			@RequestParam(required = false, defaultValue = "0") Integer status,
			@RequestParam(required = false, name = "start", defaultValue = "") String startDateString,
			@RequestParam(required = false, name = "end", defaultValue = "") String endDateString,
			@RequestParam(required = false, name = "sample_type", defaultValue = "0") Integer sampleType,
			@RequestParam(required = false, name = "patient_identifier", defaultValue = "") String patientIdentifier,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
		Date startDate = null;
		Date endDate = null;
		try {
			if (ObjectUtils.isEmpty(startDateString)) {
				Calendar calendar = Calendar.getInstance();
				calendar.set(Calendar.YEAR, 2000);
				calendar.set(Calendar.MONTH, Calendar.JANUARY);
				calendar.set(Calendar.DAY_OF_MONTH, 1);
				startDate = calendar.getTime();
			} else {
				startDate = sdf.parse(startDateString);
			}
			if (ObjectUtils.isEmpty(endDateString)) {
				endDate = new Date();
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(endDate);
				calendar.add(Calendar.DAY_OF_MONTH, 1);
				endDate = calendar.getTime();
			} else {
				endDate = sdf.parse(endDateString);
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(endDate);
				calendar.add(Calendar.DAY_OF_MONTH, 1);
				endDate = calendar.getTime();
			}

		} catch (Exception e) {
			log.warn(e.getMessage());
			startDate = null;
			startDateString = null;
			endDate = null;
			endDateString = null;
		}
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
		if (status == 0) {
			status = null;
		}
		if (sampleType == 0) {
			sampleType = null;
		}
		if (ObjectUtils.isEmpty(patientIdentifier)) {
			patientIdentifier = null;
		}

		Pageable pageable = PageRequest.of(page - 1, size, Sort.by(this.getSortOrder(sort)));

		// Apply geographic scope: silently intersect client-supplied filters with
		// what the current user is allowed to see. Out-of-scope filters force an
		// empty result so no data leaks across regions/districts/sites.
		ScopedFilter scope = userScopeService.intersectCurrent(region, district, site, lab);
		Page<Map<String, Object>> sampleListPageable;
		if (scope.isForceEmpty()) {
			log.info("Filters out of user scope, returning empty page (region={}, district={}, site={}, lab={})",
					region, district, site, lab);
			sampleListPageable = Page.empty(pageable);
		} else {
			sampleListPageable = sampleService.getSampleDetailsScoped(pageable,
					scope.getRegionId(), scope.getDistrictId(), scope.getSiteId(), scope.getLabId(),
					startDate, endDate, status, sampleType, patientIdentifier,
					scope.getAccessibleSiteIds());
		}

		List<Map<String, Object>> sites = siteService.getSiteIdAndNames();
		List<Map<String, Object>> regions = regionService.getRegionIdAndName();
		List<Map<String, Object>> districts = districtService.getDistrictIdAndNames();
		List<SampleStatus> statusList = sampleStatusService.getAll();
		List<Map<String, Object>> sampleTypeList = sampleTypeService.getSampleTypeIdAndName();
		if (ObjectUtils.isNotEmpty(region)) {
			districts = districtService.getDistrictIdAndNamesByRegion(region);
		}
		if (ObjectUtils.isNotEmpty(district)) {
			sites = siteService.getSiteIdAndNamesByDistrict(district);
		}

		List<Map<String, Object>> sampleList = sampleListPageable.getContent();
		model.addAttribute("totalElements", sampleListPageable.getTotalElements());
		model.addAttribute("totalPages", sampleListPageable.getTotalPages());
		model.addAttribute("size", sampleListPageable.getSize());
		model.addAttribute("currentPage", sampleListPageable.getNumber() + 1);
		model.addAttribute("numberOfElements", sampleListPageable.getNumberOfElements());
		model.addAttribute("first", sampleListPageable.isFirst());
		model.addAttribute("last", sampleListPageable.isLast());
		model.addAttribute("empty", sampleListPageable.isEmpty());
		model.addAttribute("selectedSite", site);
		model.addAttribute("selectedRegion", region);
		model.addAttribute("selectedDistrict", district);
		model.addAttribute("selectedLab", lab);
		model.addAttribute("selectedStartDate", startDateString);
		model.addAttribute("selectedEndDate", endDateString);
		model.addAttribute("selectedStatus", status);
		model.addAttribute("samples", sampleList);
		model.addAttribute("sampleTypeList", sampleTypeList);
		model.addAttribute("selectedSampleType", sampleType);
		model.addAttribute("natureList", sampleService.getDistinctSampleNatures());
		model.addAttribute("patientIdentifier", patientIdentifier);

		model.addAttribute("labs", labService.getLabIdAndNames());
		model.addAttribute("regions", regions);
		model.addAttribute("districts", districts);
		model.addAttribute("sites", sites);
		model.addAttribute("statusList", statusList);

		Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
		boolean canEdit = currentAuth != null && currentAuth.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.anyMatch(a -> "ROLE_ADMIN".equals(a) || "ROLE_SUPER_ADMIN".equals(a));
		model.addAttribute("canEditSamples", canEdit);

		return "sample/index";
	}

	@PostMapping(value = "/delete/{id}")
	public String deleteSample(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		UserDetails userDetails = (UserDetails) authentication.getPrincipal();
		if (userDetails.getAuthorities().stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"))) {
			if (ObjectUtils.isNotEmpty(id)) {
				if (sampleService.removeById(id)) {
					redirectAttributes.addFlashAttribute("message", "Echantillon supprimé avec succès");
					return "redirect:/sample";
				}
			}
		}
		return "redirect:/sample";
	}

	@GetMapping(value = "/{id}")
	public String getOneSample(@PathVariable("id") Integer id, Model model) {

		Sample e = sampleService.getOne(id);
		if (e == null) {
			return "";
		}
		model.addAttribute("sample", e);
		return "";
	}

	@GetMapping(value = "/update/{id}")
	public String updateSample(@PathVariable("id") Integer id, Model model) {
		SampleUpdateDTO sampleToUpdate = new SampleUpdateDTO();
		Sample sample = new Sample();
		try {
			sample = sampleService.getOne(id);
			if (ObjectUtils.isEmpty(sample)) {
				throw new ResourceNotFoundException("Impossible de retrouver les données de cet échantillon ");
			}

			BeanUtils.copyProperties(sample, sampleToUpdate);
			SampleRejection rejection = sampleRejectionService.getOneBySampleId(id);
			if (ObjectUtils.isNotEmpty(rejection)) {
				sampleToUpdate.setRejectionTypeId(rejection.getId());
				sampleToUpdate.setRejectionComment(rejection.getComment());
				sampleToUpdate.setRejectionDate(rejection.getRejectionDate());
			}
			Integer sampleRequesterId = ObjectUtils.isNotEmpty(sample.getSampleRetrieving())?sample.getSampleRetrieving().getSiteId():sample.getRequesterSiteId().intValue();
			sampleToUpdate.setRequesterSiteId(sampleRequesterId);

		} catch (Exception ex) {
			model.addAttribute("message_error", ex.getMessage());
		}

		model.addAttribute("sample", sampleToUpdate);
		model.addAttribute("sites", siteService.getSiteIdAndNames());
		model.addAttribute("sampleRejectionTypes", sampleRejectionTypeService.getAll());
		model.addAttribute("labs", labService.getLabIdAndNames());
		model.addAttribute("sampleStatus", sampleStatusService.getAll());
		model.addAttribute("sampleTypes", sampleTypeService.getAll());
		return "sample/edit";
	}

	@PostMapping(value = "/update/{id}")
	public String updateSample(@PathVariable("id") Integer id, @ModelAttribute SampleUpdateDTO sampleToUpdate,
			Model model) {
		Sample updatedSample = new Sample();
		Sample oldSample = new Sample();
		try {
			oldSample = sampleService.getOne(id);
			if (ObjectUtils.isEmpty(oldSample)) {
				throw new ResourceNotFoundException("Impossible de retrouver les données de cet échantillon ");
			}

			// FIX bug "Detached entity uninitialized version" :
			// On part de l'entité chargée (qui a `version`, `uuid`, etc.)
			// puis on applique PAR-DESSUS les champs du DTO. Si on créait
			// une entité vierge, Hibernate refuserait l'update avec id
			// existant + version null (optimistic locking @Version).
			updatedSample = oldSample;
			BeanUtils.copyProperties(sampleToUpdate, updatedSample, "id", "version", "uuid",
					"createdAt", "sampleRetrievingId");

			if (oldSample.getSampleStatusId()
					.equals(sampleStatusService.findByStatus(ESampleStatus.NON_CONFORM.name()).getId())
					&& !sampleToUpdate.getSampleStatusId()
							.equals(sampleStatusService.findByStatus(ESampleStatus.NON_CONFORM.name()).getId())) {
				// remove rejectedSample
				sampleRejectionService.removeBySampleId(id);
				updatedSample.setRejectionDate(null);

			} else if (!oldSample.getSampleStatusId()
					.equals(sampleStatusService.findByStatus(ESampleStatus.NON_CONFORM.name()).getId())
					&& sampleToUpdate.getSampleStatusId()
							.equals(sampleStatusService.findByStatus(ESampleStatus.NON_CONFORM.name()).getId())) {
				// create SampleRejection
				SampleRejection rejection = new SampleRejection();
				rejection.setCreatedAt(new Date());
				rejection.setSampleRejectionTypeId(sampleToUpdate.getRejectionTypeId());
				rejection.setSampleId(sampleToUpdate.getId());
				rejection.setComment(sampleToUpdate.getRejectionComment());
				rejection = sampleRejectionService.create(rejection);
				updatedSample.setRejectionDate(new Date());
			}

			SampleDateValidator.validate(updatedSample);
			updatedSample.setLastupdatedAt(new Date());
			updatedSample = sampleService.create(updatedSample);
			model.addAttribute("message_success", "Échantillon modifié avec succès");

		} catch (Exception ex) {
			model.addAttribute("message_error",
					"Impossible de modifier l'échantillon : " + ex.getMessage());
		}

		model.addAttribute("sample", sampleToUpdate);
		model.addAttribute("sites", siteService.getSiteIdAndNames());
		model.addAttribute("sampleRejectionTypes", sampleRejectionTypeService.getAll());
		model.addAttribute("labs", labService.getLabIdAndNames());
		model.addAttribute("sampleStatus", sampleStatusService.getAll());
		model.addAttribute("sampleTypes", sampleTypeService.getAll());
		return "sample/edit";
	}

	protected List<Order> getSortOrder(String[] sorts) {
		List<Order> orders = new ArrayList<>();
		if (sorts[0].contains(",")) {
			for (String sortOrder : sorts) {
				String[] sort = sortOrder.split(",");
				orders.add(new Order(getSortDirection(sort[1]), sort[0]));
			}
		} else {
			orders.add(new Order(getSortDirection(sorts[1]), sorts[0]));
		}

		return orders;
	}

	private Sort.Direction getSortDirection(String direction) {
		if (direction.equals("asc")) {
			return Sort.Direction.ASC;
		} else if (direction.equals("desc")) {
			return Sort.Direction.DESC;
		}
		return Sort.Direction.ASC;
	}

	/**
	 * JSON endpoint feeding the redesigned sample table (DataTables server-side).
	 * Accepts DataTables standard params (draw, start, length, search[value],
	 * order[0][column], order[0][dir], columns[i][data]) plus advanced filters.
	 */
	@GetMapping(value = "/data", produces = "application/json")
	@org.springframework.web.bind.annotation.ResponseBody
	public Map<String, Object> getSampleData(
			@RequestParam(name = "draw", defaultValue = "1") int draw,
			@RequestParam(name = "start", defaultValue = "0") int start,
			@RequestParam(name = "length", defaultValue = "20") int length,
			@RequestParam(name = "search[value]", required = false) String searchValue,
			@RequestParam(name = "order[0][column]", required = false) Integer orderColumn,
			@RequestParam(name = "order[0][dir]", required = false) String orderDir,
			// Advanced filters
			@RequestParam(required = false) Integer region,
			@RequestParam(required = false) Integer district,
			@RequestParam(required = false) Integer site,
			@RequestParam(required = false) Integer lab,
			// Listes en CSV (cf. JS data: function ci-dessus) pour éviter
			// d'avoir besoin de `traditional:true` qui casserait la
			// sérialisation des `columns[N][data]` de DataTables.
			@RequestParam(name = "status", required = false) String statusCsv,
			@RequestParam(name = "sample_type", required = false) String typeCsv,
			@RequestParam(name = "sample_nature", required = false) String natureCsv,
			@RequestParam(name = "start_date", required = false) String startDateString,
			@RequestParam(name = "end_date", required = false) String endDateString,
			@RequestParam(name = "search_text", required = false) String searchText,
			@RequestParam(name = "only_rejected", defaultValue = "false") boolean onlyRejected,
			@RequestParam(name = "stuck_days", required = false) Integer stuckDays,
			// Tous les autres paramètres (columns[X][data] dynamiques) sont
			// captés par la map ci-dessous — pas besoin de les énumérer.
			@RequestParam Map<String, String> allParams) {

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Date startDate = parseDateSilently(sdf, startDateString);
		Date endDate = parseDateSilently(sdf, endDateString);

		// Parse CSV filters → List (sent as CSV from JS to bypass jQuery
		// traditional-serialize issue with DataTables columns[N][data]).
		List<Integer> statusIds = parseIntCsv(statusCsv);
		List<Integer> typeIds = parseIntCsv(typeCsv);
		List<String> natures = parseStringCsv(natureCsv);

		// Effective search: DataTables global search overrides search_text when present
		String effectiveSearch = (searchValue != null && !searchValue.isBlank()) ? searchValue : searchText;

		// Sort: récupère le nom de la colonne triée via allParams.get("columns[N][data]")
		// au lieu d'énumérer chaque colonne — supporte un nombre quelconque
		// de colonnes (utile maintenant qu'on en a 20 avec les colonnes
		// optionnelles).
		String orderedColName = (orderColumn != null)
				? allParams.get("columns[" + orderColumn + "][data]")
				: null;
		String sortKey = (orderedColName != null && !orderedColName.isBlank())
				? mapClientColumnToDb(orderedColName)
				: "collection_date";
		String sortDir = "asc".equalsIgnoreCase(orderDir) ? "asc" : "desc";

		int page = length > 0 ? (start / length) : 0;
		Pageable pageable = PageRequest.of(page, length > 0 ? length : 20);

		// Apply geographic scope (silent intersection)
		ScopedFilter scope = userScopeService.intersectCurrent(region, district, site, lab);

		Map<String, Object> result = new java.util.LinkedHashMap<>();
		result.put("draw", draw);

		if (scope.isForceEmpty()) {
			log.info("Sample data request out of user scope, returning empty");
			result.put("recordsTotal", 0L);
			result.put("recordsFiltered", 0L);
			result.put("data", java.util.List.of());
			return result;
		}

		Page<Map<String, Object>> p = sampleService.getSampleDetailsAdvanced(pageable,
				scope.getRegionId(), scope.getDistrictId(), scope.getSiteId(), scope.getLabId(),
				startDate, endDate,
				statusIds, typeIds, natures,
				scope.getAccessibleSiteIds(),
				effectiveSearch, onlyRejected, stuckDays,
				sortKey, sortDir);

		result.put("recordsTotal", p.getTotalElements());
		result.put("recordsFiltered", p.getTotalElements());
		result.put("data", p.getContent());
		return result;
	}

	private static Date parseDateSilently(SimpleDateFormat sdf, String s) {
		if (s == null || s.isBlank()) return null;
		try { return sdf.parse(s.trim()); } catch (Exception ignored) { return null; }
	}

	private static List<Integer> parseIntCsv(String csv) {
		if (csv == null || csv.isBlank()) return null;
		List<Integer> out = new java.util.ArrayList<>();
		for (String s : csv.split(",")) {
			String t = s.trim();
			if (t.isEmpty()) continue;
			try { out.add(Integer.parseInt(t)); } catch (NumberFormatException ignored) { /* skip */ }
		}
		return out.isEmpty() ? null : out;
	}

	private static List<String> parseStringCsv(String csv) {
		if (csv == null || csv.isBlank()) return null;
		List<String> out = new java.util.ArrayList<>();
		for (String s : csv.split(",")) {
			String t = s.trim();
			if (!t.isEmpty()) out.add(t);
		}
		return out.isEmpty() ? null : out;
	}

	/**
	 * Sample detail + timeline of tracking events. Used by the redesigned
	 * list page's "view detail" modal.
	 */
	@GetMapping(value = "/{id}/details", produces = "application/json")
	@org.springframework.web.bind.annotation.ResponseBody
	public ResponseEntity<Map<String, Object>> getSampleDetails(@PathVariable("id") Integer id) {
		Sample sample = sampleService.getOne(id);
		if (sample == null) {
			return ResponseEntity.notFound().build();
		}

		Map<String, Object> result = new java.util.LinkedHashMap<>();
		Map<String, Object> sampleData = new java.util.LinkedHashMap<>();
		sampleData.put("id", sample.getId());
		sampleData.put("uuid", sample.getUuid());
		sampleData.put("sampleIdentifier", sample.getSampleIdentifier());
		sampleData.put("patientIdentifier", sample.getPatientIdentifier());
		sampleData.put("sampleNature", sample.getSampleNature());
		sampleData.put("labNumber", sample.getLabNumber());
		sampleData.put("collectionDate", sample.getCollectionDate());
		sampleData.put("pickupDate", sample.getPickupDate());
		sampleData.put("deliverAtHubDate", sample.getDeliverAtHubDate());
		sampleData.put("deliverAtLabDate", sample.getDeliverAtLabDate());
		sampleData.put("acceptedAtHubDate", sample.getAcceptedAtHubDate());
		sampleData.put("acceptedAtLabDate", sample.getAcceptedAtLabDate());
		sampleData.put("analysisCompletedDate", sample.getAnalysisCompletedDate());
		sampleData.put("analysisReleasedDate", sample.getAnalysisReleasedDate());
		sampleData.put("resultCollectionDate", sample.getResultCollectionDate());
		sampleData.put("resultDeliveryDate", sample.getResultDeliveryDate());
		sampleData.put("rejectionDate", sample.getRejectionDate());
		sampleData.put("collectionStartMileage", sample.getCollectionStartMileage());
		sampleData.put("collectionEndMileage", sample.getCollectionEndMileage());
		sampleData.put("resultStartMileage", sample.getResultStartMileage());
		sampleData.put("resultEndMileage", sample.getResultEndMileage());
		// Distances calculées (cf. liste DataTables)
		if (sample.getCollectionStartMileage() != null && sample.getCollectionEndMileage() != null) {
			sampleData.put("collectionDistance",
					sample.getCollectionEndMileage() - sample.getCollectionStartMileage());
		}
		if (sample.getResultStartMileage() != null && sample.getResultEndMileage() != null) {
			sampleData.put("resultDistance",
					sample.getResultEndMileage() - sample.getResultStartMileage());
		}
		sampleData.put("requesterSiteName", sample.getRequesterSiteName());
		sampleData.put("destinationLabName", sample.getDestinationLabName());
		// Labos additionnels (référence + hub) si présents
		if (sample.getLabId() != null) {
			Lab refLab = labService.getOne(sample.getLabId());
			if (refLab != null) sampleData.put("referenceLabName", refLab.getLabName());
		}
		if (sample.getHubId() != null) {
			Lab hubLab = labService.getOne(sample.getHubId());
			if (hubLab != null) sampleData.put("hubLabName", hubLab.getLabName());
		}
		// Conveyor (qui a collecté). sampleConveyorId est Long, getOne attend
		// un int → on cast (les IDs app_user sont en réalité de petits entiers).
		if (sample.getSampleConveyorId() != null) {
			org.itech.labSampleTracker.entities.AppUser conv =
					appUserService.getOne(sample.getSampleConveyorId().intValue());
			if (conv != null) {
				sampleData.put("conveyorName",
						(conv.getFirstName() != null ? conv.getFirstName() : "")
						+ " " + (conv.getLastName() != null ? conv.getLastName() : ""));
				sampleData.put("conveyorLogin", conv.getLogin());
			}
		}
		// Result collector (qui a collecté les résultats)
		if (sample.getResultCollectorId() != null) {
			org.itech.labSampleTracker.entities.AppUser rc =
					appUserService.getOne(sample.getResultCollectorId().intValue());
			if (rc != null) {
				sampleData.put("resultCollectorName",
						(rc.getFirstName() != null ? rc.getFirstName() : "")
						+ " " + (rc.getLastName() != null ? rc.getLastName() : ""));
			}
		}

		if (sample.getSampleType() != null) {
			sampleData.put("sampleType", sample.getSampleType().getName());
		}
		SampleStatus st = sampleStatusService.getOne(sample.getSampleStatusId());
		if (st != null) {
			sampleData.put("status", st.getStatus());
			sampleData.put("statusDescription", st.getDescription());
		}

		SampleRejection rejection = sampleRejectionService.getOneBySampleId(id);
		if (rejection != null) {
			Map<String, Object> rej = new java.util.LinkedHashMap<>();
			rej.put("comment", rejection.getComment());
			rej.put("rejectionDate", rejection.getRejectionDate());
			rej.put("rejectionTypeId", rejection.getSampleRejectionTypeId());
			result.put("rejection", rej);
		}

		List<Map<String, Object>> timeline = new java.util.ArrayList<>();
		List<org.itech.labSampleTracker.entities.TrackingEvent> events =
				trackingEventRepo.findBySampleIdOrderByCreatedAtAsc(id);
		for (var e : events) {
			Map<String, Object> ev = new java.util.LinkedHashMap<>();
			ev.put("id", e.getId());
			ev.put("eventType", e.getEventType());
			ev.put("orgType", e.getOrgType());
			ev.put("orgId", e.getOrgId());
			ev.put("mileage", e.getMileage());
			ev.put("longitude", e.getLongitude());
			ev.put("latitude", e.getLatitude());
			ev.put("createdAt", e.getCreatedAt());
			ev.put("appUserId", e.getAppUserId());
			if (e.getAppUserId() != null) {
				var user = appUserService.getOne(e.getAppUserId());
				if (user != null) {
					ev.put("appUserLogin", user.getLogin());
					ev.put("appUserName", (user.getFirstName() != null ? user.getFirstName() : "")
							+ " " + (user.getLastName() != null ? user.getLastName() : ""));
				}
			}
			timeline.add(ev);
		}

		result.put("sample", sampleData);
		result.put("timeline", timeline);
		return ResponseEntity.ok(result);
	}

	private static String mapClientColumnToDb(String clientCol) {
		// Whitelist mapping client column id → SELECT-alias of the underlying
		// native query (see SampleRepository#getSampleDetailsAdvanced).
		// We use the aliases (not table-qualified expressions) because Spring
		// Data wraps paginated native queries in a subquery — only the
		// SELECT-list aliases remain visible to the outer ORDER BY.
		// Injected as-is via JpaSort.unsafe; whitelist prevents SQL injection.
		return switch (clientCol) {
			case "district" -> "district";
			case "site" -> "site";
			case "sample_type" -> "sample_type";
			case "sample_nature" -> "sample_nature";
			case "patient_identifier" -> "patient_identifier";
			case "sample_identifier" -> "sample_identifier";
			case "collection_date" -> "collection_date";
			case "destination_lab" -> "destination_lab";
			case "status" -> "status";
			case "reception_date" -> "reception_date";
			case "lab" -> "lab";
			case "lab_number" -> "lab_number";
			case "tat_days" -> "tat_days";
			default -> "collection_date";
		};
	}

	@GetMapping(value = "/csv")
	public ResponseEntity<Resource> getSampleInCSV(Model model,
			@RequestParam(required = false, defaultValue = "0") Integer region,
			@RequestParam(required = false, defaultValue = "0") Integer district,
			@RequestParam(required = false, defaultValue = "0") Integer site,
			@RequestParam(required = false, defaultValue = "0") Integer lab,
			@RequestParam(required = false, defaultValue = "0") Integer status,
			@RequestParam(required = false, name = "sample_type", defaultValue = "0") Integer sampleType,
			@RequestParam(required = false, name = "patient_identifier", defaultValue = "") String patientIdentifier,
			@RequestParam(required = false, name = "start", defaultValue = "") String startDateString,
			@RequestParam(required = false, name = "end", defaultValue = "") String endDateString) {
		String filename = "sample_export.csv";
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
		Date startDate = null;
		Date endDate = null;
		try {
			if (ObjectUtils.isEmpty(startDateString)) {
				Calendar calendar = Calendar.getInstance();
				calendar.set(Calendar.YEAR, 2000);
				calendar.set(Calendar.MONTH, Calendar.JANUARY);
				calendar.set(Calendar.DAY_OF_MONTH, 1);
				startDate = calendar.getTime();
			} else {
				startDate = sdf.parse(startDateString);
			}
			if (ObjectUtils.isEmpty(endDateString)) {
				endDate = new Date();
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(endDate);
				calendar.add(Calendar.DAY_OF_MONTH, 1);
				endDate = calendar.getTime();
			} else {
				endDate = sdf.parse(endDateString);
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(endDate);
				calendar.add(Calendar.DAY_OF_MONTH, 1);
				endDate = calendar.getTime();
			}

		} catch (Exception e) {
			log.warn(e.getMessage());
			startDate = null;
			startDateString = null;
			endDate = null;
			endDateString = null;
		}
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
		if (status == 0) {
			status = null;
		}
		if (sampleType == 0) {
			sampleType = null;
		}
		if (ObjectUtils.isEmpty(patientIdentifier)) {
			patientIdentifier = null;
		}

		// Périmètre de l'utilisateur : même règle que la liste et le tableau de bord.
		ScopedFilter scope = userScopeService.intersectCurrent(region, district, site, lab);
		List<Map<String, String>> sampleRecords = scope.isForceEmpty() ? java.util.List.of()
				: sampleService.getAll(scope.getRegionId(), scope.getDistrictId(), scope.getSiteId(), scope.getLabId(),
						startDate, endDate, status, sampleType, patientIdentifier, scope.getAccessibleSiteIds());

		InputStreamResource file = new InputStreamResource(ExportUtils.writeCSVData(sampleRecords));

		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
				.contentType(MediaType.parseMediaType("application/csv")).body(file);

	}

}
