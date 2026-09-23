package org.itech.labSampleTracker.service.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.itech.labSampleTracker.dao.UserScopeRepository;
import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.service.AppUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Accès à la fiche d'un échantillon selon le périmètre (cahier V.3). */
class UserScopeSampleAccessTest {

	private final UserScopeRepository repo = mock(UserScopeRepository.class);
	private final AppUserService users = mock(AppUserService.class);
	private final UserScopeService service = new UserScopeService(repo, users);

	private void connect(String login, String role, int id) {
		AppUser u = new AppUser();
		u.setId(id);
		u.setLogin(login);
		u.setRole(role);
		when(users.findUserByLogin(login)).thenReturn(u);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, "x",
				List.of(new SimpleGrantedAuthority("ROLE_" + role))));
	}

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void unAdministrateurAccedeATout() {
		connect("admin", "ADMIN", 1);
		assertTrue(service.canAccessSample(999, 42));
	}

	@Test
	void unUtilisateurRestreintNAccedeQuASonPerimetre() {
		connect("conv", "USER", 36);
		when(repo.findAccessibleSiteIds(36)).thenReturn(List.of(101, 102));
		when(repo.findAccessibleLabIds(36)).thenReturn(List.of(6));
		when(repo.findAccessibleRegionIds(36)).thenReturn(List.of(28));
		when(repo.findAccessibleDistrictIds(36)).thenReturn(List.of(5));
		when(repo.findAccessibleCircuitIds(36)).thenReturn(List.of(1));

		assertTrue(service.canAccessSample(101), "site de son périmètre");
		assertTrue(service.canAccessSample(999, null, 6, null), "déposé dans l'un de ses labos");
		assertFalse(service.canAccessSample(999, 2, null, 3), "ni site ni labo du périmètre");
		assertFalse(service.canAccessSample(null), "site inconnu");
	}
}
