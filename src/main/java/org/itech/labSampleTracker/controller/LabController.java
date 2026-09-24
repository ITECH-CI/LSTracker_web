/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.controller;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.entities.Lab;
import org.itech.labSampleTracker.enums.LabType;
import org.itech.labSampleTracker.enums.UserType;
import org.itech.labSampleTracker.exception.ResourceNotFoundException;
import org.itech.labSampleTracker.service.AppUserService;
import org.itech.labSampleTracker.service.DistrictService;
import org.itech.labSampleTracker.service.LabService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <h2>LabController</h2>
 */
@Controller
@RequestMapping("/lab")
public class LabController extends BaseController {

	@Autowired
	private LabService labService;

	@Autowired
	private DistrictService districtService;

	@Autowired
	private org.itech.labSampleTracker.service.RegionService regionService;

	@Autowired
	private org.itech.labSampleTracker.dao.LabRepository labRepository;


	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@PostMapping(value = "/new")
	public String createLab(Model model, @Valid Lab lab) {
		model.addAttribute("labTypes", LabType.values());
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		try {
			lab.setCreatedAt(new Date());
			lab.setIsActive(true);
			lab.setLabType(lab.getLabType());
			Lab data = labService.create(lab);
			if (data != null) {
				model.addAttribute("message_success", "Ajout effectué avec succès");
			} else {
				model.addAttribute("message_error", "Impossible d'ajouter le lab");
			}
		} catch (Exception e) {
			model.addAttribute("message_error", e.getMessage());
		}
		model.addAttribute("lab", new Lab());
		return "lab/new";
	}

	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@GetMapping(value = "/new")
	public String newAppUser(Model model) {
		model.addAttribute("labTypes", LabType.values());
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		model.addAttribute("lab", new Lab());
		return "lab/new";
	}

	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@GetMapping(value = "")
	public String getAllLab(Model model) {
		// La liste est chargée via /lab/data (DataTables server-side).
		model.addAttribute("labTypes", LabType.values());
		model.addAttribute("regions", regionService.getRegionIdAndName());
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		return "lab/index";
	}

	/**
	 * JSON endpoint feeding the DataTables-driven lab admin list.
	 */
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@GetMapping(value = "/data", produces = "application/json")
	@org.springframework.web.bind.annotation.ResponseBody
	public Map<String, Object> getLabsData(
			@org.springframework.web.bind.annotation.RequestParam(name = "draw", defaultValue = "1") int draw,
			@org.springframework.web.bind.annotation.RequestParam(name = "start", defaultValue = "0") int start,
			@org.springframework.web.bind.annotation.RequestParam(name = "length", defaultValue = "25") int length,
			@org.springframework.web.bind.annotation.RequestParam(name = "search_text", required = false) String searchText,
			@org.springframework.web.bind.annotation.RequestParam(name = "region", required = false) Integer regionId,
			@org.springframework.web.bind.annotation.RequestParam(name = "district", required = false) Integer districtId,
			@org.springframework.web.bind.annotation.RequestParam(name = "lab_type", required = false) List<String> labTypes,
			@org.springframework.web.bind.annotation.RequestParam(name = "active", required = false) Boolean activeFlag) {

		String search = (searchText == null || searchText.isBlank()) ? null : searchText.trim();
		boolean typesActive = labTypes != null && !labTypes.isEmpty();
		List<String> effectiveTypes = typesActive ? labTypes : List.of("__none__");

		int page = length > 0 ? (start / length) : 0;
		org.springframework.data.domain.Pageable pageable =
				org.springframework.data.domain.PageRequest.of(page, length > 0 ? length : 25);

		org.springframework.data.domain.Page<Map<String, Object>> p = labRepository
				.findLabsAdvanced(pageable, search, regionId, districtId,
						effectiveTypes, typesActive, activeFlag);

		Map<String, Object> out = new java.util.LinkedHashMap<>();
		out.put("draw", draw);
		out.put("recordsTotal", p.getTotalElements());
		out.put("recordsFiltered", p.getTotalElements());
		out.put("data", p.getContent());
		return out;
	}

	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@GetMapping(value = "/update/{id}")
	public String getOneLab(@PathVariable("id") Integer id, Model model) {
		model.addAttribute("labTypes", LabType.values());
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		Lab lab = new Lab();
		try {
			lab = labService.getOne(id);
			if (ObjectUtils.isEmpty(lab)) {
				throw new ResourceNotFoundException("Impossible de retrouver les données de ce labo ");
			}
		} catch (Exception ex) {
			model.addAttribute("message_error", ex.getMessage());
		}
		model.addAttribute("lab", lab);
		return "lab/edit";
	}

	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@PostMapping(value = "/update/{id}")
	public String updateLab(@PathVariable("id") Integer id, @Valid Lab lab, Model model) {
		model.addAttribute("labTypes", LabType.values());
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		Lab labToUpdate;
		model.addAttribute("districts", districtService.getDistrictIdAndNames());
		try {
			labToUpdate = labService.getOne(id);
			if (ObjectUtils.isEmpty(lab)) {
				model.addAttribute("message_error", "Impossible de retrouver les données de ce labo");
				return "lab/edit";
			}

			BeanUtils.copyProperties(lab, labToUpdate, "id", "createdAt", "isActive");
			labToUpdate.setLabType(lab.getLabType());
			labService.create(labToUpdate);
			model.addAttribute("message_success", "Modification effectuée avec succès");
		} catch (Exception ex) {
			model.addAttribute("message_error", ex.getMessage());
			model.addAttribute("lab", lab);
			return "lab/edit";
		}

		model.addAttribute("lab", labToUpdate);
		return "lab/edit";
	}

