package org.itech.labSampleTracker.service.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.itech.labSampleTracker.entities.AppUser;
import org.junit.jupiter.api.Test;

/** Niveau central = accès national, sans restriction de zone, quel que soit le rôle. */
class NationalAccessTest {

	private static AppUser user(String role, String level) {
		AppUser u = new AppUser();
		u.setRole(role);
		u.setUserLevel(level);
		return u;
	}

	@Test
	void centralLevelSeesTheWholeCountryEvenWithRoleUser() {
		assertTrue(UserScopeService.isNational(user("USER", "CENTRAL")));
		assertTrue(UserScopeService.isNational(user("USER", "central")));
	}

	@Test
	void globalRolesSeeTheWholeCountry() {
		assertTrue(UserScopeService.isNational(user("ADMIN", null)));
		assertTrue(UserScopeService.isNational(user("SUPER_ADMIN", "DISTRICT")));
		assertTrue(UserScopeService.isNational(user("MANAGER", "REGION")));
		assertTrue(UserScopeService.isNational(user("SUPPORT", "DISTRICT")));
	}

	@Test
	void otherLevelsStayWithinTheirZone() {
		assertFalse(UserScopeService.isNational(user("USER", "REGION")));
		assertFalse(UserScopeService.isNational(user("USER", "DISTRICT")));
		assertFalse(UserScopeService.isNational(user("CONVOYEUR", "CIRCUIT")));
		assertFalse(UserScopeService.isNational(user("USER", null)));
		assertFalse(UserScopeService.isNational(null));
	}

	@Test
	void managerCanBeAssignedFromTheUserFormAndIsNational() {
		org.itech.labSampleTracker.enums.UserRole manager = org.itech.labSampleTracker.enums.UserRole.get("MANAGER");
		org.junit.jupiter.api.Assertions.assertNotNull(manager);
		assertTrue(UserScopeService.isNational(user(manager.getRole(), "REGION")));
	}
}
