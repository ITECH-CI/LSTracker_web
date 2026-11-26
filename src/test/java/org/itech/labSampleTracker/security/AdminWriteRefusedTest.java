package org.itech.labSampleTracker.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.itech.labSampleTracker.controller.ActivityLogController;
import org.itech.labSampleTracker.controller.AppRoleController;
import org.itech.labSampleTracker.controller.AppUserController;
import org.itech.labSampleTracker.controller.AppUserHasRoleController;
import org.itech.labSampleTracker.controller.CircuitController;
import org.itech.labSampleTracker.controller.CircuitSiteController;
import org.itech.labSampleTracker.controller.ConnectionAdminController;
import org.itech.labSampleTracker.controller.ConsistencyController;
import org.itech.labSampleTracker.controller.DistrictController;
import org.itech.labSampleTracker.controller.HelpDocumentController;
import org.itech.labSampleTracker.controller.LabController;
import org.itech.labSampleTracker.controller.MeshStateController;
import org.itech.labSampleTracker.controller.RegionController;
import org.itech.labSampleTracker.controller.SampleRejectionTypeController;
import org.itech.labSampleTracker.controller.SampleStatusController;
import org.itech.labSampleTracker.controller.SampleTypeController;
import org.itech.labSampleTracker.controller.SettingController;
import org.itech.labSampleTracker.controller.SiteController;
import org.itech.labSampleTracker.integration.oedatarepo.OeDataRepoAdminController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Administration centralisée : toute écriture dans l'administration (comptes,
 * maillage, référentiels, réglages) est refusée côté serveur aux profils de
 * consultation, quel que soit le menu affiché.
 */
class AdminWriteRefusedTest {

	private static final List<Class<?>> ADMIN_CONTROLLERS = List.of(AppUserController.class,
			AppRoleController.class, AppUserHasRoleController.class, RegionController.class,
			DistrictController.class, SiteController.class, LabController.class, CircuitController.class,
			CircuitSiteController.class, MeshStateController.class, SampleTypeController.class,
			SampleRejectionTypeController.class, SampleStatusController.class, HelpDocumentController.class,
			SettingController.class, ConnectionAdminController.class, ActivityLogController.class,
			ConsistencyController.class, OeDataRepoAdminController.class);

	/** Libre-service de son propre compte (profil, mot de passe) : ouvert à tout utilisateur connecté. */
	private static final Set<String> SELF_SERVICE = Set.of("AppUserController.editProfile",
			"AppUserController.changePassword");

	private final GuardEvaluator guards = new GuardEvaluator();

	private static List<Method> adminWrites() {
		return GuardEvaluator.writes(ADMIN_CONTROLLERS, SELF_SERVICE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "SUPERVISEUR_REGION", "SUPERVISEUR_DISTRICT", "MANAGER", "USER" })
	void lesProfilsDeConsultationNEcriventPasDansLAdministration(String role) {
		List<Method> writes = adminWrites();
		assertTrue(writes.size() > 20, "points d'écriture trouvés : " + writes.size());
		for (Method m : writes) {
			assertFalse(guards.allows(m, "ROLE_" + role), role + " ne doit pas pouvoir appeler " + GuardEvaluator.name(m));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "ADMIN", "SUPER_ADMIN" })
	void lesAdministrateursEcriventDansLAdministration(String role) {
		for (Method m : adminWrites()) {
			assertTrue(guards.allows(m, "ROLE_" + role), role + " doit pouvoir appeler " + GuardEvaluator.name(m));
		}
	}

	@org.junit.jupiter.api.Test
	void aucunCompteNePeutEtreSupprime() {
		// Traçabilité : un compte se désactive, il ne se supprime pas.
		for (Method m : AppUserController.class.getDeclaredMethods()) {
			assertFalse(m.getName().toLowerCase().contains("delete"), GuardEvaluator.name(m));
		}
	}
}