	@GetMapping(value = "name_by_id/{labId}", produces = "application/json")
	public ResponseEntity<Map<String, Object>> getLabName(@PathVariable Integer labId) {
		Lab thisLab = labService.getOne(labId);
		Map<String, Object> lab = new HashMap<String, Object>();
		lab.put("id", thisLab.getId());
		lab.put("name", thisLab.getLabName());
		lab.put("labType", thisLab.getLabType());
		return new ResponseEntity<Map<String, Object>>(lab, HttpStatus.OK);
	}

	@GetMapping(value = "names", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getLabNames() {
		List<Map<String, Object>> labList = labService.getLabIdAndNames();
		return new ResponseEntity<List<Map<String, Object>>>(labList, HttpStatus.OK);
	}

	@GetMapping(value = "names_by_users", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getLabNamesByRider() {
		List<Map<String, Object>> labList = new ArrayList<Map<String, Object>>();
		AppUser user = accountService.getOne(getCurrentUserId());
		// Type effectif = role OU user_type (cf. MetaService.isType)
		String biologist = UserType.BIOLOGIST.getType();
		if (biologist.equalsIgnoreCase(user.getUserType()) || biologist.equalsIgnoreCase(user.getRole())) {
			labList = labService.getAllLabIdAndNamesByLabUser(user.getId());
		} else {
			labList = labService.getAllLabIdAndNamesByRider(user.getId());	
		}
		return new ResponseEntity<List<Map<String, Object>>>(labList, HttpStatus.OK);
	}

	@GetMapping(value = "names/{district}", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getRegionNames(@PathVariable("district") Integer districtId) {
		List<Map<String, Object>> labList = labService.getLabIdAndNamesByDistrict(districtId);
		return new ResponseEntity<List<Map<String, Object>>>(labList, HttpStatus.OK);
	}

	@GetMapping(value = "reference_lab", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getReferenceLabs() {
		List<Map<String, Object>> labList = labService.getReferenceLabIdAndNames();
		return new ResponseEntity<List<Map<String, Object>>>(labList, HttpStatus.OK);
	}

	@GetMapping(value = "hub", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getHubs() {
		List<Map<String, Object>> labList = labService.getHubIdAndNames();
		return new ResponseEntity<List<Map<String, Object>>>(labList, HttpStatus.OK);
	}

}
