package org.itech.labSampleTracker.dao;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Statistiques de connexion et de fréquentation (page Administration →
 * Connexions), à partir de {@code connection_log} (tentatives de connexion)
 * et {@code user_activity_day} (visites : utilisateur × jour × canal).
 * Période = du {@code start} inclus à aujourd'hui.
 */
@Repository
public class ConnectionStatsRepository {

	/** Échecs regroupés : tout sauf la réussite. */
	private static final String FAILURE = "outcome <> 'SUCCESS'";

	private final NamedParameterJdbcTemplate jdbc;

	public ConnectionStatsRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Premier jour suivi (installation du journal), ou null si vide. */
	public LocalDate trackedSince() {
		return jdbc.queryForObject("SELECT LEAST((SELECT min(day) FROM user_activity_day), "
				+ "(SELECT CAST(min(created_at) AS DATE) FROM connection_log))", Map.of(), LocalDate.class);
	}

	public Map<String, Object> summary(LocalDate start) {
		return jdbc.queryForMap("SELECT "
				+ " (SELECT count(*) FROM user_activity_day WHERE day >= :start) AS visits, "
				+ " (SELECT count(DISTINCT app_user_id) FROM user_activity_day WHERE day >= :start) AS active_users, "
				+ " (SELECT count(*) FROM app_user WHERE is_active) AS accounts, "
				+ " (SELECT count(*) FROM connection_log WHERE created_at >= :start AND outcome = 'SUCCESS') AS logins, "
				+ " (SELECT count(*) FROM connection_log WHERE created_at >= :start AND " + FAILURE + ") AS failures, "
				+ " (SELECT count(*) FROM app_user WHERE is_active AND is_locked) AS locked",
				new MapSqlParameterSource("start", start));
	}

	/** Visites par jour et par canal, jours sans visite compris. */
	public List<Map<String, Object>> visitsPerDay(LocalDate start) {
		return jdbc.queryForList("SELECT CAST(d AS DATE) AS day, "
				+ " count(a.app_user_id) FILTER (WHERE a.channel = 'web') AS web, "
				+ " count(a.app_user_id) FILTER (WHERE a.channel = 'mobile') AS mobile "
				+ "FROM generate_series(CAST(:start AS DATE), CURRENT_DATE, INTERVAL '1 day') d "
				+ "LEFT JOIN user_activity_day a ON a.day = CAST(d AS DATE) "
				+ "GROUP BY d ORDER BY d", new MapSqlParameterSource("start", start));
	}

	/**
	 * Par type de compte et niveau : comptes actifs, utilisateurs venus sur la
	 * période (au total, sur le web, sur le mobile).
	 */
	public List<Map<String, Object>> byProfile(LocalDate start) {
		return jdbc.queryForList("SELECT u.user_type, u.user_level, count(*) AS accounts, "
				+ " count(*) FILTER (WHERE v.web OR v.mobile) AS active, "
				+ " count(*) FILTER (WHERE v.web) AS web, count(*) FILTER (WHERE v.mobile) AS mobile "
				+ "FROM app_user u LEFT JOIN ( "
				+ "  SELECT app_user_id, bool_or(channel = 'web') AS web, bool_or(channel = 'mobile') AS mobile "
				+ "  FROM user_activity_day WHERE day >= :start GROUP BY app_user_id "
				+ ") v ON v.app_user_id = u.id "
				+ "WHERE u.is_active "
				+ "GROUP BY u.user_type, u.user_level "
				+ "ORDER BY count(*) FILTER (WHERE v.web OR v.mobile) DESC, count(*) DESC, u.user_type, u.user_level",
				new MapSqlParameterSource("start", start));
	}

