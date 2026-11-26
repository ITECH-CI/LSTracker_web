package org.itech.labSampleTracker.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.itech.labSampleTracker.dao.UserScopeRepository;
import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.service.AppUserService;
import org.itech.labSampleTracker.service.security.UserScopeService.Scope;
import org.itech.labSampleTracker.service.security.UserScopeService.ScopedFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Superviseurs régional et de district : ils ne voient aucune donnée hors de
 * leurs zones. Listes, exports et tableau de bord passent par
 * {@link UserScopeService#intersectCurrent}, la fiche par
 * {@link UserScopeService#canAccessSample}, l'API mobile par
 * {@link UserScopeService#isNational} puis le périmètre résolu.
 *
 * Région A (1) : district 10, sites 100 et 101, labo 7.
 * Région B (2) : district 20, site 200, labo 8.
 */
class ZoneSupervisorScopeTest {

	private static final int REGION_A = 1, REGION_B = 2;
	private static final int DISTRICT_A = 10, DISTRICT_B = 20;
	private static final int SITE_A1 = 100, SITE_A2 = 101, SITE_B = 200;
	private static final int LAB_A = 7, LAB_B = 8;

	private final UserScopeRepository repo = mock(UserScopeRepository.class);
	private final AppUserService users = mock(AppUserService.class);
	private final UserScopeService service = new UserScopeService(repo, users);

	private AppUser connect(String role, String level, int id) {
		AppUser u = new AppUser();
		u.setId(id);
		u.setLogin("sup" + id);
		u.setRole(role);
		u.setUserLevel(level);
		when(users.findUserByLogin(u.getLogin())).thenReturn(u);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.getLogin(),
				"x", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
		return u;
	}

	/** Périmètre hérité de l'affectation à la région A (UserScopeRepository). */
	private void assignedToRegionA(int id) {
		when(repo.findAccessibleRegionIds(id)).thenReturn(List.of(REGION_A));
		when(repo.findAccessibleDistrictIds(id)).thenReturn(List.of(DISTRICT_A));
		when(repo.findAccessibleSiteIds(id)).thenReturn(List.of(SITE_A1, SITE_A2));
		when(repo.findAccessibleLabIds(id)).thenReturn(List.of(LAB_A));
		when(repo.findAccessibleCircuitIds(id)).thenReturn(List.of());
	}

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void unSuperviseurRegionalNEstJamaisNational() {
		AppUser district = new AppUser();
		district.setRole("SUPERVISEUR_DISTRICT");
		district.setUserLevel("CENTRAL");
		assertFalse(UserScopeService.isNational(district));

		AppUser u = connect("SUPERVISEUR_REGION", "CENTRAL", 50);
		assignedToRegionA(50);
		assertFalse(UserScopeService.isNational(u), "même sur un compte de niveau central");
		assertFalse(service.resolveCurrent().isGlobal());
	}

	@Test
	void sansFiltreIlNeVoitQueLesSitesDeSaRegion() {
		connect("SUPERVISEUR_REGION", "REGION", 50);
		assignedToRegionA(50);
		ScopedFilter f = service.intersectCurrent(null, null, null, null);
		assertFalse(f.isForceEmpty());
		assertEquals(List.of(SITE_A1, SITE_A2), f.getAccessibleSiteIds());
	}

	@Test
	void unFiltreSurLaRegionBNeRenvoieRien() {
		connect("SUPERVISEUR_REGION", "REGION", 50);
		assignedToRegionA(50);
		assertTrue(service.intersectCurrent(REGION_B, null, null, null).isForceEmpty(), "région B");
		assertTrue(service.intersectCurrent(null, DISTRICT_B, null, null).isForceEmpty(), "district de B");
		assertTrue(service.intersectCurrent(null, null, SITE_B, null).isForceEmpty(), "site de B");
		assertTrue(service.intersectCurrent(null, null, null, LAB_B).isForceEmpty(), "labo de B");
	}

	@Test
	void unFiltreLaboGardeLaRestrictionAuxSitesDeSaRegion() {
		// Un labo de la région A peut recevoir des échantillons de la région B :
		// le superviseur ne les voit pas pour autant.
		connect("SUPERVISEUR_REGION", "REGION", 50);
		assignedToRegionA(50);
		ScopedFilter f = service.intersectCurrent(null, null, null, LAB_A);
		assertFalse(f.isForceEmpty());
		assertEquals(LAB_A, f.getLabId());
		assertEquals(List.of(SITE_A1, SITE_A2), f.getAccessibleSiteIds());
	}

	@Test
	void laFicheDUnEchantillonDeLaRegionBEstRefusee() {
		connect("SUPERVISEUR_REGION", "REGION", 50);
		assignedToRegionA(50);
		assertTrue(service.canAccessSample(SITE_A1, LAB_B), "collecté dans sa région");
		assertFalse(service.canAccessSample(SITE_B, LAB_B), "collecté et analysé en région B");
		assertFalse(service.canAccessSample(SITE_B, LAB_A), "collecté en B, déposé dans un labo de A");
	}

	@Test
	void leSuperviseurDeDistrictEstLimiteASesDistricts() {
		connect("SUPERVISEUR_DISTRICT", "DISTRICT", 60);
		when(repo.findAccessibleRegionIds(60)).thenReturn(List.of(REGION_A));
		when(repo.findAccessibleDistrictIds(60)).thenReturn(List.of(DISTRICT_A));
		when(repo.findAccessibleSiteIds(60)).thenReturn(List.of(SITE_A1));
		when(repo.findAccessibleLabIds(60)).thenReturn(List.of(LAB_A));
		when(repo.findAccessibleCircuitIds(60)).thenReturn(List.of());

		assertEquals(List.of(SITE_A1), service.intersectCurrent(null, null, null, null).getAccessibleSiteIds());
		assertTrue(service.intersectCurrent(null, DISTRICT_B, null, null).isForceEmpty());
		assertTrue(service.intersectCurrent(REGION_B, null, null, null).isForceEmpty());
		assertFalse(service.canAccessSample(SITE_B, LAB_A));
	}

	@Test
	void uneZoneAffecteeSansSiteNeDonneRien() {
		// Région affectée dont aucun district n'a de site : une liste de sites
		// vide valait « aucune restriction » dans les requêtes.
		connect("SUPERVISEUR_REGION", "REGION", 80);
		when(repo.findAccessibleRegionIds(80)).thenReturn(List.of(REGION_A));
		when(repo.findAccessibleDistrictIds(80)).thenReturn(List.of(DISTRICT_A));
		when(repo.findAccessibleSiteIds(80)).thenReturn(List.of());
		when(repo.findAccessibleLabIds(80)).thenReturn(List.of());
		when(repo.findAccessibleCircuitIds(80)).thenReturn(List.of());

		assertTrue(service.intersectCurrent(null, null, null, null).isForceEmpty(), "sans filtre");
		assertTrue(service.intersectCurrent(REGION_A, null, null, null).isForceEmpty(), "filtre sur sa région");
		assertTrue(service.intersectCurrent(null, DISTRICT_A, null, null).isForceEmpty(), "filtre sur son district");
		assertFalse(service.canAccessSample(SITE_B));
	}

	@Test
	void sansAffectationIlNeVoitRien() {
		connect("SUPERVISEUR_REGION", "REGION", 70);
		assertTrue(service.intersectCurrent(null, null, null, null).isForceEmpty());
		assertFalse(service.canAccessSample(SITE_A1));
	}

	@Test
	void lesListesDeFiltreSeLimitentAuPerimetre() {
		connect("SUPERVISEUR_REGION", "REGION", 50);
		assignedToRegionA(50);
		java.util.function.Function<int[], List<java.util.Map<String, Object>>> rows = ids -> java.util.Arrays
				.stream(ids).mapToObj(i -> java.util.Map.<String, Object>of("id", i, "name", "Zone " + i)).toList();
		java.util.function.Function<List<java.util.Map<String, Object>>, List<Object>> idsOf = l -> l.stream()
				.map(m -> m.get("id")).toList();

		assertEquals(List.of(REGION_A), idsOf.apply(service.visibleCurrent(UserScopeService.ZoneList.REGIONS,
				rows.apply(new int[] { REGION_A, REGION_B }))));
		assertEquals(List.of(DISTRICT_A), idsOf.apply(service.visibleCurrent(UserScopeService.ZoneList.DISTRICTS,
				rows.apply(new int[] { DISTRICT_A, DISTRICT_B }))));
		assertEquals(List.of(SITE_A1, SITE_A2), idsOf.apply(service.visibleCurrent(UserScopeService.ZoneList.SITES,
				rows.apply(new int[] { SITE_A1, SITE_A2, SITE_B }))));
		assertEquals(List.of(LAB_A), idsOf.apply(service.visibleCurrent(UserScopeService.ZoneList.LABS,
				rows.apply(new int[] { LAB_A, LAB_B }))));

		// Compte national : tout le référentiel.
		Scope national = Scope.builder().global(true).build();
		assertEquals(2, UserScopeService.visible(national, UserScopeService.ZoneList.REGIONS,
				rows.apply(new int[] { REGION_A, REGION_B })).size());
		// Sans affectation : rien.
		connect("SUPERVISEUR_REGION", "REGION", 71);
		assertTrue(service.visibleCurrent(UserScopeService.ZoneList.REGIONS, rows.apply(new int[] { REGION_A }))
				.isEmpty());
	}

	@Test
	void lesAutresProfilsGardentLAccesParLeLaboDestinataire() {
		Scope labUser = Scope.builder().global(false).regionIds(List.of(REGION_A)).districtIds(List.of(DISTRICT_A))
				.siteIds(List.of(SITE_A1)).labIds(List.of(LAB_A)).circuitIds(List.of()).build();
		assertEquals(null, service.intersect(labUser, null, null, null, LAB_A).getAccessibleSiteIds());
	}
}
