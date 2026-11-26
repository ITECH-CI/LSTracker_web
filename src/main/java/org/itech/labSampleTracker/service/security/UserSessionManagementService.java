package org.itech.labSampleTracker.service.security;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Forces logout of all active web sessions for a given username.
 *
 * Used when an administrator deactivates a user, locks an account, changes
 * a user's role or resets their password — so the user cannot continue
 * acting on the old credentials/permissions.
 *
 * Note: only invalidates web (servlet) sessions. JWT tokens for the mobile
 * API are stateless and remain valid until expiration; revoking them is a
 * separate concern (refresh-token revocation).
 */
@Service
@RequiredArgsConstructor
public class UserSessionManagementService {

	private static final Logger log = LoggerFactory.getLogger(UserSessionManagementService.class);

	private final SessionRegistry sessionRegistry;

	/**
	 * Expire every active session belonging to {@code username}.
	 * Returns the number of sessions revoked.
	 */
	public int revokeSessionsFor(String username) {
		return revokeSessionsFor(username, null);
	}

	/**
	 * Comme {@link #revokeSessionsFor(String)}, sauf la session {@code keepSessionId}
	 * (changement de son propre mot de passe : les autres appareils sont
	 * déconnectés, pas la session en cours).
	 */
	public int revokeSessionsFor(String username, String keepSessionId) {
		if (username == null || username.isBlank()) {
			return 0;
		}
		int revoked = 0;
		for (Object principal : sessionRegistry.getAllPrincipals()) {
			String principalName = principalName(principal);
			if (principalName == null || !principalName.equals(username)) {
				continue;
			}
			List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
			for (SessionInformation s : sessions) {
				if (!s.isExpired() && !s.getSessionId().equals(keepSessionId)) {
					s.expireNow();
					revoked++;
				}
			}
		}
		if (revoked > 0) {
			log.info("Revoked {} active session(s) for user '{}'", revoked, username);
		} else {
			log.debug("No active session to revoke for user '{}'", username);
		}
		return revoked;
	}

	private String principalName(Object principal) {
		if (principal instanceof User springUser) {
			return springUser.getUsername();
		}
		if (principal instanceof String s) {
			return s;
		}
		return principal == null ? null : principal.toString();
	}
}
