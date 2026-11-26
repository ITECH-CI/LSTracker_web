package org.itech.labSampleTracker.dao;

import java.util.List;
import java.util.Map;

import org.itech.labSampleTracker.dto.DashboardFilter;
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

	/** Périmètre de l'utilisateur, type de labo et type d'échantillon. Alias requis : s, st. */
	private static final String SCOPE_FILTER =
			" AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
			+ DashboardFilter.SAMPLE_FILTER_SQL;

	private final NamedParameterJdbcTemplate jdbc;

	public DashboardAdvancedRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Single-row consolidated KPIs used by the redesigned dashboard cards and
	 * funnel. {@code tat_avg_days} is the average days between
	 * {@code collection_date} and the latest known status date for each sample.
	 */
	public Map<String, Object> funnel(DashboardFilter f) {
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
				+ "  COUNT(*) FILTER (WHERE (s.collection_date >= CAST(:startDate AS DATE) AND s.collection_date < CAST(:endDate AS DATE) + 1)) AS total, "
				+ "  COUNT(*) FILTER (WHERE ss.status = 'ON_TRANSIT') AS in_transit, "
				+ "  COUNT(*) FILTER (WHERE s.hub_id IS NOT NULL AND (s.deliver_at_hub_date >= CAST(:startDate AS DATE) AND s.deliver_at_hub_date < CAST(:endDate AS DATE) + 1)) AS at_hub, "
				+ "  COUNT(*) FILTER (WHERE (s.deliver_at_lab_date >= CAST(:startDate AS DATE) AND s.deliver_at_lab_date < CAST(:endDate AS DATE) + 1)) AS at_lab, "
				+ "  COUNT(*) FILTER (WHERE (s.analysis_released_date >= CAST(:startDate AS DATE) AND s.analysis_released_date < CAST(:endDate AS DATE) + 1)) AS analysed, "
				+ "  COUNT(*) FILTER (WHERE (s.result_collection_date >= CAST(:startDate AS DATE) AND s.result_collection_date < CAST(:endDate AS DATE) + 1)) AS result_collected, "
				+ "  COUNT(*) FILTER (WHERE (s.result_delivery_date >= CAST(:startDate AS DATE) AND s.result_delivery_date < CAST(:endDate AS DATE) + 1)) AS delivered, "
				+ "  COUNT(*) FILTER (WHERE ss.status = 'NON_CONFORM' AND (s.rejection_date >= CAST(:startDate AS DATE) AND s.rejection_date < CAST(:endDate AS DATE) + 1)) AS non_conform, "
				+ "  COUNT(*) FILTER (WHERE ss.status = 'ANALYSIS_FAILED' AND (s.analysis_completed_date >= CAST(:startDate AS DATE) AND s.analysis_completed_date < CAST(:endDate AS DATE) + 1)) AS failed, "
				// TAT canonique : médiane(result_delivery_date - collection_date)
				// sur les samples livrés (result_delivery_date NOT NULL).
				// PERCENTILE_CONT ignore les NULL automatiquement.
				// Cohorte = samples collectés dans la fenêtre (filter via CASE).
				+ "  COALESCE(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY "
				+ "    CASE WHEN (s.collection_date >= CAST(:startDate AS DATE) AND s.collection_date < CAST(:endDate AS DATE) + 1) "
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

		return jdbc.queryForMap(sql, f.params());
	}

	/**
	 * List samples that have not moved for at least {@code stuckDays} days and
	 * are not in a terminal state (RESULT_ON_SITE / NON_CONFORM / ANALYSIS_FAILED).
	 */
	public List<Map<String, Object>> stuckSamples(DashboardFilter f, int stuckDays, int limit) {
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
				+ "AND (CAST(:startDate AS DATE) IS NULL OR s.collection_date >= CAST(:startDate AS DATE)) "
				+ "AND (CAST(:endDate AS DATE) IS NULL OR s.collection_date < CAST(:endDate AS DATE) + 1) "
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
		MapSqlParameterSource p = f.params();
		p.addValue("stuckDays", stuckDays);
		p.addValue("rowLimit", limit);
		return jdbc.queryForList(sql, p);
	}

	/**
	 * Agrégats de la répartition (région / district / site), communs aux trois
	 * niveaux : les échantillons sont d'abord filtrés (dates indexables, labo,
	 * périmètre, filtres de l'écran) et agrégés par {@code groupExpr}, puis
	 * rattachés à la liste des zones par la requête appelante. Filtrer avant
	 * la jointure géographique évite de parcourir toute la table sur une
	 * période courte (mesuré sur 600 000 échantillons : 168 → 35 ms sur un mois).
	 */
	private static String distributionAgg(String groupExpr) {
		return "WITH agg AS ( "
				+ "  SELECT " + groupExpr + " AS gid, "
				+ "    COUNT(*) AS total, "
				+ "    SUM(CASE WHEN ss.status = 'ON_TRANSIT' THEN 1 ELSE 0 END) AS in_transit, "
				// Déposés au laboratoire qui réalise l'analyse (même date
				// que l'étape « Déposés au labo » du haut de page).
				+ "    COUNT(s.deliver_at_lab_date) AS deposited, "
				+ "    SUM(CASE WHEN ss.status = 'RESULT_ON_SITE' THEN 1 ELSE 0 END) AS delivered, "
				// Non-conformités et échecs d'analyse : deux notions distinctes (cahier VI.3).
				+ "    SUM(CASE WHEN ss.status = 'NON_CONFORM' THEN 1 ELSE 0 END) AS non_conform, "
				+ "    SUM(CASE WHEN ss.status = 'ANALYSIS_FAILED' THEN 1 ELSE 0 END) AS failed, "
				// TAT canonique (TatSql) : médiane(livraison du résultat − collecte).
				+ "    " + TatSql.MEDIAN + " AS tat_avg_days "
				+ "  FROM sample s "
				+ "  JOIN sample_status ss ON ss.id = s.sample_status_id "
				+ "  JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "  JOIN site st ON st.id = sr.site_id "
				+ "  JOIN district d ON d.id = st.district_id "
				+ "  WHERE (CAST(:startDate AS DATE) IS NULL OR s.collection_date >= CAST(:startDate AS DATE)) "
				+ "  AND (CAST(:endDate AS DATE) IS NULL OR s.collection_date < CAST(:endDate AS DATE) + 1) "
				+ "  AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "  AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				// Filtres de l'écran (cahier VI.3 : homogènes sur toutes les visualisations).
				+ "  AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
				+ "  AND (CAST(:districtId AS INT) IS NULL OR d.id = CAST(:districtId AS INT)) "
				+ "  AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ DashboardFilter.SAMPLE_FILTER_SQL
				+ "  GROUP BY " + groupExpr + " ) ";
	}

	/** Colonnes de sortie de la répartition, à partir de l'agrégat {@code a}. */
	private static final String DISTRIBUTION_COLUMNS = "COALESCE(a.total, 0) AS total, "
			+ "COALESCE(a.in_transit, 0) AS in_transit, COALESCE(a.deposited, 0) AS deposited, "
			+ "COALESCE(a.delivered, 0) AS delivered, "
			+ "COALESCE(a.non_conform, 0) AS non_conform, COALESCE(a.failed, 0) AS failed, "
			+ "CAST(COALESCE(a.tat_avg_days, 0) AS NUMERIC(10,1)) AS tat_avg_days ";

	/**
	 * Répartition par région. Toutes les régions de la sélection sont renvoyées,
	 * y compris sans activité (total = 0). Pour un utilisateur restreint : les
	 * régions où il a au moins un site.
	 */
	public List<Map<String, Object>> statsByRegion(DashboardFilter f) {
		final String sql = distributionAgg("d.region_id")
				+ "SELECT reg.id AS region_id, reg.name AS region, " + DISTRIBUTION_COLUMNS
				+ "FROM region reg LEFT JOIN agg a ON a.gid = reg.id "
				+ "WHERE (CAST(:regionId AS INT) IS NULL OR reg.id = CAST(:regionId AS INT)) "
				+ "AND (CAST(:districtId AS INT) IS NULL OR EXISTS (SELECT 1 FROM district fd "
				+ "     WHERE fd.id = CAST(:districtId AS INT) AND fd.region_id = reg.id)) "
				+ "AND (CAST(:siteId AS INT) IS NULL OR EXISTS (SELECT 1 FROM site fs JOIN district fd ON fd.id = fs.district_id "
				+ "     WHERE fs.id = CAST(:siteId AS INT) AND fd.region_id = reg.id)) "
				+ "AND (:accessibleSiteIdsActive = FALSE OR EXISTS (SELECT 1 FROM site fs JOIN district fd ON fd.id = fs.district_id "
				+ "     WHERE fd.region_id = reg.id AND fs.id IN (:accessibleSiteIds))) "
				+ "ORDER BY reg.name";
		return jdbc.queryForList(sql, f.params());
	}

	/**
	 * Répartition par district.
	 *
	 * <p>{@code regionId} null = tous les districts, toutes régions confondues
	 * (vue à plat du sélecteur de niveau) ; renseigné = districts de cette
	 * région seulement (cascade du tableau).</p>
	 */
	public List<Map<String, Object>> statsByDistrict(DashboardFilter f) {
		final String sql = distributionAgg("d.id")
				+ "SELECT d.id AS district_id, d.name AS district, r.name AS region, " + DISTRIBUTION_COLUMNS
				+ "FROM district d JOIN region r ON r.id = d.region_id LEFT JOIN agg a ON a.gid = d.id "
				+ "WHERE (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
				+ "AND (CAST(:districtId AS INT) IS NULL OR d.id = CAST(:districtId AS INT)) "
				+ "AND (CAST(:siteId AS INT) IS NULL OR EXISTS (SELECT 1 FROM site fs "
				+ "     WHERE fs.id = CAST(:siteId AS INT) AND fs.district_id = d.id)) "
				+ "AND (:accessibleSiteIdsActive = FALSE OR EXISTS (SELECT 1 FROM site fs "
				+ "     WHERE fs.district_id = d.id AND fs.id IN (:accessibleSiteIds))) "
				+ "ORDER BY r.name, d.name";
		return jdbc.queryForList(sql, f.params());
	}

	/**
	 * Répartition par site.
	 *
	 * <p>{@code districtId} null = tous les sites, tous districts confondus
	 * (vue à plat du sélecteur de niveau) ; renseigné = sites de ce district
	 * seulement (cascade du tableau).</p>
	 */
	public List<Map<String, Object>> statsBySite(DashboardFilter f) {
		final String sql = distributionAgg("st.id")
				+ "SELECT st.id AS site_id, st.name AS site, d.name AS district, r.name AS region, " + DISTRIBUTION_COLUMNS
				+ "FROM site st JOIN district d ON d.id = st.district_id JOIN region r ON r.id = d.region_id "
				+ "LEFT JOIN agg a ON a.gid = st.id "
				+ "WHERE (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
				+ "AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ "ORDER BY r.name, d.name, st.name";
		return jdbc.queryForList(sql, f.params());
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
			+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
			+ DashboardFilter.SAMPLE_FILTER_SQL;

	/**
	 * Taux de non-conformité par site (cahier VI.2) : échantillons NON_CONFORM ÷
	 * échantillons collectés, sur la période. Les échecs d'analyse n'y entrent
	 * pas (notion distincte, cahier VI.3). {@code by_type} détaille les
	 * non-conformités par type d'échantillon (« CV 8, EID 2 »). Seuil
	 * {@code minSamples} collectés pour écarter les sites trop peu actifs.
	 */
	public List<Map<String, Object>> topRejectionSites(DashboardFilter f, int limit, int minSamples) {
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
				+ "  WHERE (CAST(:startDate AS DATE) IS NULL OR s.collection_date >= CAST(:startDate AS DATE)) "
				+ "  AND (CAST(:endDate AS DATE) IS NULL OR s.collection_date < CAST(:endDate AS DATE) + 1) "
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
				+ "ORDER BY non_conform_rate DESC, non_conform DESC, b.site LIMIT :rowLimit";
		MapSqlParameterSource p = f.params()
				.addValue("rowLimit", limit).addValue("minSamples", minSamples);
		return jdbc.queryForList(sql, p);
	}

	/**
	 * Slowest labs by average TAT (collection → analysis_released) over the period.
	 */
	public List<Map<String, Object>> slowestLabs(DashboardFilter f, int limit, int minSamples) {
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
				+ "AND (CAST(:startDate AS DATE) IS NULL OR s.collection_date >= CAST(:startDate AS DATE)) "
				+ "AND (CAST(:endDate AS DATE) IS NULL OR s.collection_date < CAST(:endDate AS DATE) + 1) "
				+ "AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ RANKING_FILTER
				+ "GROUP BY lab.id, lab.lab_name "
				+ "HAVING COUNT(s.id) >= :minSamples "
				+ "ORDER BY avg_tat_days DESC, lab.lab_name LIMIT :rowLimit";
		MapSqlParameterSource p = f.params()
				.addValue("rowLimit", limit).addValue("minSamples", minSamples);
		return jdbc.queryForList(sql, p);
	}

	/**
	 * Circuits (tournées des convoyeurs) : échantillons collectés et déposés
	 * sur la période, délai médian d'acheminement (collecte → premier dépôt, au
	 * labo relais ou au labo) en jours, et nombre de sites desservis (sites
	 * actifs du circuit, dans le périmètre de l'utilisateur). Rattachement :
	 * collecte → trajet → circuit ; une collecte sans trajet donne la ligne
	 * {@code circuit_id} null (« Non rattaché »). Trié par collectes.
	 */
	public List<Map<String, Object>> circuits(DashboardFilter f) {
		final String sql = "WITH base AS ( "
				+ "  SELECT r.circuit_id, s.collection_date, "
				+ "         COALESCE(s.deliver_at_hub_date, s.deliver_at_lab_date) AS first_deposit "
				+ "  FROM sample s "
				+ "  JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "  LEFT JOIN ride r ON r.id = sr.ride_id "
				+ "  LEFT JOIN site st ON st.id = sr.site_id "
				+ "  WHERE (CAST(:startDate AS DATE) IS NULL OR s.collection_date >= CAST(:startDate AS DATE)) "
				+ "  AND (CAST(:endDate AS DATE) IS NULL OR s.collection_date < CAST(:endDate AS DATE) + 1) "
				+ "  AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ "  " + RANKING_FILTER
				+ ") "
				+ "SELECT b.circuit_id, c.circuit_number AS circuit, "
				+ "  COUNT(*) AS collected, COUNT(b.first_deposit) AS deposited, "
				+ "  CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY "
				+ "    EXTRACT(EPOCH FROM (b.first_deposit - b.collection_date)) / 86400.0 "
				+ "  ) FILTER (WHERE b.first_deposit >= b.collection_date) AS NUMERIC(10,1)) AS median_transport_days, "
				+ "  (SELECT COUNT(*) FROM circuit_site cs WHERE cs.circuit_id = b.circuit_id AND cs.is_active "
				+ "     AND (:accessibleSiteIdsActive = FALSE OR cs.site_id IN (:accessibleSiteIds))) AS sites_served "
				+ "FROM base b LEFT JOIN circuit c ON c.id = b.circuit_id "
				+ "GROUP BY b.circuit_id, c.circuit_number "
				+ "ORDER BY collected DESC, c.circuit_number";
		return jdbc.queryForList(sql, f.params());
	}

	/**
	 * Détail d'une zone du tableau de répartition, par type d'échantillon :
	 * mêmes échantillons et mêmes définitions que la ligne de la zone.
	 */
	public List<Map<String, Object>> zoneByType(DashboardFilter f) {
		final String sql = "SELECT COALESCE(typ.name, '?') AS sample_type, COUNT(*) AS total, "
				+ "  SUM(CASE WHEN ss.status = 'ON_TRANSIT' THEN 1 ELSE 0 END) AS in_transit, "
				+ "  COUNT(s.deliver_at_lab_date) AS deposited, "
				+ "  SUM(CASE WHEN ss.status = 'NON_CONFORM' THEN 1 ELSE 0 END) AS non_conform, "
				+ "  SUM(CASE WHEN ss.status = 'RESULT_ON_SITE' THEN 1 ELSE 0 END) AS delivered "
				+ ZONE_FROM + ZONE_WHERE
				+ "GROUP BY typ.name ORDER BY total DESC, typ.name";
		return jdbc.queryForList(sql, f.params());
	}

	/** Non-conformités d'une zone par motif de rejet. */
	public List<Map<String, Object>> zoneNonConformReasons(DashboardFilter f) {
		final String sql = "SELECT COALESCE(srt.rejection_type, 'Motif non renseigné') AS reason, "
				+ "  COUNT(DISTINCT s.id) AS n "
				+ ZONE_FROM
				+ "LEFT JOIN sample_rejection rj ON rj.sample_id = s.id "
				+ "LEFT JOIN sample_rejection_type srt ON srt.id = rj.sample_rejection_type_id "
				+ ZONE_WHERE
				+ "AND ss.status = 'NON_CONFORM' "
				+ "GROUP BY srt.rejection_type ORDER BY n DESC, reason";
		return jdbc.queryForList(sql, f.params());
	}

	/** Échantillons d'une zone : même cohorte et mêmes filtres que la répartition. */
	private static final String ZONE_FROM = " FROM sample s "
			+ "JOIN sample_status ss ON ss.id = s.sample_status_id "
			+ "LEFT JOIN sample_type typ ON typ.id = s.sample_type_id "
			+ "JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
			+ "JOIN site st ON st.id = sr.site_id "
			+ "JOIN district d ON d.id = st.district_id ";

	private static final String ZONE_WHERE = "WHERE (CAST(:startDate AS DATE) IS NULL OR s.collection_date >= CAST(:startDate AS DATE)) "
			+ "AND (CAST(:endDate AS DATE) IS NULL OR s.collection_date < CAST(:endDate AS DATE) + 1) "
			+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
			+ "AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
			+ "AND (CAST(:districtId AS INT) IS NULL OR d.id = CAST(:districtId AS INT)) "
			+ "AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
			+ SCOPE_FILTER;

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

	public Map<String, Object> coverage(DashboardFilter f) {
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
				+ "  WHERE (CAST(:startDate AS DATE) IS NULL OR s.collection_date >= CAST(:startDate AS DATE)) "
				+ "  AND (CAST(:endDate AS DATE) IS NULL OR s.collection_date < CAST(:endDate AS DATE) + 1) "
				+ "  AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "  AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "  AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "  AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
				+ "  AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) "
				+ DashboardFilter.SAMPLE_FILTER_SQL
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
				// Taux de couverture (cahier VI.2) : dénominateurs = éléments configurés et actifs.
				+ "SELECT "
				+ "  (SELECT COUNT(DISTINCT site_id) FROM scope_samples WHERE site_id IS NOT NULL) AS active_sites, "
				+ "  (SELECT COUNT(*) FROM site"
				+ "    WHERE is_active AND (:accessibleSiteIdsActive = FALSE OR id IN (:accessibleSiteIds))) AS total_sites, "
				+ "  (SELECT COUNT(DISTINCT district_id) FROM scope_samples WHERE district_id IS NOT NULL) AS active_districts, "
				+ "  (SELECT COUNT(*) FROM district WHERE is_active) AS total_districts, "
				+ "  (SELECT COUNT(DISTINCT region_id) FROM scope_samples WHERE region_id IS NOT NULL) AS active_regions, "
				+ "  (SELECT COUNT(*) FROM region WHERE is_active) AS total_regions, "
				+ "  (SELECT COUNT(DISTINCT conveyor_id) FROM scope_samples WHERE conveyor_id IS NOT NULL) AS active_conveyors, "
				+ "  (SELECT COUNT(DISTINCT lab_id) FROM ( "
				+ "      SELECT destination_lab_id AS lab_id FROM scope_samples WHERE destination_lab_id IS NOT NULL "
				+ "      UNION SELECT reference_lab_id FROM scope_samples WHERE reference_lab_id IS NOT NULL "
				+ "      UNION SELECT hub_id FROM scope_samples WHERE hub_id IS NOT NULL "
				+ "  ) sub) AS active_labs, "
				+ "  (SELECT COUNT(*) FROM lab WHERE is_active) AS total_labs, "
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
				f.params());

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
	public List<Map<String, Object>> typeBreakdown(DashboardFilter f) {
		final String sql = "SELECT st_type.name AS sample_type, COUNT(s.id) AS total "
				+ "FROM sample s "
				+ "JOIN sample_type st_type ON st_type.id = s.sample_type_id "
				+ "LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
				+ "LEFT JOIN site st ON st.id = sr.site_id "
				+ "LEFT JOIN district d ON d.id = st.district_id "
				+ "WHERE (CAST(:startDate AS DATE) IS NULL OR s.collection_date >= CAST(:startDate AS DATE)) "
				+ "AND (CAST(:endDate AS DATE) IS NULL OR s.collection_date < CAST(:endDate AS DATE) + 1) "
				+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
				+ "AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
				+ "AND (CAST(:districtId AS INT) IS NULL OR st.district_id = CAST(:districtId AS INT)) "
				+ "AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
				+ SCOPE_FILTER
				+ "GROUP BY st_type.name ORDER BY total DESC";
		return jdbc.queryForList(sql, f.params());
	}

	private static long toLong(Object v) {
		if (v == null) return 0L;
		if (v instanceof Number n) return n.longValue();
		try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
	}
}
