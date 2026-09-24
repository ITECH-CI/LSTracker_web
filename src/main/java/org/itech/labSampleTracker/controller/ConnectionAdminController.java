package org.itech.labSampleTracker.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.itech.labSampleTracker.dao.ConnectionStatsRepository;
import org.itech.labSampleTracker.security.ActivityLogService;
import org.itech.labSampleTracker.security.ConnectionLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Administration → Connexions : qui est en ligne, fréquentation par type de
 * compte, comptes inactifs, échecs de connexion et journal des connexions
 * (cahier V, journalisation ; consultation réservée aux administrateurs).
 */
@Controller
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class ConnectionAdminController {

	/** Périodes proposées, en jours (aujourd'hui compris). */
	static final List<Integer> PERIODS = List.of(1, 7, 30, 90);
	private static final int DEFAULT_DAYS = 30;
	private static final int PAGE_SIZE = 50;
	private static final int EXPORT_MAX = 100_000;

	static final Map<String, String> OUTCOMES = Map.of(
			"SUCCESS", "Réussie",
			"BAD_CREDENTIALS", "Identifiant ou mot de passe incorrect",
			"LOCKED", "Compte verrouillé",
			"DISABLED", "Compte désactivé",
			"EXPIRED", "Compte expiré",
			"PASSWORD_EXPIRED", "Mot de passe expiré",
			"FAILURE", "Échec");

	static final Map<String, String> USER_TYPES = Map.of(
			"CONVOYEUR", "Convoyeur", "BIOLOGISTE", "Biologiste", "ADMIN", "Administrateur", "AUTRE", "Autre");

	static final Map<String, String> USER_LEVELS = Map.of(
			"CIRCUIT", "circuit", "SITE", "site", "LABO", "laboratoire", "DISTRICT", "district",
			"REGION", "région", "CENTRAL", "central");

	private final ConnectionStatsRepository stats;
	private final ConnectionLogService connectionLog;
	private final ActivityLogService activityLog;

	public ConnectionAdminController(ConnectionStatsRepository stats, ConnectionLogService connectionLog,
			ActivityLogService activityLog) {
		this.stats = stats;
		this.connectionLog = connectionLog;
		this.activityLog = activityLog;
	}

	static int days(Integer jours) {
		return jours != null && PERIODS.contains(jours) ? jours : DEFAULT_DAYS;
	}

	static LocalDate start(int days) {
		return LocalDate.now().minusDays(days - 1L);
	}

	@GetMapping("/connexions")
	public String index(@RequestParam(required = false) Integer jours,
			@RequestParam(required = false) String login,
			@RequestParam(required = false) String resultat,
			@RequestParam(required = false) String canal,
			@RequestParam(required = false, defaultValue = "1") int page, Model model) {
		int days = days(jours);
		LocalDate start = start(days);

		model.addAttribute("periods", PERIODS);
		model.addAttribute("days", days);
		model.addAttribute("start", start);
		model.addAttribute("since", stats.trackedSince());
		model.addAttribute("summary", stats.summary(start));
		model.addAttribute("online", online());
		model.addAttribute("profiles", stats.byProfile(start));
		model.addAttribute("inactive", stats.inactiveAccounts(start));
		model.addAttribute("failures", stats.failureGroups(start, 15));
		model.addAttribute("outcomes", OUTCOMES);
		model.addAttribute("userTypes", USER_TYPES);
		model.addAttribute("userLevels", USER_LEVELS);

		List<Map<String, Object>> perDay = stats.visitsPerDay(start);
		model.addAttribute("chartDays", perDay.stream().map(r -> String.valueOf(r.get("day"))).toList());
		model.addAttribute("chartWeb", perDay.stream().map(r -> r.get("web")).toList());
		model.addAttribute("chartMobile", perDay.stream().map(r -> r.get("mobile")).toList());

		long total = stats.countLog(start, login, resultat, canal);
		int pages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
		int current = Math.min(Math.max(page, 1), pages);
		model.addAttribute("log", stats.log(start, login, resultat, canal, PAGE_SIZE, (current - 1) * PAGE_SIZE));
		model.addAttribute("logTotal", total);
		model.addAttribute("page", current);
		model.addAttribute("pages", pages);
		model.addAttribute("fLogin", login);
		model.addAttribute("fResultat", resultat);
		model.addAttribute("fCanal", canal);
		return "connexions/index";
	}

	/** En ligne maintenant, avec l'identité et le type de compte. */
	private List<Map<String, Object>> online() {
		List<ConnectionLogService.Online> now = connectionLog.onlineNow();
		Map<String, Map<String, Object>> users = stats
				.usersByLogin(now.stream().map(ConnectionLogService.Online::login).distinct().toList()).stream()
				.collect(Collectors.toMap(u -> (String) u.get("login_key"), u -> u, (a, b) -> a));
		List<Map<String, Object>> out = new ArrayList<>();
		for (ConnectionLogService.Online o : now) {
			Map<String, Object> u = users.getOrDefault(o.login(), Map.of());
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("login", u.getOrDefault("login", o.login()));
			row.put("name", fullName(u.get("first_name"), u.get("last_name")));
			row.put("userType", u.get("user_type"));
			row.put("userLevel", u.get("user_level"));
			row.put("channel", o.channel());
			row.put("minutesAgo", Duration.between(o.lastSeen(), Instant.now()).toMinutes());
			out.add(row);
		}
		return out;
	}

	/** Journal filtré, en CSV (séparateur « ; », UTF-8 avec BOM pour Excel). */
	@GetMapping("/connexions/journal.csv")
	public void export(@RequestParam(required = false) Integer jours,
			@RequestParam(required = false) String login,
			@RequestParam(required = false) String resultat,
			@RequestParam(required = false) String canal, HttpServletResponse response) throws IOException {
		int days = days(jours);
		LocalDate start = start(days);
		activityLog.recordAction(ActivityLogService.EXPORT, "Export", null,
				"Export CSV du journal des connexions : depuis le " + start
						+ (login != null && !login.isBlank() ? ", identifiant « " + login + " »" : "")
						+ (resultat != null && !resultat.isBlank() ? ", résultat " + resultat : "")
						+ (canal != null && !canal.isBlank() ? ", canal " + canal : ""));
		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader("Content-Disposition",
				"attachment; filename=\"connexions_" + start + "_" + LocalDate.now() + ".csv\"");
		SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		PrintWriter w = new PrintWriter(
				new java.io.OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
		w.write(0xFEFF); // BOM : Excel reconnaît l'UTF-8
		w.println("Date;Identifiant;Nom;Type de compte;Canal;Résultat;Adresse IP;Navigateur ou application");
		for (Map<String, Object> r : stats.log(start, login, resultat, canal, EXPORT_MAX, 0)) {
			w.println(String.join(";",
					csv(r.get("created_at") instanceof java.util.Date d ? fmt.format(d) : String.valueOf(r.get("created_at"))),
					csv(r.get("login")), csv(fullName(r.get("first_name"), r.get("last_name"))),
					csv(USER_TYPES.getOrDefault(String.valueOf(r.get("user_type")), (String) r.get("user_type"))),
					csv(r.get("channel")),
					csv(OUTCOMES.getOrDefault(String.valueOf(r.get("outcome")), String.valueOf(r.get("outcome")))),
					csv(r.get("ip")), csv(r.get("user_agent"))));
		}
		w.flush();
	}

	private static String fullName(Object first, Object last) {
		String n = ((first == null ? "" : first + " ") + (last == null ? "" : last)).trim();
		return n.isEmpty() ? null : n;
	}

	/**
	 * Champ CSV : guillemets doublés, et neutralisation des formules (une
	 * valeur saisie commençant par = + - @ serait exécutée par le tableur).
	 */
	static String csv(Object v) {
		if (v == null) {
			return "";
		}
		String s = v.toString();
		if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
			s = "'" + s;
		}
		return '"' + s.replace("\"", "\"\"") + '"';
	}
}
