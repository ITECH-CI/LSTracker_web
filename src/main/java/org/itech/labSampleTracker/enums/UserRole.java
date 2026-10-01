package org.itech.labSampleTracker.enums;

import org.apache.commons.lang3.StringUtils;

public enum UserRole {
	// MANAGER : pilotage national — accès aux données de tout le pays, sans les
	// écrans d'administration (cf. UserScopeService.isNational).
	CONVOYEUR("CONVOYEUR"), BIOLOGISTE("BIOLOGISTE"), USER("USER"), MANAGER("MANAGER"), ADMIN("ADMIN"),
	APP_USER("APP_USER");

	private String role;

	UserRole(String role) {
		this.role = role;
	}

	public String getRole() {
		return role;
	}

	public static UserRole get(final String role) {
		for (final UserRole e : UserRole.values()) {
			if (StringUtils.equals(e.getRole(), role)) {
				return e;
			}
		}
		return null;
	}

}
