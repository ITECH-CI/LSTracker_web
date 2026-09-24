/* 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:03 )
 */
package org.itech.labSampleTracker.controller;

import org.apache.commons.lang3.ObjectUtils;
import org.itech.labSampleTracker.dto.CircuitDTO;
import org.itech.labSampleTracker.entities.Circuit;
import org.itech.labSampleTracker.entities.CircuitSite;
import org.itech.labSampleTracker.exception.ResourceNotFoundException;
import org.itech.labSampleTracker.service.CircuitService;
import org.itech.labSampleTracker.service.CircuitSiteService;
import org.itech.labSampleTracker.service.SiteService;
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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <h2>CircuitController</h2>
 */
@Controller
@RequestMapping("/circuit")
public class CircuitController extends BaseController{

	@Autowired
	private CircuitService circuitService;

	@Autowired
	private CircuitSiteService circuitSiteService;

	@Autowired
	private SiteService siteService;

	@Autowired
	private org.itech.labSampleTracker.dao.CircuitRepository circuitRepository;


	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@PostMapping(value = "/new")
	public String createCircuit(Model model, @Valid CircuitDTO circuitDTO) {
		model.addAttribute("sites", siteService.getSiteIdAndNames());
		model.addAttribute("circuit", circuitDTO);
		Circuit circuit = new Circuit();
		try {
			circuit.setCircuitNumber(circuitDTO.getCircuit().getCircuitNumber());
			circuit.setCreatedAt(new Date());
			circuit.setIsActive(true);
			Circuit data = circuitService.create(circuit);
			if (data != null) {
				circuitDTO.getSites().forEach(siteId -> {
					CircuitSite circuitSite = new CircuitSite();
					circuitSite.setCircuit(circuit);
					circuitSite.setCircuitId(circuit.getId());
					circuitSite.setSiteId(siteId);
					circuitSite.setIsActive(true);
					circuitSite.setCreatedAt(new Date());
					circuitSiteService.create(circuitSite);
				});

				model.addAttribute("message_success", "Ajout effectué avec succès");
			} else {
				model.addAttribute("message_error", "Impossible d'ajouter le circuit");
			}
		} catch (Exception e) {
			model.addAttribute("message_error", e.getMessage());
		}
		model.addAttribute("circuit", new CircuitDTO());
		return "circuit/new";
	}

	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@GetMapping(value = "/new")
	public String newAppUser(Model model) {
		model.addAttribute("sites", siteService.getSiteIdAndNames());
		model.addAttribute("circuit", new CircuitDTO());
		return "circuit/new";
	}

	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@GetMapping(value = "")
	public String getAllCircuit(Model model) {
		// La liste réelle est chargée via /circuit/data (DataTables server-side).
		return "circuit/index";
	}

