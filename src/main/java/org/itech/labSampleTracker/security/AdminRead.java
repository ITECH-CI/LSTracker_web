package org.itech.labSampleTracker.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Consultation de l'administration : administrateurs et MANAGER (pilotage
 * national, en lecture seule). Toute écriture exige {@link AdminWrite}.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER')")
public @interface AdminRead {
}
