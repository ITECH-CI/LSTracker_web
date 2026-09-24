/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.controller;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.entities.Site;
import org.itech.labSampleTracker.exception.ResourceNotFoundException;
import org.itech.labSampleTracker.service.DistrictService;
import org.itech.labSampleTracker.service.SiteService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/site")
public class SiteController extends BaseController {

	@Autowired
	private SiteService siteService;

	@Autowired
	private DistrictService districtService;

	@Autowired
	private org.itech.labSampleTracker.service.RegionService regionService;

	@Autowired
	private org.itech.labSampleTracker.dao.SiteRepository siteRepository;

	@Autowired
	private org.itech.labSampleTracker.service.security.UserScopeService userScopeService;



	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@PostMapping(value = "/new")
	public String createSite(Model model, @Valid Site site) {
		try {
			Site data = siteService.create(site);
			if (data != null) {
				model.addAttribute("message_success", "Ajout effectué avec succès");
			} else {
				model.addAttribute("message_error", "Impossible d'ajouter le site");
			}
		} catch (Exception e) {
			model.addAttribute("message_error", e.getMessage());
		}
		model.addAttribute("site", new Site());
		return "site/new";
	}

	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@GetMapping(value = "/new")
	public String newAppUser(Model model) {
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		model.addAttribute("site", new Site());
		return "site/new";
	}

	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@GetMapping(value = "")
	public String getAllSite(Model model) {
		// La liste réelle est chargée via /site/data (DataTables server-side).
		// On expose juste les options de filtre.
		model.addAttribute("regions", regionService.getRegionIdAndName());
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		return "site/index";
	}

	/**
	 * JSON endpoint feeding the DataTables-driven site admin list.
	 */
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@GetMapping(value = "/data", produces = "application/json")
	@org.springframework.web.bind.annotation.ResponseBody
	public Map<String, Object> getSitesData(
			@RequestParam(name = "draw", defaultValue = "1") int draw,
			@RequestParam(name = "start", defaultValue = "0") int start,
			@RequestParam(name = "length", defaultValue = "25") int length,
			@RequestParam(name = "search_text", required = false) String searchText,
			@RequestParam(name = "region", required = false) Integer regionId,
			@RequestParam(name = "district", required = false) Integer districtId) {

		String search = (searchText == null || searchText.isBlank()) ? null : searchText.trim();
		int page = length > 0 ? (start / length) : 0;
		org.springframework.data.domain.Pageable pageable =
				org.springframework.data.domain.PageRequest.of(page, length > 0 ? length : 25);

		// Apply geographic scope: silent intersection with the user's scope.
		org.itech.labSampleTracker.service.security.UserScopeService.ScopedFilter scope =
				userScopeService.intersectCurrent(regionId, districtId, null, null);

		Map<String, Object> out = new java.util.LinkedHashMap<>();
		out.put("draw", draw);
		if (scope.isForceEmpty()) {
			out.put("recordsTotal", 0L);
			out.put("recordsFiltered", 0L);
			out.put("data", java.util.List.of());
			return out;
		}

		boolean active = scope.getAccessibleSiteIds() != null && !scope.getAccessibleSiteIds().isEmpty();
		java.util.List<Integer> siteIds = active ? scope.getAccessibleSiteIds() : java.util.List.of(-1);

		org.springframework.data.domain.Page<Map<String, Object>> p = siteRepository
				.findSitesAdvanced(pageable, search,
						scope.getRegionId(), scope.getDistrictId(),
						siteIds, active);

		out.put("recordsTotal", p.getTotalElements());
		out.put("recordsFiltered", p.getTotalElements());
		out.put("data", p.getContent());
		return out;
	}

	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@GetMapping(value = "/update/{id}")
	public String getOneSite(@PathVariable("id") Integer id, Model model) {
		Site site = new Site();
		try {
			site = siteService.getOne(id);
			if (ObjectUtils.isEmpty(site)) {
				throw new ResourceNotFoundException("Impossible de retrouver les données de ce site ");
			}
		} catch (Exception ex) {
			model.addAttribute("message_error", ex.getMessage());
		}
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		model.addAttribute("site", site);
		return "site/edit";
	}

	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@PostMapping(value = "/update/{id}")
	public String updateSite(@PathVariable("id") Integer id, @Valid Site site, Model model) {
		Site siteToUpdate;
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		try {
			siteToUpdate = siteService.getOne(id);
			if (ObjectUtils.isEmpty(site)) {
				model.addAttribute("message_error", "Impossible de retrouver les données de ce site");
				return "site/edit";
			}

			BeanUtils.copyProperties(site, siteToUpdate, "id", "isActive");
			siteService.create(siteToUpdate);
			model.addAttribute("message_success", "Modification effectuée avec succès");
		} catch (Exception ex) {
			model.addAttribute("message_error", ex.getMessage());
			model.addAttribute("site", site);
			return "site/edit";
		}
		
		model.addAttribute("site", siteToUpdate);
		return "site/edit";
	}

	@GetMapping(value = "names", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getSiteNames() {
		List<Map<String, Object>> siteList = siteService.getSiteIdAndNames();
		return new ResponseEntity<List<Map<String, Object>>>(siteList, HttpStatus.OK);
	}
	
	@GetMapping(value = "names_circuits", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getSiteNamesCircuits() {
		List<Map<String, Object>> siteList = siteService.getSiteIdAndNamesAndCicuit();
		return new ResponseEntity<List<Map<String, Object>>>(siteList, HttpStatus.OK);
	}

	@GetMapping(value = "names/{district}", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getSiteNames(@PathVariable("district") Integer districtId) {
		List<Map<String, Object>> siteList = siteService.getSiteIdAndNamesByDistrict(districtId);
		return new ResponseEntity<List<Map<String, Object>>>(siteList, HttpStatus.OK);
	}

	@GetMapping(value = "names_by_districts", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getDistrictNamesAll(@RequestParam List<Integer> districts) {
		List<Map<String, Object>> siteList = siteService.getSiteIdAndNamesByDistricts(districts);
		return new ResponseEntity<List<Map<String, Object>>>(siteList, HttpStatus.OK);
	}
	
	@GetMapping(value = "names/by_user", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getSiteNamesByUser() {
		List<Map<String, Object>> siteList = siteService.getSiteIdAndCodeAndNamesByUser(this.getCurrentUserId());
		return new ResponseEntity<List<Map<String, Object>>>(siteList, HttpStatus.OK);
	}

}
