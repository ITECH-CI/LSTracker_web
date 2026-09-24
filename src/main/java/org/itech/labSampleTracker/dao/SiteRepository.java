/*
 * Java domain class for entity "Site" 
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
 */
package org.itech.labSampleTracker.dao;

import java.util.List;
import java.util.Map;

import org.itech.labSampleTracker.entities.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiteRepository extends JpaRepository<Site, Integer>, JpaSpecificationExecutor<Site> {

	public Site findByDhisCode(String dhisCode);

	/**
	 * Paginated admin site list with full-text search and region/district filters.
	 * Returns enriched rows (region name, district name) to avoid N+1 queries
	 * at render time.
	 */
	@Query(value = "SELECT s.id, s.name, s.is_active, s.dhis_code, s.datim_code, s.longitude, s.latitude, "
			+ "       s.district_id, d.name AS district, d.region_id, r.name AS region "
			+ "FROM site s "
			+ "LEFT JOIN district d ON d.id = s.district_id "
			+ "LEFT JOIN region r ON r.id = d.region_id "
			+ "WHERE (CAST(:searchText AS TEXT) IS NULL OR ("
			+ "      s.name ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%') "
			+ "   OR s.dhis_code ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%') "
			+ "   OR s.datim_code ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%') "
			+ "   OR d.name ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%'))) "
			+ "AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
			+ "AND (CAST(:districtId AS INT) IS NULL OR s.district_id = CAST(:districtId AS INT)) "
			+ "AND (:accessibleSiteIdsActive = FALSE OR s.id IN (:accessibleSiteIds)) "
			+ "ORDER BY s.is_active DESC, r.name NULLS LAST, d.name NULLS LAST, s.name",
			countQuery = "SELECT COUNT(s.id) FROM site s "
					+ "LEFT JOIN district d ON d.id = s.district_id "
					+ "LEFT JOIN region r ON r.id = d.region_id "
					+ "WHERE (CAST(:searchText AS TEXT) IS NULL OR ("
					+ "      s.name ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%') "
					+ "   OR s.dhis_code ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%') "
					+ "   OR s.datim_code ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%') "
					+ "   OR d.name ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%'))) "
					+ "AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
					+ "AND (CAST(:districtId AS INT) IS NULL OR s.district_id = CAST(:districtId AS INT)) "
					+ "AND (:accessibleSiteIdsActive = FALSE OR s.id IN (:accessibleSiteIds))",
			nativeQuery = true)
	Page<Map<String, Object>> findSitesAdvanced(Pageable pageable,
			@Param("searchText") String searchText,
			@Param("regionId") Integer regionId,
			@Param("districtId") Integer districtId,
			@Param("accessibleSiteIds") List<Integer> accessibleSiteIds,
			@Param("accessibleSiteIdsActive") Boolean accessibleSiteIdsActive);
}
