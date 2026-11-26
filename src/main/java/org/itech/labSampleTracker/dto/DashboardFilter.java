package org.itech.labSampleTracker.dto;

import java.time.LocalDate;
import java.util.List;

import org.itech.labSampleTracker.service.security.UserScopeService.ScopedFilter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Filtres d'un indicateur du tableau de bord : période, zone, laboratoire de
 * destination, type de laboratoire, type d'échantillon, et périmètre de
 * l'utilisateur ({@code accessibleSiteIds}, null ou vide = pas de
 * restriction). Les identifiants null ne filtrent pas.
 *
 * <p>Les mêmes filtres servent au tableau de bord, aux rapports et au
 * contrôle de cohérence, pour que les chiffres se comparent à filtres
 * identiques.
 */
public record DashboardFilter(LocalDate startDate, LocalDate endDate, Integer regionId, Integer districtId,
		Integer siteId, Integer labId, String labType, Integer sampleTypeId, List<Integer> accessibleSiteIds) {

	/**
	 * Filtres type de laboratoire (laboratoire de destination, comme le filtre
	 * « Laboratoire ») et type d'échantillon. Alias requis : {@code s} (sample).
	 */
	public static final String SAMPLE_FILTER_SQL =
			" AND (CAST(:labType AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM lab flt "
			+ "      WHERE flt.id = s.destination_lab_id AND flt.lab_type = CAST(:labType AS VARCHAR))) "
			+ " AND (CAST(:sampleTypeId AS INT) IS NULL OR s.sample_type_id = CAST(:sampleTypeId AS INT)) ";

	public DashboardFilter {
		labType = labType == null || labType.isBlank() ? null : labType.trim();
		accessibleSiteIds = accessibleSiteIds == null || accessibleSiteIds.isEmpty() ? null
				: List.copyOf(accessibleSiteIds);
	}

	/** Sélection de l'écran déjà intersectée avec le périmètre de l'utilisateur. */
	public static DashboardFilter of(LocalDate startDate, LocalDate endDate, ScopedFilter scope, String labType,
			Integer sampleTypeId) {
		return new DashboardFilter(startDate, endDate, scope.getRegionId(), scope.getDistrictId(), scope.getSiteId(),
				scope.getLabId(), labType, sampleTypeId, scope.getAccessibleSiteIds());
	}

	public DashboardFilter withPeriod(LocalDate start, LocalDate end) {
		return new DashboardFilter(start, end, regionId, districtId, siteId, labId, labType, sampleTypeId,
				accessibleSiteIds);
	}

	/** Déplier une zone du tableau de répartition revient à filtrer sur elle. */
	public DashboardFilter withRegion(Integer region) {
		return new DashboardFilter(startDate, endDate, region, districtId, siteId, labId, labType, sampleTypeId,
				accessibleSiteIds);
	}

	public boolean scopeActive() {
		return accessibleSiteIds != null;
	}

	/**
	 * Paramètres nommés. Le périmètre est passé en booléen + liste plutôt
	 * qu'en tableau nullable : PostgreSQL ne sait pas typer un tableau NULL, et
	 * {@code IN (:liste)} refuse une liste vide ({@code [-1]} ne sert que de
	 * valeur syntaxique quand le booléen est faux).
	 */
	public MapSqlParameterSource params() {
		return new MapSqlParameterSource()
				.addValue("startDate", startDate).addValue("endDate", endDate)
				.addValue("regionId", regionId).addValue("districtId", districtId)
				.addValue("siteId", siteId).addValue("labId", labId)
				.addValue("labType", labType).addValue("sampleTypeId", sampleTypeId)
				.addValue("accessibleSiteIdsActive", scopeActive())
				.addValue("accessibleSiteIds", scopeActive() ? accessibleSiteIds : List.of(-1));
	}
}
