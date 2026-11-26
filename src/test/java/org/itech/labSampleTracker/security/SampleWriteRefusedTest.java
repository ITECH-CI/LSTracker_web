package org.itech.labSampleTracker.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.itech.labSampleTracker.api.controller.TrackingController;
import org.itech.labSampleTracker.api.sync.SampleSyncController;
import org.itech.labSampleTracker.controller.SampleController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Les profils de consultation (pilotage national, superviseurs régional et
 * de district) ne saisissent ni ne modifient d'échantillon, ni sur le web ni
 * par l'API mobile. Le rôle porte le préfixe ROLE_ en session web et pas dans
 * le jeton de l'API : les deux formes sont vérifiées.
 */
class SampleWriteRefusedTest {

	private final GuardEvaluator guards = new GuardEvaluator();

	private static List<Method> webWrites() {
		return GuardEvaluator.writes(List.of(SampleController.class), Set.of());
	}

	private static List<Method> apiWrites() {
		return GuardEvaluator.writes(List.of(SampleSyncController.class, TrackingController.class), Set.of());
	}

	@Test
	void tousLesPointsDeSaisieSontTrouves() {
		// Pas de création web : la saisie des échantillons passe par le mobile.
		assertEquals(2, webWrites().size(), "web : modification, suppression");
		assertEquals(14, apiWrites().size(), "API : synchronisation mobile et API historique");
	}

	@ParameterizedTest
	@ValueSource(strings = { "SUPERVISEUR_REGION", "SUPERVISEUR_DISTRICT", "MANAGER" })
	void lesProfilsDeConsultationNeSaisissentPas(String role) {
		for (Method m : webWrites()) {
			assertFalse(guards.allows(m, "ROLE_" + role), role + " web : " + GuardEvaluator.name(m));
		}
		for (Method m : apiWrites()) {
			assertFalse(guards.allows(m, role), role + " API : " + GuardEvaluator.name(m));
			assertFalse(guards.allows(m, "ROLE_" + role), role + " session : " + GuardEvaluator.name(m));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "CONVOYEUR", "BIOLOGISTE", "USER", "ADMIN" })
	void lesProfilsDeTerrainSaisissentDepuisLeMobile(String role) {
		// USER reste autorisé : beaucoup de convoyeurs ont role=USER et leur profil dans user_type.
		for (Method m : apiWrites()) {
			assertTrue(guards.allows(m, role), role + " API : " + GuardEvaluator.name(m));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "ADMIN", "SUPER_ADMIN" })
	void laSaisieWebEstReserveeAuxAdministrateurs(String role) {
		for (Method m : webWrites()) {
			assertTrue(guards.allows(m, "ROLE_" + role), role + " web : " + GuardEvaluator.name(m));
		}
		for (String other : List.of("USER", "BIOLOGISTE", "CONVOYEUR")) {
			for (Method m : webWrites()) {
				assertFalse(guards.allows(m, "ROLE_" + other), other + " web : " + GuardEvaluator.name(m));
			}
		}
	}
}
