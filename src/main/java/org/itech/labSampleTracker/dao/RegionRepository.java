/*
 * Java domain class for entity "Region"
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:04 )
 * @author Pascal
 */
package org.itech.labSampleTracker.dao;

import java.util.Map;

import org.itech.labSampleTracker.entities.Region;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * <h2>RegionRepository</h2>
 *
 * createdAt : 2024-03-31 - Time 19:08:04
 * <p>
 * Description: "Region" Repository
 */


public interface RegionRepository  extends JpaRepository<Region, Integer> , JpaSpecificationExecutor<Region> {

	/**
	 * Paginated admin region list with full-text search and aggregated
	 * child counts (districts, sites, labs).
	 */
	@Query(value = "SELECT r.id, r.name, r.is_active, "
			+ "       (SELECT COUNT(d.id) FROM district d WHERE d.region_id = r.id) AS district_count, "
			+ "       (SELECT COUNT(s.id) FROM site s LEFT JOIN district d ON d.id = s.district_id WHERE d.region_id = r.id) AS site_count, "
			+ "       (SELECT COUNT(l.id) FROM lab l LEFT JOIN district d ON d.id = l.district_id WHERE d.region_id = r.id) AS lab_count "
			+ "FROM region r "
			+ "WHERE (CAST(:searchText AS TEXT) IS NULL OR r.name ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%')) "
			+ "ORDER BY r.is_active DESC, r.name",
			countQuery = "SELECT COUNT(r.id) FROM region r "
					+ "WHERE (CAST(:searchText AS TEXT) IS NULL OR r.name ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%'))",
			nativeQuery = true)
	Page<Map<String, Object>> findRegionsAdvanced(Pageable pageable,
			@Param("searchText") String searchText);

	/**
	 * Count the number of districts belonging to a given region (used as a
	 * delete guard).
	 */
	@Query(value = "SELECT COUNT(d.id) FROM district d WHERE d.region_id = :regionId", nativeQuery = true)
	long countDistrictsByRegion(@Param("regionId") Integer regionId);
}
