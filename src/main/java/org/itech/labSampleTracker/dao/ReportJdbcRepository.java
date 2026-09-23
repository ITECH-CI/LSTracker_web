package org.itech.labSampleTracker.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportJdbcRepository {
	private final NamedParameterJdbcTemplate jdbc;

	public ReportJdbcRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	private MapSqlParameterSource params(LocalDate start, LocalDate end, Integer regionId, Integer districtId,
			Integer siteId, Integer labId, Integer riderId, List<Integer> accessibleSiteIds) {
		MapSqlParameterSource p = new MapSqlParameterSource().addValue("startDate", start).addValue("endDate", end)
				.addValue("regionId", regionId).addValue("districtId", districtId).addValue("siteId", siteId)
				.addValue("labId", labId).addValue("riderId", riderId);
		// Scope géographique (UserScope) : booléen + liste, même convention
		// que DashboardNativeRepository pour éviter les soucis de type-inference
		// PostgreSQL sur un array NULL.
		boolean active = accessibleSiteIds != null && !accessibleSiteIds.isEmpty();
		p.addValue("accessibleSiteIdsActive", active);
		p.addValue("accessibleSiteIds", active ? accessibleSiteIds : List.of(-1));
		return p;
	}

	private static final String SCOPE_JOIN = " LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
			+ " LEFT JOIN site st ON st.id = sr.site_id LEFT JOIN district dis ON dis.id = st.district_id ";

	private static final String SCOPE_WHERE = " AND (CAST(:siteId AS INT) IS NULL OR st.id = CAST(:siteId AS INT)) "
			+ " AND (CAST(:districtId AS INT) IS NULL OR dis.id = CAST(:districtId AS INT)) "
			+ " AND (CAST(:regionId AS INT) IS NULL OR dis.region_id = CAST(:regionId AS INT)) "
			+ " AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
			+ " AND (CAST(:riderId AS INT) IS NULL OR sr.app_user_id = CAST(:riderId AS INT)) "
			+ " AND (:accessibleSiteIdsActive = FALSE OR st.id IN (:accessibleSiteIds)) ";

	// --------- 1) Collectés par type (BI, BS, ...) ----------
	public List<Map<String, Object>> collectedByType(LocalDate start, LocalDate end, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, Integer riderId, List<Integer> accessibleSiteIds) {

		String sql = "SELECT stype.name AS type_code, COUNT(*) AS cnt FROM sample s "
				+ "JOIN sample_type stype ON stype.id = s.sample_type_id " + SCOPE_JOIN
				+ "WHERE (CAST(:startDate AS date) IS NULL OR CAST(s.collection_date AS DATE) >= CAST(:startDate AS DATE)) "
				+ "  AND (CAST(:endDate   AS date) IS NULL OR CAST(s.collection_date AS DATE) <= CAST(:endDate AS DATE)) " + SCOPE_WHERE
				+ "GROUP BY stype.name";

		return jdbc.queryForList(sql, params(start, end, regionId, districtId, siteId, labId, riderId, accessibleSiteIds));
	}

	// --------- 2) Réceptionnés par type ET catégorie labo ----------
	//
	// FIX bug "rejetés sans réceptionnés" : on compte désormais les samples
	// qui ONT été reçus historiquement (deliver_at_*_date IS NOT NULL),
	// indépendamment du statut courant (qui peut avoir évolué vers
	// ACCEPTED / NON_CONFORM / ANALYSIS_DONE). Le lab_kind est dérivé de
	// la table `lab` via destination_lab_id, plus du statut courant.
	//
	// Axe temporel : date effective de dépôt au labo (deliver_at_lab_date
	// ou deliver_at_hub_date selon la cible). Plus cohérent avec rejected
	// (qui filtre sur rejection_date) → received ⊇ rejected garanti.
	public List<Map<String, Object>> receivedByTypeAndLabKind(LocalDate start, LocalDate end, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, Integer riderId, List<Integer> accessibleSiteIds) {

		// Mapping lab_kind mutuellement exclusif :
		//   - RELAIS : passé par un hub, pas encore arrivé au lab final
		//              (hub_id NOT NULL ET deliver_at_hub_date NOT NULL ET
		//               deliver_at_lab_date NULL)
		//   - DISTRICT/CAT/BM : arrivé au lab final → on lit lab.lab_type
		// Conséquence : somme des 4 colonnes = total reçus dans la fenêtre.
		String sql = "SELECT stype.name AS type_code, CASE "
				+ "         WHEN s.deliver_at_lab_date IS NULL AND s.hub_id IS NOT NULL AND s.deliver_at_hub_date IS NOT NULL THEN 'RELAIS' "
				+ "         WHEN lab.lab_type = 'DISTRICT'   THEN 'DISTRICT' "
				+ "         WHEN lab.lab_type = 'CAT'        THEN 'CAT' "
				+ "         WHEN lab.lab_type = 'PLATEFORME' THEN 'BM' "
				+ "         ELSE 'NA' "
				+ "       END AS lab_kind, COUNT(*) AS cnt FROM sample s "
				+ "JOIN sample_type stype ON stype.id = s.sample_type_id "
				+ "LEFT JOIN lab ON lab.id = s.destination_lab_id " + SCOPE_JOIN
				+ "WHERE COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date) IS NOT NULL "
				+ "  AND (CAST(:startDate AS date) IS NULL OR CAST(COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date) AS DATE) >= :startDate) "
				+ "  AND (CAST(:endDate   AS date) IS NULL OR CAST(COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date) AS DATE) <= :endDate) "
				+ SCOPE_WHERE
				+ "GROUP BY stype.name, lab_kind";

		return jdbc.queryForList(sql, params(start, end, regionId, districtId, siteId, labId, riderId, accessibleSiteIds));
	}

	// --------- 3) Acceptés par type ----------
	//
	// FIX : même logique que received. On compte les samples qui ONT été
	// acceptés historiquement (accepted_at_*_date IS NOT NULL),
	// indépendamment du statut courant. Axe temporel = date d'acceptation
	// effective.
	public List<Map<String, Object>> acceptedByType(LocalDate start, LocalDate end, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, Integer riderId, List<Integer> accessibleSiteIds) {

		String sql = "SELECT stype.name AS type_code, COUNT(*) AS cnt FROM sample s "
				+ "JOIN sample_type stype ON stype.id = s.sample_type_id " + SCOPE_JOIN
				+ "WHERE COALESCE(s.accepted_at_lab_date, s.accepted_at_hub_date) IS NOT NULL "
				+ "  AND (CAST(:startDate AS date) IS NULL OR CAST(COALESCE(s.accepted_at_lab_date, s.accepted_at_hub_date) AS DATE) >= :startDate) "
				+ "  AND (CAST(:endDate   AS date) IS NULL OR CAST(COALESCE(s.accepted_at_lab_date, s.accepted_at_hub_date) AS DATE) <= :endDate) "
				+ SCOPE_WHERE
				+ "GROUP BY stype.name";

		return jdbc.queryForList(sql, params(start, end, regionId, districtId, siteId, labId, riderId, accessibleSiteIds));
	}

	// --------- 4) Résultats prêts (analysis_done) par type ----------
	public List<Map<String, Object>> resultReadyByType(LocalDate start, LocalDate end, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, Integer riderId, List<Integer> accessibleSiteIds) {

		String sql = "SELECT stype.name AS type_code, COUNT(*) AS cnt FROM sample s "
				+ "JOIN sample_status ss ON ss.id = s.sample_status_id "
				+ "JOIN sample_type   stype ON stype.id = s.sample_type_id " + SCOPE_JOIN
				+ "WHERE ss.status = 'ANALYSIS_DONE' "
				+ "  AND (CAST(:startDate AS date) IS NULL OR CAST(s.analysis_released_date AS DATE) >= :startDate) "
				+ "  AND (CAST(:endDate   AS date) IS NULL OR CAST(s.analysis_released_date AS DATE) <= :endDate) "
				+ SCOPE_WHERE + "GROUP BY stype.name";

		return jdbc.queryForList(sql, params(start, end, regionId, districtId, siteId, labId, riderId, accessibleSiteIds));
	}

	// --------- 5) Résultats collectés / déposés par type ----------
	public List<Map<String, Object>> resultCollectedByType(LocalDate start, LocalDate end, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, Integer riderId, List<Integer> accessibleSiteIds) {

		String sql = "SELECT stype.name AS type_code, COUNT(*) AS cnt FROM sample s "
				+ "JOIN sample_type stype ON stype.id = s.sample_type_id " + SCOPE_JOIN
				+ "WHERE s.result_collection_date IS NOT NULL "
				+ "  AND (CAST(:startDate AS date) IS NULL OR CAST(s.result_collection_date AS DATE) >= :startDate) "
				+ "  AND (CAST(:endDate   AS date) IS NULL OR CAST(s.result_collection_date AS DATE) <= :endDate) "
				+ SCOPE_WHERE + "GROUP BY stype.name";

		return jdbc.queryForList(sql, params(start, end, regionId, districtId, siteId, labId, riderId, accessibleSiteIds));
	}

	public List<Map<String, Object>> resultDeliveredByType(LocalDate start, LocalDate end, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, Integer riderId, List<Integer> accessibleSiteIds) {

		String sql = "SELECT stype.name AS type_code, COUNT(*) AS cnt FROM sample s "
				+ "JOIN sample_type stype ON stype.id = s.sample_type_id " + SCOPE_JOIN
				+ "WHERE s.result_delivery_date IS NOT NULL "
				+ "  AND (CAST(:startDate AS date) IS NULL OR CAST(s.result_delivery_date AS DATE) >= :startDate) "
				+ "  AND (CAST(:endDate   AS date) IS NULL OR CAST(s.result_delivery_date AS DATE) <= :endDate) " + SCOPE_WHERE
				+ "GROUP BY stype.name";

		return jdbc.queryForList(sql, params(start, end, regionId, districtId, siteId, labId, riderId, accessibleSiteIds));
	}

	// --------- 6) Rejets & échecs par type ----------
	//
	// FIX : on filtre strictement sur rejection_date IS NOT NULL. L'ancien
	// COALESCE(rejection_date, collection_date) faisait retomber les
	// NON_CONFORM sans rejection_date sur leur date de collecte, ce qui
	// pouvait les compter dans une période qui n'était pas celle du rejet.
	// On filtre aussi sur sample_status pour exclure les samples qui
	// auraient une rejection_date "résiduelle" mais qui sont passés à un
	// autre statut depuis (cas rare mais possible).
	public List<Map<String, Object>> rejectedByType(LocalDate start, LocalDate end, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, Integer riderId, List<Integer> accessibleSiteIds) {

		String sql = "SELECT stype.name AS type_code, COUNT(*) AS cnt FROM sample s "
				+ "JOIN sample_status ss ON ss.id = s.sample_status_id "
				+ "JOIN sample_type   stype ON stype.id = s.sample_type_id " + SCOPE_JOIN
				+ "WHERE ss.status = 'NON_CONFORM' AND s.rejection_date IS NOT NULL "
				+ "  AND (CAST(:startDate AS date) IS NULL OR CAST(s.rejection_date AS DATE) >= :startDate) "
				+ "  AND (CAST(:endDate   AS date) IS NULL OR CAST(s.rejection_date AS DATE) <= :endDate) "
				+ SCOPE_WHERE + "GROUP BY stype.name";

		return jdbc.queryForList(sql, params(start, end, regionId, districtId, siteId, labId, riderId, accessibleSiteIds));
	}

	// FIX : pour failedByType, on s'appuie sur analysis_completed_date
	// strictement non-null (date à laquelle l'échec a été constaté) au
	// lieu d'un COALESCE qui retombait sur des dates antérieures hors
	// période.
	public List<Map<String, Object>> failedByType(LocalDate start, LocalDate end, Integer regionId, Integer districtId,
			Integer siteId, Integer labId, Integer riderId, List<Integer> accessibleSiteIds) {

		String sql = "SELECT stype.name AS type_code, COUNT(*) AS cnt FROM sample s "
				+ " JOIN sample_status ss ON ss.id = s.sample_status_id "
				+ " JOIN sample_type   stype ON stype.id = s.sample_type_id " + SCOPE_JOIN
				+ " WHERE ss.status = 'ANALYSIS_FAILED' AND s.analysis_completed_date IS NOT NULL "
				+ " AND (CAST(:startDate AS date) IS NULL OR CAST(s.analysis_completed_date AS DATE) >= :startDate) "
				+ " AND (CAST(:endDate   AS date) IS NULL OR CAST(s.analysis_completed_date AS DATE) <= :endDate) "
				+ SCOPE_WHERE + "GROUP BY stype.name";

		return jdbc.queryForList(sql, params(start, end, regionId, districtId, siteId, labId, riderId, accessibleSiteIds));
	}

	// --------- 7) TAT médian par type d'échantillon ----------
	// Définition canonique du TAT (alignée sur dashboard) :
	//   TAT = result_delivery_date - collection_date
	// PERCENTILE_CONT ignore les NULL → les samples sans result_delivery_date
	// ne sont pas comptés. Cohorte = collectés dans la fenêtre.
	public List<Map<String, Object>> tatMedianDaysByType(LocalDate start, LocalDate end, Integer regionId,
			Integer districtId, Integer siteId, Integer labId, Integer riderId, List<Integer> accessibleSiteIds) {

		String sql = "SELECT stype.name AS type_code, "
				+ "       PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY " + TatSql.DAYS + ") AS median_days "
				+ "FROM sample s "
				+ "JOIN sample_type stype ON stype.id = s.sample_type_id " + SCOPE_JOIN
				+ "WHERE (CAST(:startDate AS date) IS NULL OR CAST(s.collection_date AS DATE) >= :startDate) "
				+ "  AND (CAST(:endDate   AS date) IS NULL OR CAST(s.collection_date AS DATE) <= :endDate) "
				+ SCOPE_WHERE
				+ "GROUP BY stype.name";

		return jdbc.queryForList(sql, params(start, end, regionId, districtId, siteId, labId, riderId, accessibleSiteIds));
	}
}
