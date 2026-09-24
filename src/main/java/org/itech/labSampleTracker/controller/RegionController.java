/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.controller;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.entities.Region;
import org.itech.labSampleTracker.exception.ResourceNotFoundException;
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
 * <h2>RegionController</h2>
 */
@Controller
@RequestMapping("/region")
public class RegionController extends BaseController {

	@Autowired
	private RegionService regionService;


	@PostMapping(value = "/new")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String createRegion(Model model, @Valid Region region) {
		try {
			Region data = regionService.create(region);
			if (data != null) {
				model.addAttribute("message_success", "Ajout effectué avec succès");
			} else {
				model.addAttribute("message_error", "Impossible d'ajouter la région");
			}
		} catch (Exception e) {
			model.addAttribute("message_error", e.getMessage());
		}
		model.addAttribute("region", new Region());
		return "region/new";
	}

	@GetMapping(value = "/new")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String newRegion(Model model) {
		model.addAttribute("region", new Region());
		return "region/new";
	}

	@GetMapping(value = "")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String getAllRegion(Model model) {
		// La liste réelle est chargée via /region/data (DataTables server-side).
		return "region/index";
	}

	/**
	 * JSON endpoint feeding the DataTables-driven region admin list.
	 */
	@GetMapping(value = "/data", produces = "application/json")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@org.springframework.web.bind.annotation.ResponseBody
	public Map<String, Object> getRegionsData(
			@RequestParam(name = "draw", defaultValue = "1") int draw,
			@RequestParam(name = "start", defaultValue = "0") int start,
			@RequestParam(name = "length", defaultValue = "25") int length,
			@RequestParam(name = "search_text", required = false) String searchText) {

		String search = (searchText == null || searchText.isBlank()) ? null : searchText.trim();
		int page = length > 0 ? (start / length) : 0;
		org.springframework.data.domain.Pageable pageable =
				org.springframework.data.domain.PageRequest.of(page, length > 0 ? length : 25);

		org.springframework.data.domain.Page<Map<String, Object>> p =
				regionService.findRegionsAdvanced(pageable, search);

		Map<String, Object> out = new java.util.LinkedHashMap<>();
		out.put("draw", draw);
		out.put("recordsTotal", p.getTotalElements());
		out.put("recordsFiltered", p.getTotalElements());
		out.put("data", p.getContent());
		return out;
	}

	@GetMapping(value = "/update/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String getOneRegion(@PathVariable("id") Integer id, Model model) {
		Region region = new Region();
		try {
			region = regionService.getOne(id);
			if (ObjectUtils.isEmpty(region)) {
				throw new ResourceNotFoundException("Impossible de retrouver les données de cette région ");
			}
		} catch (Exception ex) {
			model.addAttribute("message_error", ex.getMessage());
		}
		model.addAttribute("region", region);
		return "region/edit";
	}

	@PostMapping(value = "/update/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String updateRegion(@PathVariable("id") Integer id, @Valid Region region, Model model) {
		Region regionToUpdate;
		try {
			regionToUpdate = regionService.getOne(id);
			if (ObjectUtils.isEmpty(regionToUpdate)) {
				model.addAttribute("message_error", "Impossible de retrouver les données de cette région");
				return "region/edit";
			}

			BeanUtils.copyProperties(region, regionToUpdate, "id", "isActive");
			regionService.create(regionToUpdate);
			model.addAttribute("message_success", "Modification effectuée avec succès");
		} catch (Exception ex) {
			model.addAttribute("message_error", ex.getMessage());
			model.addAttribute("region", region);
			return "region/edit";
		}

		model.addAttribute("region", regionToUpdate);
		return "region/edit";
	}

	@GetMapping(value = "names", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getRegionNames() {
		List<Map<String, Object>> regionList = regionService.getRegionIdAndName();
		return new ResponseEntity<List<Map<String, Object>>>(regionList, HttpStatus.OK);
	}

}
