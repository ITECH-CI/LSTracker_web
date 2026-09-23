package org.itech.labSampleTracker.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Advanced dashboard queries: funnel KPIs, period-over-period trend,
 * stuck samples, hierarchical drill-down, top performers/laggards.
 *
 * Uses the boolean-flag convention to keep PostgreSQL happy with optional
 * geographic scope lists.
 */
@Repository
public class DashboardAdvancedRepository {

	private static final String SCOPE_FILTER =
			" AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) ";

	private final NamedParameterJdbcTemplate jdbc;

	public DashboardAdvancedRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Single-row consolidated KPIs used by the redesigned dashboard cards and
	 * funnel. {@code tat_avg_days} is the average days between
	 * {@code collection_date} and the latest known status date for each sample.
	 */
	public Map<String, Object> funnel(LocalDate startDate, LocalDate endDate, Integer regionId, Integer districtId,
			Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {
		// Chaque étape filtre sur SON axe-date persistante (déf. "activité
		// fenêtre", alignée sur summary + rapports) :
		//   - total : cohorte collectée dans la période
		//   - in_transit : snapshot global statut courant
		//   - at_hub / at_lab / analysed / result_collected / delivered :
		//     filtrent sur la date d'étape correspondante BETWEEN
		//   - non_conform / failed : statut + date BETWEEN
		// Le funnel ne représente PLUS la même cohorte que "total" pour
		// les étapes en aval — c'est l'activité du labo sur la fenêtre.
		// Sur une période courte avec backlog antérieur, at_lab peut donc
		// dépasser total (samples collectés avant la fenêtre, reçus dedans).
		//
		// tat_avg_days reste calculé sur la cohorte collectée (filtre
		// collection_date BETWEEN via FILTER) — c'est le délai vécu par
		// les samples collectés dans la période.
		final String sql = "SELECT "
				+ "  COUNT(*) FILTER (WHERE CAST(s.collection_date AS DATE) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE)) AS total, "
				+ "  COUNT(*) FILTER (WHERE ss.status = 'ON_TRANSIT') AS in_transit, "
				+ "  COUNT(*) FILTER (WHERE s.hub_id IS NOT NULL AND CAST(s.deliver_at_hub_date AS DATE) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE)) AS at_hub, "
				+ "  COUNT(*) FILTER (WHERE CAST(s.deliver_at_lab_date AS DATE) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE)) AS at_lab, "
				+ "  COUNT(*) FILTER (WHERE CAST(s.analysis_released_date AS DATE) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE)) AS analysed, "
				+ "  COUNT(*) FILTER (WHERE CAST(s.result_collection_date AS DATE) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE)) AS result_collected, "
				+ "  COUNT(*) FILTER (WHERE CAST(s.result_delivery_date AS DATE) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE)) AS delivered, "
				+ "  COUNT(*) FILTER (WHERE ss.status = 'NON_CONFORM' AND CAST(s.rejection_date AS DATE) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE)) AS non_conform, "
				+ "  COUNT(*) FILTER (WHERE ss.status = 'ANALYSIS_FAILED' AND CAST(s.analysis_completed_date AS DATE) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE)) AS failed, "
				// TAT canonique : médiane(result_delivery_date - collection_date)
				// sur les samples livrés (result_delivery_date NOT NULL).
				// PERCENTILE_CONT ignore les NULL automatiquement.
				// Cohorte = samples collectés dans la fenêtre (filter via CASE).
				+ "  COALESCE(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY "
				+ "    CASE WHEN CAST(s.collection_date AS DATE) BETWEEN CAST(:startDate AS DATE) AND CAST(:endDate AS DATE) "
				+ "         THEN " + TatSql.DAYS + " END "
				+ "  ), 0)::numeric(10,1) AS tat_avg_days "
				+ "FROM sample s "
				+ "JOIN sample_status ss ON ss.id = s.sample_status_id "
				+ "LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "LEFT JOIN site st ON st.id = sr.site_id "
				+ "LEFT JOIN district d ON d.id = st.district_id "
				+ "WHERE 1=1 "
				+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT))"
				+ SCOPE_FILTER;

