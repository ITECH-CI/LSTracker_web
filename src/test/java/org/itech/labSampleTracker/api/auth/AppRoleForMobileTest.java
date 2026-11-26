package org.itech.labSampleTracker.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.itech.labSampleTracker.entities.AppUser;
import org.junit.jupiter.api.Test;

/**
 * Rôle renvoyé au mobile par /api_v2/auth/login. Le mobile ne connaît que
 * ADMIN, CONVOYEUR, BIOLOGISTE et USER ; tout autre rôle affiche « Rôle non
 * reconnu ». Les profils de consultation ouvrent donc l'écran USER.
 */
class AppRoleForMobileTest {

	private static AppUser user(String role, String type) {
		AppUser u = new AppUser();
		u.setRole(role);
		u.setUserType(type);
		return u;
	}

	@Test
	void lesProfilsDeConsultationOuvrentLEcranDeConsultation() {
		assertEquals("USER", AuthApiController.appRole(user("SUPERVISEUR_REGION", "AUTRE")));
		assertEquals("USER", AuthApiController.appRole(user("SUPERVISEUR_DISTRICT", "AUTRE")));
		assertEquals("USER", AuthApiController.appRole(user("MANAGER", "AUTRE")));
		// Même si le type a été mal saisi : aucune action de collecte ni de dépôt.
		assertEquals("USER", AuthApiController.appRole(user("SUPERVISEUR_REGION", "ADMIN")));
		assertEquals("USER", AuthApiController.appRole(user("SUPERVISEUR_DISTRICT", "CONVOYEUR")));
	}

	@Test
	void lesAutresProfilsSontInchanges() {
		assertEquals("CONVOYEUR", AuthApiController.appRole(user("USER", "CONVOYEUR")));
		assertEquals("ADMIN", AuthApiController.appRole(user("ADMIN", "ADMIN")));
		assertEquals("BIOLOGISTE", AuthApiController.appRole(user("BIOLOGISTE", "AUTRE")));
		assertEquals("USER", AuthApiController.appRole(user("USER", "AUTRE")));
	}
}
