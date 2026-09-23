package org.itech.labSampleTracker.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardNativeRepository {

	/**
	 * Reusable scope-filter SQL fragment. Uses a boolean flag rather than a
	 * nullable array param so PostgreSQL never has to infer the array type
	 * from a NULL value (which fails with "cannot cast type integer to
	 * integer[]" on some driver versions).
	 *
	 * Conventions:
	 *   :accessibleSiteIdsActive — boolean ; false ⇒ no filter
	 *   :accessibleSiteIds      — list ; only consulted when active is true.
	 *                              When active=false we pass [-1] as a safe
	 *                              placeholder that will never match any row
	 *                              but keeps the IN(...) clause syntactically
	 *                              valid for the JDBC driver.
	 */
	private static final String SCOPE_FILTER =
			" AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) ";

	private final NamedParameterJdbcTemplate jdbc;

	public DashboardNativeRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Map<String, Object> summary(LocalDate startDate, LocalDate endDate, Integer regionId, Integer districtId,
			Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {
		// Chaque métrique est filtrée sur SON axe-date d'étape (activité
		// fenêtre, déf. "C") :
		//   - all_count  : collection_date BETWEEN
		//   - in_transit : snapshot global (statut courant, sans filtre date)
		//   - received/accepted/...: date d'étape BETWEEN
		// Le WHERE global ne porte plus que les filtres géographiques + scope.
		// Conséquence assumée : la métaphore "funnel" ne tient plus
		// strictement (received peut être > collected pour une période courte
		// si du backlog antérieur arrive au labo dans la fenêtre).
		final String sql = "SELECT "
				+ "  COUNT(*) FILTER (WHERE (s.collection_date >= CAST(:startDate AS DATE) AND s.collection_date < CAST(:endDate AS DATE) + 1)) AS all_count, "
				+ "  COUNT(*) FILTER (WHERE ss.status = 'ON_TRANSIT') AS in_transit, "
				+ "  COUNT(*) FILTER (WHERE CAST(COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date) AS DATE) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE)) AS received, "
				+ "  COUNT(*) FILTER (WHERE CAST(COALESCE(s.accepted_at_lab_date, s.accepted_at_hub_date) AS DATE) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE)) AS accepted, "
				+ "  COUNT(*) FILTER (WHERE (s.analysis_released_date >= CAST(:startDate AS DATE) AND s.analysis_released_date < CAST(:endDate AS DATE) + 1)) AS result_ready, "
				+ "  COUNT(*) FILTER (WHERE (s.result_collection_date >= CAST(:startDate AS DATE) AND s.result_collection_date < CAST(:endDate AS DATE) + 1)) AS result_collected, "
				+ "  COUNT(*) FILTER (WHERE (s.result_delivery_date >= CAST(:startDate AS DATE) AND s.result_delivery_date < CAST(:endDate AS DATE) + 1)) AS result_on_site, "
				+ "  COUNT(*) FILTER (WHERE ss.status = 'NON_CONFORM' AND (s.rejection_date >= CAST(:startDate AS DATE) AND s.rejection_date < CAST(:endDate AS DATE) + 1)) AS rejected, "
				+ "  COUNT(*) FILTER (WHERE ss.status = 'ANALYSIS_FAILED' AND (s.analysis_completed_date >= CAST(:startDate AS DATE) AND s.analysis_completed_date < CAST(:endDate AS DATE) + 1)) AS failed "
				+ "FROM sample s "
				+ "  JOIN sample_status ss ON ss.id = s.sample_status_id "
				+ "  LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "  LEFT JOIN site st ON st.id = sr.site_id LEFT JOIN district d ON d.id = st.district_id "
				+ "WHERE 1=1 "
				+ "  AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "  AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "  AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "  AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT))"
				+ SCOPE_FILTER;

		return jdbc.queryForMap(sql, params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds));
	}

	public List<Map<String, Object>> tsCollected(LocalDate startDate, LocalDate endDate, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {
		// generate_series garantit une ligne par jour de la période (y compris
		// les jours sans donnée). On agrège dans une sous-requête séparée pour
		// éviter de coller les filtres géo à un ON de LEFT JOIN, ce qui les
		// rendrait silencieusement inopérants (le LEFT JOIN garde la ligne
		// sample même si la condition ON échoue).
		final String sql = "SELECT CAST(d AS DATE) AS day, COALESCE(c.cnt, 0) AS cnt "
				+ "FROM generate_series(CAST(:startDate AS DATE), CAST(:endDate AS DATE), interval '1 day') d "
				+ "LEFT JOIN ( "
				+ "  SELECT COALESCE(CAST(s.pickup_date AS DATE), CAST(s.created_at AS DATE)) AS day, COUNT(*) AS cnt "
				+ "  FROM sample s "
				+ "  LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "  LEFT JOIN site st ON st.id = sr.site_id "
				+ "  LEFT JOIN district dis ON dis.id = st.district_id "
				+ "  WHERE COALESCE(CAST(s.pickup_date AS DATE), CAST(s.created_at AS DATE)) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE) "
				+ "    AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "    AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "    AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "    AND (CAST(:regionId AS INT) IS NULL OR dis.region_id = CAST(:regionId AS INT)) "
				+ "    AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ "  GROUP BY COALESCE(CAST(s.pickup_date AS DATE), CAST(s.created_at AS DATE)) "
				+ ") c ON c.day = CAST(d AS DATE) "
				+ "ORDER BY CAST(d AS DATE)";
		return jdbc.queryForList(sql, params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds));
	}

	public List<Map<String, Object>> tsDeposited(LocalDate startDate, LocalDate endDate, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {
		final String sql = "SELECT CAST(d AS DATE) AS day, COALESCE(c.cnt, 0) AS cnt "
				+ "FROM generate_series(CAST(:startDate AS DATE), CAST(:endDate AS DATE), interval '1 day') d "
				+ "LEFT JOIN ( "
				+ "  SELECT COALESCE(CAST(s.deliver_at_lab_date AS DATE), CAST(s.deliver_at_hub_date AS DATE)) AS day, COUNT(*) AS cnt "
				+ "  FROM sample s "
				+ "  LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "  LEFT JOIN site st ON st.id = sr.site_id "
				+ "  LEFT JOIN district dis ON dis.id = st.district_id "
				+ "  WHERE COALESCE(CAST(s.deliver_at_lab_date AS DATE), CAST(s.deliver_at_hub_date AS DATE)) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE) "
				+ "    AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "    AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "    AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "    AND (CAST(:regionId AS INT) IS NULL OR dis.region_id = CAST(:regionId AS INT)) "
				+ "    AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ "  GROUP BY COALESCE(CAST(s.deliver_at_lab_date AS DATE), CAST(s.deliver_at_hub_date AS DATE)) "
				+ ") c ON c.day = CAST(d AS DATE) "
				+ "ORDER BY CAST(d AS DATE)";
		return jdbc.queryForList(sql, params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds));
	}

	public List<Map<String, Object>> tsAnalysed(LocalDate startDate, LocalDate endDate, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {
		final String sql = "SELECT CAST(d AS DATE) AS day, COALESCE(c.cnt, 0) AS cnt "
				+ "FROM generate_series(CAST(:startDate AS DATE), CAST(:endDate AS DATE), interval '1 day') d "
				+ "LEFT JOIN ( "
				+ "  SELECT CAST(s.analysis_released_date AS DATE) AS day, COUNT(*) AS cnt "
				+ "  FROM sample s "
				+ "  LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "  LEFT JOIN site st ON st.id = sr.site_id "
				+ "  LEFT JOIN district dis ON dis.id = st.district_id "
				+ "  WHERE (s.analysis_released_date >= CAST(:startDate AS DATE) AND s.analysis_released_date < CAST(:endDate AS DATE) + 1) "
				+ "    AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "    AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "    AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "    AND (CAST(:regionId AS INT) IS NULL OR dis.region_id = CAST(:regionId AS INT)) "
				+ "    AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ "  GROUP BY CAST(s.analysis_released_date AS DATE) "
				+ ") c ON c.day = CAST(d AS DATE) "
				+ "ORDER BY CAST(d AS DATE)";
		return jdbc.queryForList(sql, params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds));
	}

	public List<Map<String, Object>> tsDelivered(LocalDate startDate, LocalDate endDate, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {
		final String sql = "SELECT CAST(d AS DATE) AS day, COALESCE(c.cnt, 0) AS cnt "
				+ "FROM generate_series(CAST(:startDate AS DATE), CAST(:endDate AS DATE), interval '1 day') d "
				+ "LEFT JOIN ( "
				+ "  SELECT CAST(s.result_delivery_date AS DATE) AS day, COUNT(*) AS cnt "
				+ "  FROM sample s "
				+ "  LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "  LEFT JOIN site st ON st.id = sr.site_id "
				+ "  LEFT JOIN district dis ON dis.id = st.district_id "
				+ "  WHERE (s.result_delivery_date >= CAST(:startDate AS DATE) AND s.result_delivery_date < CAST(:endDate AS DATE) + 1) "
				+ "    AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "    AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "    AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "    AND (CAST(:regionId AS INT) IS NULL OR dis.region_id = CAST(:regionId AS INT)) "
				+ "    AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ "  GROUP BY CAST(s.result_delivery_date AS DATE) "
				+ ") c ON c.day = CAST(d AS DATE) "
				+ "ORDER BY CAST(d AS DATE)";
		return jdbc.queryForList(sql, params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds));
	}

	public List<Map<String, Object>> stepDurationsDays(LocalDate startDate, LocalDate endDate, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {

		final String sql = "WITH base AS (SELECT s.*, "
				+ "         COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date)  AS deposit_dt, "
				+ "         COALESCE(s.accepted_at_lab_date, s.accepted_at_hub_date) AS accepted_dt "
				+ "  FROM sample s  LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "  LEFT JOIN site st ON st.id  = sr.site_id LEFT JOIN district dis ON dis.id = st.district_id "
				+ "  WHERE (CAST(:startDate AS DATE) IS NULL OR s.collection_date >= CAST(:startDate AS DATE)) "
				+ "    AND (CAST(:endDate AS DATE) IS NULL OR s.collection_date < CAST(:endDate AS DATE) + 1) "
				+ "    AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "    AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "    AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "    AND (CAST(:regionId AS INT) IS NULL OR dis.region_id = CAST(:regionId AS INT)) "
				+ "    AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ "), durations AS ( "
				+ "  SELECT 'collecte→dépôt' AS step, sample_type_id, "
				+ "         EXTRACT(EPOCH FROM (b.deposit_dt - COALESCE(b.pickup_date, b.created_at))) / 86400.0 AS days "
				+ "  FROM base b "
				+ "  WHERE b.deposit_dt IS NOT NULL AND COALESCE(b.pickup_date, b.created_at) IS NOT NULL AND b.deposit_dt >= COALESCE(b.pickup_date, b.created_at) "
				+ "  UNION ALL "
				+ "  SELECT 'dépôt→réception' AS step, sample_type_id, EXTRACT(EPOCH FROM (b.accepted_dt - b.deposit_dt)) / 86400.0 AS days "
				+ "  FROM base b WHERE b.accepted_dt IS NOT NULL AND b.deposit_dt IS NOT NULL AND b.accepted_dt >= b.deposit_dt "
				+ "  UNION ALL "
				+ "  SELECT 'réception→analyse' AS step, sample_type_id, EXTRACT(EPOCH FROM (b.analysis_completed_date - b.accepted_dt)) / 86400.0 AS days "
				+ "  FROM base b WHERE b.analysis_completed_date IS NOT NULL AND b.accepted_dt IS NOT NULL AND b.analysis_completed_date >= b.accepted_dt "
				+ "  UNION ALL "
				+ "  SELECT 'analyse→résultat prêt' AS step, sample_type_id, EXTRACT(EPOCH FROM (b.analysis_released_date - b.analysis_completed_date)) / 86400.0 AS days "
				+ "  FROM base b WHERE b.analysis_released_date IS NOT NULL AND b.analysis_completed_date IS NOT NULL AND b.analysis_released_date >= b.analysis_completed_date "
				+ "  UNION ALL "
				+ "  SELECT 'résultat prêt→collecte résultat' AS step, sample_type_id, EXTRACT(EPOCH FROM (b.result_collection_date - b.analysis_released_date)) / 86400.0 AS days "
				+ "  FROM base b WHERE b.result_collection_date IS NOT NULL AND b.analysis_released_date IS NOT NULL AND b.result_collection_date >= b.analysis_released_date "
				+ "  UNION ALL "
				+ "  SELECT 'collecte résultat→dépôt résultat' AS step, sample_type_id, EXTRACT(EPOCH FROM (b.result_delivery_date - b.result_collection_date)) / 86400.0 AS days "
				+ "  FROM base b WHERE b.result_delivery_date IS NOT NULL AND b.result_collection_date IS NOT NULL AND b.result_delivery_date >= b.result_collection_date "
				+ ") SELECT st.name AS sample_type, step, COUNT(*) AS n, CEIL(AVG(days)) AS avg_days, "
				+ "       CEIL(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY days)) AS median_days, "
				+ "       CEIL(MIN(days)) AS min_days,   CEIL(MAX(days)) AS max_days "
				+ "FROM durations du JOIN sample_type st ON st.id = du.sample_type_id GROUP BY st.name, step "
				+ "ORDER BY st.name, step";

		return jdbc.queryForList(sql, params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds));
	}

	/**
	 * Build named parameters. Scope is encoded with a boolean toggle rather
	 * than a nullable array, to side-step PostgreSQL's type-inference issues
	 * around NULL array params.
	 */
	private MapSqlParameterSource params(LocalDate startDate, LocalDate endDate, Integer regionId, Integer districtId,
			Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {
		MapSqlParameterSource p = new MapSqlParameterSource().addValue("startDate", startDate)
				.addValue("endDate", endDate).addValue("regionId", regionId).addValue("districtId", districtId)
				.addValue("siteId", siteId).addValue("labId", labId);

		boolean active = accessibleSiteIds != null && !accessibleSiteIds.isEmpty();
		p.addValue("accessibleSiteIdsActive", active);
		// IN (:list) cannot accept an empty list, so always provide at least one
		// value. When inactive, the boolean short-circuits the predicate and
		// the placeholder is never evaluated against any row.
		p.addValue("accessibleSiteIds", active ? accessibleSiteIds : List.of(-1));
		return p;
	}
}
