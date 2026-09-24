package org.itech.labSampleTracker.helper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.itech.labSampleTracker.service.security.UserScopeService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * En-tête des exports consolidés (cahier V.5) : titre, date et auteur de
 * l'export, période, périmètre (zone demandée et restriction de
 * l'utilisateur) et filtres. Un fichier transmis ou renommé reste ainsi
 * interprétable.
 */
@Component
public class ExportPreamble {

	private final NamedParameterJdbcTemplate jdbc;
	private final UserScopeService scopes;

	public ExportPreamble(NamedParameterJdbcTemplate jdbc, UserScopeService scopes) {
		this.jdbc = jdbc;
		this.scopes = scopes;
	}

	/**
	 * @param period  période lisible, ex. « du 01/09/2026 au 24/09/2026 (date de collecte) »
	 * @param filters filtres complémentaires lisibles, ou null
	 */
	public List<String> lines(String title, String period, Integer region, Integer district, Integer site,
			Integer lab, String filters) {
		List<String> out = new ArrayList<>();
		out.add("LSTracker — " + title);
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		out.add("Généré le " + new SimpleDateFormat("dd/MM/yyyy 'à' HH:mm").format(new Date())
				+ (auth != null ? " par " + auth.getName() : ""));
		out.add("Période : " + period);
		out.add("Périmètre : " + zone(region, district, site, lab) + " — " + scopeLabel());
		if (filters != null && !filters.isBlank()) {
			out.add("Filtres : " + filters);
		}
		return out;
	}

	/** Zone demandée : région › district › site, et laboratoire. */
	String zone(Integer region, Integer district, Integer site, Integer lab) {
		List<String> parts = new ArrayList<>();
		addName(parts, "SELECT name FROM region WHERE id = :id", region);
		addName(parts, "SELECT name FROM district WHERE id = :id", district);
		addName(parts, "SELECT name FROM site WHERE id = :id", site);
		String z = parts.isEmpty() ? "toutes les zones" : String.join(" › ", parts);
		List<String> l = new ArrayList<>();
		addName(l, "SELECT lab_name FROM lab WHERE id = :id", lab);
		return l.isEmpty() ? z : z + ", laboratoire " + l.get(0);
	}

	private void addName(List<String> parts, String sql, Integer id) {
		if (id == null) {
			return;
		}
		List<String> names = jdbc.queryForList(sql, new MapSqlParameterSource("id", id), String.class);
		parts.add(names.isEmpty() ? "n° " + id : names.get(0));
	}

	/** Libellé d'un statut d'échantillon, pour la ligne « Filtres ». */
	public String statusName(Integer id) {
		List<String> n = new ArrayList<>();
		addName(n, "SELECT COALESCE(description, status) FROM sample_status WHERE id = :id", id);
		return n.isEmpty() ? null : n.get(0);
	}

	/** Libellé d'un type d'échantillon, pour la ligne « Filtres ». */
	public String typeName(Integer id) {
		List<String> n = new ArrayList<>();
		addName(n, "SELECT name FROM sample_type WHERE id = :id", id);
		return n.isEmpty() ? null : n.get(0);
	}

	/** Restriction propre à l'utilisateur : nationale, ou son périmètre. */
	String scopeLabel() {
		UserScopeService.Scope s = scopes.resolveCurrent();
		if (s == null || s.isGlobal()) {
			return "accès national";
		}
		List<String> parts = new ArrayList<>();
		count(parts, s.getRegionIds(), "région", "régions");
		count(parts, s.getDistrictIds(), "district", "districts");
		count(parts, s.getSiteIds(), "site", "sites");
		count(parts, s.getLabIds(), "laboratoire", "laboratoires");
		return "limité au périmètre de l'utilisateur"
				+ (parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")");
	}

	private static void count(List<String> parts, List<Integer> ids, String one, String many) {
		int n = ids == null ? 0 : ids.size();
		if (n > 0) {
			parts.add(n + " " + (n > 1 ? many : one));
		}
	}
}
