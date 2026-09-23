package org.itech.labSampleTracker.service.security;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.itech.labSampleTracker.dao.UserScopeRepository;
import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.service.AppUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Resolves the geographic scope of the current user (regions / districts / sites
 * / labs / circuits she may see). Provides two helpers:
 *
 * <ul>
 *   <li>{@link #resolve(Integer)}/{@link #resolveCurrent()} — returns the full scope</li>
 *   <li>{@link #intersect(Scope, Integer, Integer, Integer, Integer)} —
 *       returns a {@link ScopedFilter} suitable for repository queries: every
 *       client-supplied filter is silently replaced by the intersection with
 *       what the user actually owns. If the requested value falls outside the
 *       user's scope, the matching forceEmpty flag is raised so the caller can
 *       short-circuit and return an empty page.</li>
 * </ul>
 *
 * Roles {@code ADMIN}, {@code SUPPORT}, {@code MANAGER} bypass scope checks
 * (they see everything; only their explicit filters apply).
 */
@Service
@RequiredArgsConstructor
public class UserScopeService {

	private static final Logger log = LoggerFactory.getLogger(UserScopeService.class);

	private static final Set<String> GLOBAL_ROLES = Set.of("ADMIN", "SUPPORT", "MANAGER");

	private final UserScopeRepository repo;
	private final AppUserService appUserService;

	@Getter
	@Builder
	public static class Scope {
		private final boolean global;
		private final List<Integer> regionIds;
		private final List<Integer> districtIds;
		private final List<Integer> siteIds;
		private final List<Integer> labIds;
		private final List<Integer> circuitIds;
	}

	@Getter
	@Builder
	public static class ScopedFilter {
		/** if true the caller should return an empty result (filter outside user scope) */
		private final boolean forceEmpty;
		/** intersected region id (may be null when no region filter) */
		private final Integer regionId;
		private final Integer districtId;
		private final Integer siteId;
		private final Integer labId;
		/** accessible siteIds when no narrower filter is applicable (null when global / no constraint) */
		private final List<Integer> accessibleSiteIds;
		private final List<Integer> accessibleLabIds;
	}

	public Scope resolveCurrent() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
			return Scope.builder().global(false)
					.regionIds(List.of()).districtIds(List.of()).siteIds(List.of())
					.labIds(List.of()).circuitIds(List.of()).build();
		}
		AppUser user = appUserService.findUserByLogin(auth.getName());
		if (user == null) {
			return Scope.builder().global(false)
					.regionIds(List.of()).districtIds(List.of()).siteIds(List.of())
					.labIds(List.of()).circuitIds(List.of()).build();
		}
		boolean global = isGlobal(user.getRole(), auth);
		if (global) {
			return Scope.builder().global(true).build();
		}
		return resolve(user.getId());
	}

	public Scope resolve(Integer userId) {
		if (userId == null) {
			return Scope.builder().global(false)
					.regionIds(List.of()).districtIds(List.of()).siteIds(List.of())
					.labIds(List.of()).circuitIds(List.of()).build();
		}
		List<Integer> regions = Optional.ofNullable(repo.findAccessibleRegionIds(userId)).orElse(List.of());
		List<Integer> districts = Optional.ofNullable(repo.findAccessibleDistrictIds(userId)).orElse(List.of());
		List<Integer> sites = Optional.ofNullable(repo.findAccessibleSiteIds(userId)).orElse(List.of());
		List<Integer> labs = Optional.ofNullable(repo.findAccessibleLabIds(userId)).orElse(List.of());
		List<Integer> circuits = Optional.ofNullable(repo.findAccessibleCircuitIds(userId)).orElse(List.of());

		return Scope.builder()
				.global(false)
				.regionIds(regions)
				.districtIds(districts)
				.siteIds(sites)
				.labIds(labs)
				.circuitIds(circuits)
				.build();
	}

	/**
	 * Intersect client-supplied filters with the user's scope. Any out-of-scope
	 * single-id filter raises {@code forceEmpty} so the caller can short-circuit
	 * the query and return empty.
	 */
	public ScopedFilter intersect(Scope scope, Integer requestedRegion, Integer requestedDistrict,
			Integer requestedSite, Integer requestedLab) {
		if (scope == null) {
			return ScopedFilter.builder().forceEmpty(true).build();
		}
		if (scope.isGlobal()) {
			return ScopedFilter.builder()
					.forceEmpty(false)
					.regionId(requestedRegion)
					.districtId(requestedDistrict)
					.siteId(requestedSite)
					.labId(requestedLab)
					.accessibleSiteIds(null)
					.accessibleLabIds(null)
					.build();
		}

		// Non-global user with no assignment whatsoever → sees nothing
		boolean hasAnyAssignment = !scope.getRegionIds().isEmpty()
				|| !scope.getDistrictIds().isEmpty()
				|| !scope.getSiteIds().isEmpty()
				|| !scope.getLabIds().isEmpty()
				|| !scope.getCircuitIds().isEmpty();
		if (!hasAnyAssignment) {
			log.warn("User has no geographic assignment, forcing empty result");
			return ScopedFilter.builder().forceEmpty(true).build();
		}

		if (requestedRegion != null && !scope.getRegionIds().contains(requestedRegion)) {
			log.info("Requested region={} outside user scope (allowed regions={}), forcing empty", requestedRegion, scope.getRegionIds());
			return ScopedFilter.builder().forceEmpty(true).build();
		}
		if (requestedDistrict != null && !scope.getDistrictIds().contains(requestedDistrict)) {
			log.info("Requested district={} outside user scope (allowed districts={}), forcing empty", requestedDistrict, scope.getDistrictIds());
			return ScopedFilter.builder().forceEmpty(true).build();
		}
		if (requestedSite != null && !scope.getSiteIds().contains(requestedSite)) {
			log.info("Requested site={} outside user scope (allowed sites={}), forcing empty", requestedSite, scope.getSiteIds());
			return ScopedFilter.builder().forceEmpty(true).build();
		}
		if (requestedLab != null && !scope.getLabIds().contains(requestedLab)) {
			log.info("Requested lab={} outside user scope (allowed labs={}), forcing empty", requestedLab, scope.getLabIds());
			return ScopedFilter.builder().forceEmpty(true).build();
		}

		// Le périmètre en sites reste appliqué quand un filtre géographique est
		// choisi : une région est « autorisée » dès que l'utilisateur y a un seul
		// site, et sans cette restriction il voyait toute la région (cahier V.3 :
		// les données hors périmètre ne doivent pas apparaître dans ses totaux).
		// Seul un filtre labo, sans filtre géographique, la lève : un labo
		// autorisé voit ce qui lui est destiné, quel que soit le site d'origine.
		boolean labOnly = requestedLab != null && requestedRegion == null
				&& requestedDistrict == null && requestedSite == null;
		boolean noNarrowFilter = requestedRegion == null && requestedDistrict == null
				&& requestedSite == null && requestedLab == null;
		List<Integer> accessibleSiteIds = labOnly ? null : scope.getSiteIds();
		List<Integer> accessibleLabIds = noNarrowFilter ? scope.getLabIds() : null;

		return ScopedFilter.builder()
				.forceEmpty(false)
				.regionId(requestedRegion)
				.districtId(requestedDistrict)
				.siteId(requestedSite)
				.labId(requestedLab)
				.accessibleSiteIds(accessibleSiteIds == null ? null : List.copyOf(accessibleSiteIds))
				.accessibleLabIds(accessibleLabIds == null ? null : List.copyOf(accessibleLabIds))
				.build();
	}

	/**
	 * L'utilisateur connecté peut-il consulter / modifier cet échantillon ?
	 * Oui s'il est global, si l'échantillon vient d'un site de son périmètre,
	 * ou s'il est destiné / déposé à l'un de ses laboratoires (cahier V.3).
	 */
	public boolean canAccessSample(Integer collectionSiteId, Integer... labIds) {
		Scope scope = resolveCurrent();
		if (scope.isGlobal()) {
			return true;
		}
		if (collectionSiteId != null && scope.getSiteIds().contains(collectionSiteId)) {
			return true;
		}
		for (Integer lab : labIds) {
			if (lab != null && scope.getLabIds().contains(lab)) {
				return true;
			}
		}
		return false;
	}

	public ScopedFilter intersectCurrent(Integer region, Integer district, Integer site, Integer lab) {
		return intersect(resolveCurrent(), region, district, site, lab);
	}

	private boolean isGlobal(String role, Authentication auth) {
		if (role != null && GLOBAL_ROLES.contains(role.toUpperCase())) {
			return true;
		}
		return auth.getAuthorities().stream()
				.map(a -> a.getAuthority() != null ? a.getAuthority().toUpperCase() : "")
				.anyMatch(GLOBAL_ROLES::contains);
	}

	public static List<Integer> emptyList() {
		return Collections.emptyList();
	}
}
