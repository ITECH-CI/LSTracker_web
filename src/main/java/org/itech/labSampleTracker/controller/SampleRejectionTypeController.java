/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.controller;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.itech.labSampleTracker.entities.SampleRejectionType;
import org.itech.labSampleTracker.exception.ResourceNotFoundException;
import org.itech.labSampleTracker.service.SampleRejectionTypeService;
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
 * <h2>SampleRejectionTypeController</h2>
 *
 * Administration du referentiel des motifs de rejet d'echantillon.
 * Meme structure que {@link SampleTypeController}.
 */
@Controller
@RequestMapping("/samplerejectiontype")
public class SampleRejectionTypeController extends BaseController {

	@Autowired
	private SampleRejectionTypeService samplerejectiontypeService;

	@GetMapping(value = "")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String getAllSampleRejectionType() {
		return "samplerejectiontype/index";
	}

	@GetMapping(value = "/data", produces = "application/json")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@ResponseBody
	public Map<String, Object> getRejectionTypesData(
			@RequestParam(name = "draw", defaultValue = "1") int draw,
			@RequestParam(name = "start", defaultValue = "0") int start,
			@RequestParam(name = "length", defaultValue = "25") int length,
			@RequestParam(name = "search_text", required = false) String searchText) {

		int page = length > 0 ? (start / length) : 0;
		Pageable pageable = PageRequest.of(page, length > 0 ? length : 25);

		Page<Map<String, Object>> p = samplerejectiontypeService.findForAdmin(pageable, searchText);

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("draw", draw);
		out.put("recordsTotal", p.getTotalElements());
		out.put("recordsFiltered", p.getTotalElements());
		out.put("data", p.getContent());
		return out;
	}

	@GetMapping(value = "/new")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String newRejectionType(Model model) {
		// Un POST en erreur redirige ici avec la saisie en flash attribute :
		// on ne la remplace pas par un formulaire vierge.
		if (!model.containsAttribute("rejectiontype")) {
			SampleRejectionType entity = new SampleRejectionType();
			entity.setIsActive(Boolean.TRUE);
			model.addAttribute("rejectiontype", entity);
		}
		return "samplerejectiontype/new";
	}

	/**
	 * Post/Redirect/Get : on redirige systematiquement apres le POST, pour que
	 * F5 sur la page d'arrivee ne propose pas de renvoyer le formulaire. Les
	 * messages et la saisie en cours transitent par des flash attributes.
	 */
	@PostMapping(value = "/new")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String createRejectionType(@Valid SampleRejectionType rejectiontype,
			RedirectAttributes redirectAttributes) {
		try {
			String label = StringUtils.trimToNull(rejectiontype.getRejectionType());
			if (label == null) {
				redirectAttributes.addFlashAttribute("message_error",
						"Le libellé du motif de rejet est obligatoire");
				redirectAttributes.addFlashAttribute("rejectiontype", rejectiontype);
				return "redirect:/samplerejectiontype/new";
			}

			rejectiontype.setRejectionType(label);
			rejectiontype.setCreatedAt(new Date());
			if (rejectiontype.getIsActive() == null) {
				rejectiontype.setIsActive(Boolean.TRUE);
			}

			SampleRejectionType data = samplerejectiontypeService.create(rejectiontype);
			if (data != null) {
				redirectAttributes.addFlashAttribute("message_success",
						"Motif de rejet « " + label + " » ajouté avec succès");
				return "redirect:/samplerejectiontype";
			}

			redirectAttributes.addFlashAttribute("message_error", "Impossible d'ajouter le motif de rejet");
			redirectAttributes.addFlashAttribute("rejectiontype", rejectiontype);
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("message_error", e.getMessage());
			redirectAttributes.addFlashAttribute("rejectiontype", rejectiontype);
		}
		return "redirect:/samplerejectiontype/new";
	}

	@GetMapping(value = "/update/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String getOneRejectionType(@PathVariable("id") Integer id, Model model) {
		// Si un POST en erreur vient de rediriger ici, le flash attribute
		// porte deja la saisie de l'utilisateur : on ne l'ecrase pas.
		if (!model.containsAttribute("rejectiontype")) {
			SampleRejectionType entity = new SampleRejectionType();
			try {
				entity = samplerejectiontypeService.getOne(id);
				if (ObjectUtils.isEmpty(entity)) {
					throw new ResourceNotFoundException("Impossible de retrouver ce motif de rejet");
				}
			} catch (Exception ex) {
				model.addAttribute("message_error", ex.getMessage());
			}
			model.addAttribute("rejectiontype", entity);
		}
		model.addAttribute("rejection_count", samplerejectiontypeService.countRejections(id));
		return "samplerejectiontype/edit";
	}

	/** Post/Redirect/Get, cf. {@link #createRejectionType}. */
	@PostMapping(value = "/update/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String updateRejectionType(@PathVariable("id") Integer id,
			@Valid SampleRejectionType rejectiontype, RedirectAttributes redirectAttributes) {
		SampleRejectionType toUpdate;
		try {
			toUpdate = samplerejectiontypeService.getOne(id);
			if (ObjectUtils.isEmpty(toUpdate)) {
				redirectAttributes.addFlashAttribute("message_error", "Impossible de retrouver ce motif de rejet");
				return "redirect:/samplerejectiontype";
			}

			String label = StringUtils.trimToNull(rejectiontype.getRejectionType());
			if (label == null) {
				redirectAttributes.addFlashAttribute("message_error",
						"Le libellé du motif de rejet est obligatoire");
				redirectAttributes.addFlashAttribute("rejectiontype", rejectiontype);
				return "redirect:/samplerejectiontype/update/" + id;
			}

			// On ne recopie que les champs modifiables : createdAt et les
			// rejets rattaches restent ceux de l'entite.
			toUpdate.setRejectionType(label);
			toUpdate.setIsActive(rejectiontype.getIsActive() != null ? rejectiontype.getIsActive() : Boolean.FALSE);

			samplerejectiontypeService.update(toUpdate);
			redirectAttributes.addFlashAttribute("message_success", "Modification effectuée avec succès");
		} catch (Exception ex) {
			redirectAttributes.addFlashAttribute("message_error", ex.getMessage());
			redirectAttributes.addFlashAttribute("rejectiontype", rejectiontype);
		}
		return "redirect:/samplerejectiontype/update/" + id;
	}

	/**
	 * Suppression gardee : un motif deja utilise par des rejets enregistres
	 * ne peut pas etre supprime.
	 */
	@GetMapping(value = "/delete/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String deleteRejectionType(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
		long used = samplerejectiontypeService.countRejections(id);
		if (used > 0) {
			redirectAttributes.addFlashAttribute("message_error",
					"Impossible de supprimer ce motif : il est utilisé par " + used
							+ " rejet(s) enregistré(s). Désactivez-le plutôt.");
			return "redirect:/samplerejectiontype";
		}

		boolean ok = samplerejectiontypeService.delete(id);
		if (ok) {
			redirectAttributes.addFlashAttribute("message_success", "Motif de rejet supprimé avec succès");
		} else {
			redirectAttributes.addFlashAttribute("message_error", "Impossible de supprimer ce motif de rejet");
		}
		return "redirect:/samplerejectiontype";
	}

	/**
	 * Motifs actifs, pour les listes deroulantes de saisie.
	 */
	@GetMapping(value = "/names", produces = "application/json")
	@ResponseBody
	public List<Map<String, Object>> getRejectionTypeNames() {
		return samplerejectiontypeService.getActiveIdAndLabel();
	}
}
