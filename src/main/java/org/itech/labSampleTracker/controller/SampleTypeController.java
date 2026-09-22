/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.controller;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.itech.labSampleTracker.entities.SampleType;
import org.itech.labSampleTracker.exception.ResourceNotFoundException;
import org.itech.labSampleTracker.service.SampleTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

/**
 * <h2>SampleTypeController</h2>
 *
 * Administration du referentiel des types d'echantillon (BI, BS, CV, EID,
 * TB, HPV...). Calque sur {@link RegionController} : liste DataTables
 * server-side, formulaires de creation et d'edition, suppression gardee.
 */
@Controller
@RequestMapping("/sampletype")
public class SampleTypeController extends BaseController {

	@Autowired
	private SampleTypeService sampletypeService;

	@GetMapping(value = "")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String getAllSampleType(Model model) {
		// La liste reelle est chargee via /sampletype/data (DataTables).
		return "sampletype/index";
	}

	/**
	 * JSON endpoint alimentant la liste DataTables.
	 */
	@GetMapping(value = "/data", produces = "application/json")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@ResponseBody
	public Map<String, Object> getSampleTypesData(
			@RequestParam(name = "draw", defaultValue = "1") int draw,
			@RequestParam(name = "start", defaultValue = "0") int start,
			@RequestParam(name = "length", defaultValue = "25") int length,
			@RequestParam(name = "search_text", required = false) String searchText) {

		int page = length > 0 ? (start / length) : 0;
		Pageable pageable = PageRequest.of(page, length > 0 ? length : 25);

		Page<Map<String, Object>> p = sampletypeService.findForAdmin(pageable, searchText);

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("draw", draw);
		out.put("recordsTotal", p.getTotalElements());
		out.put("recordsFiltered", p.getTotalElements());
		out.put("data", p.getContent());
		return out;
	}

	@GetMapping(value = "/new")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String newSampleType(Model model) {
		// Un POST en erreur redirige ici avec la saisie en flash attribute :
		// on ne la remplace pas par un formulaire vierge.
		if (!model.containsAttribute("sampletype")) {
			SampleType sampletype = new SampleType();
			sampletype.setIsActive(Boolean.TRUE);
			model.addAttribute("sampletype", sampletype);
		}
		return "sampletype/new";
	}

	/**
	 * Post/Redirect/Get : on redirige systematiquement apres le POST, pour que
	 * F5 sur la page d'arrivee ne propose pas de renvoyer le formulaire. Les
	 * messages et la saisie en cours transitent par des flash attributes.
	 */
	@PostMapping(value = "/new")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String createSampleType(@Valid SampleType sampletype, RedirectAttributes redirectAttributes) {
		try {
			String name = StringUtils.trimToNull(sampletype.getName());
			if (name == null) {
				redirectAttributes.addFlashAttribute("message_error",
						"Le nom du type d'échantillon est obligatoire");
				redirectAttributes.addFlashAttribute("sampletype", sampletype);
				return "redirect:/sampletype/new";
			}

			SampleType existing = sampletypeService.findByName(name);
			if (existing != null) {
				redirectAttributes.addFlashAttribute("message_error",
						"Un type d'échantillon nommé « " + name + " » existe déjà");
				redirectAttributes.addFlashAttribute("sampletype", sampletype);
				return "redirect:/sampletype/new";
			}

			sampletype.setName(name);
			sampletype.setCreatedAt(new Date());
			if (sampletype.getIsActive() == null) {
				sampletype.setIsActive(Boolean.TRUE);
			}

			SampleType data = sampletypeService.create(sampletype);
			if (data != null) {
				redirectAttributes.addFlashAttribute("message_success",
						"Type d'échantillon « " + name + " » ajouté avec succès");
				return "redirect:/sampletype";
			}

			redirectAttributes.addFlashAttribute("message_error",
					"Impossible d'ajouter le type d'échantillon");
			redirectAttributes.addFlashAttribute("sampletype", sampletype);
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("message_error", e.getMessage());
			redirectAttributes.addFlashAttribute("sampletype", sampletype);
		}
		return "redirect:/sampletype/new";
	}

