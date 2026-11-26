package org.itech.labSampleTracker.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Après une réinitialisation, seul le changement de mot de passe reste accessible. */
class PasswordChangeInterceptorTest {

	@Test
	void unMotDePasseReinitialiseImposeLeChangement() {
		assertTrue(PasswordChangeInterceptor.mustChangeFirst(true, "/"));
		assertTrue(PasswordChangeInterceptor.mustChangeFirst(true, "/dashboard"));
		assertTrue(PasswordChangeInterceptor.mustChangeFirst(true, "/sample/data"));
		assertTrue(PasswordChangeInterceptor.mustChangeFirst(true, "/appuser/profile/passwordx"));
	}

	@Test
	void leFormulaireLaDeconnexionEtLesErreursRestentAccessibles() {
		assertFalse(PasswordChangeInterceptor.mustChangeFirst(true, "/appuser/profile/password"));
		assertFalse(PasswordChangeInterceptor.mustChangeFirst(true, "/logout"));
		assertFalse(PasswordChangeInterceptor.mustChangeFirst(true, "/error"));
	}

	@Test
	void sansReinitialisationRienNEstImpose() {
		assertFalse(PasswordChangeInterceptor.mustChangeFirst(false, "/dashboard"));
	}
}
