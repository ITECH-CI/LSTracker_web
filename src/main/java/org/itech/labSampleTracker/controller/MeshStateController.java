package org.itech.labSampleTracker.controller;

import java.util.Map;

import org.itech.labSampleTracker.dao.CircuitRepository;
import org.itech.labSampleTracker.dao.DistrictRepository;
import org.itech.labSampleTracker.dao.LabRepository;
import org.itech.labSampleTracker.dao.RegionRepository;
import org.itech.labSampleTracker.dao.SiteRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Suppression, désactivation et réactivation des éléments du maillage
 * (régions, districts, sites, laboratoires, circuits), cahier IV.b et IV.f :
 * <ul>
 * <li>un élément référencé par des données existantes ne peut être supprimé :
 * il est désactivé, l'historique est préservé ;</li>
 * <li>un parent ne peut être désactivé tant qu'il a des enfants actifs, ni un
 * enfant réactivé sous un parent inactif ;</li>
 * <li>actions en POST uniquement (jeton anti-falsification), administrateurs
 * seulement ; écritures par JPA, donc inscrites au journal d'activité.</li>
 * </ul>
 */
@Controller
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class MeshStateController {

	private static final String TYPES = "{type:region|district|site|lab|circuit}";

	/** Références qui interdisent la suppression. */
	static final Map<String, String> USAGE = Map.of(
			"region", "SELECT (SELECT count(*) FROM district WHERE region_id = :id)"
					+ " + (SELECT count(*) FROM app_user_has_region WHERE region_id = :id)",
			"district", "SELECT (SELECT count(*) FROM site WHERE district_id = :id)"
					+ " + (SELECT count(*) FROM lab WHERE district_id = :id)"
					+ " + (SELECT count(*) FROM app_user_has_district WHERE district_id = :id)",
			"site", "SELECT (SELECT count(*) FROM sample_retrieving WHERE site_id = :id)"
					+ " + (SELECT count(*) FROM circuit_site WHERE site_id = :id)"
					+ " + (SELECT count(*) FROM app_user_has_site WHERE site_id = :id)",
			"lab", "SELECT (SELECT count(*) FROM sample WHERE :id IN (hub_id, reference_lab_id, destination_lab_id))"
					+ " + (SELECT count(*) FROM sample_at_lab WHERE lab_id = :id)"
					+ " + (SELECT count(*) FROM app_user_has_lab WHERE lab_id = :id)",
			"circuit", "SELECT (SELECT count(*) FROM circuit_site WHERE circuit_id = :id)"
					+ " + (SELECT count(*) FROM ride WHERE circuit_id = :id)"
					+ " + (SELECT count(*) FROM app_user_has_circuit WHERE circuit_id = :id)");

	/** Enfants actifs qui interdisent la désactivation du parent. */
	static final Map<String, String> ACTIVE_CHILDREN = Map.of(
			"region", "SELECT count(*) FROM district WHERE region_id = :id AND is_active",
			"district", "SELECT (SELECT count(*) FROM site WHERE district_id = :id AND is_active)"
					+ " + (SELECT count(*) FROM lab WHERE district_id = :id AND is_active)");

	/** Parent inactif qui interdit la réactivation de l'enfant (1 = parent inactif). */
	static final Map<String, String> INACTIVE_PARENT = Map.of(
			"district", "SELECT count(*) FROM district d JOIN region r ON r.id = d.region_id WHERE d.id = :id AND NOT r.is_active",
			"site", "SELECT count(*) FROM site s JOIN district d ON d.id = s.district_id WHERE s.id = :id AND NOT d.is_active",
			"lab", "SELECT count(*) FROM lab l JOIN district d ON d.id = l.district_id WHERE l.id = :id AND NOT d.is_active");

	static final Map<String, String> LABELS = Map.of("region", "Cette région", "district", "Ce district",
			"site", "Ce site", "lab", "Ce laboratoire", "circuit", "Ce circuit");

	static final Map<String, String> CHILDREN = Map.of("region", "district(s) actif(s)",
			"district", "site(s) ou laboratoire(s) actif(s)");

	private final NamedParameterJdbcTemplate jdbc;
	private final RegionRepository regions;
	private final DistrictRepository districts;
	private final SiteRepository sites;
	private final LabRepository labs;
	private final CircuitRepository circuits;

	public MeshStateController(NamedParameterJdbcTemplate jdbc, RegionRepository regions,
			DistrictRepository districts, SiteRepository sites, LabRepository labs, CircuitRepository circuits) {
		this.jdbc = jdbc;
		this.regions = regions;
		this.districts = districts;
		this.sites = sites;
		this.labs = labs;
		this.circuits = circuits;
	}

	/** Accord du participe : « cette région est désactivée ». */
	static String agree(String type, String participle) {
		return "region".equals(type) ? participle + "e" : participle;
	}

	private long count(Map<String, String> queries, String type, int id) {
		String sql = queries.get(type);
		if (sql == null) {
			return 0;
		}
		Long n = jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), Long.class);
		return n == null ? 0 : n;
	}

	@PostMapping("/" + TYPES + "/delete/{id}")
	@Transactional
	public String delete(@PathVariable String type, @PathVariable int id, RedirectAttributes flash) {
		long used = count(USAGE, type, id);
		if (used > 0) {
			flash.addFlashAttribute("message_error", LABELS.get(type) + " est " + agree(type, "utilisé") + " par " + used
					+ " enregistrement(s) (rattachements, échantillons, utilisateurs) : suppression impossible."
					+ " Désactivez plutôt : l'élément disparaîtra des listes de saisie, l'historique sera conservé.");
			return "redirect:/" + type;
		}
		switch (type) {
			case "region" -> regions.deleteById(id);
			case "district" -> districts.deleteById(id);
			case "site" -> sites.deleteById(id);
			case "lab" -> labs.deleteById(id);
			default -> circuits.deleteById(id);
		}
		flash.addFlashAttribute("message_success", LABELS.get(type) + " a été " + agree(type, "supprimé") + ".");
		return "redirect:/" + type;
	}

	@PostMapping("/" + TYPES + "/deactivate/{id}")
	@Transactional
	public String deactivate(@PathVariable String type, @PathVariable int id, RedirectAttributes flash) {
		long children = count(ACTIVE_CHILDREN, type, id);
		if (children > 0) {
			flash.addFlashAttribute("message_error", LABELS.get(type) + " a encore " + children + " "
					+ CHILDREN.get(type) + " : désactivez-les d'abord.");
			return "redirect:/" + type;
		}
		setActive(type, id, false);
		flash.addFlashAttribute("message_success", LABELS.get(type) + " est " + agree(type, "désactivé")
				+ " : plus proposé à la saisie, ses données restent consultables.");
		return "redirect:/" + type;
	}

	@PostMapping("/" + TYPES + "/activate/{id}")
	@Transactional
	public String activate(@PathVariable String type, @PathVariable int id, RedirectAttributes flash) {
		if (count(INACTIVE_PARENT, type, id) > 0) {
			flash.addFlashAttribute("message_error", LABELS.get(type)
					+ " dépend d'un élément désactivé (région ou district) : réactivez-le d'abord.");
			return "redirect:/" + type;
		}
		setActive(type, id, true);
		flash.addFlashAttribute("message_success", LABELS.get(type) + " est " + agree(type, "réactivé") + ".");
		return "redirect:/" + type;
	}

	private void setActive(String type, int id, boolean active) {
		switch (type) {
			case "region" -> regions.findById(id).ifPresent(e -> e.setIsActive(active));
			case "district" -> districts.findById(id).ifPresent(e -> e.setIsActive(active));
			case "site" -> sites.findById(id).ifPresent(e -> e.setIsActive(active));
			case "lab" -> labs.findById(id).ifPresent(e -> e.setIsActive(active));
			default -> circuits.findById(id).ifPresent(e -> e.setIsActive(active));
		}
	}
}
