package org.itech.labSampleTracker.security;

import java.util.List;

import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.service.AppUserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * Mot de passe réinitialisé par un administrateur (app_user.password_reset) :
 * le compte se connecte avec le mot de passe temporaire, puis chaque page le
 * renvoie vers le changement de mot de passe tant qu'il n'en a pas choisi un
 * nouveau. L'indicateur est lu une fois par session web ; la réinitialisation
 * ferme les sessions ouvertes, et le changement de mot de passe le lève.
 * L'API mobile, sans session, n'est pas concernée.
 */
@Component
@RequiredArgsConstructor
public class PasswordChangeInterceptor implements HandlerInterceptor {

	public static final String SESSION_FLAG = "LST_PASSWORD_CHANGE_REQUIRED";
	public static final String CHANGE_PATH = "/appuser/profile/password";

	/** Pages accessibles avant le changement : le formulaire lui-même, la sortie, les erreurs. */
	static final List<String> ALLOWED = List.of(CHANGE_PATH, "/logout", "/login", "/error", "/403");

	private final AppUserService appUserService;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		HttpSession session = request.getSession(false);
		if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
				|| session == null) {
			return true;
		}
		Boolean required = (Boolean) session.getAttribute(SESSION_FLAG);
		if (required == null) {
			AppUser user = appUserService.findUserByLogin(auth.getName());
			required = user != null && Boolean.TRUE.equals(user.getPasswordReset());
			session.setAttribute(SESSION_FLAG, required);
		}
		String path = request.getRequestURI().substring(request.getContextPath().length());
		if (mustChangeFirst(required, path)) {
			response.sendRedirect(request.getContextPath() + CHANGE_PATH);
			return false;
		}
		return true;
	}

	static boolean mustChangeFirst(boolean required, String path) {
		return required && ALLOWED.stream().noneMatch(p -> path.equals(p) || path.startsWith(p + "/"));
	}
}
