package org.itech.labSampleTracker.controller;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.itech.labSampleTracker.dao.ActivityLogRepository;
import org.itech.labSampleTracker.dao.ActivityLogRepository.Filter;
import org.itech.labSampleTracker.security.ActivityLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Administration → Journal d'activité : qui a fait quoi, quand, et la valeur
 * antérieure (cahier V, « historique consultable » ; administrateurs).
 */
@Controller
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class ActivityLogController {

	static final List<Integer> PERIODS = List.of(1, 7, 30, 90, 365);
	private static final int DEFAULT_DAYS = 7;
	private static final int PAGE_SIZE = 50;
	private static final int EXPORT_MAX = 100_000;

	static final Map<String, String> ACTIONS = Map.of("CREATE", "Création", "UPDATE", "Modification",
			"DELETE", "Suppression", "EXPORT", "Export", "RUN", "Lancement");

	static final Map<String, String> ENTITIES = Map.ofEntries(
			Map.entry("Sample", "Échantillon"), Map.entry("SampleRetrieving", "Collecte"),
			Map.entry("SamplePackage", "Colis"), Map.entry("SampleAtLab", "Échantillon au labo"),
			Map.entry("SampleRejection", "Rejet d'échantillon"), Map.entry("Ride", "Trajet"),
			Map.entry("AppUser", "Utilisateur"), Map.entry("AppRole", "Rôle"),
			Map.entry("AppUserHasRole", "Rôle d'un utilisateur"),
			Map.entry("AppUserHasSite", "Périmètre : site"), Map.entry("AppUserHasRegion", "Périmètre : région"),
			Map.entry("AppUserHasDistrict", "Périmètre : district"), Map.entry("AppUserHasLab", "Périmètre : laboratoire"),
			Map.entry("AppUserHasCircuit", "Périmètre : circuit"),
			Map.entry("Region", "Région"), Map.entry("District", "District"), Map.entry("Site", "Site de collecte"),
			Map.entry("Lab", "Laboratoire"), Map.entry("Circuit", "Circuit"), Map.entry("CircuitSite", "Site d'un circuit"),
			Map.entry("SampleType", "Type d'échantillon"), Map.entry("SampleStatus", "Statut"),
			Map.entry("SampleRejectionType", "Motif de rejet"), Map.entry("HelpDocument", "Manuel d'aide"),
			Map.entry("Export", "Export"), Map.entry("Rapport", "Rapport"));

	/** Champs qui désignent une autre donnée : type de référence à afficher par son nom. */
	static final Map<String, String> REFS = Map.ofEntries(
			Map.entry("sampleStatusId", "status"), Map.entry("sampleTypeId", "type"),
			Map.entry("referenceLabId", "lab"), Map.entry("destinationLabId", "lab"), Map.entry("hubId", "lab"),
			Map.entry("labId", "lab"), Map.entry("siteId", "site"), Map.entry("requesterSiteId", "site"),
			Map.entry("regionId", "region"), Map.entry("districtId", "district"), Map.entry("circuitId", "circuit"),
			Map.entry("appUserId", "user"), Map.entry("sampleConveyorId", "user"),
			Map.entry("resultCollectorId", "user"), Map.entry("createdBy", "user"),
			Map.entry("sampleRejectionTypeId", "rejection"), Map.entry("appRoleId", "role"));

	static final Map<String, String> FIELDS = Map.ofEntries(
			Map.entry("sampleStatusId", "Statut"), Map.entry("sampleTypeId", "Type"),
			Map.entry("sampleNature", "Nature"), Map.entry("sampleIdentifier", "N° d'échantillon"),
			Map.entry("patientIdentifier", "Identifiant patient"), Map.entry("labNumber", "N° labo"),
			Map.entry("referenceLabId", "Labo d'analyse"), Map.entry("destinationLabId", "Labo de destination"),
			Map.entry("hubId", "Labo relais"), Map.entry("labId", "Laboratoire"), Map.entry("siteId", "Site"),
			Map.entry("requesterSiteId", "Site demandeur"), Map.entry("pickupDate", "Ramassage"),
			Map.entry("collectionDate", "Collecte"), Map.entry("deliverAtHubDate", "Dépôt au labo relais"),
			Map.entry("acceptedAtHubDate", "Acceptation au labo relais"), Map.entry("deliverAtLabDate", "Dépôt au labo"),
			Map.entry("acceptedAtLabDate", "Acceptation au labo"), Map.entry("rejectionDate", "Rejet"),
			Map.entry("analysisCompletedDate", "Fin d'analyse"), Map.entry("analysisReleasedDate", "Validation"),
			Map.entry("resultReportedDate", "Résultat rendu"), Map.entry("resultCollectionDate", "Récupération du résultat"),
			Map.entry("resultDeliveryDate", "Remise du résultat"), Map.entry("sampleConveyorId", "Convoyeur"),
			Map.entry("resultCollectorId", "Collecteur du résultat"), Map.entry("collectionStartMileage", "Km départ (collecte)"),
			Map.entry("collectionEndMileage", "Km arrivée (collecte)"), Map.entry("resultStartMileage", "Km départ (résultat)"),
			Map.entry("resultEndMileage", "Km arrivée (résultat)"), Map.entry("appUserId", "Utilisateur"),
			Map.entry("regionId", "Région"), Map.entry("districtId", "District"), Map.entry("circuitId", "Circuit"),
			Map.entry("login", "Identifiant"), Map.entry("password", "Mot de passe"),
			Map.entry("isActive", "Actif"), Map.entry("isLocked", "Verrouillé"), Map.entry("firstName", "Prénom"),
			Map.entry("lastName", "Nom"), Map.entry("phoneContact", "Téléphone"), Map.entry("userType", "Type de compte"),
			Map.entry("userLevel", "Niveau"), Map.entry("role", "Rôle"), Map.entry("passwordExpireAt", "Expiration du mot de passe"),
			Map.entry("name", "Nom"), Map.entry("labName", "Nom"), Map.entry("sampleRejectionTypeId", "Motif de rejet"),
			Map.entry("appRoleId", "Rôle"), Map.entry("fileName", "Fichier"));

	private static final Pattern STORED_DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2}) (\\d{2}:\\d{2})");

	private final ActivityLogRepository repository;
	private final ActivityLogService activityLog;
	private final ObjectMapper json;

	public ActivityLogController(ActivityLogRepository repository, ActivityLogService activityLog, ObjectMapper json) {
		this.repository = repository;
		this.activityLog = activityLog;
		this.json = json;
	}

	static int days(Integer jours) {
		return jours != null && PERIODS.contains(jours) ? jours : DEFAULT_DAYS;
	}

	@GetMapping("/journal")
	public String index(@RequestParam(required = false) Integer jours,
			@RequestParam(required = false) String login, @RequestParam(required = false) String objet,
			@RequestParam(required = false) String action, @RequestParam(required = false) String id,
			@RequestParam(required = false) String canal,
			@RequestParam(required = false, defaultValue = "1") int page, Model model) {
		int days = days(jours);
		Filter f = new Filter(LocalDate.now().minusDays(days - 1L), login, objet, action, id, canal);
		long total = repository.count(f);
		int pages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
		int current = Math.min(Math.max(page, 1), pages);

		model.addAttribute("rows", present(repository.page(f, PAGE_SIZE, (current - 1) * PAGE_SIZE)));
		model.addAttribute("total", total);
		model.addAttribute("page", current);
		model.addAttribute("pages", pages);
		model.addAttribute("periods", PERIODS);
		model.addAttribute("days", days);
		model.addAttribute("start", f.start());
		model.addAttribute("entityTypes", repository.entityTypes());
		model.addAttribute("entities", ENTITIES);
		model.addAttribute("actions", ACTIONS);
		model.addAttribute("fLogin", login);
		model.addAttribute("fObjet", objet);
		model.addAttribute("fAction", action);
		model.addAttribute("fId", id);
		model.addAttribute("fCanal", canal);
		return "journal/index";
	}

	@GetMapping("/journal/export.csv")
	public void export(@RequestParam(required = false) Integer jours,
			@RequestParam(required = false) String login, @RequestParam(required = false) String objet,
			@RequestParam(required = false) String action, @RequestParam(required = false) String id,
			@RequestParam(required = false) String canal, HttpServletResponse response) throws IOException {
		int days = days(jours);
		Filter f = new Filter(LocalDate.now().minusDays(days - 1L), login, objet, action, id, canal);
		activityLog.recordAction(ActivityLogService.EXPORT, "Export", null,
				"Export CSV du journal d'activité : depuis le " + f.start()
						+ (objet != null && !objet.isBlank() ? ", objet " + objet : "")
						+ (id != null && !id.isBlank() ? " n° " + id : "")
						+ (login != null && !login.isBlank() ? ", utilisateur « " + login + " »" : ""));
		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader("Content-Disposition",
				"attachment; filename=\"journal_activite_" + f.start() + "_" + LocalDate.now() + ".csv\"");
		SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		PrintWriter w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
		w.write(0xFEFF); // BOM : Excel reconnaît l'UTF-8
		w.println("Date;Utilisateur;Canal;Action;Objet;N°;Détail;Adresse IP");
		for (Map<String, Object> r : present(repository.page(f, EXPORT_MAX, 0))) {
			@SuppressWarnings("unchecked")
			List<Map<String, String>> changes = (List<Map<String, String>>) r.get("changes");
			String detail = r.get("summary") != null ? (String) r.get("summary")
					: changes.stream().map(c -> c.get("label") + " : " + c.get("before") + " → " + c.get("after"))
							.collect(Collectors.joining(" | "));
			w.println(String.join(";",
					ConnectionAdminController.csv(r.get("created_at") instanceof java.util.Date d ? fmt.format(d) : r.get("created_at")),
					ConnectionAdminController.csv(r.get("who")), ConnectionAdminController.csv(r.get("channel")),
					ConnectionAdminController.csv(r.get("actionLabel")), ConnectionAdminController.csv(r.get("entityLabel")),
					ConnectionAdminController.csv(r.get("entity_id")), ConnectionAdminController.csv(detail),
					ConnectionAdminController.csv(r.get("ip"))));
		}
		w.flush();
	}

	/** Lignes prêtes à afficher : libellés, détail des champs, références nommées. */
	private List<Map<String, Object>> present(List<Map<String, Object>> rows) {
		List<Map<String, List<Object>>> parsed = new ArrayList<>();
		Map<String, Set<Integer>> wanted = new HashMap<>();
		for (Map<String, Object> r : rows) {
			Map<String, List<Object>> changes = parse((String) r.get("changes"));
			parsed.add(changes);
			changes.forEach((field, v) -> {
				String kind = REFS.get(field);
				if (kind != null) {
					v.forEach(x -> {
						if (x instanceof Number n) {
							wanted.computeIfAbsent(kind, k -> new HashSet<>()).add(n.intValue());
						}
					});
				}
			});
		}
		Map<String, Map<String, String>> labels = repository.labels(wanted);

		List<Map<String, Object>> out = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> r = new LinkedHashMap<>(rows.get(i));
			List<Map<String, String>> changes = new ArrayList<>();
			parsed.get(i).forEach((field, v) -> {
				Map<String, String> c = new LinkedHashMap<>();
				c.put("label", FIELDS.getOrDefault(field, field));
				c.put("before", display(field, v.size() > 0 ? v.get(0) : null, labels));
				c.put("after", display(field, v.size() > 1 ? v.get(1) : null, labels));
				changes.add(c);
			});
			r.put("changes", changes);
			r.put("entityLabel", ENTITIES.getOrDefault((String) r.get("entity_type"), (String) r.get("entity_type")));
			r.put("actionLabel", ACTIONS.getOrDefault((String) r.get("action"), (String) r.get("action")));
			String name = ((r.get("first_name") == null ? "" : r.get("first_name") + " ")
					+ (r.get("last_name") == null ? "" : r.get("last_name"))).trim();
			r.put("who", r.get("login") == null ? "Système" : (name.isEmpty() ? r.get("login") : name));
			out.add(r);
		}
		return out;
	}

	private Map<String, List<Object>> parse(String changes) {
		if (changes == null || changes.isBlank()) {
			return Map.of();
		}
		try {
			return json.readValue(changes, new TypeReference<LinkedHashMap<String, List<Object>>>() {
			});
		} catch (IOException e) {
			return Map.of();
		}
	}

	/** Valeur lisible : nom de la référence, date au format français, oui/non. */
	static String display(String field, Object v, Map<String, Map<String, String>> labels) {
		if (v == null) {
			return "—";
		}
		if (ActivityLogService.MASKED.equals(v)) {
			return "(masqué)";
		}
		if (v instanceof Boolean b) {
			return b ? "oui" : "non";
		}
		String kind = REFS.get(field);
		if (kind != null && v instanceof Number n) {
			String label = labels.getOrDefault(kind, Map.of()).get(String.valueOf(n.intValue()));
			return label != null ? label : "n° " + n;
		}
		String s = v.toString();
		var m = STORED_DATE.matcher(s);
		return m.matches() ? m.group(3) + "/" + m.group(2) + "/" + m.group(1) + " " + m.group(4) : s;
	}
}