	/**
	 * Comptes actifs sans aucune visite sur la période. Dernière activité =
	 * la plus récente entre la dernière connexion (historique) et la dernière
	 * visite ; les comptes jamais venus en tête.
	 */
	public List<Map<String, Object>> inactiveAccounts(LocalDate start) {
		return jdbc.queryForList("SELECT u.id, u.login, u.first_name, u.last_name, u.user_type, u.user_level, "
				+ " u.is_locked, u.created_at, GREATEST(u.last_login, CAST(v.last_day AS TIMESTAMP)) AS last_activity "
				+ "FROM app_user u LEFT JOIN ( "
				+ "  SELECT app_user_id, max(day) AS last_day FROM user_activity_day GROUP BY app_user_id "
				+ ") v ON v.app_user_id = u.id "
				+ "WHERE u.is_active AND NOT EXISTS (SELECT 1 FROM user_activity_day a "
				+ "  WHERE a.app_user_id = u.id AND a.day >= :start) "
				+ "ORDER BY last_activity ASC NULLS FIRST, u.login",
				new MapSqlParameterSource("start", start));
	}

	/** Identité des utilisateurs en ligne (identifiants de connexion). */
	public List<Map<String, Object>> usersByLogin(Collection<String> logins) {
		if (logins.isEmpty()) {
			return List.of();
		}
		return jdbc.queryForList("SELECT lower(login) AS login_key, login, first_name, last_name, user_type, user_level "
				+ "FROM app_user WHERE lower(login) IN (:logins)",
				new MapSqlParameterSource("logins", logins));
	}

	/** Échecs de la période regroupés par identifiant saisi et adresse IP. */
	public List<Map<String, Object>> failureGroups(LocalDate start, int limit) {
		return jdbc.queryForList("SELECT c.login, c.ip, count(*) AS attempts, max(c.created_at) AS last_attempt, "
				+ " string_agg(DISTINCT c.outcome, ', ') AS outcomes, bool_or(c.app_user_id IS NULL) AS unknown_login, "
				+ " bool_or(u.is_locked) AS locked "
				+ "FROM connection_log c LEFT JOIN app_user u ON u.id = c.app_user_id "
				+ "WHERE c.created_at >= :start AND c." + FAILURE + " "
				+ "GROUP BY c.login, c.ip ORDER BY count(*) DESC, max(c.created_at) DESC LIMIT :limit",
				new MapSqlParameterSource("start", start).addValue("limit", limit));
	}

	// --- Journal -------------------------------------------------------------

	private static String logWhere(MapSqlParameterSource p, LocalDate start, String login, String outcome,
			String channel) {
		StringBuilder w = new StringBuilder(" WHERE c.created_at >= :start ");
		p.addValue("start", start);
		if (login != null && !login.isBlank()) {
			w.append(" AND c.login ILIKE :login ");
			p.addValue("login", "%" + login.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
		}
		if ("success".equals(outcome)) {
			w.append(" AND c.outcome = 'SUCCESS' ");
		} else if ("failure".equals(outcome)) {
			w.append(" AND c.").append(FAILURE).append(' ');
		}
		if ("web".equals(channel) || "mobile".equals(channel)) {
			w.append(" AND c.channel = :channel ");
			p.addValue("channel", channel);
		}
		return w.toString();
	}

	public long countLog(LocalDate start, String login, String outcome, String channel) {
		MapSqlParameterSource p = new MapSqlParameterSource();
		String where = logWhere(p, start, login, outcome, channel);
		Long n = jdbc.queryForObject("SELECT count(*) FROM connection_log c" + where, p, Long.class);
		return n == null ? 0 : n;
	}

	/** Une page du journal, du plus récent au plus ancien ({@code limit} null = tout). */
	public List<Map<String, Object>> log(LocalDate start, String login, String outcome, String channel,
			Integer limit, int offset) {
		MapSqlParameterSource p = new MapSqlParameterSource();
		String where = logWhere(p, start, login, outcome, channel);
		String page = "";
		if (limit != null) {
			page = " LIMIT :limit OFFSET :offset";
			p.addValue("limit", limit).addValue("offset", offset);
		}
		return jdbc.queryForList("SELECT c.created_at, c.login, u.first_name, u.last_name, u.user_type, "
				+ " c.channel, c.outcome, c.ip, c.user_agent "
				+ "FROM connection_log c LEFT JOIN app_user u ON u.id = c.app_user_id" + where
				+ " ORDER BY c.created_at DESC, c.id DESC" + page, p);
	}
}
