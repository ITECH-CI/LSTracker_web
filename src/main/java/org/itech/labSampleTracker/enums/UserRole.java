package org.itech.labSampleTracker.enums;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * Rôles des comptes. Le code ({@link #getRole()}) est la valeur stockée dans
 * {@code app_user.role} ; le libellé est celui des formulaires et des listes.
 *
 * <ul>
 * <li>ADMIN, SUPER_ADMIN : seuls rôles qui écrivent dans l'administration
 * (comptes, maillage, référentiels), cf. {@code AdminWrite}.</li>
 * <li>MANAGER : pilotage national, consultation de tout le pays et des écrans
 * d'administration (cf. {@code UserScopeService.isNational}).</li>
 * <li>SUPERVISEUR_REGION, SUPERVISEUR_DISTRICT : consultation et export
 * limités aux régions ou districts affectés, aucun écran d'administration.</li>
 * </ul>
 */
public enum UserRole {
	ADMIN("ADMIN", "Administrateur national"),
	SUPER_ADMIN("SUPER_ADMIN", "Administrateur système"),
	MANAGER("MANAGER", "Pilotage national (consultation)"),
	SUPERVISEUR_REGION("SUPERVISEUR_REGION", "Superviseur régional"),
	SUPERVISEUR_DISTRICT("SUPERVISEUR_DISTRICT", "Superviseur de district"),
	BIOLOGISTE("BIOLOGISTE", "Technicien ou superviseur de laboratoire"),
	CONVOYEUR("CONVOYEUR", "Convoyeur"),
	USER("USER", "Utilisateur (consultation)"),
	APP_USER("APP_USER", "Compte applicatif");

	private final String role;
	private final String label;

	UserRole(String role, String label) {
		this.role = role;
		this.label = label;
	}

	public String getRole() {
		return role;
	}

	public String getLabel() {
		return label;
	}

	/**
	 * Niveau d'organisation imposé par le rôle : région pour le superviseur
	 * régional, district pour le superviseur de district, aucun sinon.
	 */
	public UserLevel getRequiredLevel() {
		switch (this) {
		case SUPERVISEUR_REGION:
			return UserLevel.REGION_LEVEL;
		case SUPERVISEUR_DISTRICT:
			return UserLevel.DISTRICT_LEVEL;
		default:
			return null;
		}
	}

	/** Superviseur de zone (région ou district) : périmètre limité à ses affectations. */
	public boolean isZoneSupervisor() {
		return getRequiredLevel() != null;
	}

	public static boolean isZoneSupervisor(final String role) {
		UserRole r = get(role == null ? null : role.toUpperCase());
		return r != null && r.isZoneSupervisor();
	}

	public static UserRole get(final String role) {
		for (final UserRole e : UserRole.values()) {
			if (StringUtils.equals(e.getRole(), role)) {
				return e;
			}
		}
		return null;
	}

	/** Libellé d'un code de rôle ; le code lui-même s'il est inconnu. */
	public static String labelOf(final String role) {
		UserRole r = get(role);
		return r != null ? r.getLabel() : role;
	}

	/** Code de rôle vers libellé, dans l'ordre d'affichage. */
	public static Map<String, String> labels() {
		Map<String, String> out = new LinkedHashMap<>();
		for (UserRole r : values()) {
			out.put(r.getRole(), r.getLabel());
		}
		return out;
	}

	/**
	 * Rôles proposés dans le formulaire. Le rôle Administrateur système n'est
	 * proposé qu'à un administrateur système, ou sur un compte qui l'a déjà
	 * (pour qu'il reste sélectionné à la modification).
	 */
	public static List<UserRole> assignable(boolean currentIsSuperAdmin, String editedRole) {
		boolean keepSuperAdmin = currentIsSuperAdmin || SUPER_ADMIN.getRole().equals(editedRole);
		return Arrays.stream(values())
				.filter(r -> r != SUPER_ADMIN || keepSuperAdmin)
				.toList();
	}

}
