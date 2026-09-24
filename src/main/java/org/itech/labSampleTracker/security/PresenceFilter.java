package org.itech.labSampleTracker.security;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Présence en ligne et visites : toute requête authentifiée signale son
 * utilisateur à {@link ConnectionLogService#touch}. Placé après la chaîne de
 * sécurité (ordre le plus bas), pour que l'authentification — session web ou
 * jeton mobile — soit déjà établie. Coût : une écriture en mémoire, et une
 * insertion en base au plus une fois par utilisateur, jour et canal.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class PresenceFilter extends OncePerRequestFilter {

	private final ConnectionLogService connectionLog;

	public PresenceFilter(ConnectionLogService connectionLog) {
		this.connectionLog = connectionLog;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
			connectionLog.touch(auth.getName(), ConnectionLogService.channelOf(request.getServletPath()));
		}
		chain.doFilter(request, response);
	}
}
