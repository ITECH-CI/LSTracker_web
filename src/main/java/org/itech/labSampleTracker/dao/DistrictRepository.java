/*
 * Java domain class for entity "District"
 * Created on 2024-03-31 ( Date ISO 2024-03-31 - Time 19:08:03 )
 * @author Pascal
 */
package org.itech.labSampleTracker.dao;

import java.util.Map;

import org.itech.labSampleTracker.entities.District;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * <h2>DistrictRepository</h2>
 *
 * createdAt : 2024-03-31 - Time 19:08:03
 * <p>
 * Description: "District" Repository
 */


public interface DistrictRepository  extends JpaRepository<District, Integer> , JpaSpecificationExecutor<District> {

	/**
	 * Paginated admin district list with full-text search, region filter and
	 * aggregated child counts (sites, labs).
	 */
	@Query(value = "SELECT d.id, d.name, d.region_id, d.is_active, r.name AS region_name, "
			+ "       (SELECT COUNT(s.id) FROM site s WHERE s.district_id = d.id) AS site_count, "
			+ "       (SELECT COUNT(l.id) FROM lab l WHERE l.district_id = d.id) AS lab_count "
			+ "FROM district d "
			+ "LEFT JOIN region r ON r.id = d.region_id "
			+ "WHERE (CAST(:searchText AS TEXT) IS NULL OR ("
			+ "      d.name ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%') "
			+ "   OR r.name ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%'))) "
			+ "AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT)) "
			+ "ORDER BY d.is_active DESC, r.name NULLS LAST, d.name",
			countQuery = "SELECT COUNT(d.id) FROM district d "
					+ "LEFT JOIN region r ON r.id = d.region_id "
					+ "WHERE (CAST(:searchText AS TEXT) IS NULL OR ("
					+ "      d.name ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%') "
					+ "   OR r.name ILIKE CONCAT('%', CAST(:searchText AS TEXT), '%'))) "
					+ "AND (CAST(:regionId AS INT) IS NULL OR d.region_id = CAST(:regionId AS INT))",
			nativeQuery = true)
	Page<Map<String, Object>> findDistrictsAdvanced(Pageable pageable,
			@Param("searchText") String searchText,
			@Param("regionId") Integer regionId);

	/**
	 * Count the number of sites belonging to a given district (used as a
	 * delete guard).
	 */
	@Query(value = "SELECT COUNT(s.id) FROM site s WHERE s.district_id = :districtId", nativeQuery = true)
	long countSitesByDistrict(@Param("districtId") Integer districtId);
}
