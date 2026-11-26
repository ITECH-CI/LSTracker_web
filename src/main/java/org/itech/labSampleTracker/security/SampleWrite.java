package org.itech.labSampleTracker.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Saisie d'échantillons (collecte, dépôt, réception, résultats) par l'API
 * mobile : refusée aux profils de consultation (pilotage national, superviseurs
 * régional et de district). Les autres profils restent soumis à leur
 * périmètre. Le rôle figure sans préfixe dans le jeton de l'API
 * (JwtAuthFilter) et avec le préfixe ROLE_ dans la session web : les deux
 * formes sont refusées.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("isAuthenticated() and !hasAnyAuthority("
		+ "'MANAGER','SUPERVISEUR_REGION','SUPERVISEUR_DISTRICT',"
		+ "'ROLE_MANAGER','ROLE_SUPERVISEUR_REGION','ROLE_SUPERVISEUR_DISTRICT')")
public @interface SampleWrite {
}
