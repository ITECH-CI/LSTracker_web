package org.itech.labSampleTracker.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureCredentialsExpiredEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureExpiredEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Alimente le journal des connexions à partir des événements de Spring
 * Security.
 *
 * <p>Réussites : seules les connexions explicites sont journalisées
 * (formulaire web {@code /login}, application mobile
 * {@code /api_v2/auth/login}). L'authentification Basic, rejouée à chaque
 * requête, émet aussi un événement de réussite : elle est ignorée pour ne pas
 * inonder le journal. Échecs : tous journalisés, quel que soit le point
 * d'entrée (utile pour repérer les attaques par dictionnaire).
 */
@Component
public class ConnectionLogListener {

	private static final String WEB_LOGIN = "/login";
	private static final String MOBILE_LOGIN = "/api_v2/auth/login";

	private final ConnectionLogService connectionLog;

	public ConnectionLogListener(ConnectionLogService connectionLog) {
		this.connectionLog = connectionLog;
	}

	@EventListener
	public void onSuccess(AuthenticationSuccessEvent event) {
		HttpServletRequest req = currentRequest();
		if (req == null) {
			return;
		}
		String path = req.getServletPath();
		if (!WEB_LOGIN.equals(path) && !MOBILE_LOGIN.equals(path)) {
			return;
		}
		record(event.getAuthentication().getName(), req, "SUCCESS");
	}

	@EventListener
	public void onFailure(AbstractAuthenticationFailureEvent event) {
		HttpServletRequest req = currentRequest();
		if (req != null) {
			record(event.getAuthentication().getName(), req, outcomeOf(event));
		}
	}

	@EventListener
	public void onLogout(LogoutSuccessEvent event) {
		connectionLog.forget(event.getAuthentication().getName(), ConnectionLogService.WEB);
	}

	private void record(String login, HttpServletRequest req, String outcome) {
		connectionLog.recordAttempt(login, ConnectionLogService.channelOf(req.getServletPath()), outcome,
				req.getRemoteAddr(), req.getHeader("User-Agent"));
	}

	static String outcomeOf(AbstractAuthenticationFailureEvent event) {
		if (event instanceof AuthenticationFailureBadCredentialsEvent) return "BAD_CREDENTIALS";
		if (event instanceof AuthenticationFailureLockedEvent) return "LOCKED";
		if (event instanceof AuthenticationFailureDisabledEvent) return "DISABLED";
		if (event instanceof AuthenticationFailureExpiredEvent) return "EXPIRED";
		if (event instanceof AuthenticationFailureCredentialsExpiredEvent) return "PASSWORD_EXPIRED";
		return "FAILURE";
	}

	private static HttpServletRequest currentRequest() {
		return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra
				? sra.getRequest() : null;
	}
}