	/**
	 * JSON endpoint feeding the DataTables-driven circuit admin list.
	 */
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@GetMapping(value = "/data", produces = "application/json")
	@org.springframework.web.bind.annotation.ResponseBody
	public Map<String, Object> getCircuitsData(
			@org.springframework.web.bind.annotation.RequestParam(name = "draw", defaultValue = "1") int draw,
			@org.springframework.web.bind.annotation.RequestParam(name = "start", defaultValue = "0") int start,
			@org.springframework.web.bind.annotation.RequestParam(name = "length", defaultValue = "25") int length,
			@org.springframework.web.bind.annotation.RequestParam(name = "search_text", required = false) String searchText,
			@org.springframework.web.bind.annotation.RequestParam(name = "active", required = false) Boolean activeFlag) {

		String search = (searchText == null || searchText.isBlank()) ? null : searchText.trim();
		int page = length > 0 ? (start / length) : 0;
		org.springframework.data.domain.Pageable pageable =
				org.springframework.data.domain.PageRequest.of(page, length > 0 ? length : 25);

		org.springframework.data.domain.Page<Map<String, Object>> p = circuitRepository
				.findCircuitsAdvanced(pageable, search, activeFlag);

		Map<String, Object> out = new java.util.LinkedHashMap<>();
		out.put("draw", draw);
		out.put("recordsTotal", p.getTotalElements());
		out.put("recordsFiltered", p.getTotalElements());
		out.put("data", p.getContent());
		return out;
	}

	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@GetMapping(value = "/update/{id}")
	public String getOneCircuit(@PathVariable("id") Integer id, Model model) {
		model.addAttribute("sites", siteService.getSiteIdAndNames());
		CircuitDTO circuitDTO = new CircuitDTO();
		Circuit circuit = new Circuit();
		try {
			circuit = circuitService.getOne(id);
			if (ObjectUtils.isEmpty(circuit)) {
				throw new ResourceNotFoundException("Impossible de retrouver les données de ce circuit ");
			}
			circuitDTO.setCircuit(circuit);
			List<CircuitSite> circuitSites = circuitSiteService.getAllByCircuit(circuit.getId());
			List<Integer> circuitSiteIds = circuitSites.stream().map(circuitSite -> circuitSite.getSiteId())
					.collect(Collectors.toList());
			circuitDTO.setSites(circuitSiteIds);
		} catch (Exception ex) {
			model.addAttribute("message_error", ex.getMessage());
		}
		model.addAttribute("circuit", circuitDTO);
		return "circuit/edit";
	}

	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@PostMapping(value = "/update/{id}")
	public String updateCircuit(@PathVariable("id") Integer id, @Valid CircuitDTO circuitDTO, Model model) {
		model.addAttribute("sites", siteService.getSiteIdAndNames());
		model.addAttribute("circuit", circuitDTO);
		Circuit circuitToUpdate;
		try {
			circuitToUpdate = circuitService.getOne(id);
			if (ObjectUtils.isEmpty(circuitDTO)) {
				model.addAttribute("message_error", "Impossible de retrouver les données de ce circuit");
				return "circuit/edit";
			}
			circuitToUpdate.setIsActive(circuitDTO.getCircuit().getIsActive());
			circuitToUpdate.setCircuitNumber(circuitDTO.getCircuit().getCircuitNumber());
			Circuit circuitUpdated = circuitService.create(circuitToUpdate);
			if (circuitSiteService.removeAllByCircuitId(circuitUpdated.getId())) {
				circuitDTO.getSites().forEach(siteId -> {
					CircuitSite circuitSite = new CircuitSite();
					circuitSite.setCircuit(circuitUpdated);
					circuitSite.setCircuitId(circuitUpdated.getId());
					circuitSite.setSiteId(siteId);
					circuitSite.setIsActive(true);
					circuitSite.setCreatedAt(new Date());
					circuitSite = circuitSiteService.create(circuitSite);
				});
			}
			circuitDTO.setCircuit(circuitUpdated);
			model.addAttribute("message_success", "Modification effectuée avec succès");
		} catch (Exception ex) {
			model.addAttribute("message_error", ex.getMessage());
			return "circuit/edit";
		}
		model.addAttribute("circuit", circuitDTO);
		return "circuit/edit";
	}

	@GetMapping(value = "numbers", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getCircuitNames() {
		List<Map<String, Object>> circuitList = circuitService.getCircuitIdAndNumber();
		return new ResponseEntity<List<Map<String, Object>>>(circuitList, HttpStatus.OK);
	}
	
	@GetMapping(value = "numbers_by_users", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getCircuitNamesByUser() {
		List<Map<String, Object>> circuitList = circuitService.getCircuitIdAndNumberByUser(this.getCurrentUserId());
		return new ResponseEntity<List<Map<String, Object>>>(circuitList, HttpStatus.OK);
	}

	@GetMapping(value = "sites/{circuitId}", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getCircuitSites(@PathVariable Integer circuitId) {
		List<Map<String, Object>> circuitList = circuitService.getCircuitSitesIdAndName(circuitId);
		return new ResponseEntity<List<Map<String, Object>>>(circuitList, HttpStatus.OK);
	}
	
	@GetMapping(value = "by_user", produces = "application/json")
	public ResponseEntity<List<Map<String, Object>>> getCircuitByUser() {
		List<Map<String, Object>> circuitList = circuitService.getCircuitIdAndNumberByUser(this.getCurrentUserId());
		return new ResponseEntity<List<Map<String, Object>>>(circuitList, HttpStatus.OK);
	}

}
