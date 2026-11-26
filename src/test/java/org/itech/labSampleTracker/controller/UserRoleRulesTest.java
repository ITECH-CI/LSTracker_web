package org.itech.labSampleTracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.itech.labSampleTracker.dto.UserDTO;
import org.itech.labSampleTracker.enums.UserLevel;
import org.itech.labSampleTracker.enums.UserRole;
import org.itech.labSampleTracker.enums.UserType;
import org.junit.jupiter.api.Test;

/** Profils des comptes : libellés, règles du formulaire pour les superviseurs. */
class UserRoleRulesTest {

	@Test
	void chaqueProfilAUnLibelle() {
		Map<String, String> labels = UserRole.labels();
		assertEquals("Administrateur national", labels.get("ADMIN"));
		assertEquals("Administrateur système", labels.get("SUPER_ADMIN"));
		assertEquals("Pilotage national (consultation)", labels.get("MANAGER"));
		assertEquals("Superviseur régional", labels.get("SUPERVISEUR_REGION"));
		assertEquals("Superviseur de district", labels.get("SUPERVISEUR_DISTRICT"));
		assertEquals("Technicien ou superviseur de laboratoire", labels.get("BIOLOGISTE"));
		assertEquals("Convoyeur", labels.get("CONVOYEUR"));
		assertEquals("Utilisateur (consultation)", labels.get("USER"));
		assertEquals("INCONNU", UserRole.labelOf("INCONNU"));
		// Aucun tiret cadratin ou demi-cadratin dans les textes affichés.
		labels.values().forEach(l -> assertFalse(l.contains("—") || l.contains("–"), l));
	}

	@Test
	void leNiveauSuitLeRoleDeSuperviseur() {
		assertEquals(UserLevel.REGION_LEVEL, UserRole.SUPERVISEUR_REGION.getRequiredLevel());
		assertEquals(UserLevel.DISTRICT_LEVEL, UserRole.SUPERVISEUR_DISTRICT.getRequiredLevel());
		Arrays.stream(UserRole.values()).filter(r -> !r.isZoneSupervisor())
				.forEach(r -> assertNull(r.getRequiredLevel(), r.name()));
		assertTrue(UserRole.isZoneSupervisor("superviseur_region"));
		assertFalse(UserRole.isZoneSupervisor(null));
	}

	private static UserDTO form(UserRole role, List<Integer> regions, List<Integer> districts) {
		UserDTO dto = new UserDTO();
		dto.setRole(role);
		dto.setUserLevel(UserLevel.CENTRAL_LEVEL);
		dto.setUserType(UserType.ADMIN);
		dto.setRegions(regions);
		dto.setDistricts(districts);
		return dto;
	}

	@Test
	void unSuperviseurRegionalSansRegionEstRefuse() {
		assertNotNull(AppUserController.applySupervisorRules(form(UserRole.SUPERVISEUR_REGION, null, List.of(3))));
		assertNotNull(AppUserController.applySupervisorRules(form(UserRole.SUPERVISEUR_REGION, List.of(), null)));
	}

	@Test
	void unSuperviseurDeDistrictSansDistrictEstRefuse() {
		assertNotNull(AppUserController.applySupervisorRules(form(UserRole.SUPERVISEUR_DISTRICT, List.of(1), null)));
	}

	@Test
	void leSuperviseurRecoitLeNiveauEtLeTypeDeSonRole() {
		UserDTO dto = form(UserRole.SUPERVISEUR_REGION, List.of(1, 1, 2), null);
		assertNull(AppUserController.applySupervisorRules(dto));
		assertEquals(UserLevel.REGION_LEVEL, dto.getUserLevel(), "jamais central : il serait national");
		assertEquals(UserType.OTHER, dto.getUserType());
		assertEquals(List.of(1, 2), AppUserController.supervisorZones(dto));

		UserDTO district = form(UserRole.SUPERVISEUR_DISTRICT, List.of(1), List.of(10));
		assertNull(AppUserController.applySupervisorRules(district));
		assertEquals(UserLevel.DISTRICT_LEVEL, district.getUserLevel());
		assertEquals(List.of(10), AppUserController.supervisorZones(district), "ses districts, pas les régions");
	}

	@Test
	void lesAutresRolesNeSontPasTouches() {
		UserDTO dto = form(UserRole.USER, null, null);
		assertNull(AppUserController.applySupervisorRules(dto));
		assertEquals(UserLevel.CENTRAL_LEVEL, dto.getUserLevel());
		assertEquals(UserType.ADMIN, dto.getUserType());
	}

	@Test
	void seulUnAdministrateurSystemeAttribueOuRetireCeRole() {
		assertNotNull(AppUserController.superAdminRuleError(false, null, UserRole.SUPER_ADMIN), "attribution");
		assertNotNull(AppUserController.superAdminRuleError(false, "SUPER_ADMIN", UserRole.ADMIN), "retrait");
		assertNull(AppUserController.superAdminRuleError(false, "SUPER_ADMIN", UserRole.SUPER_ADMIN), "inchangé");
		assertNull(AppUserController.superAdminRuleError(false, "USER", UserRole.SUPERVISEUR_REGION));
		assertNull(AppUserController.superAdminRuleError(true, null, UserRole.SUPER_ADMIN));
	}

	@Test
	void leRoleAdministrateurSystemeNEstProposeQuAuxAdministrateursSysteme() {
		assertFalse(UserRole.assignable(false, "USER").contains(UserRole.SUPER_ADMIN));
		assertTrue(UserRole.assignable(false, "SUPER_ADMIN").contains(UserRole.SUPER_ADMIN));
		assertTrue(UserRole.assignable(true, null).contains(UserRole.SUPER_ADMIN));
		assertTrue(UserRole.assignable(false, null).contains(UserRole.SUPERVISEUR_REGION));
	}
}
