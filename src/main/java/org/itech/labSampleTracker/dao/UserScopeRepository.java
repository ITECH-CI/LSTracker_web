package org.itech.labSampleTracker.dao;

import java.util.List;

import org.itech.labSampleTracker.entities.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Resolves the geographic scope (region / district / site / lab / circuit IDs)
 * that a given user is allowed to see. Inherits down the hierarchy:
 * Region → all Districts of region → all Sites of those districts.
 * District → all Sites of district.
 * Lab → its containing District (and that district's Sites).
 * Circuit → its assigned Sites, and the Labs of those sites' Districts.
 *
 * Anchored on AppUser (any spring-data managed entity in the package would work)
 * just so Spring picks it up as a JPA repository.
 */
public interface UserScopeRepository extends JpaRepository<AppUser, Integer> {

	@Query(value = "SELECT DISTINCT r.id FROM app_user_has_region uhr "
			+ "JOIN region r ON r.id = uhr.region_id WHERE uhr.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT d.region_id FROM app_user_has_district uhd "
			+ "JOIN district d ON d.id = uhd.district_id WHERE uhd.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT d.region_id FROM app_user_has_site uhs "
			+ "JOIN site s ON s.id = uhs.site_id "
			+ "JOIN district d ON d.id = s.district_id WHERE uhs.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT d.region_id FROM app_user_has_lab uhl "
			+ "JOIN lab l ON l.id = uhl.lab_id "
			+ "JOIN district d ON d.id = l.district_id WHERE uhl.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT d.region_id FROM app_user_has_circuit uhc "
			+ "JOIN circuit_site cs ON cs.circuit_id = uhc.circuit_id "
			+ "JOIN site s ON s.id = cs.site_id "
			+ "JOIN district d ON d.id = s.district_id WHERE uhc.app_user_id = :userId", nativeQuery = true)
	List<Integer> findAccessibleRegionIds(@Param("userId") Integer userId);

	@Query(value = "SELECT DISTINCT d.id FROM app_user_has_region uhr "
			+ "JOIN district d ON d.region_id = uhr.region_id WHERE uhr.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT uhd.district_id FROM app_user_has_district uhd WHERE uhd.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT s.district_id FROM app_user_has_site uhs "
			+ "JOIN site s ON s.id = uhs.site_id WHERE uhs.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT l.district_id FROM app_user_has_lab uhl "
			+ "JOIN lab l ON l.id = uhl.lab_id WHERE uhl.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT s.district_id FROM app_user_has_circuit uhc "
			+ "JOIN circuit_site cs ON cs.circuit_id = uhc.circuit_id "
			+ "JOIN site s ON s.id = cs.site_id WHERE uhc.app_user_id = :userId", nativeQuery = true)
	List<Integer> findAccessibleDistrictIds(@Param("userId") Integer userId);

	@Query(value = "SELECT DISTINCT s.id FROM app_user_has_region uhr "
			+ "JOIN district d ON d.region_id = uhr.region_id "
			+ "JOIN site s ON s.district_id = d.id WHERE uhr.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT s.id FROM app_user_has_district uhd "
			+ "JOIN site s ON s.district_id = uhd.district_id WHERE uhd.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT uhs.site_id FROM app_user_has_site uhs WHERE uhs.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT s.id FROM app_user_has_lab uhl "
			+ "JOIN lab l ON l.id = uhl.lab_id "
			+ "JOIN site s ON s.district_id = l.district_id WHERE uhl.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT cs.site_id FROM app_user_has_circuit uhc "
			+ "JOIN circuit_site cs ON cs.circuit_id = uhc.circuit_id WHERE uhc.app_user_id = :userId", nativeQuery = true)
	List<Integer> findAccessibleSiteIds(@Param("userId") Integer userId);

	@Query(value = "SELECT DISTINCT l.id FROM app_user_has_region uhr "
			+ "JOIN district d ON d.region_id = uhr.region_id "
			+ "JOIN lab l ON l.district_id = d.id WHERE uhr.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT l.id FROM app_user_has_district uhd "
			+ "JOIN lab l ON l.district_id = uhd.district_id WHERE uhd.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT uhl.lab_id FROM app_user_has_lab uhl WHERE uhl.app_user_id = :userId "
			+ "UNION "
			+ "SELECT DISTINCT l.id FROM app_user_has_circuit uhc "
			+ "JOIN circuit_site cs ON cs.circuit_id = uhc.circuit_id "
			+ "JOIN site s ON s.id = cs.site_id "
			+ "JOIN lab l ON l.district_id = s.district_id WHERE uhc.app_user_id = :userId", nativeQuery = true)
	List<Integer> findAccessibleLabIds(@Param("userId") Integer userId);

	/**
	 * Labos qu'un convoyeur peut choisir : ceux des districts couverts par ses
	 * circuits (circuit -> site -> district -> labo), actifs uniquement. Même
	 * règle que {@code LabServiceImpl.getAllLabIdAndNamesByRider} (métadonnées
	 * mobile).
	 */
	@Query(value = "SELECT DISTINCT l.id FROM app_user_has_circuit uhc "
			+ "JOIN circuit c ON c.id = uhc.circuit_id "
			+ "JOIN circuit_site cs ON cs.circuit_id = uhc.circuit_id "
			+ "JOIN site s ON s.id = cs.site_id "
			+ "JOIN lab l ON l.district_id = s.district_id "
			+ "WHERE uhc.app_user_id = :userId AND l.is_active AND cs.is_active AND c.is_active", nativeQuery = true)
	List<Integer> findRiderLabIds(@Param("userId") Integer userId);

	@Query(value = "SELECT DISTINCT uhc.circuit_id FROM app_user_has_circuit uhc WHERE uhc.app_user_id = :userId", nativeQuery = true)
	List<Integer> findAccessibleCircuitIds(@Param("userId") Integer userId);
}
