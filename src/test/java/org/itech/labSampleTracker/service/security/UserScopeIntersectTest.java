package org.itech.labSampleTracker.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.itech.labSampleTracker.service.security.UserScopeService.Scope;
import org.itech.labSampleTracker.service.security.UserScopeService.ScopedFilter;
import org.junit.jupiter.api.Test;

/**
 * Intersection des filtres de l'écran avec le périmètre de l'utilisateur
 * (cahier V.3 : les données hors périmètre ne doivent apparaître ni à l'écran
 * ni dans les totaux).
 */
class UserScopeIntersectTest {

	// intersect() ne dépend pas des dépendances injectées.
	private final UserScopeService service = new UserScopeService(null, null);

	/** Convoyeur : deux sites de la région 28 (district 5), un labo. */
	private static Scope convoyeur() {
		return Scope.builder().global(false)
				.regionIds(List.of(28)).districtIds(List.of(5)).siteIds(List.of(101, 102))
				.labIds(List.of(6)).circuitIds(List.of(1)).build();
	}

	@Test
	void sansFiltreLePerimetreEnSitesSApplique() {
		ScopedFilter f = service.intersect(convoyeur(), null, null, null, null);
		assertFalse(f.isForceEmpty());
		assertEquals(List.of(101, 102), f.getAccessibleSiteIds());
	}

	@Test
	void unFiltreRegionGardeLaRestrictionAuxSitesDeLUtilisateur() {
		// Avant correction : accessibleSiteIds = null → toute la région 28.
		ScopedFilter f = service.intersect(convoyeur(), 28, null, null, null);
		assertFalse(f.isForceEmpty());
		assertEquals(28, f.getRegionId());
		assertEquals(List.of(101, 102), f.getAccessibleSiteIds());
	}

	@Test
	void unFiltreDistrictOuSiteGardeAussiLaRestriction() {
		assertEquals(List.of(101, 102), service.intersect(convoyeur(), null, 5, null, null).getAccessibleSiteIds());
		assertEquals(List.of(101, 102), service.intersect(convoyeur(), null, null, 101, null).getAccessibleSiteIds());
	}

	@Test
	void unFiltreLaboSeulLeveLaRestrictionAuxSites() {
		// Un labo autorisé voit ce qui lui est destiné, quel que soit le site d'origine.
		ScopedFilter f = service.intersect(convoyeur(), null, null, null, 6);
		assertFalse(f.isForceEmpty());
		assertNull(f.getAccessibleSiteIds());
		assertEquals(6, f.getLabId());
	}

	@Test
	void laboEtRegionEnsembleGardentLaRestriction() {
		assertEquals(List.of(101, 102), service.intersect(convoyeur(), 28, null, null, 6).getAccessibleSiteIds());
	}

	@Test
	void unFiltreHorsPerimetreNeRenvoieRien() {
		assertTrue(service.intersect(convoyeur(), 18, null, null, null).isForceEmpty());
		assertTrue(service.intersect(convoyeur(), null, null, 999, null).isForceEmpty());
		assertTrue(service.intersect(convoyeur(), null, null, null, 2).isForceEmpty());
	}

	@Test
	void unUtilisateurNationalNEstPasRestreint() {
		ScopedFilter f = service.intersect(Scope.builder().global(true).build(), 28, null, null, null);
		assertFalse(f.isForceEmpty());
		assertNull(f.getAccessibleSiteIds());
		assertEquals(28, f.getRegionId());
	}
}