	@GetMapping(value = "/update/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String getOneSampleType(@PathVariable("id") Integer id, Model model) {
		// Si un POST en erreur vient de rediriger ici, le flash attribute
		// porte deja la saisie de l'utilisateur : on ne l'ecrase pas.
		if (!model.containsAttribute("sampletype")) {
			SampleType sampletype = new SampleType();
			try {
				sampletype = sampletypeService.getOne(id);
				if (ObjectUtils.isEmpty(sampletype)) {
					throw new ResourceNotFoundException("Impossible de retrouver ce type d'échantillon");
				}
			} catch (Exception ex) {
				model.addAttribute("message_error", ex.getMessage());
			}
			model.addAttribute("sampletype", sampletype);
		}
		model.addAttribute("sample_count", sampletypeService.countSamples(id));
		return "sampletype/edit";
	}

	/** Post/Redirect/Get, cf. {@link #createSampleType}. */
	@PostMapping(value = "/update/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String updateSampleType(@PathVariable("id") Integer id, @Valid SampleType sampletype,
			RedirectAttributes redirectAttributes) {
		SampleType toUpdate;
		try {
			toUpdate = sampletypeService.getOne(id);
			if (ObjectUtils.isEmpty(toUpdate)) {
				redirectAttributes.addFlashAttribute("message_error",
						"Impossible de retrouver ce type d'échantillon");
				return "redirect:/sampletype";
			}

			String name = StringUtils.trimToNull(sampletype.getName());
			if (name == null) {
				redirectAttributes.addFlashAttribute("message_error",
						"Le nom du type d'échantillon est obligatoire");
				redirectAttributes.addFlashAttribute("sampletype", sampletype);
				return "redirect:/sampletype/update/" + id;
			}

			SampleType duplicate = sampletypeService.findByName(name);
			if (duplicate != null && !duplicate.getId().equals(id)) {
				redirectAttributes.addFlashAttribute("message_error",
						"Un autre type d'échantillon porte déjà le nom « " + name + " »");
				redirectAttributes.addFlashAttribute("sampletype", sampletype);
				return "redirect:/sampletype/update/" + id;
			}

			// On ne recopie que les champs modifiables : createdAt et la
			// collection d'echantillons rattaches restent ceux de l'entite.
			toUpdate.setName(name);
			toUpdate.setDescription(sampletype.getDescription());
			toUpdate.setIsActive(sampletype.getIsActive() != null ? sampletype.getIsActive() : Boolean.FALSE);

			sampletypeService.update(toUpdate);
			redirectAttributes.addFlashAttribute("message_success", "Modification effectuée avec succès");
		} catch (Exception ex) {
			redirectAttributes.addFlashAttribute("message_error", ex.getMessage());
			redirectAttributes.addFlashAttribute("sampletype", sampletype);
		}
		return "redirect:/sampletype/update/" + id;
	}

	/**
	 * Suppression gardee : un type deja utilise par des echantillons ne peut
	 * pas etre supprime (contrainte de cle etrangere, et perte de sens des
	 * donnees historiques). On propose alors de le desactiver.
	 */
	@GetMapping(value = "/delete/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String deleteSampleType(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
		long used = sampletypeService.countSamples(id);
		if (used > 0) {
			redirectAttributes.addFlashAttribute("message_error",
					"Impossible de supprimer ce type : il est utilisé par " + used
							+ " échantillon(s). Désactivez-le plutôt.");
			return "redirect:/sampletype";
		}

		boolean ok = sampletypeService.delete(id);
		if (ok) {
			redirectAttributes.addFlashAttribute("message_success", "Type d'échantillon supprimé avec succès");
		} else {
			redirectAttributes.addFlashAttribute("message_error", "Impossible de supprimer ce type d'échantillon");
		}
		return "redirect:/sampletype";
	}

	/**
	 * Types actifs, pour les listes deroulantes de saisie.
	 */
	@GetMapping(value = "/names", produces = "application/json")
	@ResponseBody
	public java.util.List<Map<String, Object>> getSampleTypeNames() {
		return sampletypeService.getActiveIdAndName();
	}
}
