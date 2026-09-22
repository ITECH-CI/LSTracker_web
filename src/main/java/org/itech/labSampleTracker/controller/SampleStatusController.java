/*
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 */
package org.itech.labSampleTracker.controller;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.itech.labSampleTracker.entities.SampleStatus;
import org.itech.labSampleTracker.exception.ResourceNotFoundException;
import org.itech.labSampleTracker.service.SampleStatusService;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * <h2>SampleStatusController</h2>
 *
 * Consultation du referentiel des statuts d'echantillon.
 *
 * <p><b>Volontairement sans creation ni suppression.</b> Le champ
 * {@code status} est la machine d'etat du suivi : ses codes sont references
 * en dur dans les tableaux de bord, les rapports, le calcul du TAT et
 * l'application mobile. Creer un statut produirait une ligne qu'aucune
 * requete ne sait traiter ; en renommer le code ou en supprimer un
 * casserait silencieusement ces surfaces. Seul le libelle affiche
 * ({@code description}) est modifiable ici.</p>
 */
@Controller
@RequestMapping("/samplestatus")
public class SampleStatusController extends BaseController {

	@Autowired
	private SampleStatusService samplestatusService;

	@GetMapping(value = "")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String getAllSampleStatus(Model model) {
		model.addAttribute("statuses", samplestatusService.findAllForAdmin());
		return "samplestatus/index";
	}

	/**
	 * JSON, pour les usages annexes (la page rend la liste cote serveur).
	 */
	@GetMapping(value = "/data", produces = "application/json")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	@ResponseBody
	public List<Map<String, Object>> getStatusesData() {
		return samplestatusService.findAllForAdmin();
	}

	@GetMapping(value = "/update/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String getOneSampleStatus(@PathVariable("id") Integer id, Model model) {
		// Si un POST en erreur vient de rediriger ici, le flash attribute
		// porte deja la saisie de l'utilisateur : on ne l'ecrase pas.
		if (!model.containsAttribute("samplestatus")) {
			SampleStatus entity = new SampleStatus();
			try {
				entity = samplestatusService.getOne(id);
				if (ObjectUtils.isEmpty(entity)) {
					throw new ResourceNotFoundException("Impossible de retrouver ce statut");
				}
			} catch (Exception ex) {
				model.addAttribute("message_error", ex.getMessage());
			}
			model.addAttribute("samplestatus", entity);
		}
		return "samplestatus/edit";
	}

	/**
	 * Seul le libelle est accepte : le code de statut n'est pas lu depuis le
	 * formulaire, meme s'il y etait present.
	 *
	 * <p>Post/Redirect/Get : on redirige systematiquement apres le POST, pour
	 * que F5 sur la page d'arrivee ne propose pas de renvoyer le formulaire.
	 * Les messages et la saisie en cours transitent par des flash
	 * attributes.</p>
	 */
	@PostMapping(value = "/update/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public String updateSampleStatus(@PathVariable("id") Integer id,
			@RequestParam(name = "description", required = false) String description,
			RedirectAttributes redirectAttributes) {
		try {
			String label = StringUtils.trimToNull(description);
			if (label == null) {
				redirectAttributes.addFlashAttribute("message_error", "Le libellé est obligatoire");
				return "redirect:/samplestatus/update/" + id;
			}

			SampleStatus updated = samplestatusService.updateDescription(id, label);
			if (updated == null) {
				redirectAttributes.addFlashAttribute("message_error", "Impossible de retrouver ce statut");
				return "redirect:/samplestatus/update/" + id;
			}

			redirectAttributes.addFlashAttribute("message_success", "Libellé modifié avec succès");
			return "redirect:/samplestatus";
		} catch (Exception ex) {
			redirectAttributes.addFlashAttribute("message_error", ex.getMessage());
		}
		return "redirect:/samplestatus/update/" + id;
	}
}
