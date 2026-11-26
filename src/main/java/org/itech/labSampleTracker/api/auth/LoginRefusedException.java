package org.itech.labSampleTracker.api.auth;

/**
 * Connexion refusée par /api_v2/auth/login, avec le message à afficher
 * (tentatives restantes, blocage) : cf. LoginAttemptListener.
 */
public class LoginRefusedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LoginRefusedException(String message) {
		super(message);
	}
}
