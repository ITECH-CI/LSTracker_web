/*
 * Java domain class for entity "Sample" 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
 */
package org.itech.labSampleTracker.dao;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.itech.labSampleTracker.entities.Sample;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SampleRepository extends JpaRepository<Sample, Integer>, JpaSpecificationExecutor<Sample> {
	public Sample findBySampleIdentifier(String sampleIdentifier);

	Optional<Sample> findByUuid(String uuid);

	List<Sample> findAllByLastupdatedAtGreaterThanEqual(Date since);

	@Query(value = "SELECT s.* FROM sample s LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
			+ "WHERE (CAST(:since AS TIMESTAMP) IS NULL OR s.lastupdated_at >= CAST(:since AS TIMESTAMP)) "
			+ "ORDER BY s.lastupdated_at ASC", nativeQuery = true)
	List<Sample> findAllForAdminSinceNative(@Param("since") Date since);

	@Query(value = "SELECT s.* FROM sample s LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
			+ "WHERE (CAST(:since AS TIMESTAMP) IS NULL OR s.lastupdated_at >= CAST(:since AS TIMESTAMP))  AND ( "
			+ "sr.site_id IN (SELECT cs.site_id FROM app_user_has_circuit auhc join circuit_site cs "
			+ " on cs.circuit_id =auhc.circuit_id  WHERE auhc.app_user_id = :userId) "
			+ "OR sr.site_id IN (SELECT cs.site_id FROM app_user_has_circuit auhc join circuit_site cs "
			+ "on cs.circuit_id =auhc.circuit_id  WHERE auhc.app_user_id = :userId) "
			+ "OR s.hub_id IN (SELECT aul.lab_id FROM app_user_has_lab aul WHERE aul.app_user_id = :userId) "
			+ "OR s.reference_lab_id IN (SELECT aul.lab_id FROM app_user_has_lab aul WHERE aul.app_user_id = :userId) "
			+ "OR s.destination_lab_id IN (SELECT aul.lab_id FROM app_user_has_lab aul WHERE aul.app_user_id = :userId) "
			+ ") ORDER BY s.lastupdated_at ASC", nativeQuery = true)
	List<Sample> findAllForUserSinceNative(@Param("since") Date since, @Param("userId") Integer userId);

	/**
	 * Same as {@link #findAllForUserSinceNative(Date, Integer)} but additionally
	 * widens the scope with the sites accessible via region/district direct
	 * assignments (inherited). {@code accessibleSiteIds} should contain the
	 * sites the user can reach through Region/District direct assignments — pass
	 * empty when none.
	 */
	@Query(value = "SELECT s.* FROM sample s LEFT JOIN sample_retrieving sr ON sr.id = s.sample_retrieving_id "
			+ "WHERE (CAST(:since AS TIMESTAMP) IS NULL OR s.lastupdated_at >= CAST(:since AS TIMESTAMP)) AND ( "
			+ "sr.site_id IN (SELECT cs.site_id FROM app_user_has_circuit auhc JOIN circuit_site cs "
			+ "  ON cs.circuit_id = auhc.circuit_id WHERE auhc.app_user_id = :userId) "
			+ "OR sr.site_id IN (SELECT uhs.site_id FROM app_user_has_site uhs WHERE uhs.app_user_id = :userId) "
			+ "OR s.hub_id IN (SELECT aul.lab_id FROM app_user_has_lab aul WHERE aul.app_user_id = :userId) "
			+ "OR s.reference_lab_id IN (SELECT aul.lab_id FROM app_user_has_lab aul WHERE aul.app_user_id = :userId) "
			+ "OR s.destination_lab_id IN (SELECT aul.lab_id FROM app_user_has_lab aul WHERE aul.app_user_id = :userId) "
			+ "OR (CAST(CAST(:hasExtraSites AS TEXT) AS BOOLEAN) IS TRUE AND sr.site_id IN (:accessibleSiteIds)) "
			+ ") ORDER BY s.lastupdated_at ASC", nativeQuery = true)
	List<Sample> findAllForUserSinceWithScopeNative(@Param("since") Date since,
			@Param("userId") Integer userId,
			@Param("accessibleSiteIds") List<Integer> accessibleSiteIds,
			@Param("hasExtraSites") Boolean hasExtraSites);

	/**
	 * Une VAGUE d'échantillons éligibles à la synchronisation oedatarepo : un
	 * labNumber est renseigné, et le statut courant n'est PAS terminal (résultat
	 * déjà obtenu/échoué ou cycle de résultat entamé).
	 * Filtre sur le CODE du statut (sample_status.status) pour ne pas dépendre
	 * d'ids. Exclut les labno épuisés (attempts >= maxAttempts ET dernier
	 * résultat NOT_FOUND), réintroduits seulement par un reset admin ou un
	 * résultat exploitable ultérieur.
	 *
	 * Tri = CURSEUR DE VAGUE : par date du dernier check (oedatarepo_sample_sync.
	 * last_at) ASC NULLS FIRST → les jamais-checkés d'abord, puis les checkés il y
	 * a le plus longtemps. Comme {@code recordOutcome} met last_at=now() après
	 * chaque check, un échantillon traité passe en fin de file ; la vague suivante
	 * (même requête, même LIMIT) ramène les suivants. C'est ce qui permet à un
	 * cycle de couvrir TOUS les éligibles par vagues successives (cf.
	 * {@code OeAnalysisBatchService.runBatch}). {@code s.id} = tie-breaker
	 * déterministe en cas de last_at égaux.
	 */
	@Query(value = "SELECT s.* FROM sample s "
			+ "JOIN sample_status ss ON ss.id = s.sample_status_id "
			+ "LEFT JOIN oedatarepo_sample_sync oss ON oss.sample_id = s.id "
			+ "WHERE s.lab_number IS NOT NULL AND TRIM(s.lab_number) <> '' "
			+ "AND ss.status NOT IN ("
			+ "  'ANALYSIS_DONE','NON_CONFORM','ANALYSIS_FAILED','RESULT_COLLECTED','RESULT_ON_SITE') "
			+ "AND NOT (COALESCE(oss.attempts, 0) >= :maxAttempts AND oss.last_outcome = 'NOT_FOUND') "
			+ "ORDER BY oss.last_at ASC NULLS FIRST, s.id ASC LIMIT :maxRows", nativeQuery = true)
	List<Sample> findEligibleForAnalysisSync(@Param("maxRows") int maxRows,
			@Param("maxAttempts") int maxAttempts);

	/**
	 * Liste paginée des échantillons éligibles (même filtre de statut/labno que
	 * {@link #findEligibleForAnalysisSync}) décorée de leur état de suivi
	 * oedatarepo, MAIS sans exclure les épuisés (pour les afficher et permettre
	 * un reset depuis la page admin). Filtre optionnel sur le labno.
	 */
	@Query(value = "SELECT s.id, s.lab_number, ss.status AS status_code, ss.description AS status_desc, "
			+ "s.analysis_completed_date, s.analysis_released_date, s.lastupdated_at, "
			+ "COALESCE(oss.attempts, 0) AS attempts, oss.last_outcome, oss.last_at "
			+ "FROM sample s "
			+ "JOIN sample_status ss ON ss.id = s.sample_status_id "
			+ "LEFT JOIN oedatarepo_sample_sync oss ON oss.sample_id = s.id "
			+ "WHERE s.lab_number IS NOT NULL AND TRIM(s.lab_number) <> '' "
			+ "AND ss.status NOT IN ("
			+ "  'ANALYSIS_DONE','NON_CONFORM','ANALYSIS_FAILED','RESULT_COLLECTED','RESULT_ON_SITE') "
			+ "AND (:searchText IS NULL OR s.lab_number ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%')) ",
			countQuery = "SELECT COUNT(s.id) FROM sample s "
					+ "JOIN sample_status ss ON ss.id = s.sample_status_id "
					+ "WHERE s.lab_number IS NOT NULL AND TRIM(s.lab_number) <> '' "
					+ "AND ss.status NOT IN ("
					+ "  'ANALYSIS_DONE','NON_CONFORM','ANALYSIS_FAILED','RESULT_COLLECTED','RESULT_ON_SITE') "
					+ "AND (:searchText IS NULL OR s.lab_number ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%'))",
			nativeQuery = true)
	Page<Map<String, Object>> findEligibleWithSyncMeta(Pageable pageable, @Param("searchText") String searchText);

	/**
	 * Liste paginée des échantillons, restreinte aux {@code accessibleSiteIds}. If the list is {@code null} or
	 * empty, no extra geographic restriction is applied (caller responsibility:
	 * pass null only for ADMIN/global roles or when the requested filters
	 * already match the user's scope).
	 */
	@Query(value = "SELECT s.id,reg.name AS region, d.name AS district, site.name AS site, "
			+ "st.name AS sample_type, s.sample_identifier, s.patient_identifier, "
			+ "s.collection_date, destination_lab.lab_name AS destination_lab, "
			+ "ss2.description status, COALESCE(lab.lab_name, hub.lab_name) AS lab, "
			+ "COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date) AS reception_date, s.lab_number, "
			+ "(s.collection_end_mileage - s.collection_start_mileage) distance, "
			+ "analysis_completed_date, analysis_released_date, result_collection_date, result_delivery_date, "
			+ "sr2.comment rejection_comment, "
			+ TatSql.SAMPLE_DAYS + " AS tat_days "
			+ "FROM sample s JOIN sample_type st ON st.id = s.sample_type_id "
			+ "JOIN sample_status ss2 ON ss2.id = s.sample_status_id "
			+ "LEFT JOIN sample_retrieving sr ON s.sample_retrieving_id = sr.id "
			+ "JOIN site ON sr.site_id = site.id JOIN district d ON d.id = site.district_id "
			+ "JOIN region reg ON reg.id = d.region_id "
			+ "LEFT JOIN sample_rejection sr2 ON sr2.sample_id = s.id "
			+ "LEFT JOIN lab ON lab.id = s.reference_lab_id "
			+ "LEFT JOIN lab hub ON hub.id = s.hub_id "
			+ "LEFT JOIN lab destination_lab ON destination_lab.id = s.destination_lab_id "
			+ "WHERE (:regionId IS NULL OR reg.id = :regionId) "
			+ "AND (:districtId IS NULL OR d.id = :districtId) "
			+ "AND (:siteId IS NULL OR site.id = :siteId) "
			+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
			+ "AND (TRUE = :#{#startDate == null} OR s.collection_date >= CAST(:startDate AS DATE)) "
			+ "AND (TRUE = :#{#endDate == null} OR s.collection_date <= CAST(:endDate AS DATE)) "
			+ "AND (:status IS NULL OR ss2.id = :status) "
			+ "AND (:sampleType IS NULL OR st.id = :sampleType) "
			+ "AND (:patientIdentifier IS NULL OR s.patient_identifier LIKE CONCAT('%', :patientIdentifier , '%')) "
			+ "AND (TRUE = :#{#accessibleSiteIds == null || #accessibleSiteIds.isEmpty()} OR site.id IN (:accessibleSiteIds)) ",
			countQuery = "SELECT COUNT(s.id) FROM sample s JOIN sample_type st ON st.id = s.sample_type_id "
					+ "JOIN sample_status ss2 ON ss2.id = s.sample_status_id "
					+ "LEFT JOIN sample_retrieving sr ON s.sample_retrieving_id = sr.id "
					+ "JOIN site ON sr.site_id = site.id JOIN district d ON d.id = site.district_id "
					+ "JOIN region reg ON reg.id = d.region_id "
					+ "LEFT JOIN sample_rejection sr2 ON sr2.sample_id = s.id "
					+ "LEFT JOIN lab ON lab.id = s.reference_lab_id "
					+ "LEFT JOIN lab hub ON hub.id = s.hub_id "
					+ "LEFT JOIN lab destination_lab ON destination_lab.id = s.destination_lab_id "
					+ "WHERE (:regionId IS NULL OR reg.id = :regionId) "
					+ "AND (:districtId IS NULL OR d.id = :districtId) "
					+ "AND (:siteId IS NULL OR site.id = :siteId) "
					+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
					+ "AND (TRUE = :#{#startDate == null} OR CAST(s.collection_date AS DATE) >= :startDate) "
					+ "AND (TRUE = :#{#endDate == null} OR CAST(s.collection_date AS DATE) <= :endDate) "
					+ "AND (:status IS NULL OR ss2.id = :status) "
					+ "AND (:sampleType IS NULL OR st.id = :sampleType) "
					+ "AND (:patientIdentifier IS NULL OR s.patient_identifier LIKE CONCAT('%', :patientIdentifier , '%')) "
					+ "AND (TRUE = :#{#accessibleSiteIds == null || #accessibleSiteIds.isEmpty()} OR site.id IN (:accessibleSiteIds)) ",
			nativeQuery = true)
	Page<Map<String, Object>> getSampleDetailsScoped(Pageable pageable,
			@Param("regionId") Integer regionId, @Param("districtId") Integer districtId,
			@Param("siteId") Integer siteId, @Param("labId") Integer labId,
			@Param("startDate") Date startDate, @Param("endDate") Date endDate,
			@Param("status") Integer status, @Param("sampleType") Integer sampleType,
			@Param("patientIdentifier") String patientIdentifier,
			@Param("accessibleSiteIds") List<Integer> accessibleSiteIds);

	/**
	 * Advanced sample listing for the redesigned web UI.
	 *
	 * Supports:
	 * - full-text search across sample_identifier / patient_identifier / lab_number
	 * - multi-status filtering ({@code statusIds})
	 * - multi-type filtering ({@code sampleTypeIds})
	 * - "stuck" filter: samples with no movement for at least {@code stuckDays} days
	 * - "only rejected" toggle ({@code onlyRejected}: NON_CONFORM or ANALYSIS_FAILED)
	 * - geographic scope ({@code accessibleSiteIds})
	 * - returns a computed {@code tat_days} and {@code sla_color} (green/orange/red) per row
	 *
	 * Boolean-flag convention is used for nullable list params to avoid
	 * PostgreSQL type-inference issues with empty/null arrays.
	 */
	@Query(value = "SELECT s.id, reg.name AS region, d.name AS district, site.name AS site, "
			+ "st.name AS sample_type, st.id AS sample_type_id, s.sample_nature, "
			+ "s.sample_identifier, s.patient_identifier, s.collection_date, "
			+ "destination_lab.lab_name AS destination_lab, "
			+ "ss2.description AS status, ss2.status AS status_code, ss2.id AS status_id, "
			+ "COALESCE(lab.lab_name, hub.lab_name) AS lab, "
			+ "COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date) AS reception_date, "
			+ "s.lab_number, "
			+ "(s.collection_end_mileage - s.collection_start_mileage) AS distance, "
			+ "s.analysis_completed_date, s.analysis_released_date, "
			+ "s.result_collection_date, s.result_delivery_date, "
			+ "sr2.comment AS rejection_comment, "
			// TAT : se FIGE sur les statuts terminaux (la date de fin depend du
			// statut), sinon court depuis la collecte. Sans cela le TAT d'un
			// dossier clos continue d'augmenter indefiniment.
			// Cas limite assume : un dossier terminal dont la date de fin n'a
			// jamais ete saisie retombe sur LOCALTIMESTAMP et continue donc de
			// courir. C'est voulu : le TAT anormal signale la donnee manquante
			// plutot que de la masquer derriere une date technique.
			+ TatSql.SAMPLE_DAYS + " AS tat_days, "
			+ "CAST(FLOOR(EXTRACT(EPOCH FROM (NOW() - GREATEST(s.collection_date, "
			+ "    COALESCE(s.deliver_at_hub_date, s.collection_date), "
			+ "    COALESCE(s.deliver_at_lab_date, s.collection_date), "
			+ "    COALESCE(s.accepted_at_hub_date, s.collection_date), "
			+ "    COALESCE(s.accepted_at_lab_date, s.collection_date), "
			+ "    COALESCE(s.analysis_completed_date, s.collection_date), "
			+ "    COALESCE(s.result_collection_date, s.collection_date), "
			+ "    COALESCE(s.result_delivery_date, s.collection_date)"
			+ "))) / 86400) AS INT) AS days_since_last_movement, "
			// Les statuts terminaux sont traites par les deux premieres branches,
			// donc les seuils 3/7 j ne concernent que les dossiers encore en
			// cours : NOW() y est la bonne reference.
			+ "CASE WHEN ss2.status IN ('RESULT_ON_SITE') THEN 'green' "
			+ "     WHEN ss2.status IN ('NON_CONFORM','ANALYSIS_FAILED') THEN 'red' "
			+ "     WHEN FLOOR(EXTRACT(EPOCH FROM (NOW() - s.collection_date)) / 86400) < 3 THEN 'green' "
			+ "     WHEN FLOOR(EXTRACT(EPOCH FROM (NOW() - s.collection_date)) / 86400) < 7 THEN 'orange' "
			+ "     ELSE 'red' END AS sla_color "
			+ "FROM sample s "
			+ "JOIN sample_type st ON st.id = s.sample_type_id "
			+ "JOIN sample_status ss2 ON ss2.id = s.sample_status_id "
			+ "LEFT JOIN sample_retrieving sr ON s.sample_retrieving_id = sr.id "
			+ "JOIN site ON sr.site_id = site.id "
			+ "JOIN district d ON d.id = site.district_id "
			+ "JOIN region reg ON reg.id = d.region_id "
			+ "LEFT JOIN sample_rejection sr2 ON sr2.sample_id = s.id "
			+ "LEFT JOIN lab ON lab.id = s.reference_lab_id "
			+ "LEFT JOIN lab hub ON hub.id = s.hub_id "
			+ "LEFT JOIN lab destination_lab ON destination_lab.id = s.destination_lab_id "
			+ "WHERE (CAST(:regionId AS INT) IS NULL OR reg.id = CAST(:regionId AS INT)) "
			+ "AND (CAST(:districtId AS INT) IS NULL OR d.id = CAST(:districtId AS INT)) "
			+ "AND (CAST(:siteId AS INT) IS NULL OR site.id = CAST(:siteId AS INT)) "
			+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
			+ "AND (CAST(:startDate AS DATE) IS NULL OR s.collection_date >= CAST(:startDate AS DATE)) "
			+ "AND (CAST(:endDate AS DATE) IS NULL OR s.collection_date <= CAST(:endDate AS DATE)) "
			+ "AND (:statusIdsActive = FALSE OR ss2.id IN (:statusIds)) "
			+ "AND (:typeIdsActive = FALSE OR st.id IN (:typeIds)) "
			+ "AND (:naturesActive = FALSE OR s.sample_nature IN (:natures)) "
			+ "AND (:accessibleSiteIdsActive = FALSE OR site.id IN (:accessibleSiteIds)) "
			+ "AND (CAST(:searchText AS TEXT) IS NULL OR ("
			+ "      s.sample_identifier ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%') "
			+ "   OR s.patient_identifier ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%') "
			+ "   OR s.lab_number ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%'))) "
			+ "AND (:onlyRejected = FALSE OR ss2.status IN ('NON_CONFORM','ANALYSIS_FAILED')) "
			+ "AND (:stuckActive = FALSE OR ("
			+ "      ss2.status NOT IN ('RESULT_ON_SITE','NON_CONFORM','ANALYSIS_FAILED') "
			+ "  AND FLOOR(EXTRACT(EPOCH FROM (NOW() - GREATEST(s.collection_date, "
			+ "        COALESCE(s.deliver_at_hub_date, s.collection_date), "
			+ "        COALESCE(s.deliver_at_lab_date, s.collection_date), "
			+ "        COALESCE(s.accepted_at_hub_date, s.collection_date), "
			+ "        COALESCE(s.accepted_at_lab_date, s.collection_date), "
			+ "        COALESCE(s.analysis_completed_date, s.collection_date), "
			+ "        COALESCE(s.result_collection_date, s.collection_date), "
			+ "        COALESCE(s.result_delivery_date, s.collection_date)"
			+ "  ))) / 86400) >= CAST(:stuckDays AS INT))) "
			// Sort embedded in the query (Spring Data wraps native paginated
			// queries and breaks any table-alias-qualified ORDER BY). The
			// :sortKey + :sortDir parameters are validated via a whitelist
			// in SampleController#mapClientColumnToDb.
			+ "ORDER BY "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'district' THEN d.name END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'district' THEN d.name END DESC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'site' THEN site.name END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'site' THEN site.name END DESC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'sample_type' THEN st.name END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'sample_type' THEN st.name END DESC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'sample_nature' THEN s.sample_nature END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'sample_nature' THEN s.sample_nature END DESC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'patient_identifier' THEN s.patient_identifier END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'patient_identifier' THEN s.patient_identifier END DESC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'sample_identifier' THEN s.sample_identifier END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'sample_identifier' THEN s.sample_identifier END DESC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'collection_date' THEN s.collection_date END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'collection_date' THEN s.collection_date END DESC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'destination_lab' THEN destination_lab.lab_name END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'destination_lab' THEN destination_lab.lab_name END DESC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'status' THEN ss2.description END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'status' THEN ss2.description END DESC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'reception_date' THEN COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date) END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'reception_date' THEN COALESCE(s.deliver_at_lab_date, s.deliver_at_hub_date) END DESC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'lab' THEN COALESCE(lab.lab_name, hub.lab_name) END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'lab' THEN COALESCE(lab.lab_name, hub.lab_name) END DESC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'lab_number' THEN s.lab_number END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'lab_number' THEN s.lab_number END DESC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'asc' AND :sortKey = 'tat_days' THEN " + TatSql.SAMPLE_DAYS + " END ASC NULLS LAST, "
			+ "  CASE WHEN :sortDir = 'desc' AND :sortKey = 'tat_days' THEN " + TatSql.SAMPLE_DAYS + " END DESC NULLS LAST, "
			+ "  s.collection_date DESC ",
			countQuery = "SELECT COUNT(s.id) FROM sample s "
					+ "JOIN sample_type st ON st.id = s.sample_type_id "
					+ "JOIN sample_status ss2 ON ss2.id = s.sample_status_id "
					+ "LEFT JOIN sample_retrieving sr ON s.sample_retrieving_id = sr.id "
					+ "JOIN site ON sr.site_id = site.id "
					+ "JOIN district d ON d.id = site.district_id "
					+ "JOIN region reg ON reg.id = d.region_id "
					+ "WHERE (CAST(:regionId AS INT) IS NULL OR reg.id = CAST(:regionId AS INT)) "
					+ "AND (CAST(:districtId AS INT) IS NULL OR d.id = CAST(:districtId AS INT)) "
					+ "AND (CAST(:siteId AS INT) IS NULL OR site.id = CAST(:siteId AS INT)) "
					+ "AND (CAST(:labId AS INT) IS NULL OR s.destination_lab_id = CAST(:labId AS INT)) "
					+ "AND (CAST(:startDate AS DATE) IS NULL OR s.collection_date >= CAST(:startDate AS DATE)) "
					+ "AND (CAST(:endDate AS DATE) IS NULL OR s.collection_date <= CAST(:endDate AS DATE)) "
					+ "AND (:statusIdsActive = FALSE OR ss2.id IN (:statusIds)) "
					+ "AND (:typeIdsActive = FALSE OR st.id IN (:typeIds)) "
					+ "AND (:accessibleSiteIdsActive = FALSE OR site.id IN (:accessibleSiteIds)) "
					+ "AND (CAST(:searchText AS TEXT) IS NULL OR ("
					+ "      s.sample_identifier ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%') "
					+ "   OR s.patient_identifier ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%') "
					+ "   OR s.lab_number ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%'))) "
					+ "AND (:onlyRejected = FALSE OR ss2.status IN ('NON_CONFORM','ANALYSIS_FAILED')) "
					+ "AND (:stuckActive = FALSE OR ("
					+ "      ss2.status NOT IN ('RESULT_ON_SITE','NON_CONFORM','ANALYSIS_FAILED') "
					+ "  AND FLOOR(EXTRACT(EPOCH FROM (NOW() - GREATEST(s.collection_date, "
					+ "        COALESCE(s.deliver_at_hub_date, s.collection_date), "
					+ "        COALESCE(s.deliver_at_lab_date, s.collection_date), "
					+ "        COALESCE(s.accepted_at_hub_date, s.collection_date), "
					+ "        COALESCE(s.accepted_at_lab_date, s.collection_date), "
					+ "        COALESCE(s.analysis_completed_date, s.collection_date), "
					+ "        COALESCE(s.result_collection_date, s.collection_date), "
					+ "        COALESCE(s.result_delivery_date, s.collection_date)"
					+ "  ))) / 86400) >= CAST(:stuckDays AS INT))) ",
			nativeQuery = true)
	Page<Map<String, Object>> getSampleDetailsAdvanced(Pageable pageable,
			@Param("regionId") Integer regionId, @Param("districtId") Integer districtId,
			@Param("siteId") Integer siteId, @Param("labId") Integer labId,
			@Param("startDate") Date startDate, @Param("endDate") Date endDate,
			@Param("statusIds") List<Integer> statusIds, @Param("statusIdsActive") Boolean statusIdsActive,
			@Param("typeIds") List<Integer> typeIds, @Param("typeIdsActive") Boolean typeIdsActive,
			@Param("natures") List<String> natures, @Param("naturesActive") Boolean naturesActive,
			@Param("accessibleSiteIds") List<Integer> accessibleSiteIds,
			@Param("accessibleSiteIdsActive") Boolean accessibleSiteIdsActive,
			@Param("searchText") String searchText,
			@Param("onlyRejected") Boolean onlyRejected,
			@Param("stuckDays") Integer stuckDays, @Param("stuckActive") Boolean stuckActive,
			@Param("sortKey") String sortKey, @Param("sortDir") String sortDir);

	/**
	 * Liste des valeurs distinctes non-null de {@code sample_nature} présentes
	 * en base, triées alphabétiquement. Utilisé pour peupler le filtre
	 * "Nature de l'échantillon" sur l'écran /sample.
	 *
	 * NB : {@code sample_nature} est un champ texte libre saisi côté mobile
	 * (DBS, SANG TOTAL, PLASMA, etc.) et non une référence vers une table.
	 */
	@Query(value = "SELECT DISTINCT s.sample_nature FROM sample s "
			+ "WHERE s.sample_nature IS NOT NULL AND s.sample_nature <> '' "
			+ "ORDER BY s.sample_nature ASC", nativeQuery = true)
	List<String> findDistinctSampleNatures();

}
