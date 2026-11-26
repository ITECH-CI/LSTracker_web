package org.itech.labSampleTracker.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.itech.labSampleTracker.entities.AppUser;
import org.itech.labSampleTracker.service.AppUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Échecs de connexion consécutifs, par identifiant saisi (web et API mobile).
 *
 * <ul>
 * <li>À partir du {@value #WARN_FROM}ᵉ échec, le message indique le nombre de
 * tentatives restantes ; au {@value #MAX_ATTEMPTS}ᵉ, le compte est annoncé
 * bloqué ({@link #failureMessage(String)}).</li>
 * <li>Pour ne pas révéler quels identifiants existent, le compteur et les
 * messages valent pour tout identifiant saisi, qu'il existe ou non : un
 * identifiant inexistant est annoncé bloqué après {@value #MAX_ATTEMPTS}
 * essais, comme un vrai compte. Seul un compte réel est verrouillé en base
 * (is_locked).</li>
 * <li>Sous le seuil, le compteur s'efface après {@value #COUNTER_TTL_MINUTES}
 * minutes sans échec. Un identifiant bloqué le reste jusqu'à une connexion
 * réussie ou au déverrouillage du compte par un administrateur
 * ({@link #reset(String)}).</li>
 * </ul>
 *
 * Compteur en mémoire, par instance (pas de migration) ; au plus
 * {@value #MAX_TRACKED} identifiants suivis, pour qu'un flot d'identifiants
 * inventés ne remplisse pas la mémoire.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptListener {

	private static final Logger log = LoggerFactory.getLogger(LoginAttemptListener.class);

	static final int MAX_ATTEMPTS = 5;
	static final int WARN_FROM = 3;
	private static final long COUNTER_TTL_MINUTES = 30;
	static final int MAX_TRACKED = 10_000;

	static final String INVALID = "Identifiant ou mot de passe incorrect.";
	static final String BLOCKED = "Compte bloqué après " + MAX_ATTEMPTS
			+ " tentatives. Contactez l'administration centrale.";

	private final AppUserService appUserService;

	private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

	@EventListener
	public void onFailure(AbstractAuthenticationFailureEvent event) {
		String principal = principalName(event.getAuthentication().getName());
		if (principal == null) return;
		if (!attempts.containsKey(principal) && attempts.size() >= MAX_TRACKED) {
			attempts.values().removeIf(Attempts::isExpired);
			if (attempts.size() >= MAX_TRACKED) {
				log.warn("Too many identifiers tracked ({}), failure for '{}' not counted", MAX_TRACKED, principal);
				return;
			}
		}

		Attempts a = attempts.compute(principal, (k, current) -> {
			if (current == null || current.isExpired()) {
				return new Attempts();
			}
			current.touch();
			current.counter.incrementAndGet();
			return current;
		});

		int count = a.counter.get();
		log.info("Login failure for '{}' ({}/{} consecutive attempts in window)",
				principal, count, MAX_ATTEMPTS);

		if (count >= MAX_ATTEMPTS) {
			lockUserIfPossible(principal);
		}
	}

	@EventListener
	public void onSuccess(AuthenticationSuccessEvent event) {
		String principal = principalName(extractUsername(event));
		if (principal != null) {
			Attempts removed = attempts.remove(principal);
			if (removed != null) {
				log.debug("Cleared {} failed-attempt counter for '{}'", removed.counter.get(), principal);
			}
		}
	}

	/** Message à afficher après un échec de connexion de cet identifiant. */
	public String failureMessage(String username) {
		String principal = principalName(username);
		Attempts a = principal == null ? null : attempts.get(principal);
		return messageFor(a == null || a.isExpired() ? 0 : a.counter.get());
	}

	static String messageFor(int consecutiveFailures) {
		if (consecutiveFailures >= MAX_ATTEMPTS) {
			return BLOCKED;
		}
		if (consecutiveFailures >= WARN_FROM) {
			return "Identifiant ou mot de passe incorrect. Il vous reste " + (MAX_ATTEMPTS - consecutiveFailures)
					+ " tentative(s) avant le blocage du compte.";
		}
		return INVALID;
	}

	/** Compte déverrouillé par un administrateur : le compteur repart de zéro. */
	public void reset(String username) {
		String principal = principalName(username);
		if (principal != null && attempts.remove(principal) != null) {
			log.info("Failed-attempt counter cleared for '{}' (account unlocked)", principal);
		}
	}

	private void lockUserIfPossible(String username) {
		try {
			AppUser user = appUserService.findUserByLogin(username);
			if (user == null) {
				// Identifiant inexistant : rien à verrouiller en base. Il reste
				// annoncé bloqué (compteur), comme un vrai compte.
				log.warn("Cannot lock unknown user '{}' (reached {} attempts)", username, MAX_ATTEMPTS);
				return;
			}
			if (Boolean.TRUE.equals(user.getIsLocked())) {
				return; // already locked
			}
			user.setIsLocked(true);
			user.setLastUpdatedAt(new java.util.Date());
			appUserService.update(user, false);
			log.warn("Account '{}' locked after {} failed login attempts", username, MAX_ATTEMPTS);
		} catch (Exception ex) {
			log.error("Failed to lock user '{}' after threshold: {}", username, ex.getMessage(), ex);
		}
	}

	private static String principalName(String raw) {
		return (raw == null || raw.isBlank()) ? null : raw.trim().toLowerCase();
	}

	private static String extractUsername(AuthenticationSuccessEvent event) {
		Object p = event.getAuthentication().getPrincipal();
		if (p instanceof UserDetails ud) return ud.getUsername();
		if (p instanceof String s) return s;
		return event.getAuthentication().getName();
	}

	/** Per-username failure record with sliding TTL; never expires once blocked. */
	private static class Attempts {
		final AtomicInteger counter = new AtomicInteger(1);
		volatile Instant lastTouched = Instant.now();

		void touch() { lastTouched = Instant.now(); }

		boolean isExpired() {
			return counter.get() < MAX_ATTEMPTS && Duration.between(lastTouched, Instant.now())
					.compareTo(Duration.ofMinutes(COUNTER_TTL_MINUTES)) > 0;
		}
	}
}
