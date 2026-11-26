package org.itech.labSampleTracker.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Création, modification, désactivation, suppression dans l'administration :
 * administrateurs seulement (le MANAGER consulte, cf. {@link AdminRead}).
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public @interface AdminWrite {
}
