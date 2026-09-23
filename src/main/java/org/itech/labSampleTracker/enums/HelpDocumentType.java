package org.itech.labSampleTracker.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * Manuels d'aide téléversables (observation 3.2). {@code fallback} : fichier
 * livré avec l'application, servi tant qu'aucune version n'a été téléversée.
 */
public enum HelpDocumentType {
	USER_MANUAL("manuel-utilisateur", "Manuel utilisateur", "/pdf/Manuel_user_Septembre_2025_vf.pdf"),
	PROCEDURE_MANUAL("manuel-procedure", "Manuel de procédure", null);

	private final String slug;
	private final String label;
	private final String fallback;

	HelpDocumentType(String slug, String label, String fallback) {
		this.slug = slug;
		this.label = label;
		this.fallback = fallback;
	}

	public String getSlug() {
		return slug;
	}

	public String getLabel() {
		return label;
	}

	public String getFallback() {
		return fallback;
	}

	public static Optional<HelpDocumentType> fromSlug(String slug) {
		return Arrays.stream(values()).filter(t -> t.slug.equals(slug)).findFirst();
	}
}
