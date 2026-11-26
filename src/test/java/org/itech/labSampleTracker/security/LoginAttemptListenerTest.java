package org.itech.labSampleTracker.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

/** Messages de connexion avant blocage, identiques pour un compte réel et un identifiant inexistant. */
class LoginAttemptListenerTest {

	private final AppUserService users = mock(AppUserService.class);
	private final LoginAttemptListener attempts = new LoginAttemptListener(users);

	private void fail(String login) {
		attempts.onFailure(new AuthenticationFailureBadCredentialsEvent(
				new UsernamePasswordAuthenticationToken(login, "x"), new BadCredentialsException("x")));
	}

	@Test
	void lesMessagesSuiventLeNombreDEchecs() {
		assertEquals("Identifiant ou mot de passe incorrect.", LoginAttemptListener.messageFor(1));
		assertEquals("Identifiant ou mot de passe incorrect.", LoginAttemptListener.messageFor(2));
		assertEquals("Identifiant ou mot de passe incorrect. Il vous reste 2 tentative(s) avant le blocage du compte.",
				LoginAttemptListener.messageFor(3));
		assertEquals("Identifiant ou mot de passe incorrect. Il vous reste 1 tentative(s) avant le blocage du compte.",
				LoginAttemptListener.messageFor(4));
		assertEquals("Compte bloqué après 5 tentatives. Contactez l'administration centrale.",
				LoginAttemptListener.messageFor(5));
		assertEquals(LoginAttemptListener.BLOCKED, LoginAttemptListener.messageFor(9));
	}

	@Test
	void unIdentifiantInexistantEstTraiteCommeUnVraiCompte() {
		AppUser reel = new AppUser();
		reel.setLogin("reel");
		when(users.findUserByLogin("reel")).thenReturn(reel);
		for (int i = 1; i <= 5; i++) {
			fail("inconnu");
			fail("Reel");
		}
		assertEquals(LoginAttemptListener.BLOCKED, attempts.failureMessage("inconnu"));
		assertEquals(LoginAttemptListener.BLOCKED, attempts.failureMessage("REEL"), "casse ignorée");
		// Seul le compte réel est verrouillé en base.
		verify(users).update(any(AppUser.class), anyBoolean());
		assertTrue(reel.getIsLocked());
	}

	@Test
	void uneConnexionReussieOuUnDeverrouillageRemettentLeCompteurAZero() {
		for (int i = 0; i < 4; i++) {
			fail("agent");
		}
		assertTrue(attempts.failureMessage("agent").contains("1 tentative(s)"));
		attempts.onSuccess(new AuthenticationSuccessEvent(new UsernamePasswordAuthenticationToken("agent", "x")));
		assertEquals(LoginAttemptListener.INVALID, attempts.failureMessage("agent"));

		for (int i = 0; i < 5; i++) {
			fail("bloque");
		}
		attempts.reset("bloque");
		assertEquals(LoginAttemptListener.INVALID, attempts.failureMessage("bloque"));
	}

	@Test
	void lesIdentifiantsSuivisSontPlafonnes() {
		for (int i = 0; i < LoginAttemptListener.MAX_TRACKED; i++) {
			fail("invente" + i);
		}
		for (int i = 0; i < 5; i++) {
			fail("au-dela");
		}
		// Au-delà du plafond, l'échec n'est plus compté : message de base, aucun verrouillage tenté.
		assertEquals(LoginAttemptListener.INVALID, attempts.failureMessage("au-dela"));
		verify(users, never()).findUserByLogin("au-dela");
	}
}
