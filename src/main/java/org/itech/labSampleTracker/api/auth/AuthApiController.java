package org.itech.labSampleTracker.api.auth;

import java.util.List;

import org.itech.labSampleTracker.api.auth.AuthDtos.LoginRequest;
import org.itech.labSampleTracker.api.auth.AuthDtos.LoginResponse;
import org.itech.labSampleTracker.api.auth.AuthDtos.RefreshRequest;
import org.itech.labSampleTracker.api.auth.AuthDtos.RefreshResponse;
import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.enums.UserType;
import org.itech.labSampleTracker.security.JwtService;
import org.itech.labSampleTracker.security.RefreshToken;
import org.itech.labSampleTracker.security.RefreshTokenService;
import org.itech.labSampleTracker.service.AppUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api_v2/auth")
public class AuthApiController {

	@Autowired
	private AuthenticationManager authenticationManager;
	@Autowired
	private JwtService jwtService;
	@Autowired
	private RefreshTokenService refreshTokenService;
	@Autowired
	private AppUserService appUserService;

	@PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
		var auth = authenticationManager
				.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()));
		var username = auth.getName();
		AppUser appUser = appUserService.findUserByLogin(username);
		var role = appUser.getRole();
		Long userId = appUser.getId().longValue();

		String access = jwtService.generateToken(username, role, userId);
		String refresh = refreshTokenService.issue(userId, username, role, clientIp(http));

		return new LoginResponse(access, refresh, appRole(appUser), userId);
	}

	/**
	 * Rôle renvoyé au mobile, qui s'en sert uniquement pour l'affichage (tableau
	 * de bord, menus). Beaucoup de comptes ont role=USER et le vrai profil dans
	 * user_type (ex. CONVOYEUR) : sans cela, un convoyeur tombait sur le tableau
	 * de bord générique, sans action de collecte. Le JWT garde le rôle brut.
	 * Même logique « role OU user_type » que MetaService.isType.
	 */
	private static String appRole(AppUser user) {
		for (UserType type : List.of(UserType.ADMIN, UserType.RIDER, UserType.BIOLOGIST)) {
			String t = type.getType();
			if (t.equalsIgnoreCase(user.getRole()) || t.equalsIgnoreCase(user.getUserType())) {
				return t;
			}
		}
		return user.getRole();
	}

	@PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public RefreshResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
		RefreshToken rt = refreshTokenService.verify(req.refresh_token());
		String access = jwtService.generateToken(rt.getUsername(), rt.getRole(), rt.getUserId());
		String newRefresh = refreshTokenService.rotate(rt, clientIp(http));
		return new RefreshResponse(access, newRefresh);
	}

	@PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public void logout(@Valid @RequestBody RefreshRequest req) {
		refreshTokenService.revoke(req.refresh_token());
	}

	private static String clientIp(HttpServletRequest http) {
		String h = http.getHeader("X-Forwarded-For");
		return (h == null || h.isBlank()) ? http.getRemoteAddr() : h.split(",")[0].trim();
	}
}
