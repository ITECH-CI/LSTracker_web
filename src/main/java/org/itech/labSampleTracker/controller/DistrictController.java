/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:03 )
 */
package org.itech.labSampleTracker.controller;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.entities.District;
import org.itech.labSampleTracker.exception.ResourceNotFoundException;
import org.itech.labSampleTracker.service.DistrictService;
import org.itech.labSampleTracker.service.RegionService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

/**
 * <h2>DistrictController</h2>
 */
@Controller
@RequestMapping("/district")
public class DistrictController extends BaseController {

	@Autowired
	private DistrictService districtService;

	@Autowired
	private RegionService regionService;


	@PostMapping(value = "/new")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String createDistrict(Model model, @Valid District district) {
		model.addAttribute("regions", regionService.getRegionIdAndName());
		try {
			District data = districtService.create(district);
			if (data != null) {
				model.addAttribute("message_success", "Ajout effectué avec succès");
			} else {
				model.addAttribute("message_error", "Impossible d'ajouter le district");
			}
		} catch (Exception e) {
			model.addAttribute("message_error", e.getMessage());
		}
		model.addAttribute("district", new District());
		return "district/new";
	}

	@GetMapping(value = "/new")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String newDistrict(Model model) {
		model.addAttribute("regions", regionService.getRegionIdAndName());
		model.addAttribute("district", new District());
		return "district/new";
	}

	@GetMapping(value = "")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String getAllDistrict(Model model) {
		// La liste réelle est chargée via /district/data (DataTables server-side).
		model.addAttribute("regions", regionService.getRegionIdAndName());
		return "district/index";
	}

	/**
	 * JSON endpoint feeding the DataTables-driven district admin list.
	 */
	@GetMapping(value = "/data", produces = "application/json")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@org.springframework.web.bind.annotation.ResponseBody
	public Map<String, Object> getDistrictsData(
			@RequestParam(name = "draw", defaultValue = "1") int draw,
			@RequestParam(name = "start", defaultValue = "0") int start,
			@RequestParam(name = "length", defaultValue = "25") int length,
			@RequestParam(name = "search_text", required = false) String searchText,
			@RequestParam(name = "region", required = false) Integer regionId) {

		String search = (searchText == null || searchText.isBlank()) ? null : searchText.trim();
		int page = length > 0 ? (start / length) : 0;
		org.springframework.data.domain.Pageable pageable =
				org.springframework.data.domain.PageRequest.of(page, length > 0 ? length : 25);

		org.springframework.data.domain.Page<Map<String, Object>> p =
				districtService.findDistrictsAdvanced(pageable, search, regionId);

		Map<String, Object> out = new java.util.LinkedHashMap<>();
		out.put("draw", draw);
		out.put("recordsTotal", p.getTotalElements());
		out.put("recordsFiltered", p.getTotalElements());
		out.put("data", p.getContent());
		return out;
	}

	@GetMapping(value = "/update/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String getOneDistrict(@PathVariable("id") Integer id, Model model) {
		District district = new District();
		try {
			district = districtService.getOne(id);
			if (ObjectUtils.isEmpty(district)) {
				throw new ResourceNotFoundException("Impossible de retrouver les données de ce district ");
			}
		} catch (Exception ex) {
			model.addAttribute("message_error", ex.getMessage());
		}
		model.addAttribute("regions", regionService.getRegionIdAndName());
		model.addAttribute("district", district);
		return "district/edit";
	}

	@PostMapping(value = "/update/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String updateDistrict(@PathVariable("id") Integer id, @Valid District district, Model model) {
		model.addAttribute("regions", regionService.getRegionIdAndName());
		District districtToUpdate;
		try {
			districtToUpdate = districtService.getOne(id);
			if (ObjectUtils.isEmpty(districtToUpdate)) {
				model.addAttribute("message_error", "Impossible de retrouver les données de ce district");
				return "district/edit";
			}

			BeanUtils.copyProperties(district, districtToUpdate, "id", "isActive");
			districtService.create(districtToUpdate);
			model.addAttribute("message_success", "Modification effectuée avec succès");
		} catch (Exception ex) {
			model.addAttribute("message_error", ex.getMessage());
			model.addAttribute("district", district);
			return "district/edit";
		}

		model.addAttribute("district", districtToUpdate);
		return "district/edit";
	}

	@GetMapping(value = "/names", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getDistrictNames() {
		List<Map<String, Object>> districtList = districtService.getDistrictIdAndNames();
		return new ResponseEntity<List<Map<String, Object>>>(districtList, HttpStatus.OK);
	}

	@GetMapping(value = "/names/{region}", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getDistrictNames(@PathVariable("region") Integer regionId) {
		List<Map<String, Object>> districtList = districtService.getDistrictIdAndNamesByRegion(regionId);
		return new ResponseEntity<List<Map<String, Object>>>(districtList, HttpStatus.OK);
	}

	@GetMapping(value = "/names_by_regions", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getDistrictNamesAll(@RequestParam List<Integer> regions) {
		List<Map<String, Object>> districtList = districtService.getDistrictIdAndNamesByRegions(regions);
		return new ResponseEntity<List<Map<String, Object>>>(districtList, HttpStatus.OK);
	}

}