		return jdbc.queryForMap(sql, params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds));
	}

	/**
	 * List samples that have not moved for at least {@code stuckDays} days and
	 * are not in a terminal state (RESULT_ON_SITE / NON_CONFORM / ANALYSIS_FAILED).
	 */
	public List<Map<String, Object>> stuckSamples(LocalDate startDate, LocalDate endDate,
			Integer regionId, Integer districtId, Integer siteId, Integer labId,
			List<Integer> accessibleSiteIds, int stuckDays, int limit) {
		final String sql = "SELECT s.id, s.sample_identifier, s.patient_identifier, "
				+ "  st_type.name AS sample_type, st.name AS site, d.name AS district, reg.name AS region, "
				+ "  ss.status AS status_code, ss.description AS status, "
				+ "  s.collection_date, "
				+ "  GREATEST(s.collection_date,"
				+ "    COALESCE(s.deliver_at_hub_date, s.collection_date),"
				+ "    COALESCE(s.deliver_at_lab_date, s.collection_date),"
				+ "    COALESCE(s.accepted_at_hub_date, s.collection_date),"
				+ "    COALESCE(s.accepted_at_lab_date, s.collection_date),"
				+ "    COALESCE(s.analysis_completed_date, s.collection_date),"
				+ "    COALESCE(s.result_collection_date, s.collection_date),"
				+ "    COALESCE(s.result_delivery_date, s.collection_date)"
				+ "  ) AS last_movement, "
				+ "  CAST(FLOOR(EXTRACT(EPOCH FROM (NOW() - GREATEST(s.collection_date,"
				+ "    COALESCE(s.deliver_at_hub_date, s.collection_date),"
				+ "    COALESCE(s.deliver_at_lab_date, s.collection_date),"
				+ "    COALESCE(s.accepted_at_hub_date, s.collection_date),"
				+ "    COALESCE(s.accepted_at_lab_date, s.collection_date),"
				+ "    COALESCE(s.analysis_completed_date, s.collection_date),"
				+ "    COALESCE(s.result_collection_date, s.collection_date),"
				+ "    COALESCE(s.result_delivery_date, s.collection_date)"
				+ "  ))) / 86400) AS INT) AS days_idle "
				+ "FROM sample s "
				+ "JOIN sample_status ss ON ss.id = s.sample_status_id "
				+ "JOIN sample_type st_type ON st_type.id = s.sample_type_id "
				+ "LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "JOIN site st ON st.id = sr.site_id "
				+ "JOIN district d ON d.id = st.district_id "
				+ "JOIN region reg ON reg.id = d.region_id "
				+ "WHERE ss.status NOT IN ('RESULT_ON_SITE','NON_CONFORM','ANALYSIS_FAILED') "
				+ "AND (CAST(:startDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) >= CAST(:startDate AS DATE)) "
				+ "AND (CAST(:endDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) <= CAST(:endDate AS DATE)) "
				+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT))"
				+ SCOPE_FILTER
				+ "AND FLOOR(EXTRACT(EPOCH FROM (NOW() - GREATEST(s.collection_date,"
				+ "    COALESCE(s.deliver_at_hub_date, s.collection_date),"
				+ "    COALESCE(s.deliver_at_lab_date, s.collection_date),"
				+ "    COALESCE(s.accepted_at_hub_date, s.collection_date),"
				+ "    COALESCE(s.accepted_at_lab_date, s.collection_date),"
				+ "    COALESCE(s.analysis_completed_date, s.collection_date),"
				+ "    COALESCE(s.result_collection_date, s.collection_date),"
				+ "    COALESCE(s.result_delivery_date, s.collection_date)"
				+ "))) / 86400) >= :stuckDays "
				+ "ORDER BY days_idle DESC LIMIT :rowLimit";
		MapSqlParameterSource p = params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds);
		p.addValue("stuckDays", stuckDays);
		p.addValue("rowLimit", limit);
		return jdbc.queryForList(sql, p);
	}

	/**
	 * Aggregated stats grouped by region (always returns every region in scope,
	 * even with zero samples — useful for a "you've got nothing here" honest UI).
	 */
	public List<Map<String, Object>> statsByRegion(LocalDate startDate, LocalDate endDate,
			Integer regionId, Integer districtId, Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {
		final String sql = "SELECT reg.id AS region_id, reg.name AS region, "
				+ "  COUNT(s.id) AS total, "
				+ "  SUM(CASE WHEN ss.status = 'ON_TRANSIT' THEN 1 ELSE 0 END) AS in_transit, "
				+ "  SUM(CASE WHEN ss.status = 'RESULT_ON_SITE' THEN 1 ELSE 0 END) AS delivered, "
				// Non-conformités et échecs d'analyse : deux notions distinctes (cahier VI.3).
				+ "  SUM(CASE WHEN ss.status = 'NON_CONFORM' THEN 1 ELSE 0 END) AS non_conform, "
				+ "  SUM(CASE WHEN ss.status = 'ANALYSIS_FAILED' THEN 1 ELSE 0 END) AS failed, "
				// TAT canonique (TatSql) : médiane(livraison du résultat − collecte).
				+ "  " + TatSql.MEDIAN + " AS tat_avg_days "
				+ "FROM region reg "
				+ "LEFT JOIN district d ON d.region_id = reg.id "
				+ "LEFT JOIN site st ON st.district_id = d.id "
				+ "LEFT JOIN sample_retrieving sr ON sr.site_id = st.id "
				// Filtre date ET lab dans le ON du LEFT JOIN : on garde toutes les
				// régions (total=0 si aucun sample ne matche) plutôt que de les
				// faire disparaître via le WHERE.
				+ "LEFT JOIN sample s ON s.sample_retrieving_id = sr.id AND ("
				+ "    (CAST(:startDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) >= CAST(:startDate AS DATE)) "
				+ "AND (CAST(:endDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) <= CAST(:endDate AS DATE)) "
				+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT))) "
				+ "LEFT JOIN sample_status ss ON ss.id = s.sample_status_id "
				+ "WHERE (:accessibleSiteIdsActive = FALSE OR st.id IS NULL OR st.id IN (:accessibleSiteIds)) "
				// Filtres de l'écran (cahier VI.3 : homogènes sur toutes les visualisations).
				+ "AND (CAST(:regionId AS INT) IS NULL OR reg.id = CAST(:regionId AS INT)) "
				+ "AND (CAST(:districtId AS INT) IS NULL OR d.id = CAST(:districtId AS INT)) "
				+ "AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "GROUP BY reg.id, reg.name ORDER BY reg.name";
		return jdbc.queryForList(sql, params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds));
	}

	/**
	 * Aggregated stats grouped by district.
	 *
	 * <p>{@code regionId} null = tous les districts, toutes régions confondues
	 * (vue à plat du sélecteur de niveau) ; renseigné = districts de cette
	 * région seulement (cascade du tableau).</p>
	 */
	public List<Map<String, Object>> statsByDistrict(LocalDate startDate, LocalDate endDate,
			Integer regionId, Integer districtId, Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {
		final String sql = "SELECT d.id AS district_id, d.name AS district, r.name AS region, "
				+ "  COUNT(s.id) AS total, "
				+ "  SUM(CASE WHEN ss.status = 'ON_TRANSIT' THEN 1 ELSE 0 END) AS in_transit, "
				+ "  SUM(CASE WHEN ss.status = 'RESULT_ON_SITE' THEN 1 ELSE 0 END) AS delivered, "
				// Non-conformités et échecs d'analyse : deux notions distinctes (cahier VI.3).
				+ "  SUM(CASE WHEN ss.status = 'NON_CONFORM' THEN 1 ELSE 0 END) AS non_conform, "
				+ "  SUM(CASE WHEN ss.status = 'ANALYSIS_FAILED' THEN 1 ELSE 0 END) AS failed, "
				// TAT canonique (TatSql) : médiane(livraison du résultat − collecte).
				+ "  " + TatSql.MEDIAN + " AS tat_avg_days "
				+ "FROM district d "
				+ "JOIN region r ON r.id = d.region_id "
				+ "LEFT JOIN site st ON st.district_id = d.id "
				+ "LEFT JOIN sample_retrieving sr ON sr.site_id = st.id "
				+ "LEFT JOIN sample s ON s.sample_retrieving_id = sr.id AND ("
				+ "    (CAST(:startDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) >= CAST(:startDate AS DATE)) "
				+ "AND (CAST(:endDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) <= CAST(:endDate AS DATE)) "
				+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT))) "
				+ "LEFT JOIN sample_status ss ON ss.id = s.sample_status_id "
				+ "WHERE (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
				+ "AND (CAST(:districtId AS INT) IS NULL OR d.id = CAST(:districtId AS INT)) "
				+ "AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "AND (:accessibleSiteIdsActive = FALSE OR st.id IS NULL OR st.id IN (:accessibleSiteIds)) "
				+ "GROUP BY d.id, d.name, r.name ORDER BY r.name, d.name";
		return jdbc.queryForList(sql, params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds));
	}

	/**
	 * Aggregated stats grouped by site.
	 *
	 * <p>{@code districtId} null = tous les sites, tous districts confondus
	 * (vue à plat du sélecteur de niveau) ; renseigné = sites de ce district
	 * seulement (cascade du tableau).</p>
	 */
	public List<Map<String, Object>> statsBySite(LocalDate startDate, LocalDate endDate,
			Integer regionId, Integer districtId, Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {
		final String sql = "SELECT st.id AS site_id, st.name AS site, d.name AS district, r.name AS region, "
				+ "  COUNT(s.id) AS total, "
				+ "  SUM(CASE WHEN ss.status = 'ON_TRANSIT' THEN 1 ELSE 0 END) AS in_transit, "
				+ "  SUM(CASE WHEN ss.status = 'RESULT_ON_SITE' THEN 1 ELSE 0 END) AS delivered, "
				// Non-conformités et échecs d'analyse : deux notions distinctes (cahier VI.3).
				+ "  SUM(CASE WHEN ss.status = 'NON_CONFORM' THEN 1 ELSE 0 END) AS non_conform, "
				+ "  SUM(CASE WHEN ss.status = 'ANALYSIS_FAILED' THEN 1 ELSE 0 END) AS failed, "
				// TAT canonique (TatSql) : médiane(livraison du résultat − collecte).
				+ "  " + TatSql.MEDIAN + " AS tat_avg_days "
				+ "FROM site st "
				+ "JOIN district d ON d.id = st.district_id "
				+ "JOIN region r ON r.id = d.region_id "
				+ "LEFT JOIN sample_retrieving sr ON sr.site_id = st.id "
				+ "LEFT JOIN sample s ON s.sample_retrieving_id = sr.id AND ("
				+ "    (CAST(:startDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) >= CAST(:startDate AS DATE)) "
				+ "AND (CAST(:endDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) <= CAST(:endDate AS DATE)) "
				+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT))) "
				+ "LEFT JOIN sample_status ss ON ss.id = s.sample_status_id "
				+ "WHERE (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
				+ "AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ "GROUP BY st.id, st.name, d.name, r.name ORDER BY r.name, d.name, st.name";
		return jdbc.queryForList(sql, params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds));
	}

	/**
	 * Filtre géographique et labo des classements (sélection faite à l'écran,
	 * déjà intersectée avec le périmètre de l'utilisateur). Alias requis :
	 * s (sample) et st (site de collecte). Labo = labo de destination, comme le
	 * reste du tableau de bord.
	 */
	private static final String RANKING_FILTER =
			"AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
			+ "AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
			+ "AND (CAST(:regionId AS INT) IS NULL OR EXISTS (SELECT 1 FROM district fd "
			+ "     WHERE fd.id = st.district_id AND fd.region_id = CAST(:regionId AS INT))) "
			+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) ";

	/**
	 * Taux de non-conformité par site (cahier VI.2) : échantillons NON_CONFORM ÷
	 * échantillons collectés, sur la période. Les échecs d'analyse n'y entrent
	 * pas (notion distincte, cahier VI.3). {@code by_type} détaille les
	 * non-conformités par type d'échantillon (« CV 8, EID 2 »). Seuil
	 * {@code minSamples} collectés pour écarter les sites trop peu actifs.
	 */
	public List<Map<String, Object>> topRejectionSites(LocalDate startDate, LocalDate endDate,
			Integer regionId, Integer districtId, Integer siteId, Integer labId,
			List<Integer> accessibleSiteIds, int limit, int minSamples) {
		final String sql = "WITH base AS ( "
				+ "  SELECT st.id AS site_id, st.name AS site, d.name AS district, reg.name AS region, "
				+ "         COALESCE(typ.name, '?') AS sample_type, ss.status "
				+ "  FROM sample s "
				+ "  JOIN sample_status ss ON ss.id = s.sample_status_id "
				+ "  LEFT JOIN sample_type typ ON typ.id = s.sample_type_id "
				+ "  LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "  JOIN site st ON st.id = sr.site_id "
				+ "  JOIN district d ON d.id = st.district_id "
				+ "  JOIN region reg ON reg.id = d.region_id "
				+ "  WHERE (CAST(:startDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) >= CAST(:startDate AS DATE)) "
				+ "  AND (CAST(:endDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) <= CAST(:endDate AS DATE)) "
				+ "  AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ "  " + RANKING_FILTER
				+ "), by_type AS ( "
				+ "  SELECT site_id, string_agg(sample_type || ' ' || n, ', ' ORDER BY n DESC, sample_type) AS by_type "
				+ "  FROM (SELECT site_id, sample_type, COUNT(*) AS n FROM base WHERE status = 'NON_CONFORM' "
				+ "        GROUP BY site_id, sample_type) t GROUP BY site_id "
				+ ") "
				+ "SELECT b.site_id, b.site, b.district, b.region, "
				+ "  COUNT(*) AS total, "
				+ "  SUM(CASE WHEN b.status = 'NON_CONFORM' THEN 1 ELSE 0 END) AS non_conform, "
				+ "  CAST(ROUND(100.0 * SUM(CASE WHEN b.status = 'NON_CONFORM' THEN 1 ELSE 0 END) "
				+ "    / NULLIF(COUNT(*), 0), 1) AS NUMERIC(5,1)) AS non_conform_rate, "
				+ "  MAX(bt.by_type) AS by_type "
				+ "FROM base b LEFT JOIN by_type bt ON bt.site_id = b.site_id "
				+ "GROUP BY b.site_id, b.site, b.district, b.region "
				+ "HAVING COUNT(*) >= :minSamples AND SUM(CASE WHEN b.status = 'NON_CONFORM' THEN 1 ELSE 0 END) > 0 "
				+ "ORDER BY non_conform_rate DESC, non_conform DESC LIMIT :rowLimit";
		MapSqlParameterSource p = params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds)
				.addValue("rowLimit", limit).addValue("minSamples", minSamples);
		return jdbc.queryForList(sql, p);
	}

	/**
	 * Slowest labs by average TAT (collection → analysis_released) over the period.
	 */
	public List<Map<String, Object>> slowestLabs(LocalDate startDate, LocalDate endDate,
			Integer regionId, Integer districtId, Integer siteId, Integer labId,
			List<Integer> accessibleSiteIds, int limit, int minSamples) {
		// NB : indicateur SPÉCIFIQUE au segment labo (analysis_released - deliver_at_lab).
		// Diffère volontairement du TAT canonique (result_delivery - collection)
		// utilisé ailleurs, car ce widget benchmarke la performance pure du labo
		// sans inclure le transport amont/aval. Médiane plutôt que moyenne pour
		// robustesse aux outliers (samples bloqués plusieurs mois).
		final String sql = "SELECT lab.id AS lab_id, lab.lab_name AS lab, "
				+ "  COUNT(s.id) AS total, "
				+ "  CAST(COALESCE(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY "
				+ "    EXTRACT(EPOCH FROM (s.analysis_released_date - s.deliver_at_lab_date)) / 86400.0 "
				+ "  ), 0) AS NUMERIC(10,1)) AS avg_tat_days "
				+ "FROM sample s "
				+ "JOIN lab ON lab.id = s.reference_lab_id "
				+ "LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "LEFT JOIN site st ON st.id = sr.site_id "
				+ "WHERE s.deliver_at_lab_date IS NOT NULL AND s.analysis_released_date IS NOT NULL "
				+ "AND s.analysis_released_date > s.deliver_at_lab_date "
				+ "AND (CAST(:startDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) >= CAST(:startDate AS DATE)) "
				+ "AND (CAST(:endDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) <= CAST(:endDate AS DATE)) "
				+ "AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ RANKING_FILTER
				+ "GROUP BY lab.id, lab.lab_name "
				+ "HAVING COUNT(s.id) >= :minSamples "
				+ "ORDER BY avg_tat_days DESC LIMIT :rowLimit";
		MapSqlParameterSource p = params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds)
				.addValue("rowLimit", limit).addValue("minSamples", minSamples);
		return jdbc.queryForList(sql, p);
	}

	/**
	 * Classement des convoyeurs (cahier VI.2) : collectes et dépôts réalisés, et
	 * délai médian d'acheminement (collecte → premier dépôt, au labo relais ou
	 * au labo), en jours. Taux de non-conformité à titre indicatif. Trié par
	 * nombre de collectes.
	 */
	public List<Map<String, Object>> topConveyors(LocalDate startDate, LocalDate endDate,
			Integer regionId, Integer districtId, Integer siteId, Integer labId,
			List<Integer> accessibleSiteIds, int limit) {
		final String sql = "SELECT u.id AS user_id, u.first_name, u.last_name, u.login, "
				+ "  COUNT(s.id) AS samples_handled, "
				+ "  COUNT(COALESCE(s.deliver_at_hub_date, s.deliver_at_lab_date)) AS deposits, "
				+ "  CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY "
				+ "    EXTRACT(EPOCH FROM (COALESCE(s.deliver_at_hub_date, s.deliver_at_lab_date) - s.collection_date)) / 86400.0 "
				+ "  ) FILTER (WHERE COALESCE(s.deliver_at_hub_date, s.deliver_at_lab_date) >= s.collection_date) "
				+ "  AS NUMERIC(10,1)) AS median_transport_days, "
				+ "  SUM(CASE WHEN ss.status = 'NON_CONFORM' THEN 1 ELSE 0 END) AS non_conform, "
				+ "  CAST(ROUND(100.0 * SUM(CASE WHEN ss.status = 'NON_CONFORM' THEN 1 ELSE 0 END) "
				+ "    / NULLIF(COUNT(s.id), 0), 1) AS NUMERIC(5,1)) AS non_conform_rate "
				+ "FROM sample s "
				+ "JOIN sample_status ss ON ss.id = s.sample_status_id "
				+ "JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "JOIN app_user u ON u.id = sr.app_user_id "
				+ "LEFT JOIN site st ON st.id = sr.site_id "
				+ "WHERE (CAST(:startDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) >= CAST(:startDate AS DATE)) "
				+ "AND (CAST(:endDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) <= CAST(:endDate AS DATE)) "
				+ "AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ RANKING_FILTER
				+ "GROUP BY u.id, u.first_name, u.last_name, u.login "
				+ "ORDER BY samples_handled DESC LIMIT :rowLimit";
		MapSqlParameterSource p = params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds)
				.addValue("rowLimit", limit);
		return jdbc.queryForList(sql, p);
	}

	/** Au-delà, un trajet (relevé départ → arrivée) est considéré comme une erreur de saisie. */
	static final int MAX_TRIP_KM = 1000;

	/**
	 * Trajet exploitable : départ renseigné et non nul (les données 2024 de
	 * l'ancienne application portent un départ à 0 : la « distance » était alors
	 * le compteur entier du véhicule — 382 M km sur la démo), arrivée après le
	 * départ, et au plus {@link #MAX_TRIP_KM} km.
	 */
	static String validLeg(String start, String end) {
		return "(" + start + " > 0 AND " + end + " > " + start + " AND " + end + " - " + start + " <= " + MAX_TRIP_KM + ")";
	}

	public Map<String, Object> coverage(LocalDate startDate, LocalDate endDate, Integer regionId, Integer districtId,
			Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {
		final String sql = "WITH scope_samples AS ("
				+ "  SELECT s.id, s.collection_start_mileage, s.collection_end_mileage, "
				+ "         s.result_start_mileage, s.result_end_mileage, "
				+ "         sr.app_user_id AS conveyor_id, "
				+ "         st.id AS site_id, st.district_id, d.region_id, "
				+ "         s.destination_lab_id, s.reference_lab_id, s.hub_id "
				+ "  FROM sample s "
				+ "  LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "  LEFT JOIN site st ON st.id = sr.site_id "
				+ "  LEFT JOIN district d ON d.id = st.district_id "
				+ "  WHERE (CAST(:startDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) >= CAST(:startDate AS DATE)) "
				+ "  AND (CAST(:endDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) <= CAST(:endDate AS DATE)) "
				+ "  AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "  AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "  AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "  AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
				+ "  AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ "), "
				// Trajets (« legs ») : relevés de compteur départ / arrivée, pour la
				// collecte et pour la restitution des résultats. Voir validLeg().
				+ "legs AS ( "
				+ "  SELECT 'C' AS leg, conveyor_id, collection_start_mileage AS st, collection_end_mileage AS en "
				+ "  FROM scope_samples WHERE collection_end_mileage IS NOT NULL "
				+ "  UNION ALL "
				+ "  SELECT 'R', NULL, result_start_mileage, result_end_mileage "
				+ "  FROM scope_samples WHERE result_end_mileage IS NOT NULL "
				+ "), "
				// Un trajet partagé par plusieurs échantillons (mêmes relevés) n'est
				// compté qu'une fois : sinon 20 échantillons d'une tournée de 100 km
				// comptaient 2 000 km.
				+ "valid_legs AS (SELECT DISTINCT leg, conveyor_id, st, en FROM legs WHERE " + validLeg("st", "en") + ") "
				+ "SELECT "
				+ "  (SELECT COUNT(DISTINCT site_id) FROM scope_samples WHERE site_id IS NOT NULL) AS active_sites, "
				+ "  (SELECT COUNT(*) FROM site"
				+ "    WHERE (:accessibleSiteIdsActive = FALSE OR id IN (:accessibleSiteIds))) AS total_sites, "
				+ "  (SELECT COUNT(DISTINCT district_id) FROM scope_samples WHERE district_id IS NOT NULL) AS active_districts, "
				+ "  (SELECT COUNT(*) FROM district) AS total_districts, "
				+ "  (SELECT COUNT(DISTINCT region_id) FROM scope_samples WHERE region_id IS NOT NULL) AS active_regions, "
				+ "  (SELECT COUNT(*) FROM region) AS total_regions, "
				+ "  (SELECT COUNT(DISTINCT conveyor_id) FROM scope_samples WHERE conveyor_id IS NOT NULL) AS active_conveyors, "
				+ "  (SELECT COUNT(DISTINCT lab_id) FROM ( "
				+ "      SELECT destination_lab_id AS lab_id FROM scope_samples WHERE destination_lab_id IS NOT NULL "
				+ "      UNION SELECT reference_lab_id FROM scope_samples WHERE reference_lab_id IS NOT NULL "
				+ "      UNION SELECT hub_id FROM scope_samples WHERE hub_id IS NOT NULL "
				+ "  ) sub) AS active_labs, "
				+ "  (SELECT COUNT(*) FROM lab) AS total_labs, "
				+ "  COALESCE((SELECT SUM(en - st) FROM valid_legs), 0) AS total_distance_km, "
				+ "  (SELECT COUNT(*) FROM valid_legs) AS trips, "
				+ "  (SELECT COUNT(*) FROM scope_samples) AS total_samples, "
				// Relevés saisis mais inexploitables (départ à 0 ou absent, arrivée
				// avant le départ, trajet de plus de MAX_TRIP_KM) : écartés du
				// total et comptés à part pour que l'écart reste visible.
				+ "  (SELECT COUNT(*) FROM legs WHERE en > 0 AND NOT COALESCE(" + validLeg("st", "en") + ", FALSE)) AS rejected_mileage_readings, "
				// Echantillons sans AUCUN trajet exploitable (ni collecte, ni
				// restitution). Ils entrent au denominateur du km moyen sans rien
				// apporter au numerateur : on les expose pour que la dilution soit
				// visible plutot que silencieuse.
				+ "  (SELECT COUNT(*) FROM scope_samples "
				+ "     WHERE NOT COALESCE(" + validLeg("collection_start_mileage", "collection_end_mileage") + ", FALSE) "
				+ "       AND NOT COALESCE(" + validLeg("result_start_mileage", "result_end_mileage") + ", FALSE)"
				+ "  ) AS samples_without_mileage";
		Map<String, Object> base = jdbc.queryForMap(sql,
				params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds));

		// Derived: km moyen par échantillon.
		// Le dénominateur reste le TOTAL des échantillons du périmètre (et non
		// les seuls échantillons ayant un trajet saisi) : c'est la définition
		// historique, et la changer modifierait le chiffre affiché sans que
		// l'utilisateur puisse le rapprocher des valeurs précédentes. Le
		// nombre d'échantillons sans kilométrage est exposé à côté pour
		// signaler la part de saisie manquante.
		long totalKm = toLong(base.get("total_distance_km"));
		long totalSamples = toLong(base.get("total_samples"));
		double avgKm = totalSamples > 0 ? (double) totalKm / (double) totalSamples : 0d;
		java.util.Map<String, Object> out = new java.util.LinkedHashMap<>(base);
		out.put("avg_distance_per_sample_km", Math.round(avgKm * 10) / 10d);
		return out;
	}

	/**
	 * Sample type breakdown for the period — used for the type repartition
	 * mini-chart on the dashboard coverage section.
	 */
	public List<Map<String, Object>> typeBreakdown(LocalDate startDate, LocalDate endDate,
			Integer regionId, Integer districtId, Integer siteId, Integer labId,
			List<Integer> accessibleSiteIds) {
		final String sql = "SELECT st_type.name AS sample_type, COUNT(s.id) AS total "
				+ "FROM sample s "
				+ "JOIN sample_type st_type ON st_type.id = s.sample_type_id "
				+ "LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "LEFT JOIN site st ON st.id = sr.site_id "
				+ "LEFT JOIN district d ON d.id = st.district_id "
				+ "WHERE (CAST(:startDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) >= CAST(:startDate AS DATE)) "
				+ "AND (CAST(:endDate AS DATE) IS NULL OR CAST(s.collection_date AS DATE) <= CAST(:endDate AS DATE)) "
				+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
				+ SCOPE_FILTER
				+ "GROUP BY st_type.name ORDER BY total DESC";
		return jdbc.queryForList(sql, params(startDate, endDate, regionId, districtId, siteId, labId, accessibleSiteIds));
	}

	private static long toLong(Object v) {
		if (v == null) return 0L;
		if (v instanceof Number n) return n.longValue();
		try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
	}

	private MapSqlParameterSource params(LocalDate startDate, LocalDate endDate, Integer regionId, Integer districtId,
			Integer siteId, Integer labId, List<Integer> accessibleSiteIds) {
		MapSqlParameterSource p = new MapSqlParameterSource()
				.addValue("startDate", startDate).addValue("endDate", endDate)
				.addValue("regionId", regionId).addValue("districtId", districtId)
				.addValue("siteId", siteId).addValue("labId", labId);
		boolean active = accessibleSiteIds != null && !accessibleSiteIds.isEmpty();
		p.addValue("accessibleSiteIdsActive", active);
		p.addValue("accessibleSiteIds", active ? accessibleSiteIds : List.of(-1));
		return p;
	}
}
