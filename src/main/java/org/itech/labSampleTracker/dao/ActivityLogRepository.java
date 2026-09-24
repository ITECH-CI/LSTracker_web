package org.itech.labSampleTracker.dao;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consultation du journal d'activité (page Administration → Journal d'activité). */
@Repository
public class ActivityLogRepository {

	/** Libellé d'une référence, par type : requête sur les identifiants demandés. */
	private static final Map<String, String> REF_QUERIES = Map.of(
			"status", "SELECT id, COALESCE(description, status) AS label FROM sample_status WHERE id IN (:ids)",
			"type", "SELECT id, name AS label FROM sample_type WHERE id IN (:ids)",
			"lab", "SELECT id, lab_name AS label FROM lab WHERE id IN (:ids)",
			"site", "SELECT id, name AS label FROM site WHERE id IN (:ids)",
			"region", "SELECT id, name AS label FROM region WHERE id IN (:ids)",
			"district", "SELECT id, name AS label FROM district WHERE id IN (:ids)",
			"circuit", "SELECT id, 'Circuit ' || circuit_number AS label FROM circuit WHERE id IN (:ids)",
			"user", "SELECT id, login AS label FROM app_user WHERE id IN (:ids)",
			"rejection", "SELECT id, rejection_type AS label FROM sample_rejection_type WHERE id IN (:ids)",
			"role", "SELECT id, name AS label FROM app_role WHERE id IN (:ids)");

	private final NamedParameterJdbcTemplate jdbc;

	public ActivityLogRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Filter(LocalDate start, String login, String entityType, String action, String entityId,
			String channel) {
	}

	private static String where(Filter f, MapSqlParameterSource p) {
		StringBuilder w = new StringBuilder(" WHERE a.created_at >= :start ");
		p.addValue("start", f.start());
		if (f.login() != null && !f.login().isBlank()) {
			w.append(" AND a.login ILIKE :login ");
			p.addValue("login", "%" + f.login().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
		}
		if (f.entityType() != null && !f.entityType().isBlank()) {
			w.append(" AND a.entity_type = :type ");
			p.addValue("type", f.entityType());
		}
		if (f.action() != null && !f.action().isBlank()) {
			w.append(" AND a.action = :action ");
			p.addValue("action", f.action());
		}
		if (f.entityId() != null && !f.entityId().isBlank()) {
			w.append(" AND a.entity_id = :entityId ");
			p.addValue("entityId", f.entityId().trim());
		}
		if (f.channel() != null && !f.channel().isBlank()) {
			w.append(" AND a.channel = :channel ");
			p.addValue("channel", f.channel());
		}
		return w.toString();
	}

	public long count(Filter f) {
		MapSqlParameterSource p = new MapSqlParameterSource();
		Long n = jdbc.queryForObject("SELECT count(*) FROM activity_log a" + where(f, p), p, Long.class);
		return n == null ? 0 : n;
	}

	/** Du plus récent au plus ancien ; {@code limit} null = tout. */
	public List<Map<String, Object>> page(Filter f, Integer limit, int offset) {
		MapSqlParameterSource p = new MapSqlParameterSource();
		String sql = "SELECT a.id, a.created_at, a.login, u.first_name, u.last_name, a.channel, a.action, "
				+ " a.entity_type, a.entity_id, CAST(a.changes AS TEXT) AS changes, a.summary, a.ip "
				+ "FROM activity_log a LEFT JOIN app_user u ON u.id = a.app_user_id" + where(f, p)
				+ " ORDER BY a.created_at DESC, a.id DESC";
		if (limit != null) {
			sql += " LIMIT :limit OFFSET :offset";
			p.addValue("limit", limit).addValue("offset", offset);
		}
		return jdbc.queryForList(sql, p);
	}

	/** Types d'objet présents dans le journal (liste du filtre). */
	public List<String> entityTypes() {
		return jdbc.queryForList("SELECT DISTINCT entity_type FROM activity_log ORDER BY entity_type", Map.of(),
				String.class);
	}

	/** Libellés des références demandées : type → (identifiant → libellé). */
	public Map<String, Map<String, String>> labels(Map<String, ? extends Collection<Integer>> idsByKind) {
		Map<String, Map<String, String>> out = new HashMap<>();
		idsByKind.forEach((kind, ids) -> {
			String sql = REF_QUERIES.get(kind);
			if (sql == null || ids.isEmpty()) {
				return;
			}
			Map<String, String> m = new HashMap<>();
			jdbc.query(sql, new MapSqlParameterSource("ids", ids),
					rs -> {
						m.put(String.valueOf(rs.getInt("id")), rs.getString("label"));
					});
			out.put(kind, m);
		});
		return out;
	}
}
